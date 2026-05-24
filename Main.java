import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;

public class Main extends Application {
    private Stage stage;
    public int[][] board = new int[8][8];
    private ImageView selectedPiece = null;
    private List<Rectangle> moveHighlights = new ArrayList<>();
    private final StackPane[][] boardSquares = new StackPane[8][8];
    private final int cellSize = 100;
    
    private boolean whiteTurn = true;
    private boolean gameOver = false;
    private Button restartButton;
    private Label turnLabel;
    
    private boolean whiteKingMoved = false;
    private boolean whiteRookKingSideMoved = false;
    private boolean whiteRookQueenSideMoved = false;
    private boolean blackKingMoved = false;
    private boolean blackRookKingSideMoved = false;
    private boolean blackRookQueenSideMoved = false;
    
    private boolean vsComputer = false;
    private boolean playerColor = true;
    private int level = 0;
    private boolean boardFlipped = false;
    private boolean computerPromoting = false;
    
    private UCIEngine scoriaEngine;
    private String pendingFEN = null;
    
    private Stack<GameState> moveHistory = new Stack<>();
    private List<String> gameNotation = new ArrayList<>();
    private int moveNumber = 1;
    private List<FullMoveRecord> fullMoveHistory = new ArrayList<>();
    
    private class FullMoveRecord {
        int fromRow, fromCol, toRow, toCol;
        int movedPiece, capturedPiece;
        boolean wasWhiteTurn, wasCastle, wasPromotion;
        String notation;
        boolean wKingMoved, wRookKMoved, wRookQMoved;
        boolean bKingMoved, bRookKMoved, bRookQMoved;
    }
    
    private ChessClock chessClock;
    private Label whiteTimeLabel;
    private Label blackTimeLabel;
    private boolean clockEnabled = true;
    private int initialTimeMinutes = 10;
    
    private NetworkClient networkClient;
    private boolean networkGame = false;
    private boolean networkPlayerIsWhite = true;
    private Label connectionStatusLabel;
    private TextArea chatArea;
    private TextField chatInput;
    private Button sendChatButton;

    // ==================== КЛАСС GameState ====================
    private class GameState {
        int[][] boardState;
        boolean whiteKingMoved, whiteRookKingSideMoved, whiteRookQueenSideMoved;
        boolean blackKingMoved, blackRookKingSideMoved, blackRookQueenSideMoved;
        boolean whiteTurn;
        
        GameState() {
            boardState = new int[8][8];
            for (int i = 0; i < 8; i++)
                System.arraycopy(board[i], 0, boardState[i], 0, 8);
            this.whiteKingMoved = Main.this.whiteKingMoved;
            this.whiteRookKingSideMoved = Main.this.whiteRookKingSideMoved;
            this.whiteRookQueenSideMoved = Main.this.whiteRookQueenSideMoved;
            this.blackKingMoved = Main.this.blackKingMoved;
            this.blackRookKingSideMoved = Main.this.blackRookKingSideMoved;
            this.blackRookQueenSideMoved = Main.this.blackRookQueenSideMoved;
            this.whiteTurn = Main.this.whiteTurn;
        }
        
        void restore() {
            for (int i = 0; i < 8; i++)
                System.arraycopy(boardState[i], 0, board[i], 0, 8);
            Main.this.whiteKingMoved = this.whiteKingMoved;
            Main.this.whiteRookKingSideMoved = this.whiteRookKingSideMoved;
            Main.this.whiteRookQueenSideMoved = this.whiteRookQueenSideMoved;
            Main.this.blackKingMoved = this.blackKingMoved;
            Main.this.blackRookKingSideMoved = this.blackRookKingSideMoved;
            Main.this.blackRookQueenSideMoved = this.blackRookQueenSideMoved;
            Main.this.whiteTurn = this.whiteTurn;
        }
    }

    // ==================== КЛАСС ChessClock ====================
    private class ChessClock {
        private long whiteTimeMillis, blackTimeMillis, lastMoveTime;
        private volatile boolean running = false;
        private ScheduledExecutorService executor;
        
        public ChessClock(int minutes) {
            whiteTimeMillis = minutes * 60 * 1000L;
            blackTimeMillis = minutes * 60 * 1000L;
            executor = Executors.newSingleThreadScheduledExecutor();
        }
        
        public void start() {
            if (!running && !gameOver) {
                running = true;
                lastMoveTime = System.currentTimeMillis();
                executor.scheduleAtFixedRate(() -> {
                    if (running && !gameOver) {
                        long now = System.currentTimeMillis();
                        long elapsed = now - lastMoveTime;
                        lastMoveTime = now;
                        if (whiteTurn) whiteTimeMillis = Math.max(0, whiteTimeMillis - elapsed);
                        else blackTimeMillis = Math.max(0, blackTimeMillis - elapsed);
                        Platform.runLater(() -> updateTimeLabels());
                        if (whiteTimeMillis <= 0) {
                            running = false;
                            Platform.runLater(() -> { gameOver = true; showGameOver("Время белых истекло! Черные победили!", true); });
                        } else if (blackTimeMillis <= 0) {
                            running = false;
                            Platform.runLater(() -> { gameOver = true; showGameOver("Время черных истекло! Белые победили!", true); });
                        }
                    }
                }, 0, 100, TimeUnit.MILLISECONDS);
            }
        }
        
        public void switchTurn() { if (running) lastMoveTime = System.currentTimeMillis(); }
        public void stop() { running = false; executor.shutdown(); }
        public void pause() { running = false; }
        public String formatTime(long millis) {
            long s = millis / 1000, m = s / 60; s %= 60;
            return String.format("%02d:%02d.%d", m, s, (millis % 1000) / 100);
        }
        public long getWhiteTime() { return whiteTimeMillis; }
        public long getBlackTime() { return blackTimeMillis; }
    }
    
    private void updateTimeLabels() {
        if (whiteTimeLabel != null && blackTimeLabel != null && chessClock != null) {
            whiteTimeLabel.setText(chessClock.formatTime(chessClock.getWhiteTime()));
            blackTimeLabel.setText(chessClock.formatTime(chessClock.getBlackTime()));
        }
    }

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        showMainMenu();
    }

    // ==================== ГЛАВНОЕ МЕНЮ ====================
    private void showMainMenu() {
        VBox root = new VBox(20);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: linear-gradient(to bottom, #1a1a2e, #16213e, #0f3460); -fx-padding: 60px;");
        
        Label title = new Label("♔ NIKCHESS ♚");
        title.setStyle("-fx-font-size: 48px; -fx-font-weight: bold; -fx-text-fill: linear-gradient(to right, #f39c12, #e74c3c, #f39c12);");
        
        Label subtitle = new Label("Шахматы • Компьютер • Сеть • Задачи");
        subtitle.setStyle("-fx-font-size: 14px; -fx-text-fill: #7f8c8d; -fx-font-weight: bold;");
        
        String btnStyle = "-fx-font-size: 16px; -fx-font-weight: bold; -fx-padding: 16px 40px; -fx-background-radius: 12px; -fx-text-fill: white; -fx-cursor: hand; -fx-min-width: 320px; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 8, 0, 2, 4);";
        
        Button btn1 = new Button("🆚 Играть с компьютером");
        btn1.setStyle(btnStyle + "-fx-background-color: linear-gradient(to right, #e67e22, #d35400);");
        btn1.setOnAction(e -> showColorChoiceDialog());
        
        Button btn2 = new Button("👥 Играть с другом");
        btn2.setStyle(btnStyle + "-fx-background-color: linear-gradient(to right, #2ecc71, #27ae60);");
        btn2.setOnAction(e -> { vsComputer = false; pendingFEN = null; showGameBoard(); });
        
        Button btn3 = new Button("🌐 Игра по сети");
        btn3.setStyle(btnStyle + "-fx-background-color: linear-gradient(to right, #3498db, #2980b9);");
        btn3.setOnAction(e -> showNetworkMenuFull());
        
        Button btn4 = new Button("📜 Записи партий");
        btn4.setStyle(btnStyle + "-fx-background-color: linear-gradient(to right, #9b59b6, #8e44ad);");
        btn4.setOnAction(e -> showSavedGames());
        
        Button btn5 = new Button("🧩 Задачи");
        btn5.setStyle(btnStyle + "-fx-background-color: linear-gradient(to right, #f39c12, #e67e22);");
        btn5.setOnAction(e -> showPuzzles());
        
        Button btnExit = new Button("🚪 Выйти");
        btnExit.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 10px 30px; -fx-background-radius: 20px; -fx-text-fill: #95a5a6; -fx-background-color: transparent; -fx-border-color: #7f8c8d; -fx-border-radius: 20px; -fx-cursor: hand;");
        btnExit.setOnAction(e -> stage.close());
        
        root.getChildren().addAll(title, subtitle, new Separator(), btn1, btn2, btn3, btn4, btn5, btnExit);
        
        Scene scene = new Scene(root, 520, 700);
        stage.setTitle("NikChess — Главное меню");
        stage.setScene(scene);
        stage.show();
    }
    
    private void showColorChoiceDialog() {
        Stage d = new Stage(); d.initOwner(stage); d.setTitle("Выбор цвета");
        VBox v = new VBox(20); v.setPadding(new Insets(20)); v.setAlignment(Pos.CENTER);
        ToggleGroup g = new ToggleGroup();
        RadioButton wb = new RadioButton("Белые (ходят первыми)"); wb.setToggleGroup(g); wb.setSelected(true);
        RadioButton bb = new RadioButton("Черные (ходят вторыми)"); bb.setToggleGroup(g);
        Button start = new Button("Продолжить");
        start.setStyle("-fx-font-size: 16px; -fx-background-color: #4CAF50; -fx-text-fill: white;");
        start.setOnAction(e -> { playerColor = wb.isSelected(); vsComputer = true; pendingFEN = null; d.close(); showLevel(); });
        v.getChildren().addAll(new Label("За кого будете играть?"), wb, bb, start);
        d.setScene(new Scene(v, 300, 250)); d.show();
    }
    
    private void showLevel() {
    Stage d = new Stage(); d.initOwner(stage); d.setTitle("Выбор уровня");
    VBox v = new VBox(12); v.setPadding(new Insets(20)); v.setAlignment(Pos.TOP_CENTER);
    v.setStyle("-fx-background-color: #1a1a2e;");
    
    Label title = new Label("🎯 Выберите уровень:");
    title.setStyle("-fx-font-size: 20px; -fx-text-fill: white; -fx-font-weight: bold;");
    
    ToggleGroup g = new ToggleGroup();
    
    String style = "-fx-font-size: 14px; -fx-text-fill: white; -fx-padding: 5px;";
    
    RadioButton l0 = new RadioButton("🐣 Новичок (случайные ходы)");
    RadioButton l1 = new RadioButton("🐥 Любитель (оценка позиции)");
    RadioButton l2 = new RadioButton("🦊 Разрядник (Scoria ~1000)");
    RadioButton l3 = new RadioButton("🦅 КМС (Scoria ~1400)");
    RadioButton l4 = new RadioButton("🦈 Мастер (Scoria ~1800)");
    RadioButton l5 = new RadioButton("👑 Гроссмейстер (Scoria ~2200)");
    
    RadioButton[] levels = {l0, l1, l2, l3, l4, l5};
    for (RadioButton rb : levels) {
        rb.setToggleGroup(g);
        rb.setStyle(style);
        v.getChildren().add(rb);
    }
    l0.setSelected(true);
    
    Button start = new Button("🎮 Начать игру");
    start.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-padding: 14px 35px; " +
        "-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-background-radius: 12px; " +
        "-fx-cursor: hand; -fx-margin: 15px;");
    start.setOnAction(e -> {
        for (int i = 0; i < levels.length; i++) {
            if (levels[i].isSelected()) { level = i; break; }
        }
        d.close();
        showGameBoard();
    });
    
    v.getChildren().addAll(new Separator(), start);
    d.setScene(new Scene(v, 380, 450)); d.show();
}

  
// Обновите меню
    // ==================== ЗАДАЧИ ====================
    private void showPuzzles() {
        Stage ps = new Stage(); ps.setTitle("Шахматные задачи");
        VBox vbox = new VBox(15); vbox.setPadding(new Insets(20));
        vbox.setStyle("-fx-background-color: #1a1a2e;"); vbox.setAlignment(Pos.CENTER);
        
        Label title = new Label("🧩 Шахматные задачи");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: white;");
        
        String bs = "-fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 12px 25px; " +
            "-fx-background-radius: 10px; -fx-text-fill: white; -fx-cursor: hand; -fx-min-width: 300px;";
        
        Button btn1 = new Button("📅 Задача дня");
        btn1.setStyle(bs + "-fx-background-color: #e74c3c;");
        btn1.setOnAction(e -> { ps.close(); loadDailyPuzzle(); });
        
        Button btn2 = new Button("🎲 Случайная задача");
        btn2.setStyle(bs + "-fx-background-color: #3498db;");
        btn2.setOnAction(e -> { ps.close(); loadRandomPuzzle(); });
        
        Button closeBtn = new Button("Закрыть");
        closeBtn.setStyle("-fx-font-size: 14px; -fx-padding: 10px 30px; " +
            "-fx-background-color: #7f8c8d; -fx-text-fill: white; -fx-background-radius: 20px;");
        closeBtn.setOnAction(e -> ps.close());
        
        vbox.getChildren().addAll(title, btn1, btn2, closeBtn);
        ps.setScene(new Scene(vbox, 400, 300)); ps.show();
    }

    private void loadDailyPuzzle() {
        showMessage("Загрузка задачи дня...");
        new Thread(() -> {
            String fen = null;
            try { String json = fetchFromUrl("https://lichess.org/api/puzzle/daily"); fen = extractJsonField(json, "fen"); } catch (Exception e) {}
            if (fen == null || fen.isEmpty()) {
                try { String json = fetchFromUrl("https://api.chesspuzzle.net/v1/puzzle/daily"); fen = extractJsonField(json, "fen"); } catch (Exception e2) {}
            }
            final String f = fen;
            Platform.runLater(() -> {
                if (f != null && !f.isEmpty()) startPuzzle(f);
                else showMessage("Серверы недоступны.\nПроверьте интернет.");
            });
        }).start();
    }

    private void loadRandomPuzzle() {
        showMessage("Загрузка задачи...");
        new Thread(() -> {
            String fen = null;
            String[] urls = {
                "https://lichess.org/api/puzzle?count=1",
                "https://api.chesspuzzle.net/v1/puzzle",
                "https://puzzle-chess.com/api/puzzle/random"
            };
            for (String url : urls) {
                try { String json = fetchFromUrl(url); fen = extractJsonField(json, "fen"); if (fen != null && !fen.isEmpty()) break; } catch (Exception e) {}
            }
            // Если все API недоступны — жёстко закодированные FEN
            if (fen == null || fen.isEmpty()) {
                String[] hard = {
                    "2kr1b1r/ppp2ppp/2n5/4P3/3q4/1Q3N2/P1P2PPP/R1B2RK1 w - - 0 15",
                    "r2q1rk1/ppp2ppp/2n1bn2/3p4/3P4/2NQ1N2/PPP2PPP/R1B2RK1 w - - 4 10",
                    "r4rk1/ppp2ppp/2n1bn2/3p2q1/3P4/2NQ1N2/PPP2PPP/R1B1R1K1 w - - 6 12",
                };
                fen = hard[(int)(Math.random() * hard.length)];
            }
            final String f = fen;
            Platform.runLater(() -> {
                if (f != null && !f.isEmpty()) startPuzzle(f);
                else showMessage("Не удалось загрузить задачу.");
            });
        }).start();
    }

    private void startPuzzle(String fen) {
        pendingFEN = fen;
        vsComputer = false;
        playerColor = fen.contains(" w ");
        showGameBoard();
    }

    private String fetchFromUrl(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 NikChess/1.0");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();
        return sb.toString();
    }

    private String extractJsonField(String json, String field) {
        String search = "\"" + field + "\":";
        int start = json.indexOf(search);
        if (start == -1) return "";
        start += search.length();
        while (start < json.length() && json.charAt(start) == ' ') start++;
        if (start >= json.length()) return "";
        if (json.charAt(start) == '"') { start++; int end = json.indexOf('"', start); return end == -1 ? "" : json.substring(start, end); }
        else { int end = start; while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}' && json.charAt(end) != ']') end++; return json.substring(start, end).trim(); }
    }

    public void showMessage(String msg) {
        Platform.runLater(() -> {
            Alert a = new Alert(AlertType.INFORMATION, msg);
            a.setTitle("NikChess");
            a.setHeaderText(null);
            a.show();
        });
    }

    // ==================== СОХРАНЕНИЕ ПАРТИЙ ====================
    private void saveCurrentGame() {
        if (gameNotation.isEmpty()) { showMessage("Нет ходов для сохранения!"); return; }
        File dir = new File("saved_games");
        if (!dir.exists()) dir.mkdirs();
        String date = java.time.LocalDate.now().toString();
        String time = java.time.LocalTime.now().toString().substring(0, 8).replace(":", "-");
        String filename = "saved_games/" + date + "_" + time + ".pgn";
        try (PrintWriter pw = new PrintWriter(new FileWriter(filename))) {
            pw.println("[Event \"NikChess Game\"]");
            pw.println("[Date \"" + date + "\"]");
            pw.println("[White \"" + (playerColor ? "Игрок" : "Компьютер") + "\"]");
            pw.println("[Black \"" + (playerColor ? "Компьютер" : "Игрок") + "\"]");
            pw.println();
            for (String move : gameNotation) pw.print(move + " ");
            pw.println();
            showMessage("✅ Партия сохранена:\nsaved_games/" + date + "_" + time + ".pgn");
        } catch (IOException e) { showMessage("Ошибка сохранения!"); }
    }
private void replayGameFromFile(String path) {
    try {
        List<String> lines = java.nio.file.Files.readAllLines(new File(path).toPath());
        
        // Собираем всю информацию
        StringBuilder info = new StringBuilder();
        StringBuilder moves = new StringBuilder();
        
        for (String line : lines) {
            if (line.startsWith("[")) {
                info.append(line).append("\n");
            } else if (!line.trim().isEmpty()) {
                moves.append(line).append(" ");
            }
        }
        
        // Показываем в окне с возможностью пролистать
        Stage replayStage = new Stage();
        replayStage.setTitle("Просмотр партии");
        
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(15));
        vbox.setStyle("-fx-background-color: #1a1a2e;");
        
        Label titleLabel = new Label("📜 Партия");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: white;");
        
        TextArea infoArea = new TextArea(info.toString());
        infoArea.setEditable(false);
        infoArea.setPrefRowCount(6);
        infoArea.setStyle("-fx-font-size: 12px; -fx-font-family: 'Consolas', monospace; " +
            "-fx-control-inner-background: #16213e; -fx-text-fill: #bdc3c7;");
        
        TextArea movesArea = new TextArea(moves.toString());
        movesArea.setEditable(false);
        movesArea.setPrefRowCount(20);
        movesArea.setWrapText(true);
        movesArea.setStyle("-fx-font-size: 16px; -fx-font-family: 'Consolas', monospace; " +
            "-fx-control-inner-background: #1a1a2e; -fx-text-fill: white; " +
            "-fx-highlight-fill: #3498db; -fx-highlight-text-fill: white;");
        
        Button closeBtn = new Button("Закрыть");
        closeBtn.setStyle("-fx-font-size: 14px; -fx-padding: 10px 30px; " +
            "-fx-background-color: #7f8c8d; -fx-text-fill: white; -fx-background-radius: 8px;");
        closeBtn.setOnAction(e -> replayStage.close());
        
        vbox.getChildren().addAll(titleLabel, infoArea, new Label("Ходы:") {{ setStyle("-fx-text-fill: #f39c12; -fx-font-weight: bold;"); }}, movesArea, closeBtn);
        
        replayStage.setScene(new Scene(vbox, 550, 600));
        replayStage.show();
        
    } catch (Exception e) {
        showMessage("Ошибка загрузки партии: " + e.getMessage());
    }
}
// Вспомогательный метод: перезагружает доску и применяет N ходов
private void reloadBoardAndApplyMoves(List<int[]> allMoves, int count) {
    // Сбрасываем доску в начальную позицию
    for (int r = 0; r < 8; r++) Arrays.fill(board[r], 0);
    whiteTurn = true;
    whiteKingMoved = false; whiteRookKingSideMoved = false; whiteRookQueenSideMoved = false;
    blackKingMoved = false; blackRookKingSideMoved = false; blackRookQueenSideMoved = false;
    
    // Расставляем фигуры
    setupPiecesDirectly();
    redrawBoard();
    
    // Применяем ходы до указанного
    for (int i = 0; i < count; i++) {
        int[] move = allMoves.get(i);
        applyMoveDirectly(move);
    }
    redrawBoard();
}

// Прямая расстановка фигур (без UI)
private void setupPiecesDirectly() {
    // Белые пешки
    for (int c = 0; c < 8; c++) board[6][c] = 1;
    // Черные пешки
    for (int c = 0; c < 8; c++) board[1][c] = 7;
    // Белые фигуры
    int[] order = {4, 2, 3, 5, 6, 3, 2, 4};
    for (int c = 0; c < 8; c++) board[7][c] = order[c];
    // Черные фигуры
    for (int c = 0; c < 8; c++) board[0][c] = order[c] + 6;
}

// Применяет ход напрямую к board[][]
private void applyMoveDirectly(int[] move) {
    int fr = move[0], fc = move[1], tr = move[2], tc = move[3];
    board[tr][tc] = board[fr][fc];
    board[fr][fc] = 0;
    whiteTurn = !whiteTurn;
}
    private void showSavedGames() {
        Stage sg = new Stage(); sg.setTitle("Сохранённые партии");
        VBox vbox = new VBox(15); vbox.setPadding(new Insets(20)); vbox.setStyle("-fx-background-color: #1a1a2e;");
        Label title = new Label("📜 Сохранённые партии"); title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: white;");
        
        javafx.scene.control.ListView<String> gameList = new javafx.scene.control.ListView<>();
        gameList.setStyle("-fx-background-color: #16213e; -fx-text-fill: white; -fx-font-size: 14px;");
        
        File dir = new File("saved_games");
        if (dir.exists()) {
            File[] files = dir.listFiles((d, n) -> n.endsWith(".pgn"));
            if (files != null) for (File f : files) gameList.getItems().add(f.getName());
        }
        if (gameList.getItems().isEmpty()) gameList.getItems().add("Нет сохранённых партий");
        
        Button loadBtn = new Button("📂 Посмотреть");
        loadBtn.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 10px 20px; -fx-background-color: #2ecc71; -fx-text-fill: white; -fx-background-radius: 8px;");
        loadBtn.setOnAction(e -> {
    String sel = gameList.getSelectionModel().getSelectedItem();
    if (sel != null && !sel.equals("Нет сохранённых партий")) {
        replayGameFromFile("saved_games/" + sel);
        sg.close();
    }
});
        
        Button delBtn = new Button("🗑 Удалить");
        delBtn.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 10px 20px; -fx-background-color: #c0392b; -fx-text-fill: white; -fx-background-radius: 8px;");
        delBtn.setOnAction(e -> {
            String sel = gameList.getSelectionModel().getSelectedItem();
            if (sel != null && !sel.equals("Нет сохранённых партий")) {
                new File("saved_games/" + sel).delete();
                gameList.getItems().remove(sel);
            }
        });
        
        Button closeBtn = new Button("Закрыть");
        closeBtn.setStyle("-fx-font-size: 14px; -fx-padding: 10px 20px; -fx-background-color: #7f8c8d; -fx-text-fill: white; -fx-background-radius: 8px;");
        closeBtn.setOnAction(e -> sg.close());
        
        HBox btns = new HBox(10, loadBtn, delBtn, closeBtn); btns.setAlignment(Pos.CENTER);
        vbox.getChildren().addAll(title, gameList, btns);
        sg.setScene(new Scene(vbox, 500, 500)); sg.show();
    }
    private int[] parseAlgebraicMove(String move) {
    if (move == null || move.length() < 2) return null;
    
    // Убираем символы +, #, x
    move = move.replace("+", "").replace("#", "").replace("x", "");
    if (move.isEmpty()) return null;
    
    // Рокировка
    if (move.equals("O-O") || move.equals("0-0")) {
        int row = whiteTurn ? 7 : 0;
        return new int[]{row, 4, row, 6};
    }
    if (move.equals("O-O-O") || move.equals("0-0-0")) {
        int row = whiteTurn ? 7 : 0;
        return new int[]{row, 4, row, 2};
    }
    
    // Последние 2 символа — клетка назначения
    char tc = move.charAt(move.length() - 2);
    char tr = move.charAt(move.length() - 1);
    int toCol = tc - 'a';
    int toRow = 8 - (tr - '0');
    
    if (toCol < 0 || toCol > 7 || toRow < 0 || toRow > 7) return null;
    
    // Определяем тип фигуры по первой букве
    char pieceChar = move.charAt(0);
    if (pieceChar >= 'a' && pieceChar <= 'h') pieceChar = 'P'; // пешка
    
    // Ищем фигуру, которая может пойти на toRow, toCol
    for (int r = 0; r < 8; r++) {
        for (int c = 0; c < 8; c++) {
            if (board[r][c] != 0 && isPieceWhite(board[r][c]) == whiteTurn) {
                Piece p = getPieceType(r, c);
                boolean match = false;
                if (pieceChar == 'K' && p instanceof King) match = true;
                else if (pieceChar == 'Q' && p instanceof Queen) match = true;
                else if (pieceChar == 'R' && p instanceof Castle) match = true;
                else if (pieceChar == 'B' && p instanceof Bishop) match = true;
                else if (pieceChar == 'N' && p instanceof Knight) match = true;
                else if (pieceChar == 'P' && p instanceof Pawn) match = true;
                
                if (match) {
                    List<int[]> legal = getLegalMovesForPiece(p, r, c, whiteTurn);
                    for (int[] m : legal) {
                        if (m[0] == toRow && m[1] == toCol) {
                            return new int[]{r, c, toRow, toCol};
                        }
                    }
                }
            }
        }
    }
    return null;
}
    private void loadAndShowGame(String filename) {
        try {
            List<String> lines = java.nio.file.Files.readAllLines(new File(filename).toPath());
            StringBuilder sb = new StringBuilder();
            for (String line : lines) if (!line.startsWith("[")) sb.append(line).append(" ");
            Stage vs = new Stage(); vs.setTitle("Просмотр партии");
            TextArea ta = new TextArea(sb.toString()); ta.setEditable(false);
           ta.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 14px; " +
    "-fx-control-inner-background: #1a1a2e; -fx-text-fill: white; " +
    "-fx-highlight-fill: #3498db; -fx-highlight-text-fill: white;");
            Button cb = new Button("Закрыть"); cb.setStyle("-fx-font-size: 14px; -fx-padding: 10px 30px; -fx-background-color: #7f8c8d; -fx-text-fill: white; -fx-background-radius: 8px;");
            cb.setOnAction(e -> vs.close());
            VBox v = new VBox(10, ta, cb); v.setPadding(new Insets(15)); v.setStyle("-fx-background-color: #1a1a2e;"); v.setAlignment(Pos.CENTER);
            vs.setScene(new Scene(v, 550, 500)); vs.show();
        } catch (IOException e) { showMessage("Ошибка загрузки: " + e.getMessage()); }
    }

    // ==================== СЕТЬ ====================
    private void showNetworkMenuFull() {
        Stage ds = new Stage(); ds.initOwner(stage); ds.setTitle("Сетевая игра");
        VBox vbox = new VBox(20); vbox.setPadding(new Insets(20)); vbox.setAlignment(Pos.CENTER);
        Button hostBtn = new Button("Создать сервер"); hostBtn.setOnAction(e -> { ds.close(); showHostDialog(); });
        Button joinBtn = new Button("Подключиться к серверу"); joinBtn.setOnAction(e -> { ds.close(); showJoinDialog(); });
        vbox.getChildren().addAll(new Label("Выберите режим:"), hostBtn, joinBtn);
        ds.setScene(new Scene(vbox, 300, 200)); ds.show();
    }
    
    private void showHostDialog() {
        TextInputDialog dialog = new TextInputDialog("5555");
        dialog.setTitle("Создание сервера"); dialog.setContentText("Порт:");
        dialog.showAndWait().ifPresent(port -> {
            try {
                int p = Integer.parseInt(port);
                new Thread(() -> { new ChessServer(p).start(); }).start();
                networkClient = new NetworkClient(this);
                if (networkClient.connect("localhost", p)) { networkGame = true; showWaitingScreen(); }
            } catch (NumberFormatException e) { showMessage("Некорректный порт!"); }
        });
    }
    
    private void showJoinDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Подключение к серверу");
        ButtonType connectBtn = new ButtonType("Подключиться", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(connectBtn, ButtonType.CANCEL);
        GridPane grid = new GridPane(); grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(20, 150, 10, 10));
        TextField hostField = new TextField("localhost"); TextField portField = new TextField("5555");
        grid.add(new Label("Хост:"), 0, 0); grid.add(hostField, 1, 0);
        grid.add(new Label("Порт:"), 0, 1); grid.add(portField, 1, 1);
        dialog.getDialogPane().setContent(grid);
        dialog.showAndWait().ifPresent(r -> {
            if (r == connectBtn) {
                networkClient = new NetworkClient(this);
                if (networkClient.connect(hostField.getText(), Integer.parseInt(portField.getText()))) { networkGame = true; showWaitingScreen(); }
                else showMessage("Не удалось подключиться!");
            }
        });
    }
    
    private void showWaitingScreen() {
        VBox root = new VBox(20); root.setAlignment(Pos.CENTER); root.setStyle("-fx-padding: 50px;");
        root.getChildren().addAll(new Label("Ожидание соперника..."), new ProgressIndicator(), new Button("Отмена") {{ setOnAction(e -> { networkClient.disconnect(); networkGame = false; showMainMenu(); }); }});
        stage.setScene(new Scene(root, 400, 300));
    }

    public void setNetworkPlayerColor(boolean w) { this.networkPlayerIsWhite = w; this.playerColor = w; }
    public boolean isWhiteTurn() { return whiteTurn; }
    public void startNetworkGame() { Platform.runLater(this::showGameBoard); }
    public void receiveOpponentMove(String moveStr) {
        String[] parts = moveStr.split(",");
        if (parts.length == 4) Platform.runLater(() -> applyOpponentMove(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3])));
    }
    private void applyOpponentMove(int fr, int fc, int tr, int tc) {
        ImageView piece = findPieceAt(fr, fc);
        if (piece != null) {
            boolean iw = isPieceWhite(board[fr][fc]);
            if (getPieceType(fr, fc) instanceof King && Math.abs(tc - fc) == 2) performCastle(iw, tc > fc);
            else movePieceToNetwork(piece, tr, tc, fr, fc, iw);
            clearHighlights();
            if (selectedPiece != null) { selectedPiece.setStyle(""); selectedPiece = null; }
        }
    }
    private void movePieceToNetwork(ImageView piece, int nr, int nc, int or, int oc, boolean iw) {
        Piece pt = getPieceType(or, oc);
        if (pt instanceof King) { if (iw) whiteKingMoved = true; else blackKingMoved = true; }
        else if (pt instanceof Castle) { if (iw) { if (oc == 7) whiteRookKingSideMoved = true; if (oc == 0) whiteRookQueenSideMoved = true; } else { if (oc == 7) blackRookKingSideMoved = true; if (oc == 0) blackRookQueenSideMoved = true; } }
        int cap = board[nr][nc];
        boardSquares[or][oc].getChildren().remove(piece);
        if (cap != 0) boardSquares[nr][nc].getChildren().removeIf(n -> n instanceof ImageView && n != piece);
        boardSquares[nr][nc].getChildren().add(piece);
        board[or][oc] = 0; board[nr][nc] = iw ? getPieceValueForColor(pt, true) : getPieceValueForColor(pt, false);
        piece.getProperties().put("row", nr); piece.getProperties().put("col", nc);
        Piece cp = (Piece) piece.getProperties().get("piece");
        if (cp != null) cp.setPosition(new double[]{nc * cellSize + cellSize/2, nr * cellSize + cellSize/2});
        if (cp instanceof Pawn && (nr == 0 || nr == 7)) {
            if (iw != networkPlayerIsWhite) promotePawnComputer(piece, nr, nc, iw);
            else promotePawn(piece, nr, nc, iw);
            return;
        }
        boolean opp = !iw;
        if (isCheckmate(opp)) { gameOver = true; if (chessClock != null) chessClock.pause(); showGameOver("Мат! " + (iw ? "Белые" : "Черные") + " победили!", true); }
        else if (isStalemate(opp)) { gameOver = true; if (chessClock != null) chessClock.pause(); showGameOver("Пат! Ничья!", true); }
        else { whiteTurn = !whiteTurn; updateTurnLabel(); if (chessClock != null) chessClock.switchTurn(); if (isKingInCheck(opp)) showCheckNotification(opp); saveGameState(); }
    }
    public void receiveChatMessage(String msg) { if (chatArea != null) chatArea.appendText("Соперник: " + msg + "\n"); }
    public void opponentResigned() { Platform.runLater(() -> { gameOver = true; showGameOver("Соперник сдался! Вы победили!", true); }); }
    public void receiveDrawOffer() {
        Platform.runLater(() -> {
            Alert a = new Alert(AlertType.CONFIRMATION); a.setTitle("Ничья"); a.setContentText("Соперник предлагает ничью. Принять?");
            a.showAndWait().ifPresent(r -> { if (r == ButtonType.OK) { networkClient.sendDrawAccept(); gameOver = true; showGameOver("Ничья!", true); } });
        });
    }
    public void drawAccepted() { Platform.runLater(() -> { gameOver = true; showGameOver("Ничья!", true); }); }
    public void showConnectionLost() { Alert a = new Alert(AlertType.ERROR, "Соединение потеряно"); a.showAndWait(); networkClient.disconnect(); networkGame = false; showMainMenu(); }

    // ==================== ЗАПИСЬ ХОДОВ ====================
    private String moveToNotation(int fr, int fc, int tr, int tc, Piece p, boolean cap, boolean cas, boolean chk, boolean mate) {
        StringBuilder sb = new StringBuilder();
        if (cas) { sb.append(tc > fc ? "O-O" : "O-O-O"); }
        else {
            if (p instanceof King) sb.append("K");
            else if (p instanceof Queen) sb.append("Q");
            else if (p instanceof Castle) sb.append("R");
            else if (p instanceof Bishop) sb.append("B");
            else if (p instanceof Knight) sb.append("N");
            if (cap) { if (p instanceof Pawn) sb.append((char)('a'+fc)); sb.append("x"); }
            sb.append((char)('a'+tc)).append(8-tr);
        }
        if (mate) sb.append("#"); else if (chk) sb.append("+");
        return sb.toString();
    }
    
    private void addMoveToHistory(int fr, int fc, int tr, int tc, boolean iw, boolean cas, boolean prom) {
        Piece p = getPieceType(fr, fc);
        boolean cap = board[tr][tc] != 0;
        int sc = board[tr][tc], sm = board[fr][fc];
        board[tr][tc] = sm; board[fr][fc] = 0;
        boolean chk = isKingInCheck(!iw), mate = chk && isCheckmate(!iw);
        board[fr][fc] = sm; board[tr][tc] = sc;
        String not = moveToNotation(fr, fc, tr, tc, p, cap, cas, chk, mate);
        FullMoveRecord r = new FullMoveRecord();
        r.fromRow=fr; r.fromCol=fc; r.toRow=tr; r.toCol=tc; r.movedPiece=sm; r.capturedPiece=sc;
        r.wasWhiteTurn=iw; r.wasCastle=cas; r.wasPromotion=prom; r.notation=not;
        r.wKingMoved=whiteKingMoved; r.wRookKMoved=whiteRookKingSideMoved; r.wRookQMoved=whiteRookQueenSideMoved;
        r.bKingMoved=blackKingMoved; r.bRookKMoved=blackRookKingSideMoved; r.bRookQMoved=blackRookQueenSideMoved;
        fullMoveHistory.add(r);
        if (iw) gameNotation.add(moveNumber + ". " + not);
        else { if (!gameNotation.isEmpty()) gameNotation.set(gameNotation.size()-1, gameNotation.get(gameNotation.size()-1)+" "+not); moveNumber++; }
    }
    
    private void showGameHistory() {
        Stage hs = new Stage(); hs.setTitle("История партии");
        TextArea ta = new TextArea(); ta.setEditable(false);
        ta.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 14px; -fx-background-color: #1a1a2e; -fx-text-fill: white;");
        StringBuilder sb = new StringBuilder("=== НОТАЦИЯ ===\n\n");
        for (String s : gameNotation) sb.append(s).append("\n");
        sb.append("\n=== ВСЕ ХОДЫ ===\n\n");
        for (FullMoveRecord r : fullMoveHistory) {
            sb.append(r.wasWhiteTurn ? "Белые: " : "Черные: ");
            sb.append((char)('a'+r.fromCol)).append(8-r.fromRow);
            sb.append(r.wasCastle ? " ♜ " : " → ");
            sb.append((char)('a'+r.toCol)).append(8-r.toRow);
            sb.append("  [").append(r.notation).append("]\n");
        }
        ta.setText(sb.toString());
        Button cb = new Button("Закрыть"); cb.setOnAction(e -> hs.close());
        VBox v = new VBox(10, ta, cb); v.setPadding(new Insets(15)); v.setStyle("-fx-background-color: #1a1a2e;");
        hs.setScene(new Scene(v, 500, 500)); hs.show();
    }

    // ==================== ВЫПОЛНЕНИЕ ХОДА ====================
    private void executeMove(int[] move) {
        saveGameState();
        ImageView piece = findPieceAt(move[0], move[1]);
        if (piece != null) {
            boolean iw = isPieceWhite(board[move[0]][move[1]]);
            Piece pt = getPieceType(move[0], move[1]);
            if (pt instanceof King && Math.abs(move[3]-move[1])==2) {
                performCastle(iw, move[3]>move[1]);
                addMoveToHistory(move[0], move[1], move[2], move[3], iw, true, false);
                clearHighlights();
                if (selectedPiece!=null){selectedPiece.setStyle("");selectedPiece=null;}
                boolean opp=!iw;
                if (isCheckmate(opp)){gameOver=true;if(chessClock!=null)chessClock.pause();showGameOver("Мат! "+(iw?"Белые":"Черные")+" победили!",true);}
                else if (isStalemate(opp)){gameOver=true;if(chessClock!=null)chessClock.pause();showGameOver("Пат! Ничья!",true);}
                else{whiteTurn=!whiteTurn;updateTurnLabel();if(chessClock!=null)chessClock.switchTurn();if(isKingInCheck(opp))showCheckNotification(opp);if(vsComputer&&whiteTurn!=playerColor)scheduleComputerMove();}
            } else {
                movePieceTo(piece, move[2], move[3], move[0], move[1], iw);
            }
        }
    }

    private void saveGameState() {
    moveHistory.push(new GameState());
    System.out.println("SAVED state #" + moveHistory.size() + " whiteTurn=" + whiteTurn);
}

    // ==================== ОТМЕНА ХОДА ====================
  private void undoMove() {
    System.out.println("STACK SIZE: " + moveHistory.size());
    
    // Для отладки — просто проверяем, что стек не пуст
    if (moveHistory.size() <= 1) {
        System.out.println("STACK TOO SMALL");
        return;
    }
    
    // Удаляем 2 последних
    if (moveHistory.size() >= 3) {
        moveHistory.pop();
        moveHistory.pop();
    } else {
        moveHistory.pop();
    }
    
    System.out.println("STACK AFTER POP: " + moveHistory.size());
    
    // Восстанавливаем
    moveHistory.peek().restore();
    redrawBoard();
    
    // Очистка
    if (selectedPiece != null) { selectedPiece.setStyle(""); selectedPiece = null; }
    clearHighlights();
    gameOver = false;
    computerPromoting = false;
    whiteTurn = playerColor;
    updateTurnLabel();
}
    private Piece createPieceFromValue(int v) { int t=v>6?v-6:v; switch(t){case 1:return new Pawn(new double[]{0,0});case 2:return new Knight(new double[]{0,0});case 3:return new Bishop(new double[]{0,0});case 4:return new Castle(new double[]{0,0});case 5:return new Queen(new double[]{0,0});case 6:return new King(new double[]{0,0});default:return null;} }

    // ==================== ХОД КОМПЬЮТЕРА ====================
    private void scheduleComputerMove() { PauseTransition p=new PauseTransition(Duration.seconds(0.3)); if(level==0)p.setOnFinished(e->makeComputerMoveLevel0()); else if(level==1)p.setOnFinished(e->makeComputerMoveLevel1()); else p.setOnFinished(e->makeComputerMoveLevel2()); p.play(); }
    private void redrawBoard() {
    // Удаляем все фигуры с доски
    for (int row = 0; row < 8; row++) {
        for (int col = 0; col < 8; col++) {
            boardSquares[row][col].getChildren().removeIf(n -> n instanceof ImageView);
        }
    }
    
    // Перерисовываем фигуры по массиву board
    for (int row = 0; row < 8; row++) {
        for (int col = 0; col < 8; col++) {
            int pieceValue = board[row][col];
            if (pieceValue != 0) {
                boolean isWhite = isPieceWhite(pieceValue);
                Piece piece = createPieceFromValue(pieceValue);
                
                if (piece != null) {
                    ImageView pieceView = piece.createPieceView(isWhite);
                    pieceView.setFitWidth(cellSize * 0.8);
                    pieceView.setFitHeight(cellSize * 0.8);
                    pieceView.setPreserveRatio(true);
                    
                    pieceView.getProperties().put("piece", piece);
                    pieceView.getProperties().put("row", row);
                    pieceView.getProperties().put("col", col);
                    pieceView.getProperties().put("isWhite", isWhite);
                    
                    boardSquares[row][col].getChildren().add(pieceView);
                    
                    String pieceName = (isWhite ? "Бел" : "Черн") + 
                        (piece instanceof Pawn ? "ая пешка" :
                         piece instanceof Knight ? "ый конь" :
                         piece instanceof Bishop ? "ый слон" :
                         piece instanceof Castle ? "ая ладья" :
                         piece instanceof Queen ? "ый ферзь" : "ый король");
                    
                    addClickHandler(pieceView, pieceName, piece, isWhite);
                }
            }
        }
    }
}
    private void makeComputerMoveLevel2() {
    if (gameOver || computerPromoting) return;
    boolean cc = !playerColor;
    if (whiteTurn != cc) return;
    
    try {
        if (scoriaEngine == null) {
            scoriaEngine = new UCIEngine();
            Thread.sleep(1000);
        }
        if (scoriaEngine != null && scoriaEngine.isReady) {
            // Уровень 2, 3, 4 → разная сила
            String best = scoriaEngine.getBestMove(level);
            if (best != null && !best.equals("(none)")) {
                int[] move = scoriaEngine.parseMove(best);
                if (move != null && board[move[0]][move[1]] != 0 
                    && isPieceWhite(board[move[0]][move[1]]) == cc) {
                    executeMove(move);
                }
            }
        }
    } catch (Exception e) { e.printStackTrace(); }
}

       private class UCIEngine {
        private Process engineProcess;
        private BufferedReader engineReader;
        private PrintWriter engineWriter;
        public boolean isReady = false;
        
        public UCIEngine() {
            try {
                File engineFile = null;
                String[] paths = {"lib/scoria.jar", "scoria.jar", "../lib/scoria.jar", "./lib/scoria.jar"};
                for (String p : paths) { File f = new File(p); if (f.exists()) { engineFile = f; break; } }
                if (engineFile == null) { System.err.println("Движок не найден"); return; }
                
                engineProcess = new ProcessBuilder("java", "-jar", engineFile.getAbsolutePath()).start();
                engineReader = new BufferedReader(new InputStreamReader(engineProcess.getInputStream()));
                engineWriter = new PrintWriter(engineProcess.getOutputStream(), true);
                
                new Thread(() -> {
                    try { BufferedReader er = new BufferedReader(new InputStreamReader(engineProcess.getErrorStream()));
                        String l; while ((l = er.readLine()) != null) System.err.println("Scoria ERR: " + l);
                    } catch (IOException e) {}
                }).start();
                
                Thread.sleep(1000);
                if (!engineProcess.isAlive()) return;
                
                sendCommand("uci");
                sendCommand("setoption name Skill Level value 1"); // начинаем с минимального
sendCommand("isready");
                String line; long start = System.currentTimeMillis();
                while ((line = engineReader.readLine()) != null) {
                    if (line.equals("uciok")) break;
                    if (System.currentTimeMillis() - start > 10000) return;
                }
                
                sendCommand("isready"); start = System.currentTimeMillis();
                while ((line = engineReader.readLine()) != null) {
                    if (line.equals("readyok")) { isReady = true; break; }
                    if (System.currentTimeMillis() - start > 10000) return;
                }
            } catch (Exception e) { e.printStackTrace(); }
        }
        
        private void sendCommand(String cmd) { if (engineWriter != null) { engineWriter.println(cmd); engineWriter.flush(); } }
        
          public String getBestMove(int strength) {
        if (!isReady) return null;
        try {
            String fen = boardToFEN();
            sendCommand("position fen " + fen);
            switch (strength) {
                case 2: sendCommand("go nodes 50 depth 1"); break;
                case 3: sendCommand("go nodes 300 depth 2"); break;
                case 4: sendCommand("go nodes 2000 depth 4"); break;
                case 5: sendCommand("go nodes 10000 depth 8"); break;
                default: sendCommand("go nodes 100 depth 2");
            }
            String line;
            long start = System.currentTimeMillis();
            while ((line = engineReader.readLine()) != null) {
                if (line.startsWith("bestmove")) { String[] parts = line.split(" "); if (parts.length >= 2) return parts[1]; }
                if (System.currentTimeMillis() - start > 10000) break;
            }
        } catch (IOException e) {}
        return null;
    }
        
        private String boardToFEN() {
            StringBuilder fen = new StringBuilder();
            for (int r = 0; r < 8; r++) {
                int empty = 0;
                for (int c = 0; c < 8; c++) {
                    if (board[r][c] == 0) empty++;
                    else {
                        if (empty > 0) { fen.append(empty); empty = 0; }
                        switch (board[r][c]) {
                            case 1: fen.append('P'); break; case 2: fen.append('N'); break;
                            case 3: fen.append('B'); break; case 4: fen.append('R'); break;
                            case 5: fen.append('Q'); break; case 6: fen.append('K'); break;
                            case 7: fen.append('p'); break; case 8: fen.append('n'); break;
                            case 9: fen.append('b'); break; case 10: fen.append('r'); break;
                            case 11: fen.append('q'); break; case 12: fen.append('k'); break;
                        }
                    }
                }
                if (empty > 0) fen.append(empty);
                if (r < 7) fen.append('/');
            }
            fen.append(whiteTurn ? " w" : " b");
            String cas = "";
            if (!whiteKingMoved) { if (!whiteRookKingSideMoved) cas += "K"; if (!whiteRookQueenSideMoved) cas += "Q"; }
            if (!blackKingMoved) { if (!blackRookKingSideMoved) cas += "k"; if (!blackRookQueenSideMoved) cas += "q"; }
            fen.append(" ").append(cas.isEmpty() ? "-" : cas).append(" - 0 1");
            return fen.toString();
        }
        
        public int[] parseMove(String s) {
            if (s == null || s.length() < 4) return null;
            try {
                int fc = s.charAt(0) - 'a', fr = 8 - (s.charAt(1) - '0');
                int tc = s.charAt(2) - 'a', tr = 8 - (s.charAt(3) - '0');
                if (fr < 0 || fr >= 8 || fc < 0 || fc >= 8 || tr < 0 || tr >= 8 || tc < 0 || tc >= 8) return null;
                return new int[]{fr, fc, tr, tc};
            } catch (Exception e) { return null; }
        }
        
        public void close() {
            if (engineProcess != null) { sendCommand("quit"); try { engineProcess.waitFor(1, TimeUnit.SECONDS); } catch (Exception e) {} engineProcess.destroy(); }
        }
    }

    private class MoveScore {
        int[] move; int score;
        MoveScore(int[] m, int s) { move = m; score = s; }
    }

    private void makeComputerMoveLevel1() {
        if (gameOver || computerPromoting) return;
        boolean cc = !playerColor;
        if (whiteTurn != cc) return;
        List<MoveScore> scored = new ArrayList<>();
        for (int r = 0; r < 8; r++) for (int c = 0; c < 8; c++) if (board[r][c] != 0 && isPieceWhite(board[r][c]) == cc) {
            Piece p = getPieceType(r, c);
            for (int[] m : getLegalMovesForPiece(p, r, c, cc))
                scored.add(new MoveScore(new int[]{r, c, m[0], m[1]}, evaluateMoveFast(r, c, m[0], m[1], cc)));
        }
        if (!scored.isEmpty()) {
            scored.sort((a, b) -> b.score - a.score);
            int idx = 0;
            if (scored.size() > 3 && Math.random() < 0.15) idx = 1 + new Random().nextInt(Math.min(2, scored.size() - 1));
            executeMove(scored.get(idx).move);
        }
    }

    private int evaluateMoveFast(int fr, int fc, int tr, int tc, boolean iw) {
        int score = 0;
        if (board[tr][tc] != 0) {
            int cv = getPieceValue(getPieceType(tr, tc));
            if (!isSquareAttacked(tr, tc, !iw)) score += cv * 10;
            else {
                int mv = getPieceValue(getPieceType(fr, fc));
                if (cv > mv) score += cv * 8;
                else if (cv < mv) score -= mv * 5;
                else score += 30;
            }
        }
        if (wouldGiveCheck(fr, fc, tr, tc, iw)) score += 100;
        if (isMoveSafe(fr, fc, tr, tc, iw)) score += 20; else score -= 30;
        if (tr >= 2 && tr <= 5 && tc >= 2 && tc <= 5) score += 15;
        if (getPieceType(fr, fc) instanceof Pawn && (tr == 0 || tr == 7)) score += 200;
        return score;
    }

    private int getPieceValue(Piece p) {
        if (p instanceof Pawn) return 100; if (p instanceof Knight) return 320; if (p instanceof Bishop) return 330;
        if (p instanceof Castle) return 500; if (p instanceof Queen) return 900; if (p instanceof King) return 20000;
        return 0;
    }

    private boolean isMoveSafe(int fr, int fc, int tr, int tc, boolean iw) {
        int cap = board[tr][tc], mov = board[fr][fc];
        board[fr][fc] = 0; board[tr][tc] = mov;
        boolean at = isSquareAttacked(tr, tc, !iw);
        board[fr][fc] = mov; board[tr][tc] = cap;
        return !at;
    }

    private boolean wouldGiveCheck(int fr, int fc, int tr, int tc, boolean iw) {
        int cap = board[tr][tc], mov = board[fr][fc];
        board[fr][fc] = 0; board[tr][tc] = mov;
        boolean chk = isKingInCheck(!iw);
        board[fr][fc] = mov; board[tr][tc] = cap;
        return chk;
    }

    private void makeComputerMoveLevel0() {
        if (gameOver || computerPromoting) return;
        boolean cc = !playerColor;
        if (whiteTurn != cc) return;
        List<int[]> all = new ArrayList<>();
        for (int r = 0; r < 8; r++) for (int c = 0; c < 8; c++) if (board[r][c] != 0 && isPieceWhite(board[r][c]) == cc)
            for (int[] m : getLegalMovesForPiece(getPieceType(r, c), r, c, cc)) all.add(new int[]{r, c, m[0], m[1]});
        if (!all.isEmpty()) executeMove(all.get((int)(Math.random() * all.size())));
    }

    // ==================== MOVE PIECE TO ====================
    private void movePieceTo(ImageView piece, int nr, int nc, int or, int oc, boolean iw) {
    if (iw != whiteTurn) return;
    Piece pt = getPieceType(or, oc);
    if (pt instanceof King && isSquareAttacked(nr, nc, !iw)) return;
    if (networkGame && networkClient != null && iw == networkPlayerIsWhite) networkClient.sendMove(or, oc, nr, nc);
    
    // СОХРАНЯЕМ СОСТОЯНИЕ ПЕРЕД ХОДОМт
    saveGameState();
    
    if (pt instanceof King) { if (iw) whiteKingMoved = true; else blackKingMoved = true; }
    else if (pt instanceof Castle) {
        if (iw) { if (oc == 7) whiteRookKingSideMoved = true; if (oc == 0) whiteRookQueenSideMoved = true; }
        else { if (oc == 7) blackRookKingSideMoved = true; if (oc == 0) blackRookQueenSideMoved = true; }
    }
    
    addMoveToHistory(or, oc, nr, nc, iw, false, pt instanceof Pawn && (nr == 0 || nr == 7));
    boardSquares[or][oc].getChildren().remove(piece);
    if (board[nr][nc] != 0) boardSquares[nr][nc].getChildren().removeIf(n -> n instanceof ImageView);
    boardSquares[nr][nc].getChildren().add(piece);
    board[or][oc] = 0;
    board[nr][nc] = iw ? getPieceValueForColor(pt, true) : getPieceValueForColor(pt, false);
    piece.getProperties().put("row", nr);
    piece.getProperties().put("col", nc);
    
    if (pt instanceof Pawn && (nr == 0 || nr == 7)) {
        if (vsComputer && iw != playerColor) { computerPromoting = true; promotePawnComputer(piece, nr, nc, iw); computerPromoting = false; }
        else promotePawn(piece, nr, nc, iw);
        return;
    }
    
    boolean opp = !iw;
    if (isCheckmate(opp)) {
        gameOver = true;
        if (chessClock != null) chessClock.pause();
        showGameOver("Мат! " + (iw ? "Белые" : "Черные") + " победили!", true);
    } else if (isStalemate(opp)) {
        gameOver = true;
        if (chessClock != null) chessClock.pause();
        showGameOver("Пат! Ничья!", true);
    } else {
        whiteTurn = !whiteTurn;
        updateTurnLabel();
        if (chessClock != null) chessClock.switchTurn();
        if (isKingInCheck(opp)) showCheckNotification(opp);
        
        if (vsComputer && whiteTurn != playerColor && !gameOver && !computerPromoting) {
            scheduleComputerMove();
        }
    }
}

    // ==================== ВСПОМОГАТЕЛЬНЫЕ ====================
    private ImageView findPieceAt(int r, int c) { for(Node n:boardSquares[r][c].getChildren()) if(n instanceof ImageView) return (ImageView)n; return null; }
    private boolean isPieceWhite(int v) { return v>=1 && v<=6; }
    private int getPieceValueForColor(Piece p, boolean w) { if(p instanceof Pawn)return w?1:7; if(p instanceof Knight)return w?2:8; if(p instanceof Bishop)return w?3:9; if(p instanceof Castle)return w?4:10; if(p instanceof Queen)return w?5:11; if(p instanceof King)return w?6:12; return 0; }
    private Piece getPieceType(int r, int c) { for(Node n:boardSquares[r][c].getChildren()) if(n instanceof ImageView) return (Piece)((ImageView)n).getProperties().get("piece"); return null; }
    private void clearHighlights() { for(int r=0;r<8;r++) for(int c=0;c<8;c++) boardSquares[r][c].getChildren().removeAll(moveHighlights); moveHighlights.clear(); }
    private void updateTurnLabel() { if(turnLabel!=null){ String t="Ход "+(whiteTurn?"БЕЛЫХ":"ЧЕРНЫХ"); if(vsComputer) t+=(whiteTurn==playerColor?" (игрок)":" (компьютер)"); turnLabel.setText(t); } }
    private void showGameOver(String msg, boolean rst) {
        Platform.runLater(() -> {
            if(chessClock!=null) chessClock.pause();
            if(!gameNotation.isEmpty()){
                Alert sa=new Alert(AlertType.CONFIRMATION); sa.setTitle("Игра окончена"); sa.setHeaderText(msg); sa.setContentText("Сохранить партию?");
                sa.getButtonTypes().setAll(new ButtonType("💾 Сохранить"), new ButtonType("Нет"));
                sa.showAndWait().ifPresent(r->{ if(r.getText().contains("Сохранить")) saveCurrentGame(); });
            } else { new Alert(AlertType.INFORMATION, msg).showAndWait(); }
            if(rst&&restartButton!=null) restartButton.setVisible(true);
        });
    }

    private void restartGame(boolean f) {
        moveHistory.clear(); gameNotation.clear(); fullMoveHistory.clear(); moveNumber=1;
        if(chessClock!=null) chessClock.stop();
        for(int r=0;r<8;r++) for(int c=0;c<8;c++) { boardSquares[r][c].getChildren().clear(); boardSquares[r][c].getChildren().add(new Rectangle(cellSize,cellSize,(r+c)%2==0?Color.web("#f0d9b5"):Color.web("#b58863"))); }
        selectedPiece=null; whiteTurn=true; gameOver=false;
        whiteKingMoved=false;whiteRookKingSideMoved=false;whiteRookQueenSideMoved=false;
        blackKingMoved=false;blackRookKingSideMoved=false;blackRookQueenSideMoved=false;
        for(int r=0;r<8;r++) Arrays.fill(board[r],0);
        if(pendingFEN!=null){ setBoardFromFEN(pendingFEN); pendingFEN=null; redrawBoard(); }
        else setupPieces(null,true);
        saveGameState();
        if(scoriaEngine!=null){scoriaEngine.close();scoriaEngine=null;}
        if(clockEnabled){chessClock=new ChessClock(initialTimeMinutes);chessClock.start();}
        updateTurnLabel(); restartButton.setVisible(false); updateTimeLabels();
        if(vsComputer&&!playerColor) scheduleComputerMove();
    }

    private void showCheckNotification(boolean iw) {
        for(int r=0;r<8;r++) for(int c=0;c<8;c++) if(board[r][c]!=0&&isPieceWhite(board[r][c])==iw&&getPieceType(r,c) instanceof King){
            Rectangle hl=new Rectangle(cellSize-10,cellSize-10); hl.setFill(Color.rgb(255,0,0,0.3)); hl.setStroke(Color.RED); hl.setStrokeWidth(3);
            boardSquares[r][c].getChildren().add(hl);
            final int cr=r,cc=c; PauseTransition p=new PauseTransition(Duration.seconds(2));
            p.setOnFinished(e->boardSquares[cr][cc].getChildren().remove(hl)); p.play();
            break;
        }
    }

    // ==================== ШАХ / МАТ ====================
    private boolean isKingInCheck(boolean iw) {
        for(int r=0;r<8;r++) for(int c=0;c<8;c++) if(board[r][c]!=0&&isPieceWhite(board[r][c])==iw&&getPieceType(r,c) instanceof King) return isSquareAttacked(r,c,!iw);
        return false;
    }
    private boolean isSquareAttacked(int r, int c, boolean byW) {
        for(int rr=0;rr<8;rr++) for(int cc=0;cc<8;cc++) if(board[rr][cc]!=0&&isPieceWhite(board[rr][cc])==byW&&canPieceAttackSquare(getPieceType(rr,cc),rr,cc,r,c,byW)) return true;
        return false;
    }
    private boolean canPieceAttackSquare(Piece p, int fr, int fc, int tr, int tc, boolean iw) {
        if(p instanceof Pawn){ int d=iw?-1:1; return tr==fr+d&&Math.abs(tc-fc)==1; }
        for(int[] m:getAttackMoves(p,fr,fc,iw)) if(m[0]==tr&&m[1]==tc) return true;
        return false;
    }
    private List<int[]> getAttackMoves(Piece p, int r, int c, boolean iw) {
        List<int[]> a=new ArrayList<>();
        if(p instanceof Knight){ int[][] km={{r+2,c+1},{r+2,c-1},{r-2,c+1},{r-2,c-1},{r+1,c+2},{r+1,c-2},{r-1,c+2},{r-1,c-2}}; for(int[] m:km) if(m[0]>=0&&m[0]<8&&m[1]>=0&&m[1]<8) a.add(m); }
        else if(p instanceof Bishop){ int[][] d={{1,1},{1,-1},{-1,1},{-1,-1}}; for(int[] dd:d) for(int i=1;i<8;i++){ int rr=r+dd[0]*i,cc=c+dd[1]*i; if(rr<0||rr>=8||cc<0||cc>=8) break; a.add(new int[]{rr,cc}); if(board[rr][cc]!=0) break; } }
        else if(p instanceof Castle){ int[][] d={{1,0},{-1,0},{0,1},{0,-1}}; for(int[] dd:d) for(int i=1;i<8;i++){ int rr=r+dd[0]*i,cc=c+dd[1]*i; if(rr<0||rr>=8||cc<0||cc>=8) break; a.add(new int[]{rr,cc}); if(board[rr][cc]!=0) break; } }
        else if(p instanceof Queen){ int[][] d={{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}}; for(int[] dd:d) for(int i=1;i<8;i++){ int rr=r+dd[0]*i,cc=c+dd[1]*i; if(rr<0||rr>=8||cc<0||cc>=8) break; a.add(new int[]{rr,cc}); if(board[rr][cc]!=0) break; } }
        else if(p instanceof King){ int[][] km={{r+1,c},{r-1,c},{r,c+1},{r,c-1},{r+1,c+1},{r+1,c-1},{r-1,c+1},{r-1,c-1}}; for(int[] m:km) if(m[0]>=0&&m[0]<8&&m[1]>=0&&m[1]<8) a.add(m); }
        return a;
    }

    // ==================== ЛЕГАЛЬНЫЕ ХОДЫ ====================
    private List<int[]> getRawMoves(Piece p, int r, int c, boolean iw) {
        List<int[]> m=new ArrayList<>();
        if(p instanceof Pawn){ int d=iw?-1:1,os=r+d; if(os>=0&&os<8){ if(board[os][c]==0){ m.add(new int[]{os,c}); if((iw&&r==6)||(!iw&&r==1)){ int ts=r+2*d; if(board[ts][c]==0) m.add(new int[]{ts,c}); } } for(int cc:new int[]{c-1,c+1}) if(cc>=0&&cc<8&&board[os][cc]!=0&&iw!=isPieceWhite(board[os][cc])) m.add(new int[]{os,cc}); } }
        else if(p instanceof Knight){ int[][] km={{r+2,c+1},{r+2,c-1},{r-2,c+1},{r-2,c-1},{r+1,c+2},{r+1,c-2},{r-1,c+2},{r-1,c-2}}; for(int[] mm:km){ int rr=mm[0],cc=mm[1]; if(rr>=0&&rr<8&&cc>=0&&cc<8&&(board[rr][cc]==0||iw!=isPieceWhite(board[rr][cc]))) m.add(new int[]{rr,cc}); } }
        else if(p instanceof Bishop){ int[][] d={{1,1},{1,-1},{-1,1},{-1,-1}}; for(int[] dd:d) for(int i=1;i<8;i++){ int rr=r+dd[0]*i,cc=c+dd[1]*i; if(rr<0||rr>=8||cc<0||cc>=8) break; if(board[rr][cc]==0) m.add(new int[]{rr,cc}); else{ if(iw!=isPieceWhite(board[rr][cc])) m.add(new int[]{rr,cc}); break; } } }
        else if(p instanceof Castle){ int[][] d={{1,0},{-1,0},{0,1},{0,-1}}; for(int[] dd:d) for(int i=1;i<8;i++){ int rr=r+dd[0]*i,cc=c+dd[1]*i; if(rr<0||rr>=8||cc<0||cc>=8) break; if(board[rr][cc]==0) m.add(new int[]{rr,cc}); else{ if(iw!=isPieceWhite(board[rr][cc])) m.add(new int[]{rr,cc}); break; } } }
        else if(p instanceof Queen){ int[][] d={{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}}; for(int[] dd:d) for(int i=1;i<8;i++){ int rr=r+dd[0]*i,cc=c+dd[1]*i; if(rr<0||rr>=8||cc<0||cc>=8) break; if(board[rr][cc]==0) m.add(new int[]{rr,cc}); else{ if(iw!=isPieceWhite(board[rr][cc])) m.add(new int[]{rr,cc}); break; } } }
        else if(p instanceof King){ int[][] km={{r+1,c},{r-1,c},{r,c+1},{r,c-1},{r+1,c+1},{r+1,c-1},{r-1,c+1},{r-1,c-1}}; for(int[] mm:km){ int rr=mm[0],cc=mm[1]; if(rr>=0&&rr<8&&cc>=0&&cc<8&&(board[rr][cc]==0||iw!=isPieceWhite(board[rr][cc]))) m.add(new int[]{rr,cc}); } }
        return m;
    }
    private boolean isMoveLegal(Piece p, int fr, int fc, int tr, int tc, boolean iw) { int cap=board[tr][tc],mov=board[fr][fc]; board[fr][fc]=0;board[tr][tc]=mov; boolean chk=isKingInCheck(iw); board[fr][fc]=mov;board[tr][tc]=cap; return !chk; }
    private List<int[]> getLegalMovesForPiece(Piece p, int r, int c, boolean iw) {
        List<int[]> pos=new ArrayList<>();
        for(int[] m:getRawMoves(p,r,c,iw)) if(isMoveLegal(p,r,c,m[0],m[1],iw)){ if(p instanceof King&&isSquareAttacked(m[0],m[1],!iw)) continue; pos.add(m); }
        if(p instanceof King){ if(canCastle(iw,true)&&isMoveLegal(p,r,c,r,6,iw)) pos.add(new int[]{r,6}); if(canCastle(iw,false)&&isMoveLegal(p,r,c,r,2,iw)) pos.add(new int[]{r,2}); }
        return pos;
    }
    private boolean hasAnyMove(boolean iw) { for(int r=0;r<8;r++) for(int c=0;c<8;c++) if(board[r][c]!=0&&isPieceWhite(board[r][c])==iw&&!getLegalMovesForPiece(getPieceType(r,c),r,c,iw).isEmpty()) return true; return false; }
    private boolean isCheckmate(boolean iw) { return isKingInCheck(iw)&&!hasAnyMove(iw); }
    private boolean isStalemate(boolean iw) { return !isKingInCheck(iw)&&!hasAnyMove(iw); }

    // ==================== РОКИРОВКА ====================
        private boolean canCastle(boolean iw, boolean ks) {
        if (gameOver) return false;
        int row = iw ? 7 : 0;
        if (iw) { if (whiteKingMoved) return false; if (ks && whiteRookKingSideMoved) return false; if (!ks && whiteRookQueenSideMoved) return false; }
        else { if (blackKingMoved) return false; if (ks && blackRookKingSideMoved) return false; if (!ks && blackRookQueenSideMoved) return false; }
        int e1 = ks ? 5 : 1, e2 = ks ? 6 : 2, e3 = ks ? -1 : 3;
        if (board[row][e1] != 0 || board[row][e2] != 0) return false;
        if (!ks && board[row][e3] != 0) return false;
        if (isSquareAttacked(row, 4, !iw) || isSquareAttacked(row, ks ? 5 : 3, !iw) || isSquareAttacked(row, ks ? 6 : 2, !iw)) return false;
        return true;
    }

    private void performCastle(boolean iw, boolean ks) {
        int row = iw ? 7 : 0;
        int okc = 4, nkc = ks ? 6 : 2, orc = ks ? 7 : 0, nrc = ks ? 5 : 3;
        ImageView kv = findPieceAt(row, okc), rv = findPieceAt(row, orc);
        if (kv == null || rv == null) return;
        saveGameState();
        boardSquares[row][okc].getChildren().remove(kv); boardSquares[row][orc].getChildren().remove(rv);
        boardSquares[row][nkc].getChildren().add(kv); boardSquares[row][nrc].getChildren().add(rv);
        board[row][okc] = 0; board[row][orc] = 0;
        board[row][nkc] = iw ? 6 : 12; board[row][nrc] = iw ? 4 : 10;
        kv.getProperties().put("row", row); kv.getProperties().put("col", nkc);
        rv.getProperties().put("row", row); rv.getProperties().put("col", nrc);
        if (iw) { whiteKingMoved = true; if (ks) whiteRookKingSideMoved = true; else whiteRookQueenSideMoved = true; }
        else { blackKingMoved = true; if (ks) blackRookKingSideMoved = true; else blackRookQueenSideMoved = true; }
    }
        private void promotePawn(ImageView pv, int r, int c, boolean iw) {
        ChoiceDialog<String> d = new ChoiceDialog<>("Ферзь", Arrays.asList("Ферзь", "Ладья", "Слон", "Конь"));
        d.setTitle("Превращение пешки"); d.setHeaderText("Выберите фигуру:");
        d.showAndWait().ifPresent(ch -> {
            boardSquares[r][c].getChildren().remove(pv);
            Piece np; int val;
            switch (ch) {
                case "Ферзь": np = new Queen(new double[]{0,0}); val = iw ? 5 : 11; break;
                case "Ладья": np = new Castle(new double[]{0,0}); val = iw ? 4 : 10; break;
                case "Слон": np = new Bishop(new double[]{0,0}); val = iw ? 3 : 9; break;
                default: np = new Knight(new double[]{0,0}); val = iw ? 2 : 8;
            }
            board[r][c] = val;
            ImageView nv = np.createPieceView(iw); nv.setFitWidth(cellSize*0.8); nv.setFitHeight(cellSize*0.8); nv.setPreserveRatio(true);
            nv.getProperties().put("piece", np); nv.getProperties().put("row", r); nv.getProperties().put("col", c); nv.getProperties().put("isWhite", iw);
            boardSquares[r][c].getChildren().add(nv);
            addClickHandler(nv, (iw?"Бел":"Черн") + (np instanceof Queen?"ый ферзь":np instanceof Castle?"ая ладья":np instanceof Bishop?"ый слон":"ый конь"), np, iw);
        });
    }

    private void promotePawnComputer(ImageView pv, int r, int c, boolean iw) {
        boardSquares[r][c].getChildren().remove(pv);
        Queen np = new Queen(new double[]{0,0});
        int val = iw ? 5 : 11;
        board[r][c] = val;
        ImageView nv = np.createPieceView(iw); nv.setFitWidth(cellSize*0.8); nv.setFitHeight(cellSize*0.8); nv.setPreserveRatio(true);
        nv.getProperties().put("piece", np); nv.getProperties().put("row", r); nv.getProperties().put("col", c); nv.getProperties().put("isWhite", iw);
        boardSquares[r][c].getChildren().add(nv);
        addClickHandler(nv, (iw?"Белый":"Черный") + " ферзь", np, iw);
    }

    // ==================== ПОДСВЕТКА ХОДОВ ====================
    private void possibleMoves(ImageView piece, int r, int c, boolean iw, Piece pt) {
        if(gameOver||iw!=whiteTurn){clearHighlights();return;}
        clearHighlights();
        for(int[] m:getLegalMovesForPiece(pt,r,c,iw)){
            Rectangle hl=new Rectangle(cellSize-10,cellSize-10);
            if(pt instanceof King&&Math.abs(m[1]-c)==2) hl.setFill(Color.rgb(128,0,128,0.3));
            else if(board[m[0]][m[1]]==0) hl.setFill(Color.rgb(0,255,0,0.3));
            else hl.setFill(Color.rgb(255,0,0,0.3));
            hl.setStroke(Color.GREEN);hl.setStrokeWidth(2);
            boardSquares[m[0]][m[1]].getChildren().add(hl); moveHighlights.add(hl);
            final int ftr=m[0],ftc=m[1],fr=r,fc=c; final boolean fiw=iw; final ImageView fp=piece;
            hl.setOnMouseClicked(ev->{
                if(pt instanceof King&&Math.abs(ftc-fc)==2){ performCastle(fiw,ftc>fc); addMoveToHistory(fr,fc,ftr,ftc,fiw,true,false); }
                else movePieceTo(fp,ftr,ftc,fr,fc,fiw);
                clearHighlights(); if(selectedPiece!=null){selectedPiece.setStyle("");selectedPiece=null;}
                ev.consume();
            });
        }
    }

    private void addClickHandler(ImageView iv, String nm, Piece p, boolean iw) {
        iv.getProperties().put("piece",p); iv.getProperties().put("pieceName",nm); iv.getProperties().put("isWhite",iw);
        iv.setOnMouseClicked(ev->{
            if(gameOver)return; if(vsComputer&&whiteTurn!=playerColor)return;
            int cr=(int)iv.getProperties().get("row"),cc=(int)iv.getProperties().get("col");
            if((boolean)iv.getProperties().get("isWhite")!=whiteTurn)return;
            if(selectedPiece!=null&&selectedPiece!=iv){selectedPiece.setStyle("");clearHighlights();}
            if(selectedPiece==iv){iv.setStyle("");selectedPiece=null;clearHighlights();}
            else{iv.setStyle("-fx-effect: dropshadow(three-pass-box, gold, 30, 0.5, 0, 0);");selectedPiece=iv;possibleMoves(iv,cr,cc,iw,p);}
            ev.consume();
        });
    }

    // ==================== FEN ====================
    private void setBoardFromFEN(String fen) {
        for(int r=0;r<8;r++) Arrays.fill(board[r],0);
        String[] parts=fen.split(" ");
        String[] rows=parts[0].split("/");
        for(int r=0;r<8;r++){ int c=0; for(char ch:rows[r].toCharArray()){ if(Character.isDigit(ch)) c+=ch-'0'; else{ int v; switch(ch){ case'P':v=1;break;case'N':v=2;break;case'B':v=3;break;case'R':v=4;break;case'Q':v=5;break;case'K':v=6;break; case'p':v=7;break;case'n':v=8;break;case'b':v=9;break;case'r':v=10;break;case'q':v=11;break;case'k':v=12;break; default:v=0; } board[r][c]=v; c++; } } }
        whiteTurn=parts.length>1&&parts[1].equals("w");
        whiteKingMoved=true;whiteRookKingSideMoved=true;whiteRookQueenSideMoved=true;
        blackKingMoved=true;blackRookKingSideMoved=true;blackRookQueenSideMoved=true;
        if(parts.length>2&&!parts[2].equals("-")){ String cas=parts[2]; if(cas.contains("K")){whiteKingMoved=false;whiteRookKingSideMoved=false;} if(cas.contains("Q")){whiteKingMoved=false;whiteRookQueenSideMoved=false;} if(cas.contains("k")){blackKingMoved=false;blackRookKingSideMoved=false;} if(cas.contains("q")){blackKingMoved=false;blackRookQueenSideMoved=false;} }
    }

    // ==================== РАССТАНОВКА ====================
    private void setupPieces(GridPane gp, boolean f) {
        for(int r=0;r<8;r++) Arrays.fill(board[r],0);
        for(int c=0;c<8;c++){ Pawn pp=new Pawn(new double[]{0,0}); ImageView pv=pp.createPieceView(true); pv.setFitWidth(cellSize*0.8);pv.setFitHeight(cellSize*0.8);pv.setPreserveRatio(true); pv.getProperties().put("row",6);pv.getProperties().put("col",c); boardSquares[6][c].getChildren().add(pv); addClickHandler(pv,"Белая пешка",pp,true); board[6][c]=1; }
        setupBackRank(7,true);
        for(int c=0;c<8;c++){ Pawn pp=new Pawn(new double[]{0,0}); ImageView pv=pp.createPieceView(false); pv.setFitWidth(cellSize*0.8);pv.setFitHeight(cellSize*0.8);pv.setPreserveRatio(true); pv.getProperties().put("row",1);pv.getProperties().put("col",c); boardSquares[1][c].getChildren().add(pv); addClickHandler(pv,"Черная пешка",pp,false); board[1][c]=7; }
        setupBackRank(0,false);
    }
    private void setupBackRank(int r, boolean iw) {
        int[] o={4,2,3,5,6,3,2,4};
        for(int c=0;c<8;c++){ Piece p; switch(o[c]){ case 2:p=new Knight(new double[]{0,0});break; case 3:p=new Bishop(new double[]{0,0});break; case 4:p=new Castle(new double[]{0,0});break; case 5:p=new Queen(new double[]{0,0});break; case 6:p=new King(new double[]{0,0});break; default:p=new Castle(new double[]{0,0}); } ImageView pv=p.createPieceView(iw); pv.setFitWidth(cellSize*0.8);pv.setFitHeight(cellSize*0.8);pv.setPreserveRatio(true); pv.getProperties().put("row",r);pv.getProperties().put("col",c); boardSquares[r][c].getChildren().add(pv); addClickHandler(pv,"",p,iw); board[r][c]=iw?o[c]:o[c]+6; }
    }

    // ==================== SHOW GAME BOARD ====================
    private void showGameBoard() {
        GridPane gamePane = new GridPane(); gamePane.setAlignment(Pos.CENTER);
        selectedPiece = null; whiteTurn = true; gameOver = false;
        moveHistory.clear(); gameNotation.clear(); fullMoveHistory.clear(); moveNumber = 1;
        boardFlipped = !playerColor;
        whiteKingMoved=false;whiteRookKingSideMoved=false;whiteRookQueenSideMoved=false;
        blackKingMoved=false;blackRookKingSideMoved=false;blackRookQueenSideMoved=false;
        for (int i=0;i<8;i++){ gamePane.getColumnConstraints().add(new ColumnConstraints(cellSize)); gamePane.getRowConstraints().add(new RowConstraints(cellSize)); }
        for (int r=0;r<8;r++) for (int c=0;c<8;c++) {
            StackPane pane = new StackPane(); pane.setPrefSize(cellSize, cellSize);
            Rectangle sq = new Rectangle(cellSize, cellSize);
            sq.setFill((r+c)%2==0?Color.web("#f0d9b5"):Color.web("#b58863"));
            sq.setArcWidth(4); sq.setArcHeight(4);
            sq.setStroke(Color.web("#8b7355")); sq.setStrokeWidth(0.5);
            pane.getChildren().add(sq);
            // КООРДИНАТЫ
            if (r==7){ Label ll=new Label(String.valueOf((char)('a'+c))); ll.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;"); ll.setTextFill((c)%2==0?Color.web("#b58863"):Color.web("#f0d9b5")); StackPane.setAlignment(ll,Pos.BOTTOM_RIGHT); StackPane.setMargin(ll,new Insets(0,3,2,0)); pane.getChildren().add(ll); }
            if (c==0){ Label nl=new Label(String.valueOf(8-r)); nl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;"); nl.setTextFill((r)%2==0?Color.web("#b58863"):Color.web("#f0d9b5")); StackPane.setAlignment(nl,Pos.TOP_LEFT); StackPane.setMargin(nl,new Insets(2,0,0,3)); pane.getChildren().add(nl); }
            gamePane.add(pane, boardFlipped?7-c:c, boardFlipped?7-r:r);
            boardSquares[r][c] = pane;
        }
        if(pendingFEN!=null){ setBoardFromFEN(pendingFEN); pendingFEN=null; redrawBoard(); whiteTurn=playerColor; }
        else setupPieces(gamePane,true);
        saveGameState();
        VBox info = new VBox(12); info.setPadding(new Insets(20)); info.setStyle("-fx-background-color: linear-gradient(to bottom, #2c3e50, #1a252f);"); info.setPrefWidth(240); info.setAlignment(Pos.TOP_CENTER);
        Label gt=new Label("♔ NIKCHESS ♚"); gt.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: linear-gradient(to right, #f39c12, #e74c3c);");
        turnLabel = new Label("Ход БЕЛЫХ"); turnLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: white; -fx-background-color: rgba(255,255,255,0.1); -fx-padding: 10px; -fx-background-radius: 8px;"); turnLabel.setMaxWidth(Double.MAX_VALUE); turnLabel.setAlignment(Pos.CENTER);
        VBox cb=new VBox(5); cb.setAlignment(Pos.CENTER); cb.setStyle("-fx-background-color: rgba(0,0,0,0.3); -fx-padding: 10px; -fx-background-radius: 8px;");
        whiteTimeLabel=new Label("10:00.0"); whiteTimeLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: white; -fx-font-family: 'Consolas', monospace;");
        blackTimeLabel=new Label("10:00.0"); blackTimeLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #bdc3c7; -fx-font-family: 'Consolas', monospace;");
        cb.getChildren().addAll(new Label("БЕЛЫЕ"){{setStyle("-fx-font-size:10px;-fx-text-fill:#bdc3c7;");}}, whiteTimeLabel, new Separator(), blackTimeLabel, new Label("ЧЕРНЫЕ"){{setStyle("-fx-font-size:10px;-fx-text-fill:#bdc3c7;");}});
        String bs="-fx-font-size:12px;-fx-font-weight:bold;-fx-padding:8px 14px;-fx-background-radius:6px;-fx-text-fill:white;-fx-cursor:hand;-fx-max-width:infinity;";
        Button backBtn=new Button("← В МЕНЮ"); backBtn.setStyle(bs+"-fx-background-color:#7f8c8d;"); backBtn.setOnAction(e->{if(chessClock!=null)chessClock.stop();if(scoriaEngine!=null){scoriaEngine.close();scoriaEngine=null;}showMainMenu();});
        Button undoBtn=new Button("↩ ОТМЕНИТЬ"); undoBtn.setStyle(bs+"-fx-background-color:#e67e22;"); undoBtn.setOnAction(e->undoMove());
        Button histBtn=new Button("📜 ИСТОРИЯ"); histBtn.setStyle(bs+"-fx-background-color:#3498db;"); histBtn.setOnAction(e->showGameHistory());
        Button saveBtn=new Button("💾 СОХРАНИТЬ"); saveBtn.setStyle(bs+"-fx-background-color:#8e44ad;"); saveBtn.setOnAction(e->saveCurrentGame());
        restartButton=new Button("🔄 НОВАЯ ИГРА"); restartButton.setStyle(bs+"-fx-background-color:#27ae60;"); restartButton.setOnAction(e->restartGame(true)); restartButton.setVisible(false);
        info.getChildren().addAll(gt,new Separator(),turnLabel,cb,new Separator(),backBtn,undoBtn,histBtn,saveBtn,restartButton);
        BorderPane root=new BorderPane(); root.setCenter(gamePane); root.setRight(info); root.setStyle("-fx-background-color: #1a1a2e;"); BorderPane.setMargin(gamePane,new Insets(20));
        root.setOnMouseClicked(ev->{ if(!(ev.getPickResult().getIntersectedNode() instanceof ImageView)&&!(ev.getPickResult().getIntersectedNode() instanceof Rectangle)){ if(selectedPiece!=null){selectedPiece.setStyle("");selectedPiece=null;clearHighlights();} } });
        Scene gs=new Scene(root,1060,860); stage.setScene(gs); stage.setTitle("NikChess — Шахматы");
        if(clockEnabled){chessClock=new ChessClock(initialTimeMinutes);chessClock.start();}
        updateTurnLabel(); updateTimeLabels();
        if(vsComputer&&!playerColor) scheduleComputerMove();
    }

    public static void main(String[] args) { launch(args); }
}