import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;
import javax.swing.*;

public class ChatClient {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 1234;
    private static final int BUFFER_SIZE = 4096;

    private static String username;
    private static PrintWriter writer;
    private static BufferedReader reader;
    private static JTextArea chatArea;
    private static JTextField textField;
    private static JComboBox<String> groupDropdown;
    private static final List<String> chatHistory = new ArrayList<>();
    private static final String USERS_FILE = "users.txt"; // File to store registered usernames and passwords

    public static void main(String[] args) {
        showLoginScreen();
    }

    private static Socket socket; // Store the socket connection

    private static void showLoginScreen() {
        JFrame loginFrame = new JFrame("Login/Register");
        loginFrame.setSize(400, 300);
        loginFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        loginFrame.setLayout(new BorderLayout());

        JLabel header = new JLabel("Welcome to the Chat App", SwingConstants.CENTER);
        header.setFont(new Font("Arial", Font.BOLD, 16));
        header.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 10, 10, 10);

        JLabel usernameLabel = new JLabel("Username:");
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(usernameLabel, gbc);

        JTextField usernameField = new JTextField(15);
        gbc.gridx = 1;
        gbc.gridy = 0;
        panel.add(usernameField, gbc);

        JLabel passwordLabel = new JLabel("Password:");
        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(passwordLabel, gbc);

        JPasswordField passwordField = new JPasswordField(15);
        gbc.gridx = 1;
        gbc.gridy = 1;
        panel.add(passwordField, gbc);

        JButton loginButton = new JButton("Login");
        gbc.gridx = 0;
        gbc.gridy = 2;
        panel.add(loginButton, gbc);

        JButton registerButton = new JButton("Register");
        gbc.gridx = 1;
        gbc.gridy = 2;
        panel.add(registerButton, gbc);

        loginButton.addActionListener(e -> {
            username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword()).trim();
            if (!username.isEmpty() && !password.isEmpty()) {
                if (isUserValid(username, password)) {
                    attemptLogin();
                    loginFrame.dispose();
                } else {
                    JOptionPane.showMessageDialog(null, "Invalid username or password.");
                }
            } else {
                JOptionPane.showMessageDialog(null, "Username and password cannot be empty.");
            }
        });

        registerButton.addActionListener(e -> {
            registerUser();
        });

        loginFrame.add(header, BorderLayout.NORTH);
        loginFrame.add(panel, BorderLayout.CENTER);
        loginFrame.setVisible(true);
    }

    private static void registerUser() {
        String newUsername = JOptionPane.showInputDialog("Enter a username to register:");
        if (newUsername != null && !newUsername.trim().isEmpty()) {
            String newPassword = JOptionPane.showInputDialog("Enter a password:");
            if (newPassword != null && !newPassword.trim().isEmpty()) {
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(USERS_FILE, true))) {
                    writer.write(newUsername.trim() + " | " + newPassword.trim());
                    writer.newLine();
                    JOptionPane.showMessageDialog(null, "Registration successful. You can now log in.");
                } catch (IOException e) {
                    JOptionPane.showMessageDialog(null, "Error saving user. Please try again.");
                }
            } else {
                JOptionPane.showMessageDialog(null, "Password cannot be empty.");
            }
        } else {
            JOptionPane.showMessageDialog(null, "Username cannot be empty.");
        }
    }

    private static boolean isUserValid(String username, String password) {
        try (BufferedReader reader = new BufferedReader(new FileReader(USERS_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\|");
                if (parts.length == 2) {
                    String fileUsername = parts[0].trim();
                    String filePassword = parts[1].trim();
                    if (fileUsername.equalsIgnoreCase(username) && filePassword.equals(password)) {
                        return true;
                    }
                }
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Error reading users file.");
        }
        return false;
    }

    private static void attemptLogin() {
        try {
            // Initialize the socket and store it
            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);

            // Use the socket for writer and reader initialization
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));

            writer.println(username);
            showChatWindow();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Unable to connect to server.");
        }
    }

    private static void showChatWindow() {
        JFrame chatFrame = new JFrame(username + "'s Chat");
        chatFrame.setSize(600, 500);
        chatFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel topPanel = new JPanel(new BorderLayout());
        groupDropdown = new JComboBox<>();
        groupDropdown.addItem("Broadcast");
        topPanel.add(groupDropdown, BorderLayout.WEST);

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(chatArea);

        textField = new JTextField();
        textField.addActionListener(e -> sendMessage());

        JMenuBar menuBar = new JMenuBar();
        JMenu optionsMenu = new JMenu("Options");

        // Create menu items
        JMenuItem createGroup = new JMenuItem("Group Chat");
        JMenuItem fileTransfer = new JMenuItem("Send File");
        JMenuItem chatHistory = new JMenuItem("Chat History");

        createGroup.addActionListener(e -> createOrJoinGroup());
        fileTransfer.addActionListener(e -> sendFile());
        chatHistory.addActionListener(e -> showChatHistory());

        // Add menu items to the Options menu
        optionsMenu.add(createGroup);
        optionsMenu.add(fileTransfer);
        optionsMenu.add(chatHistory);
        menuBar.add(optionsMenu);

        chatFrame.add(topPanel, BorderLayout.NORTH);
        chatFrame.add(scrollPane, BorderLayout.CENTER);
        chatFrame.add(textField, BorderLayout.SOUTH);
        chatFrame.setJMenuBar(menuBar);
        chatFrame.setVisible(true);

        new Thread(ChatClient::receiveMessages).start();
    }

    private static void sendMessage() {
        String message = textField.getText();
        if (!message.trim().isEmpty()) {
            String selectedGroup = (String) groupDropdown.getSelectedItem();
            if ("Broadcast".equals(selectedGroup)) {
                writer.println(message);
            } else {
                writer.println("GROUP_MSG " + selectedGroup + " " + message);
            }
            textField.setText("");
        }
    }

    private static void receiveMessages() {
        try {
            String message;
            while ((message = reader.readLine()) != null) {
                if (message.startsWith("FILE_RECEIVED")) {
                    // Detect file transfer messages and handle them
                    handleFileReception(message);
                } else {
                    // Process regular messages
                    final String serverMessage = message;
                    chatHistory.add("[" + new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date()) + "] " + serverMessage);
                    SwingUtilities.invokeLater(() -> chatArea.append(serverMessage + "\n"));
                }
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Error receiving messages.");
        }
    }    

    private static void createOrJoinGroup() {
        String[] options = {"Create Group", "Join Group"};
        int choice = JOptionPane.showOptionDialog(
            null,
            "Would you like to create a new group or join an existing group?",
            "Group Chat",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[0]
        );

        if (choice == 0) {
            String groupCode = JOptionPane.showInputDialog("Enter a unique group code:");
            if (groupCode != null && !groupCode.trim().isEmpty()) {
                writer.println("GROUP_CREATE " + groupCode.trim());
                groupDropdown.addItem(groupCode.trim());
            } else {
                JOptionPane.showMessageDialog(null, "Group code cannot be empty.");
            }
        } else if (choice == 1) {
            String groupCode = JOptionPane.showInputDialog("Enter the group code to join:");
            if (groupCode != null && !groupCode.trim().isEmpty()) {
                writer.println("GROUP_JOIN " + groupCode.trim());
                groupDropdown.addItem(groupCode.trim());
            } else {
                JOptionPane.showMessageDialog(null, "Group code cannot be empty.");
            }
        }
    }

    
    private static void sendFile() {
        JFileChooser fileChooser = new JFileChooser();
        int returnValue = fileChooser.showOpenDialog(null);
    
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            String selectedGroup = (String) groupDropdown.getSelectedItem();
            String target = "Broadcast".equals(selectedGroup) ? "BROADCAST" : "GROUP " + selectedGroup;
    
            try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
                // Send file metadata
                writer.println("FILE_TRANSFER " + target + " " + file.getName() + " " + file.length());
    
                // Display "Sending..." in the chat area
                SwingUtilities.invokeLater(() -> chatArea.append("Sending file: " + file.getName() + "...\n"));
    
                // Send file binary data
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = bis.read(buffer)) != -1) {
                    writer.println(new String(buffer, 0, bytesRead));
                }
    
                writer.flush();
                SwingUtilities.invokeLater(() -> chatArea.append("File sent: " + file.getName() + "\n"));
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null, "Error sending file: " + e.getMessage());
            }
        }
    }

    private static void handleFileReception(String metadata) {
        try {
            String[] parts = metadata.split(" ");
            if (parts.length != 4) {
                SwingUtilities.invokeLater(() -> chatArea.append("Invalid file metadata received.\n"));
                return;
            }
    
            String fileName = parts[2];
            long fileSize = Long.parseLong(parts[3]);
    
            // Show "Receiving..." in the chat area
            SwingUtilities.invokeLater(() -> chatArea.append("Receiving file: " + fileName + "...\n"));
    
            // Save the file locally
            File receivedFile = new File(fileName);
            try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(receivedFile))) {
                byte[] buffer = new byte[BUFFER_SIZE];
                long totalBytesRead = 0;
    
                InputStream inputStream = socket.getInputStream();
                while (totalBytesRead < fileSize) {
                    int bytesRead = inputStream.read(buffer);
                    if (bytesRead == -1) break;
                    bos.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;
                }
    
                if (totalBytesRead == fileSize) {
                    // Add clickable link to the chat area after the file is fully received
                    SwingUtilities.invokeLater(() -> {
                        chatArea.append("File received: ");
                        addClickableLink(fileName);
                    });
                } else {
                    SwingUtilities.invokeLater(() -> chatArea.append("File transfer incomplete: " + fileName + "\n"));
                }
            }
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> chatArea.append("Error receiving file: " + e.getMessage() + "\n"));
        }
    }    
    
    private static void addClickableLink(String fileName) {
        chatArea.append(fileName + " (Click to open)\n");
    
        chatArea.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    try {
                        File file = new File(fileName);
                        if (file.exists()) {
                            Desktop.getDesktop().open(file);
                        } else {
                            JOptionPane.showMessageDialog(null, "File not found: " + fileName);
                        }
                    } catch (IOException e) {
                        JOptionPane.showMessageDialog(null, "Unable to open file: " + e.getMessage());
                    }
                }
            }
        });
    }        

    private static void showChatHistory() {
        JFrame historyFrame = new JFrame("Chat History");
        historyFrame.setSize(500, 400);
        historyFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        DefaultListModel<String> historyModel = new DefaultListModel<>();
        for (String entry : chatHistory) {
            historyModel.addElement(entry);
        }

        JList<String> historyList = new JList<>(historyModel);
        JScrollPane scrollPane = new JScrollPane(historyList);

        JButton deleteButton = new JButton("Delete Selected");
        deleteButton.addActionListener(e -> {
            int[] selectedIndices = historyList.getSelectedIndices();
            if (selectedIndices.length > 0) {
                List<String> toRemove = new ArrayList<>();
                for (int index : selectedIndices) {
                    toRemove.add(historyModel.getElementAt(index));
                }
                toRemove.forEach(historyModel::removeElement);
                chatHistory.removeAll(toRemove);
            } else {
                JOptionPane.showMessageDialog(historyFrame, "No message selected for deletion.");
            }
        });

        historyFrame.add(scrollPane, BorderLayout.CENTER);
        historyFrame.add(deleteButton, BorderLayout.SOUTH);
        historyFrame.setVisible(true);
    }
}