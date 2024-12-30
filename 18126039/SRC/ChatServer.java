import java.io.*;
import java.net.*;
import java.util.*;

public class ChatServer {
    private static final int PORT = 1234;
    private static final int BUFFER_SIZE = 4096;

    private static Set<PrintWriter> clientWriters = Collections.synchronizedSet(new HashSet<>());
    private static Map<String, List<PrintWriter>> groups = Collections.synchronizedMap(new HashMap<>());

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started on port: " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            System.err.println("Could not start server: " + e.getMessage());
        }
    }

    static class ClientHandler extends Thread {
        private Socket clientSocket;
        private PrintWriter writer;
        private BufferedReader reader;
        private String username;

        public ClientHandler(Socket socket) throws IOException {
            this.clientSocket = socket;
            this.writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
        }        

        @Override
        public void run() {
            try {
                username = reader.readLine();
                if (username == null || username.trim().isEmpty()) {
                    writer.println("LOGIN_FAILED Invalid username.");
                    closeConnection();
                    return;
                }

                synchronized (clientWriters) {
                    clientWriters.add(writer);
                    System.out.println(username + " has connected.");
                    writer.println("LOGIN_SUCCESS Welcome to the chat, " + username);
                }

                String inputLine;
                while ((inputLine = reader.readLine()) != null) {
                    if (inputLine.startsWith("GROUP_CREATE")) {
                        createGroup(inputLine.split(" ", 2)[1]);
                    } else if (inputLine.startsWith("GROUP_JOIN")) {
                        joinGroup(inputLine.split(" ", 2)[1]);
                    } else if (inputLine.startsWith("GROUP_MSG")) {
                        String[] parts = inputLine.split(" ", 3);
                        sendGroupMessage(parts[1], username + ": " + parts[2]);
                    } else if (inputLine.startsWith("FILE_TRANSFER")) {
                        handleFileTransfer(inputLine);
                    } else {
                        broadcastMessage(username + ": " + inputLine);
                    }
                }
            } catch (IOException e) {
                System.err.println("Communication error with client: " + e.getMessage());
            } finally {
                closeConnection();
            }
        }

        private void createGroup(String groupCode) {
            synchronized (groups) {
                if (!groups.containsKey(groupCode)) {
                    groups.put(groupCode, new ArrayList<>());
                    groups.get(groupCode).add(writer);
                    writer.println("GROUP_CREATED Successfully created group with code: " + groupCode);
                } else {
                    writer.println("GROUP_ERROR Group code already exists.");
                }
            }
        }

        private void joinGroup(String groupCode) {
            synchronized (groups) {
                if (groups.containsKey(groupCode)) {
                    groups.get(groupCode).add(writer);
                    writer.println("GROUP_JOINED Successfully joined group: " + groupCode);
                } else {
                    writer.println("GROUP_ERROR Group does not exist.");
                }
            }
        }

        private void sendGroupMessage(String groupCode, String message) {
            synchronized (groups) {
                if (groups.containsKey(groupCode)) {
                    for (PrintWriter groupWriter : groups.get(groupCode)) {
                        groupWriter.println("[Group " + groupCode + "] " + message);
                    }
                } else {
                    writer.println("GROUP_ERROR Group does not exist.");
                }
            }
        }

        private void broadcastMessage(String message) {
            synchronized (clientWriters) {
                for (PrintWriter pw : clientWriters) {
                    pw.println(message);
                }
            }
        }

        private void handleFileTransfer(String metadata) {
            try {
                String[] parts = metadata.split(" ", 4);
                if (parts.length < 4) {
                    writer.println("FILE_ERROR Invalid file transfer metadata.");
                    return;
                }

                String mode = parts[1]; // "BROADCAST" or "GROUP"
                String target = parts[2]; // "Broadcast" or groupCode
                String fileName = parts[3];

                long fileSize = Long.parseLong(reader.readLine());

                File tempFile = File.createTempFile("server_received_", "_" + fileName);
                try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    long totalBytesRead = 0;

                    InputStream inputStream = clientSocket.getInputStream();
                    while (totalBytesRead < fileSize) {
                        int bytesRead = inputStream.read(buffer);
                        if (bytesRead == -1) break;
                        fos.write(buffer, 0, bytesRead);
                        totalBytesRead += bytesRead;
                    }

                    if (totalBytesRead == fileSize) {
                        forwardFile(mode, target, fileName, fileSize, tempFile);
                        writer.println("FILE_SUCCESS File transfer completed.");
                    } else {
                        writer.println("FILE_ERROR Incomplete file received.");
                    }
                }
            } catch (Exception e) {
                writer.println("FILE_ERROR " + e.getMessage());
            }
        }

        private void forwardFile(String mode, String target, String fileName, long fileSize, File tempFile) {
            try {
                List<PrintWriter> recipients = new ArrayList<>();
                if ("BROADCAST".equalsIgnoreCase(mode)) {
                    synchronized (clientWriters) {
                        recipients.addAll(clientWriters);
                    }
                } else if ("GROUP".equalsIgnoreCase(mode)) {
                    synchronized (groups) {
                        if (groups.containsKey(target)) {
                            recipients.addAll(groups.get(target));
                        } else {
                            writer.println("FILE_ERROR Group does not exist.");
                            return;
                        }
                    }
                }

                for (PrintWriter recipientWriter : recipients) {
                    recipientWriter.println("FILE_RECEIVED " + fileName + " " + fileSize);

                    try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(tempFile))) {
                        byte[] buffer = new byte[BUFFER_SIZE];
                        int bytesRead;
                        while ((bytesRead = bis.read(buffer)) != -1) {
                            recipientWriter.println(new String(buffer, 0, bytesRead));
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Error forwarding file: " + e.getMessage());
            }
        }

        private void closeConnection() {
            try {
                synchronized (clientWriters) {
                    clientWriters.remove(writer);
                }
                if (writer != null) writer.close();
                if (reader != null) reader.close();
                if (clientSocket != null) clientSocket.close();
                System.out.println(username + " has disconnected.");
            } catch (IOException e) {
                System.err.println("Error closing connection: " + e.getMessage());
            }
        }
    }
}