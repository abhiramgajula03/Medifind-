package com.medifind.service;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import nu.pattern.OpenCV;
import org.springframework.stereotype.Service;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class BillDetectionService {
    private static final boolean OPEN_CV_AVAILABLE;

    static {
        boolean loaded = false;
        try {
            OpenCV.loadLocally();
            loaded = true;
        } catch (Throwable error) {
            System.err.println("[BillDetectionService] OpenCV unavailable; using the original image: " + error.getMessage());
        }
        OPEN_CV_AVAILABLE = loaded;
    }

    public record DetectedBill(BufferedImage cropped, double confidence, List<Point> corners) {}

    public BufferedImage preprocess(BufferedImage original) throws IOException {
        if (!OPEN_CV_AVAILABLE) return original;
        Mat mat = bufferedImageToMat(original);
        Mat gray = new Mat();
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY);

        // Denoise with median blur
        Mat denoised = new Mat();
        Imgproc.medianBlur(gray, denoised, 3);

        // Preserve character detail for docTR; binary thresholding destroyed
        // table digits around folds and low-contrast invoice rows.
        Mat equalized = new Mat();
        Imgproc.createCLAHE(2.0, new Size(8, 8)).apply(denoised, equalized);

        // Gentle unsharp mask, then restore three channels for the OCR model.
        Mat soft = new Mat();
        Imgproc.GaussianBlur(equalized, soft, new Size(0, 0), 1.2);
        Mat sharpened = new Mat();
        Core.addWeighted(equalized, 1.35, soft, -0.35, 0, sharpened);
        Mat enhanced = new Mat();
        Imgproc.cvtColor(sharpened, enhanced, Imgproc.COLOR_GRAY2BGR);

        return matToBufferedImage(enhanced);
    }

    public DetectedBill detectAndCrop(BufferedImage original) throws IOException {
        if (!OPEN_CV_AVAILABLE) return new DetectedBill(original, 0.0, new ArrayList<>());
        Mat mat = bufferedImageToMat(original);
        Mat gray = new Mat();
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY);

        // Blur + edge detection
        Mat blurred = new Mat();
        Imgproc.GaussianBlur(gray, blurred, new Size(5, 5), 0);
        Mat edges = new Mat();
        Imgproc.Canny(blurred, edges, 50, 150);

        // Dilate to close gaps
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3));
        Imgproc.dilate(edges, edges, kernel);

        // Find contours
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);

        double maxArea = 0;
        MatOfPoint2f maxContour = null;
        for (MatOfPoint contour : contours) {
            double area = Imgproc.contourArea(contour);
            if (area > maxArea && area > (mat.rows() * mat.cols() * 0.05)) {
                maxArea = area;
                maxContour = new MatOfPoint2f(contour.toArray());
            }
        }

        if (maxContour == null) {
            return new DetectedBill(null, 0.0, new ArrayList<>());
        }

        // Approximate polygon
        MatOfPoint2f approx = new MatOfPoint2f();
        double peri = Imgproc.arcLength(maxContour, true);
        Imgproc.approxPolyDP(maxContour, approx, 0.02 * peri, true);

        Point[] points = approx.toArray();
        List<Point> cornerPoints = new ArrayList<>();
        for (Point p : points) cornerPoints.add(p);

        // Order points for perspective transform
        Rect boundingRect = Imgproc.boundingRect(approx);
        if (cornerPoints.size() == 4) {
            Mat quad = perspectiveTransform(mat, cornerPoints);
            BufferedImage cropped = matToBufferedImage(quad);
            double confidence = Math.min(0.95, 0.5 + (maxArea / (mat.rows() * mat.cols())));
            return new DetectedBill(cropped, confidence, cornerPoints);
        }

        return new DetectedBill(null, 0.0, cornerPoints);
    }

    private Mat perspectiveTransform(Mat src, List<Point> pts) {
        List<Point> ordered = orderPoints(pts);
        Point tl = ordered.get(0);
        Point tr = ordered.get(1);
        Point br = ordered.get(2);
        Point bl = ordered.get(3);

        double widthA = Math.hypot(br.x - bl.x, br.y - bl.y);
        double widthB = Math.hypot(tr.x - tl.x, tr.y - tl.y);
        double maxWidth = Math.max(widthA, widthB);

        double heightA = Math.hypot(tr.x - br.x, tr.y - br.y);
        double heightB = Math.hypot(tl.x - bl.x, tl.y - bl.y);
        double maxHeight = Math.max(heightA, heightB);

        Mat dst = new Mat();
        Size dsize = new Size(Math.max(1, (int) Math.round(maxWidth)), Math.max(1, (int) Math.round(maxHeight)));
        Imgproc.warpPerspective(src, dst, Imgproc.getPerspectiveTransform(
                new MatOfPoint2f(tl, tr, br, bl),
                new MatOfPoint2f(new Point(0, 0), new Point(maxWidth - 1, 0), new Point(maxWidth - 1, maxHeight - 1), new Point(0, maxHeight - 1))
        ), dsize);
        return dst;
    }

    private List<Point> orderPoints(List<Point> pts) {
        if (pts.size() != 4) throw new IllegalArgumentException("A document requires four corners");
        Point topLeft = pts.stream().min((a, b) -> Double.compare(a.x + a.y, b.x + b.y)).orElseThrow();
        Point bottomRight = pts.stream().max((a, b) -> Double.compare(a.x + a.y, b.x + b.y)).orElseThrow();
        // x - y is greatest at the top-right and smallest at the bottom-left.
        Point topRight = pts.stream().max((a, b) -> Double.compare(a.x - a.y, b.x - b.y)).orElseThrow();
        Point bottomLeft = pts.stream().min((a, b) -> Double.compare(a.x - a.y, b.x - b.y)).orElseThrow();
        List<Point> ordered = new ArrayList<>();
        ordered.add(topLeft);
        ordered.add(topRight);
        ordered.add(bottomRight);
        ordered.add(bottomLeft);
        return ordered;
    }

    private Mat bufferedImageToMat(BufferedImage bi) throws IOException {
        Path temp = Files.createTempFile("opencv_", ".png");
        ImageIO.write(bi, "png", temp.toFile());
        Mat mat = Imgcodecs.imread(temp.toString());
        Files.deleteIfExists(temp);
        return mat;
    }

    private BufferedImage matToBufferedImage(Mat mat) throws IOException {
        if (mat.empty()) throw new IOException("Empty Mat");
        Path temp = Files.createTempFile("opencv_", ".png");
        Imgcodecs.imwrite(temp.toString(), mat);
        BufferedImage bi = ImageIO.read(temp.toFile());
        Files.deleteIfExists(temp);
        return bi;
    }
}
