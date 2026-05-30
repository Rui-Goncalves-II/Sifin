package br.investimentos.ui.util;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

public final class UIUtil {

    private UIUtil() {}

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
