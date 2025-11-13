package ca.concordia.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import ca.concordia.filesystem.FileSystemManager;

public class FileServer {

    /**
     * FileServer class acts as the main server for handling client requests. It
     * uses a thread pool to manage multiple clients concurrently and
     * synchronizes access to the file system so that only one writer can modify
     * data at a time.
     */
    // File system manager instance
    private FileSystemManager fsManager;

    // port number where server listens to the client connections
    private int port;

    // Read-write lock for synchronizing file system access
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // Thread pool for handling client connections simultaneously (max 10 clients)
    private final ExecutorService pool;

    // Constructor to initialize server
    public FileServer(int port, String fileSystemName, int totalSize) {
        this.fsManager = new FileSystemManager(fileSystemName, totalSize);
        this.port = port;
        this.pool = Executors.newFixedThreadPool(10); // supports 10 concurrent clients
    }

    // Start server
    // Each client connection is handled by a separate thread from the pool
    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("File Server started. Listening on port " + port + "...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println(" Connected: " + clientSocket);

                // Handle each client in a separate thread
                pool.execute(new ClientHandler(clientSocket, fsManager, lock));
            }

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Could not start server on port " + port);
        }
    }

    /**
     * Inner class that represents a worker thread handling one client. Each
     * client connection is assigned a new ClientHandler instance.
     */
    // Client handler class
    private static class ClientHandler implements Runnable {

        private final Socket socket; // Client socket
        private final FileSystemManager fs; // File system manager (shared)
        private final ReentrantReadWriteLock lock; // Read-write lock for synchronization

        // Constructor for client handler
        public ClientHandler(Socket socket, FileSystemManager fs, ReentrantReadWriteLock lock) {
            this.socket = socket;
            this.fs = fs;
            this.lock = lock;
        }

        // Main run method for handling client commands
        @Override
        public void run() {
            try (
                    // Reader to receive client commands
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream())); // Writer to send responses back to client
                     PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {
                writer.println("Welcome to the File Server!");
                String line;

                // Read commands from client until disconnection
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) {
                        continue;
                    }
                    System.out.println("Received: " + line);
                    String response = handleCommand(line.trim());
                    writer.println(response);
                    writer.flush();

                    if (response.equals("SUCCESS: Disconnecting.")) {
                        break;
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                // Close client socket when done
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
                System.out.println("Client disconnected: " + socket);
            }
        }

        // Method to process and respond to client commands
        // Supports CREATE, WRITE, READ, DELETE, LIST, QUIT commands
        private String handleCommand(String commandLine) {
            try {
                if (commandLine.isEmpty()) {
                    return "ERROR: Empty command";
                }

                // Split command and arguments into 3 parts: command, filename, content
                String[] parts = commandLine.split(" ", 3);
                String cmd = parts[0].toUpperCase();

                switch (cmd) {

                    // Create a new file
                    case "CREATE":
                        if (parts.length < 2) {
                            return "ERROR: Missing filename";
                        }
                        lock.writeLock().lock();
                        try {
                            fs.createFile(parts[1]);
                            return "SUCCESS: File '" + parts[1] + "' created.";
                        } finally {
                            lock.writeLock().unlock();
                        }

                    // Write content to a file
                    case "WRITE":
                        if (parts.length < 3) {
                            return "ERROR: Missing filename or content";
                        }
                        lock.writeLock().lock();
                        try {
                            fs.writeFile(parts[1], parts[2].getBytes());
                            return "SUCCESS: File '" + parts[1] + "' written.";
                        } finally {
                            lock.writeLock().unlock();
                        }

                    // Read content from a file
                    case "READ":
                        if (parts.length < 2) {
                            return "ERROR: Missing filename";
                        }
                        lock.readLock().lock();
                        try {
                            byte[] data = fs.readFile(parts[1]);
                            return "SUCCESS: " + new String(data);
                        } finally {
                            lock.readLock().unlock();
                        }

                    // Delete a file
                    case "DELETE":
                        if (parts.length < 2) {
                            return "ERROR: Missing filename";
                        }
                        lock.writeLock().lock();
                        try {
                            fs.deleteFile(parts[1]);
                            return "SUCCESS: File '" + parts[1] + "' deleted.";
                        } finally {
                            lock.writeLock().unlock();
                        }

                    // List all files
                    case "LIST":
                        lock.readLock().lock();
                        try {
                            String[] files = fs.listFiles();
                            if (files.length == 0) {
                                return "No files found.";
                            }
                            return "Files: " + String.join(", ", files);
                        } finally {
                            lock.readLock().unlock();
                        }

                    // Client Disconnects
                    case "QUIT":
                        return "SUCCESS: Disconnecting.";

                    default:
                        return "ERROR: Unknown command.";
                }

                // Catch any exceptions and return error message
            } catch (Exception e) {
                return "ERROR: " + e.getMessage();
            }
        }
    }

    // Main method to start the server
    public static void main(String[] args) {
        FileServer server = new FileServer(12345, "server_disk.img", 128 * 10);
        server.start(); // Start listening for client connections
    }
}
