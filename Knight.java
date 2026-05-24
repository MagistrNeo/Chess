import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class Knight implements Piece{
    private double x, y;
    private boolean isWhite;
    
    public Knight(double[] position) {
        this.x = position[0];
        this.y = position[1];
    }
    
    @Override
    public ImageView createPieceView(boolean flag) {
        Image knightImage;
        if (flag) {
            knightImage = new Image(getClass().getResourceAsStream("/Knight.png"));
        }
        else{ 
            knightImage = new Image(getClass().getResourceAsStream("/BlackKnight.png"));
        }
        ImageView knightView = new ImageView(knightImage);
        knightView.setFitWidth(80);
        knightView.setFitHeight(80);
        knightView.setPreserveRatio(true);
        knightView.setLayoutX(x);
        knightView.setLayoutY(y);
        return knightView;
            
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