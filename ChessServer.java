import java.io.*;
import java.net.*;
import java.util.*;

public class ChessServer {
    private ServerSocket serverSocket;
    private List<ClientHandler> clients = new ArrayList<>();
    private ClientHandler whitePlayer;
    private ClientHandler blackPlayer;
    private boolean gameStarted = false;
    
    public ChessServer(int port) {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("🎮 Шахматный сервер запущен на порту " + port);
        } catch (IOException e) {
            System.err.println("❌ Ошибка запуска сервера: " + e.getMessage());
        }
    }
    
    public void start() {
        while (true) {
            try {
                Socket clientSocket = serverSocket.accept();
                System.out.println("👤 Новый игрок подключился: " + clientSocket.getInetAddress());
                
                ClientHandler handler = new ClientHandler(clientSocket);
                clients.add(handler);
                new Thread(handler).start();
                
            } catch (IOException e) {
                System.err.println("❌ Ошибка подключения: " + e.getMessage());
            }
        }
    }
    
    private class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String playerColor;
        
        public ClientHandler(Socket socket) {
            this.socket = socket;
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        @Override
        public void run() {
            try {
                // Назначаем цвет игроку
                synchronized (this) {
                    if (whitePlayer == null) {
                        whitePlayer = this;
                        playerColor = "WHITE";
                        out.println("COLOR:WHITE");
                        out.println("MESSAGE:Вы играете белыми. Ожидание соперника...");
                    } else if (blackPlayer == null) {
                        blackPlayer = this;
                        playerColor = "BLACK";
                        out.println("COLOR:BLACK");
                        out.println("MESSAGE:Вы играете черными. Игра начинается!");
                        
                        // Уведомляем обоих игроков о начале игры
                        whitePlayer.out.println("MESSAGE:Соперник подключился! Игра начинается!");
                        whitePlayer.out.println("GAME_START");
                        blackPlayer.out.println("GAME_START");
                        gameStarted = true;
                    } else {
                        out.println("MESSAGE:Сервер полон. Попробуйте позже.");
                        socket.close();
                        return;
                    }
                }
                
                // Ждем начала игры
                while (!gameStarted && playerColor.equals("WHITE")) {
                    Thread.sleep(100);
                }
                
                // Основной цикл обработки сообщений
                String input;
                while ((input = in.readLine()) != null) {
                    System.out.println("📨 Получено от " + playerColor + ": " + input);
                    
                    // Пересылаем ход сопернику
                    if (input.startsWith("MOVE:")) {
                        ClientHandler opponent = playerColor.equals("WHITE") ? blackPlayer : whitePlayer;
                        if (opponent != null) {
                            opponent.out.println(input);
                        }
                    } else if (input.startsWith("CHAT:")) {
                        // Чат между игроками
                        String message = input.substring(5);
                        ClientHandler opponent = playerColor.equals("WHITE") ? blackPlayer : whitePlayer;
                        if (opponent != null) {
                            opponent.out.println("CHAT:" + message);
                        }
                    } else if (input.equals("RESIGN")) {
                        // Игрок сдался
                        ClientHandler opponent = playerColor.equals("WHITE") ? blackPlayer : whitePlayer;
                        if (opponent != null) {
                            opponent.out.println("OPPONENT_RESIGN");
                        }
                    } else if (input.equals("DRAW_OFFER")) {
                        // Предложение ничьей
                        ClientHandler opponent = playerColor.equals("WHITE") ? blackPlayer : whitePlayer;
                        if (opponent != null) {
                            opponent.out.println("DRAW_OFFER");
                        }
                    } else if (input.equals("DRAW_ACCEPT")) {
                        // Принятие ничьей
                        ClientHandler opponent = playerColor.equals("WHITE") ? blackPlayer : whitePlayer;
                        if (opponent != null) {
                            opponent.out.println("DRAW_ACCEPT");
                        }
                    }
                }
                
            } catch (IOException | InterruptedException e) {
                System.err.println("❌ Ошибка в обработчике клиента: " + e.getMessage());
            } finally {
                // Отключаем игрока
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                
                synchronized (this) {
                    if (this == whitePlayer) {
                        whitePlayer = null;
                        if (blackPlayer != null) {
                            blackPlayer.out.println("MESSAGE:Соперник отключился. Игра окончена.");
                        }
                    } else if (this == blackPlayer) {
                        blackPlayer = null;
                        if (whitePlayer != null) {
                            whitePlayer.out.println("MESSAGE:Соперник отключился. Игра окончена.");
                        }
                    }
                    clients.remove(this);
                    gameStarted = false;
                }
            }
        }
    }
    
    public static void main(String[] args) {
        ChessServer server = new ChessServer(5555);
        server.start();
    }
}