import javafx.scene.image.ImageView;
public interface Piece{
    ImageView createPieceView(boolean flag);
    double[] getPosition();
    void setPosition(double x, double y);
    boolean isWhite();
    void setPosition(double[] position);
}