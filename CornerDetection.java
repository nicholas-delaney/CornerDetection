import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import javax.imageio.*;
import javax.swing.*;

// Main class
public class CornerDetection extends Frame implements ActionListener {

    BufferedImage input;
    int width, height;
    double sensitivity = .1;
    int threshold = 20;
    ImageCanvas source, target;
    final int GREY_LEVEL = 256;
    CheckboxGroup metrics = new CheckboxGroup();
    float[] maxIs;      // used for normalizing A matrix
    // Constructor

    public CornerDetection(String name) {
        super("Corner Detection");
        // load image
        try {
            input = ImageIO.read(new File(name));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        width = input.getWidth();
        height = input.getHeight();
        // prepare the panel for image canvas.
        Panel main = new Panel();
        source = new ImageCanvas(input);
        target = new ImageCanvas(width, height);
        main.setLayout(new GridLayout(1, 2, 10, 10));
        main.add(source);
        main.add(target);
        // prepare the panel for buttons.
        Panel controls = new Panel();
        Button button = new Button("Derivatives");
        button.addActionListener(this);
        controls.add(button);
        // Use a slider to change sensitivity
        JLabel label1 = new JLabel("sensitivity=" + sensitivity);
        controls.add(label1);
        JSlider slider1 = new JSlider(1, 25, (int) (sensitivity * 100));
        slider1.setPreferredSize(new Dimension(50, 20));
        controls.add(slider1);
        slider1.addChangeListener(changeEvent -> {
            sensitivity = slider1.getValue() / 100.0;
            label1.setText("sensitivity=" + (int) (sensitivity * 100) / 100.0);
        });
        button = new Button("Corner Response");
        button.addActionListener(this);
        controls.add(button);
        JLabel label2 = new JLabel("threshold=" + threshold);
        controls.add(label2);
        JSlider slider2 = new JSlider(0, 100, threshold);
        slider2.setPreferredSize(new Dimension(50, 20));
        controls.add(slider2);
        slider2.addChangeListener(changeEvent -> {
            threshold = slider2.getValue();
            label2.setText("threshold=" + threshold);
        });
        button = new Button("Thresholding");
        button.addActionListener(this);
        controls.add(button);
        button = new Button("Non-max Suppression");
        button.addActionListener(this);
        controls.add(button);
        button = new Button("Display Corners");
        button.addActionListener(this);
        controls.add(button);
        // add two panels
        add("Center", main);
        add("South", controls);
        addWindowListener(new ExitListener());
        setSize(Math.max(width * 2 + 100, 850), height + 110);
        setVisible(true);
    }

    class ExitListener extends WindowAdapter {

        public void windowClosing(WindowEvent e) {
            System.exit(0);
        }
    }
    // Action listener for button click events

    public void actionPerformed(ActionEvent e) {
        if (((Button) e.getSource()).getLabel().equals("Derivatives")) {
            maxIs = derivativeOfGaussian();
        }
        if (((Button) e.getSource()).getLabel().equals("Corner Response")) {
            cornerResponse(maxIs);
        }
        if (((Button) e.getSource()).getLabel().equals("Thresholding")) {
            thresholding();
        }
        if (((Button) e.getSource()).getLabel().equals("Non-max Suppression")) {
            nonMaxSuppression();
        }
        if (((Button) e.getSource()).getLabel().equals("Display Corners")) {
            displayCorners();
        }
    }

    // gets luminosity from rgb color
    public float RGBtoL(Color clr) {
        float[] rgb = clr.getRGBColorComponents(null);
        float r = rgb[0];
        float g = rgb[1];
        float b = rgb[2];
        // get Min and Max rgb values
        float min = Math.min(r, Math.min(g, b));
        float max = Math.min(r, Math.max(g, b));
        // get luminance 
        float l = (max + min) / 2;
        return l * 100;
    }

    public float[] derivativeOfGaussian() {
        float[][] Ix = new float[width][height];
        float[][] Iy = new float[width][height];
        // get derivative of gaussian kernels (x and y directions) 
        float sigma = 0.5f;
        int w = 4;
        int kSize = (2 * w + 1);
        float[][] dogKernelX = new float[kSize][kSize];
        float[][] dogKernelY = new float[kSize][kSize];
        float sumX = 0;
        float sumY = 0;
        // get derivative of gaussian kernels 
        for (int i = -w; i <= w; i++) {
            for (int j = -w; j <= w; j++) {
                dogKernelX[i + w][j + w] = (float) ((-i / 2 * Math.PI * sigma * sigma * sigma * sigma) * Math.exp((-(i * i) - (j * j)) / 2 * sigma * sigma));
                dogKernelY[i + w][j + w] = (float) ((-j / 2 * Math.PI * sigma * sigma * sigma * sigma) * Math.exp((-(i * i) - (j * j)) / 2 * sigma * sigma));
                sumX += (dogKernelX[i + w][j + w] > 0) ? dogKernelX[i + w][j + w] : 0;
                sumY += (dogKernelY[i + w][j + w] > 0) ? dogKernelY[i + w][j + w] : 0;
            }
        }

        // normalize derivative of gaussian kernels
        for (int i = -w; i <= w; i++) {
            for (int j = -w; j <= w; j++) {
                dogKernelX[i + w][j + w] /= sumX;
                dogKernelY[i + w][j + w] /= sumY;
            }
        }
        float maxIy = 0;
        float maxIx = 0;
        float maxIxIy = 0;

        // apply derivative of gaussian filter 
        // also get max values for A matrix for normalizing later
        for (int q = 0; q < height; q++) {
            for (int p = 0; p < width; p++) {
                sumX = 0;
                sumY = 0;
                for (int v = -w; v <= w; v++) {
                    for (int u = -w; u <= w; u++) {
                        int x = (p + u <= 0) ? 0 : (p + u >= width) ? width - 1 : p + u;
                        int y = (q + v <= 0) ? 0 : (q + v >= height) ? height - 1 : q + v;
                        float lumi = RGBtoL(new Color(source.image.getRGB(x, y)));
                        sumX += lumi * dogKernelX[u + w][v + w];
                        sumY += lumi * dogKernelX[u + w][v + w];
                        maxIx = (sumX * sumX > maxIx) ? sumX * sumX : maxIx;
                        maxIy = (sumY * sumY > maxIy) ? sumY * sumY : maxIy;
                        maxIxIy = (sumX * sumY > maxIxIy) ? sumX * sumY : maxIxIy;
                    }
                }

            }
        }

        // normalize A matrix values
        // possible issue: unnormalized values are actually passed through
        //                 target image is all black otherwise.
        for (int q = 0; q < height; q++) {
            for (int p = 0; p < width; p++) {
                sumX = 0;
                sumY = 0;
                for (int v = -w; v <= w; v++) {
                    for (int u = -w; u <= w; u++) {
                        int x = (p + u <= 0) ? 0 : (p + u >= width) ? width - 1 : p + u;
                        int y = (q + v <= 0) ? 0 : (q + v >= height) ? height - 1 : q + v;
                        float lumi = RGBtoL(new Color(source.image.getRGB(x, y)));
                        sumX += lumi * dogKernelX[u + w][v + w];
                        sumY += lumi * dogKernelX[u + w][v + w];
                    }
                }
                target.image.setRGB(p, q, (int) (sumX * sumX) << 16 | (int) (sumY * sumY) << 8 | (int) (sumX * sumY));

            }
        }
        target.repaint();
        return new float[]{maxIx, maxIy, maxIxIy};
    }

    public void cornerResponse(float[] maxI) {
        float maxIx = maxI[0];
        float maxIy = maxI[1];
        float maxIxIy = maxI[2];

        float[][] a = new float[2][2];
        float r, det, trace;
        float max = -999999999;
        float min = 999999999;
        // calculate r for each pixel
        for (int q = 0; q < height; q++) {
            for (int p = 0; p < width; p++) {
                Color clr = new Color(target.image.getRGB(p, q));
                a[0][0] = clr.getRed();
                a[0][1] = clr.getBlue();
                a[1][0] = clr.getBlue();
                a[1][1] = clr.getGreen();
                det = a[0][0] * a[1][1] - a[0][1] * a[1][0];
                trace = a[0][0] + a[1][1];
                r = (float) (det - sensitivity * trace * trace);
                if (r > max) {
                    max = r;
                }
            }
        }
        // normalize 
        for (int q = 0; q < height; q++) {
            for (int p = 0; p < width; p++) {
                Color clr = new Color(target.image.getRGB(p, q));
                a[0][0] = clr.getRed();
                a[0][1] = clr.getBlue();
                a[1][0] = clr.getBlue();
                a[1][1] = clr.getGreen();
                det = a[0][0] * a[1][1] - a[0][1] * a[1][0];
                trace = a[0][0] + a[1][1];
                r = (float) (det - sensitivity * trace * trace);
                if (r <= 0) {
                    r = 0;
                } else if (r > 0) {
                    r = (r / max) * 255;
                }
                target.image.setRGB(p, q, (int) r << 16 | (int) r << 8 | (int) r);
            }
        }
        target.repaint();
    }

    public void thresholding() {
        // get max r
        int max = 0;
        int r;
        for (int q = 0; q < height; q++) {
            for (int p = 0; p < width; p++) {
                Color clr = new Color(target.image.getRGB(p, q));
                r = clr.getRed();
                if (r > max) {
                    max = r;
                }
            }
        }
        // perform thresholding on all pixels
        int t = max * threshold / 100;
        for (int q = 0; q < height; q++) {
            for (int p = 0; p < width; p++) {
                Color clr = new Color(target.image.getRGB(p, q));
                r = clr.getRed();
                r = (r >= t) ? 255 : 0;
                target.image.setRGB(p, q, (int) r << 16 | (int) r << 8 | (int) r);
            }
        }
        target.repaint();
    }

    // iterate through image with 3x3 window 
    // turn max pixel white and the rest black
    public void nonMaxSuppression() {
        int winWidth = 1;
        for (int q = winWidth; q < height; q += winWidth + 1) {
            for (int p = winWidth + 1; p < width; p += winWidth + 1) {
                int r = 0;
                int max = 0;
                int x, y;
                for (int i = -winWidth; i < winWidth; i++) {
                    for (int j = -winWidth; j < winWidth; j++) {
                        Color clr = new Color(target.image.getRGB(p + i, q + j));
                        r = clr.getRed();
                        max = (r > max) ? r : max;
                    }
                }
                int color;
                for (int i = -winWidth; i < winWidth; i++) {
                    for (int j = -winWidth; j < winWidth; j++) {
                        Color clr = new Color(target.image.getRGB(p + i, q + j));
                        if (max > 0) {
                            color = (clr.getRed() == max) ? 255 : 0;
                            target.image.setRGB(p, q, color << 16 | color << 8 | color);
                        }
                    }
                }
            }
        }
        target.repaint();
    }

    // draws a circle of radius around white pixels
    public void displayCorners() {
        int x, y;
        int radius = 10;
        for (int k = 0; k < height; k++) {
            for (int h = 0; h < width; h++) {
                Color clr = new Color(target.image.getRGB(h, k));
                if (clr.getRed() == 255) {
                    for (int i = 0; i < 360; i++) {
                        x = (int) (h + radius * Math.cos(i));
                        y = (int) (k + radius * Math.sin(i));
                        if (x > 0 && x < width && y > 0 && y < height) {
                            source.image.setRGB(x, y, new Color(255, 0, 0).getRGB());
                        }
                    }
                }
            }
        }
        source.repaint();
    }

    public static void main(String[] args) {
        new CornerDetection(args.length == 1 ? args[0] : "rectangle.png");
    }

}