package org.baej;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
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

        run();
    }

    private void run() {
        try (ServerSocket serverSocket = new ServerSocket(localPort)) {
            System.out.printf("Proxy to %s:%d started on port %d\n", destinationHost, destinationPort, localPort);

            // Listen for sockets
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Opened socked connection with " + socket.getInetAddress());
                socket.setSoTimeout(60_000);

                // Handle new socket connection
                executorService.submit(() -> handleProxy(socket));
            }
        } catch (IOException e) {
            System.out.println("Proxy exception " + e.getMessage());
        }
    }

    private void handleProxy(Socket clientSocket) {
        try (clientSocket;
             Socket apiSocket = new Socket(destinationHost, destinationPort);
             var apiReader = apiSocket.getInputStream();
             var apiWriter = apiSocket.getOutputStream();

             var clientReader = clientSocket.getInputStream();
             var clientWriter = clientSocket.getOutputStream();
        ) {

            Thread clientToApi = messageRelay(clientReader, apiWriter);
            Thread apiToClient = messageRelay(apiReader, clientWriter);

            // Start relays
            clientToApi.start();
            apiToClient.start();

            // Wait for relays
            clientToApi.join();
            apiToClient.join();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private Thread messageRelay(InputStream inputStream, OutputStream outputStream) {
        return new Thread(
                () -> {
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    try {
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer,0,bytesRead);
                            outputStream.flush();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
        );
    }
}
