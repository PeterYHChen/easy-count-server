import org.opencv.core.*;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

public class Main {
    // Compulsory
    static{ System.loadLibrary(Core.NATIVE_LIBRARY_NAME); }

    public static final String OUTPUT_SUFFIX = ".out";
    public static final int MAX_DELTA = 15;
    public static final double CANNY_LOW_THREASHOLD = 7;

    public static void main(String[] args) throws IOException {
        File[] files = new File("data").listFiles();
        BufferedImage img = null;
        BufferedImage target = null;

        for (File file : files) {
            String filePath = file.getPath();
            // Skip output file
            if(filePath.endsWith(OUTPUT_SUFFIX)) {
                continue;
            }

            System.out.println("Processing file " + file.getName());
            img = ImageIO.read(file);
            if (img == null) {
                System.err.println("image is null");
                continue;
            }
            img = detectCircles(img);
            File outputfile = new File(filePath.substring(0, file.getPath().indexOf(".")) + OUTPUT_SUFFIX);
            ImageIO.write(img, "jpg", outputfile);
        }
    }

    private static BufferedImage detectSimilarColorRegion(BufferedImage image) {
        List<PointRegion> pointRegions = new ArrayList<>();

        int[] offset = {-1, 0, 1, 0, 0, -1, 0, 1, 1, 1, -1, -1, 1, -1, -1, 1};

        // Initialization
        boolean[][] markCnt = new boolean[image.getWidth()][image.getHeight()];
        PointRegion.PointAttribute[][] pointAttributes = new PointRegion.PointAttribute[image.getWidth()][image.getHeight()];
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                pointAttributes[x][y] = new PointRegion.PointAttribute(image.getRGB(x, y), false);
                markCnt[x][y] = false;
            }
        }

        int[] objectPixelCntSum = new int[100];
        Arrays.fill(objectPixelCntSum, 0);

        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                // transparent pixel, this pixel doesn't display, skip
                if (image.getRGB(x, y) == 0)
                    continue;

                // use bfs to mark all adjacent object pixels to recognize as one object
                if (!markCnt[x][y]) {
                    java.awt.Point pos = new java.awt.Point(x, y);
                    Queue<java.awt.Point> queue = new LinkedList<>();
                    queue.add(pos);

                    java.awt.Point currPos, nextPos;
                    while (!queue.isEmpty()) {
                        currPos = queue.poll();
                        int xPos = (int) currPos.getX();
                        int yPos = (int) currPos.getY();

                        // Mark current point as visited
                        markCnt[xPos][yPos] = true;

                        int nextXPos, nextYPos;
                        for (int i = 0; i < 16; i += 2) {
                            nextXPos = xPos + offset[i];
                            nextYPos = yPos + offset[i+1];

                            // skip invalid pixel position (out of bound or transparent)
                            if (nextXPos < 0 || nextXPos >= image.getWidth()
                                    || nextYPos < 0 || nextYPos >= image.getHeight()
                                    || image.getRGB(nextXPos, nextYPos) == 0) {
                                continue;
                            }

                            // skip pixels that have been marked already
                            if (markCnt[nextXPos][nextYPos]) {
                                continue;
                            }

                            // If the current point is has different color from the adjacent point,
                            // set both points as edges and skip
                            if (!colorsAreSimilar(image.getRGB(xPos, yPos), image.getRGB(nextXPos, nextYPos), MAX_DELTA)) {
                                pointAttributes[xPos][yPos].isEdge = true;
                                pointAttributes[nextXPos][nextYPos].isEdge = true;
                                image.setRGB(xPos, yPos, Color.BLACK.getRGB());
//                                image.setRGB(nextXPos, nextYPos, Color.BLACK.getRGB());
                                continue;
                            }

                            // The adjacent point is a valid, not visited before, and has similar color
                            nextPos = new java.awt.Point(nextXPos, nextYPos);
                            markCnt[nextXPos][nextYPos] = true;

                            queue.add(nextPos);
                        }
                    }

//                    if (objectSize >= 800 || objectSize <= 3) {
//                        objectCnt--;
//                        while (!resumeQ.isEmpty()) {
//                            image.setRGB((int)resumeQ.peek().getX(), (int)resumeQ.peek().getY(), Color.BLACK.getRGB());
//                            resumeQ.poll();
//                        }
//                    } else {
//                        while (!resumeQ.isEmpty()) {
//                            image.setRGB((int)resumeQ.peek().getX(), (int)resumeQ.peek().getY(), Color.RED.getRGB());
//                            resumeQ.poll();
//                        }
//                    }
//                    if (objectSize >= 100)
//                        objectPixelCntSum[objectPixelCntSum.length-1]++;
//                    else
//                        objectPixelCntSum[objectSize]++;
                }
            }
        }

        return image;
    }

    private static BufferedImage naiveDetect(BufferedImage image) {
        boolean[][] isObject = new boolean[image.getWidth()][image.getHeight()];
        boolean[][] markCnt = new boolean[image.getWidth()][image.getHeight()];
        int objectCnt = 0;
        int percentage = 0;

        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                // init markCnt to all false
                markCnt[x][y] =false;

                // perform similarity check
                isObject[x][y] = colorsAreSimilar(new Color(image.getRGB(x, y)), Color.decode("#D7D4C3"), 80);
            }
        }

        int[] offset = {-1, 0, 1, 0, 0, -1, 0, 1, 1, 1, -1, -1, 1, -1, -1, 1};
        int[] objectPixelCntSum = new int[100];
        Arrays.fill(objectPixelCntSum, 0);

        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                if (isObject[x][y]) {
                    image.setRGB(x, y, Color.WHITE.getRGB());

                    // use bfs to mark all adjacent object pixels to recognize as one object
                    if (!markCnt[x][y]) {
                        objectCnt++;
                        AbstractMap.SimpleEntry<Integer, Integer> pos =
                                new AbstractMap.SimpleEntry<Integer, Integer>(x, y);
                        Queue<AbstractMap.SimpleEntry<Integer, Integer>> queue = new LinkedList<>();
                        Queue<AbstractMap.SimpleEntry<Integer, Integer>> resumeQ = new LinkedList<>();
                        queue.add(pos);

                        int objectSize = 0;
                        while (!queue.isEmpty()) {
                            AbstractMap.SimpleEntry<Integer, Integer> currPos = queue.poll();
                            int xPos = currPos.getKey();
                            int yPos = currPos.getValue();

                            // skip invalid pixel position
                            if (xPos < 0 || xPos >= image.getWidth() || yPos < 0 || yPos >= image.getHeight())
                                continue;

                            // skip not object pixels
                            if (!isObject[xPos][yPos])
                                continue;

                            // skip pixels that have been marked already
                            if (markCnt[xPos][yPos])
                                continue;

                            objectSize++;
                            markCnt[xPos][yPos] = true;
                            resumeQ.add(currPos);

                            for (int i = 0; i < 16; i += 2) {
                                queue.add(new AbstractMap.SimpleEntry<Integer, Integer>(xPos + offset[i], yPos + offset[i+1]));
                            }
                        }
                        if (objectSize >= 150 || objectSize <= 10) {
                            objectCnt--;
                            while (!resumeQ.isEmpty()) {
                                isObject[resumeQ.peek().getKey()][resumeQ.peek().getValue()] = false;
                                image.setRGB(resumeQ.peek().getKey(), resumeQ.peek().getValue(), Color.BLACK.getRGB());
                                resumeQ.poll();
                            }
                        }
                        if (objectSize >= 100)
                            objectPixelCntSum[objectPixelCntSum.length-1]++;
                        else
                            objectPixelCntSum[objectSize]++;
                    }
                } else {
                    image.setRGB(x, y, Color.BLACK.getRGB());
                }
            }
        }
        int len = objectPixelCntSum.length;

        return image;
    }

    private static BufferedImage detectIgnoreBackground(BufferedImage image) {
        boolean[][] isObject = new boolean[image.getWidth()][image.getHeight()];
        boolean[][] markCnt = new boolean[image.getWidth()][image.getHeight()];
        int objectCnt = 0;
        int percentage = 0;

        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                // init markCnt to all false
                markCnt[x][y] =false;

                // perform similarity check on non-transparent pixels
                if (image.getRGB(x, y) != 0)
                    // If not background color, then it is object
                    isObject[x][y] = !colorsAreSimilar(new Color(image.getRGB(x, y)), Color.decode("#8F9392"), 100);
            }
        }

        int[] offset = {-1, 0, 1, 0, 0, -1, 0, 1, 1, 1, -1, -1, 1, -1, -1, 1};
        int[] objectPixelCntSum = new int[100];
        Arrays.fill(objectPixelCntSum, 0);

        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                // transparent pixel, this pixel doesn't display, skip
                if (image.getRGB(x, y) == 0)
                    continue;

                if (isObject[x][y]) {
                    // use bfs to mark all adjacent object pixels to recognize as one object
                    if (!markCnt[x][y]) {
                        objectCnt++;
                        AbstractMap.SimpleEntry<Integer, Integer> pos =
                                new AbstractMap.SimpleEntry<Integer, Integer>(x, y);
                        Queue<AbstractMap.SimpleEntry<Integer, Integer>> queue = new LinkedList<>();
                        Queue<AbstractMap.SimpleEntry<Integer, Integer>> resumeQ = new LinkedList<>();
                        queue.add(pos);

                        int objectSize = 0;
                        AbstractMap.SimpleEntry<Integer, Integer> currPos, nextPos;
                        while (!queue.isEmpty()) {
                            currPos = queue.poll();
                            int xPos = currPos.getKey();
                            int yPos = currPos.getValue();
                            int nextXPos, nextYPos;


                            if (objectSize > 20)
                                objectSize = objectSize;

                            for (int i = 0; i < 16; i += 2) {
                                nextXPos = xPos + offset[i];
                                nextYPos = yPos + offset[i+1];

                                // skip invalid pixel position (out of bound or transparent)
                                if (nextXPos < 0 || nextXPos >= image.getWidth()
                                        || nextYPos < 0 || nextYPos >= image.getHeight()
                                        || image.getRGB(nextXPos, nextYPos) == 0)
                                    continue;

                                // skip not object pixels
                                if (!isObject[nextXPos][nextYPos])
                                    continue;

                                // skip pixels that have been marked already
                                if (markCnt[nextXPos][nextYPos])
                                    continue;

                                nextPos = new AbstractMap.SimpleEntry<Integer, Integer>(nextXPos, nextYPos);
                                objectSize++;
                                resumeQ.add(nextPos);
                                markCnt[nextXPos][nextYPos] = true;

                                queue.add(nextPos);
                            }
                        }
                        if (objectSize >= 800 || objectSize <= 3) {
                            objectCnt--;
                            while (!resumeQ.isEmpty()) {
                                isObject[resumeQ.peek().getKey()][resumeQ.peek().getValue()] = false;
                                image.setRGB(resumeQ.peek().getKey(), resumeQ.peek().getValue(), Color.BLACK.getRGB());
                                resumeQ.poll();
                            }
                        } else {
                            while (!resumeQ.isEmpty()) {
                                image.setRGB(resumeQ.peek().getKey(), resumeQ.peek().getValue(), Color.RED.getRGB());
                                resumeQ.poll();
                            }
                        }
                        if (objectSize >= 100)
                            objectPixelCntSum[objectPixelCntSum.length-1]++;
                        else
                            objectPixelCntSum[objectSize]++;
                    }
                } else {
                    image.setRGB(x, y, Color.BLACK.getRGB());
                }
            }
        }
        int len = objectPixelCntSum.length;

        return image;
    }

//    private static BufferedImage naiveDetectGrayscale(BufferedImage image) {
//        Mat mat = new Mat(image.getWidth(), image.getHeight(), CvType.CV_8UC1);
//
//        bitmapToMat(image, mat);
//        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_BGR2GRAY);
//        matToBufferedImage(mat, image);
//
//        float[] hsv = new float[3];
//        Color currColor = null;
//        for (int x = 0; x < image.getWidth(); x++) {
//            for (int y = 0; y < image.getHeight(); y++) {
//                currColor = new Color(image.getRGB(x, y));
//                Color.RGBtoHSB(currColor.getRed(), currColor.getBlue(), currColor.getGreen(), hsv);
//                // satuation
////                    Log.d("value",""+hsv[2]);
//                if (hsv[2] < 0.58)
//                    image.setRGB(x, y, Color.BLACK.getRGB());
//            }
//        }
//    }
    private static BufferedImage detectEdges(BufferedImage image) {
                /* convert bitmap to mat */
        Mat mat = bufferedImageToMat(image);
        Mat grayMat = new Mat(image.getHeight(), image.getWidth(), CvType.CV_8UC1);

            /* convert to grayscale */
        int colorChannels = (mat.channels() == 3 || mat.channels() == 4) ? Imgproc.COLOR_BGR2GRAY : 1;

        Imgproc.cvtColor(mat, grayMat, colorChannels);
    //        Imgproc.equalizeHist(grayMat, grayMat);
                /* reduce the noise so we avoid false circle detection */
//        Imgproc.dilate(grayMat, grayMat, new Mat());
        Imgproc.blur(grayMat, grayMat, new Size(7, 7));
        Imgproc.Canny(grayMat, grayMat, CANNY_LOW_THREASHOLD, CANNY_LOW_THREASHOLD*3, 3, false);

//        Mat dest = new Mat();
//        Core.add(dest, Scalar.all(0), dest);
//        frame.copyTo(dest, detectedEdges);
        /* convert back to buffered image */
        BufferedImage result = matToBufferedImage(grayMat);
        return result;
    }

    private static BufferedImage detectCircles(BufferedImage image) {
            /* convert bitmap to mat */
        Mat mat = bufferedImageToMat(image);
        Mat grayMat = new Mat(image.getHeight(), image.getWidth(), CvType.CV_8UC1);

        /* convert to grayscale */
        int colorChannels = (mat.channels() == 3 || mat.channels() == 4) ? Imgproc.COLOR_BGR2GRAY : 1;

        Imgproc.cvtColor(mat, grayMat, colorChannels);
//        Imgproc.equalizeHist(grayMat, grayMat);
            /* reduce the noise so we avoid false circle detection */
//        Imgproc.dilate(grayMat, grayMat, new Mat());
//        Imgproc.GaussianBlur(grayMat, grayMat, new Size(5, 5), 2, 2);
        Imgproc.blur(grayMat, grayMat, new Size(5, 5));

        // accumulator value
        double dp = 1;
        // minimum distance between the center coordinates of detected circles in pixels
        double minDist = 20;

        // min and max radii (set these values as you desire)
        int minRadius = 20, maxRadius = 40;

        // param1 = gradient value used to handle edge detection
        // param2 = Accumulator threshold value for the
        // cv2.CV_HOUGH_GRADIENT method.
        // The smaller the threshold is, the more circles will be
        // detected (including false circles).
        // The larger the threshold is, the more circles will
        // potentially be returned.
        double param1 = 50, param2 = 10;

            /* create a Mat object to store the circles detected */
        Mat circles = new Mat(image.getHeight(),
                image.getWidth(), CvType.CV_8UC1);

            /* find the circle in the image */
        Imgproc.HoughCircles(grayMat, circles,
                Imgproc.CV_HOUGH_GRADIENT, dp, minDist, param1,
                param2, minRadius, maxRadius);

            /* get the number of circles detected */
        int numberOfCircles = circles.cols();

            /* draw the circles found on the image */
        for (int i=0; i<numberOfCircles; i++) {
                /* get the circle details, circleCoordinates[0, 1, 2] = (x,y,r)
                 * (x,y) are the coordinates of the circle's center
                 */
            double[] circleCoordinates = circles.get(0, i);

            int x = (int) circleCoordinates[0], y = (int) circleCoordinates[1];
            Point center = new Point(x, y);

            int radius = (int) circleCoordinates[2];

                /* circle's outline */
            Imgproc.circle(grayMat, center, radius, new Scalar(0, 255, 0), 1);
        }

        /* convert back to buffered image */
        BufferedImage result = matToBufferedImage(grayMat);
        return result;
    }

    // Put image data into a matrix
    private static Mat bufferedImageToMat(BufferedImage image) {
        Mat mat = new Mat(image.getHeight(), image.getWidth(), CvType.CV_8UC3);
        mat.put(0, 0, ((DataBufferByte) image.getRaster().getDataBuffer()).getData());
        return mat;
    }

    private static BufferedImage matToBufferedImage(Mat mat) {
        byte[] data = new byte[mat.rows() * mat.cols() * (int)(mat.elemSize())];
        mat.get(0, 0, data);
        BufferedImage image = new BufferedImage(mat.cols(), mat.rows(), BufferedImage.TYPE_BYTE_GRAY);
        image.getRaster().setDataElements(0, 0, mat.cols(), mat.rows(), data);
        return image;
    }


    // use CIE76 ΔE*ab to compute color similarity
    public static boolean colorsAreSimilar(int rgb1, int rgb2, int maxDelta) {
        return colorsAreSimilar(new Color(rgb1), new Color(rgb2), maxDelta);
    }

    // use CIE76 ΔE*ab to compute color similarity
    // a and b are RGB values
    public static boolean colorsAreSimilar(Color a, Color b, int maxDelta) {

        int redDiff = a.getRed() - b.getRed();
        int blueDiff = a.getBlue() - b.getBlue();
        int greenDiff = a.getGreen() - b.getGreen();

        double deltaE = Math.sqrt(2*redDiff*redDiff + 4*blueDiff*blueDiff + 3*greenDiff*greenDiff);
//        System.out.println("deltaE: " + deltaE);
        return deltaE < maxDelta;
    }
}
