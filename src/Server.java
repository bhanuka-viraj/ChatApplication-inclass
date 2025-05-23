import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;

public class Server {
    private static final int PORT = 5000;
    private static HashSet<ObjectOutputStream> writers = new HashSet<>();
    private static ServerSocket serverSocket;
    private static boolean isRunning = false;

    public static void main(String[] args) {
        startServer();
    }

    private static void startServer() {
        if (isRunning) return;
        isRunning = true;

        System.out.println("Server started on port : " + PORT);
        try {
            System.out.println("Listening for client connections...");

            //creating the server socket which should- when the clients requests to connect with the server,
            // the server socket accepts and create a private socket(client specific socket)
            serverSocket = new ServerSocket(PORT);

            while (isRunning) {
                Socket socket = serverSocket.accept(); // a private socket will be created and passed to the Client Handler
                new Thread(() -> new ClientHandler(socket).run()).start();
                //Client handler is the class which will deal with private client connections by broadcasting messages
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class ClientHandler {
        private Socket socket;
        private ObjectOutputStream out;
        private ObjectInputStream in;
        private String clientName;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                in = new ObjectInputStream(socket.getInputStream());

                while (true) {
                    out.writeObject("SUBMITCLIENTNAME");
                    out.flush();
                    clientName = (String) in.readObject();

                    if (clientName != null && !clientName.isEmpty()) {
                        break;
                    }
                    System.out.println("Invalid name, requesting again..");
                }

                out.writeObject("NAMEACCEPTED");
                out.flush();
                System.out.println("Client connected : " + clientName);

                synchronized (writers) {
                    writers.add(out);
                }

                while (true) {
                    Object message = in.readObject();
                    if (message == null) {
                        break;
                    }
                    if (message instanceof String) {
                        broadcast("TEXT " + clientName + ": " + message);
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                System.out.println("Error in client handler: " + e.getMessage());
            } finally {
                try {
                    if (out != null) {
                        synchronized (writers) {
                            writers.remove(out);
                        }
                        out.close();
                    }
                    if (in != null) in.close();
                    if (socket != null) socket.close();
                } catch (IOException e) {
                }
                if (clientName != null) {
                    System.out.println("Client disconnected: " + clientName);
                    broadcast("TEXT " + clientName + " has left the chat");
                }
            }
        }

        private void broadcast(String message) {
            synchronized (writers) {
                for (ObjectOutputStream writer : writers) {
                    try {
                        writer.writeObject(message);
                        writer.flush();
                    } catch (IOException e) {
                        System.out.println("Error sending message: " + e.getMessage());
                    }
                }
            }
        }
    }
}