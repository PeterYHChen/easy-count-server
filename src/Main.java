import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class Main {
    public static final String OUTPUT_SUFFIX = ".out";

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
            img = detectIgnoreBackground(img);
            File outputfile = new File(filePath.substring(0, file.getPath().indexOf(".")) + OUTPUT_SUFFIX);
            ImageIO.write(img, "jpg", outputfile);
        }
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