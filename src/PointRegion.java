import java.awt.Point;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by yonghong on 3/27/18.
 */
public class PointRegion {
    public static class PointAttribute {
        private int rgb = 0;
        boolean isEdge = false;

        public PointAttribute(int rgb, boolean isEdge) {
            this.rgb = rgb;
            this.isEdge = isEdge;
        }

        public int getRGB() {
            return rgb;
        }

        public void setRGB(int rgb) {
            this.rgb = rgb;
        }
    }

    Map<Point, PointAttribute> points = new HashMap<>();
    public void addPixel(int x, int y, int rgb, boolean isEdge) {
        PointAttribute pointAttribute = new PointAttribute(rgb, isEdge);
        points.put(new Point(x, y), pointAttribute);
    }

    public void removePixel(int x, int y) {
        Point point = new Point(x, y);
        if (points.containsKey(point)) {
            points.remove(point);
        }
    }
    public int size() {
        return points.size();
    }
}
