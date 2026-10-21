package br.investimentos.ui.util;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

public final class UIUtil {

    private UIUtil() {}

    // Ícones vetoriais (viewBox 24x24): o JavaFX não renderiza emoji colorido no Linux.
    public static final String ICON_DASHBOARD = "M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z";
    public static final String ICON_RENDA_VARIAVEL = "M16 6l2.29 2.29-4.88 4.88-4-4L2 16.59 3.41 18l6-6 4 4 6.3-6.29L22 12V6z";

    /** Ícone a partir de um path SVG 24x24, na mesma cor de {@link #loadTintedIcon}. */
    public static Node svgIcon(String svgPath, int size) {
        SVGPath path = new SVGPath();
        path.setContent(svgPath);
        path.setFill(Color.web("#adbac7"));
        double scale = size / 24.0;
        path.setScaleX(scale);
        path.setScaleY(scale);
        StackPane box = new StackPane(new Group(path));
        box.setMinSize(size, size);
        box.setPrefSize(size, size);
        box.setMaxSize(size, size);
        return box;
    }

    public static ImageView loadTintedIcon(Class<?> context, String resourcePath, int size) {
        var stream = context.getResourceAsStream(resourcePath);
        if (stream == null) return new ImageView();
        Image original = new Image(stream, size * 2, size * 2, true, true);
        int w = (int) original.getWidth();
        int h = (int) original.getHeight();
        WritableImage result = new WritableImage(w, h);
        var reader = original.getPixelReader();
        var writer = result.getPixelWriter();
        Color target = Color.web("#adbac7");
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Color px = reader.getColor(x, y);
                double lum = 0.299 * px.getRed() + 0.587 * px.getGreen() + 0.114 * px.getBlue();
                double alpha = (1.0 - lum) * px.getOpacity();
                writer.setColor(x, y, alpha > 0.05
                    ? new Color(target.getRed(), target.getGreen(), target.getBlue(), Math.min(alpha, 1.0))
                    : Color.TRANSPARENT);
            }
        }
        ImageView iv = new ImageView(result);
        iv.setFitWidth(size);
        iv.setFitHeight(size);
        iv.setSmooth(true);
        return iv;
    }
}
