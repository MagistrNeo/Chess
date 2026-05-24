import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class Castle implements Piece {
    private double x, y;
    private boolean isWhite;
    
    public Castle(double[] position) {
        this.x = position[0];
        this.y = position[1];
    }
    @Override
    public ImageView createPieceView(boolean flag) {
        Image castleImage;
        if (flag) {
            castleImage = new Image(getClass().getResourceAsStream("/Castle.png"));
        }
        else{ 
            castleImage = new Image(getClass().getResourceAsStream("/BlackCastle.png"));
        }
        ImageView castleView = new ImageView(castleImage);
        castleView.setFitWidth(80);
        castleView.setFitHeight(80);
        castleView.setPreserveRatio(true);
        castleView.setLayoutX(x);
        castleView.setLayoutY(y);
        
        return castleView;
            
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