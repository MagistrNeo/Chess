import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class Queen implements Piece {
    private double x, y;
    private boolean isWhite;
    
    public Queen(double[] position) {
        this.x = position[0];
        this.y = position[1];
    }
    @Override
    public ImageView createPieceView(boolean flag) {
        Image queenImage;
        if (flag) {
            queenImage = new Image(getClass().getResourceAsStream("/Queen.png"));
        }
        else{ 
            queenImage = new Image(getClass().getResourceAsStream("/BlackQueen.png"));
        }
        ImageView queenView = new ImageView(queenImage);
        queenView.setFitWidth(80);
        queenView.setFitHeight(80);
        queenView.setPreserveRatio(true);
        queenView.setLayoutX(x);
        queenView.setLayoutY(y);
        
        return queenView;
            
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