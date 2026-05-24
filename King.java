import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class King implements Piece {
    private double x, y;
    private boolean isWhite;

    public King(double[] position) {
        this.x = position[0];
        this.y = position[1];
    }
    @Override
    public ImageView createPieceView(boolean flag) {
        Image kingImage;
        if (flag) {
            kingImage = new Image(getClass().getResourceAsStream("/King.png"));
        }
        else{ 
            kingImage = new Image(getClass().getResourceAsStream("/BlackKing.png"));
        }
        ImageView kingView = new ImageView(kingImage);
        kingView.setFitWidth(80);
        kingView.setFitHeight(80);
        kingView.setPreserveRatio(true);
        kingView.setLayoutX(x);
        kingView.setLayoutY(y);
        
        return kingView;
            
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