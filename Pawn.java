import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class Pawn implements Piece {
    private double x, y;
    private boolean isWhite;
    
    public Pawn(double[] position) {
        this.x = position[0];
        this.y = position[1];
    }
    
    @Override
    public ImageView createPieceView(boolean isWhite) {
        this.isWhite = isWhite;
        Image image;
        if (isWhite) {
            image = new Image(getClass().getResourceAsStream("/Pawn.png"));
        }
        else{ 
            image = new Image(getClass().getResourceAsStream("/BlackPawn.png"));
        }
        ImageView view = new ImageView(image);
        view.setFitWidth(80);
        view.setFitHeight(80);
        view.setPreserveRatio(true);
        view.setLayoutX(x);
        view.setLayoutY(y);
        return view;
    }
    
    @Override
    public double[] getPosition() {
        return new double[]{x, y};
    }
    
    @Override
    public void setPosition(double x, double y) {
        this.x = x;
        this.y = y;
    }
    
    @Override
    public boolean isWhite() {
        return isWhite;
    }
    
    @Override
    public void setPosition(double[] position) {
        this.x = position[0];
        this.y = position[1];
    }
}