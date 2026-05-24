import java.io.*;
import java.net.*;
import javafx.application.Platform;

public class NetworkClient {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String playerColor;
    private boolean connected = false;
    private Main mainApp;
    private boolean gameStarted = false;
    
    public NetworkClient(Main mainApp) {
        this.mainApp = mainApp;
    }
    
    public boolean connect(String host, int port) {
        try {
            socket = new Socket(host, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            connected = true;
            
            // Запускаем поток для приема сообщений
            new Thread(this::receiveMessages).start();
            
            return true;
        } catch (IOException e) {
            System.err.println("❌ Ошибка подключения: " + e.getMessage());
            return false;
        }
    }
    
    private void receiveMessages() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                final String msg = message;
                Platform.runLater(() -> processMessage(msg));
            }
        } catch (IOException e) {
            if (connected) {
                System.err.println("❌ Соединение разорвано: " + e.getMessage());
                Platform.runLater(() -> {
                    mainApp.showConnectionLost();
                });
            }
        }
    }
    
    private void processMessage(String message) {
        System.out.println("📨 Получено: " + message);
        
        if (message.startsWith("COLOR:")) {
            playerColor = message.substring(6);
            mainApp.setNetworkPlayerColor(playerColor.equals("WHITE"));
            System.out.println("🎨 Ваш цвет: " + playerColor);
        } else if (message.startsWith("MESSAGE:")) {
            String msg = message.substring(8);
            mainApp.showMessage(msg);
        } else if (message.equals("GAME_START")) {
            gameStarted = true;
            mainApp.startNetworkGame();
        } else if (message.startsWith("MOVE:")) {
            // Получен ход от соперника
            String moveStr = message.substring(5);
            mainApp.receiveOpponentMove(moveStr);
        } else if (message.startsWith("CHAT:")) {
            String chatMsg = message.substring(5);
            mainApp.receiveChatMessage(chatMsg);
        } else if (message.equals("OPPONENT_RESIGN")) {
            mainApp.opponentResigned();
        } else if (message.equals("DRAW_OFFER")) {
            mainApp.receiveDrawOffer();
        } else if (message.equals("DRAW_ACCEPT")) {
            mainApp.drawAccepted();
        }
    }
    
    public void sendMove(int fromRow, int fromCol, int toRow, int toCol) {
        if (out != null) {
            String move = String.format("MOVE:%d,%d,%d,%d", fromRow, fromCol, toRow, toCol);
            out.println(move);
            System.out.println("📤 Отправлен ход: " + move);
        }
    }
    
    public void sendChatMessage(String message) {
        if (out != null) {
            out.println("CHAT:" + message);
        }
    }
    
    public void sendResign() {
        if (out != null) {
            out.println("RESIGN");
        }
    }
    
    public void sendDrawOffer() {
        if (out != null) {
            out.println("DRAW_OFFER");
        }
    }
    
    public void sendDrawAccept() {
        if (out != null) {
            out.println("DRAW_ACCEPT");
        }
    }
    
    public boolean isMyTurn() {
        if (!gameStarted) return false;
        boolean isWhite = playerColor.equals("WHITE");
        return isWhite == mainApp.isWhiteTurn();
    }
    
    public String getPlayerColor() {
        return playerColor;
    }
    
    public boolean isConnected() {
        return connected;
    }
    
    public void disconnect() {
        connected = false;
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}