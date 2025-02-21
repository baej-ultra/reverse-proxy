package org.baej;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProxyHandler {

    private final int localPort;
    private final String destinationHost;
    private final int destinationPort;
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public ProxyHandler(int localPort, String destinationHost, int destinationPort) {
        this.localPort = localPort;
        this.destinationHost = destinationHost;
        this.destinationPort = destinationPort;

        start();
    }

    private void start() {
        try (ServerSocket serverSocket = new ServerSocket(localPort)) {
            System.out.printf("Proxy to %s:%d started on port %d\n", destinationHost, destinationPort, localPort);

            // Listen for sockets
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Opened socked connection with " + socket.getInetAddress());
                socket.setSoTimeout(60_000);

                // Handle new socket connection
                executorService.submit(new SocketRelay(socket));
            }
        } catch (IOException e) {
            System.out.println("Proxy exception " + e.getMessage());
        } finally {
            executorService.shutdownNow();
        }
    }

    private class SocketRelay implements Runnable {

        private final List<Thread> threads = new ArrayList<>(2);
        private final Socket clientSocket;

        public SocketRelay(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            try (clientSocket;
                 Socket apiSocket = new Socket(destinationHost, destinationPort);
                 var apiReader = apiSocket.getInputStream();
                 var apiWriter = apiSocket.getOutputStream();
                 var clientReader = clientSocket.getInputStream();
                 var clientWriter = clientSocket.getOutputStream();
            ) {

                // Spawn threads
                Thread clientToApi = spawnRelayThread(clientReader, apiWriter);
                Thread apiToClient = spawnRelayThread(apiReader, clientWriter);

                // Add threads for tracking
                threads.add(clientToApi);
                threads.add(apiToClient);

                // Start relays
                clientToApi.start();
                apiToClient.start();

                // Wait for relays
                clientToApi.join(60_000);
                apiToClient.join(60_000);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }

        private Thread spawnRelayThread(InputStream inputStream, OutputStream outputStream) {
            return new Thread(
                    () -> {
                        byte[] buffer = new byte[1024];
                        int bytesRead;
                        // Relay inputStream to the other guy
                        try {
                            while ((bytesRead = inputStream.read(buffer)) != -1) {
                                outputStream.write(buffer, 0, bytesRead);
                                outputStream.flush();

                                if (Thread.currentThread().isInterrupted()) {
                                    break;
                                }
                            }
                        } catch (IOException e) {
                            // Interrupt the other thread on IOException
                            for (Thread thread : threads) {
                                thread.interrupt();
                                try {
                                    thread.join(1000);
                                } catch (InterruptedException ignored) {
                                }
                            }
                        }
                    }
            );
        }
    }
}
