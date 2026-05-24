import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class Bishop implements Piece {
    private double x, y;
    private boolean isWhite;
    
    public Bishop(double[] position) {
        this.x = position[0];
        this.y = position[1];
    }
    @Override
    public ImageView createPieceView(boolean flag) {
        Image bishopImage;
        if (flag) {
            bishopImage = new Image(getClass().getResourceAsStream("/Bishop.png"));
        }
        else{ 
            bishopImage = new Image(getClass().getResourceAsStream("/BlackBishop.png"));
        }
        ImageView bishopView = new ImageView(bishopImage);
        bishopView.setFitWidth(80);
        bishopView.setFitHeight(80);
        bishopView.setPreserveRatio(true);
        bishopView.setLayoutX(x);
        bishopView.setLayoutY(y);
        
        return bishopView;
            
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