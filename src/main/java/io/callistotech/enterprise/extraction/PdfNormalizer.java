package io.callistotech.enterprise.extraction;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.imgscalr.Scalr;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Stateless helper that re-renders a PDF and iteratively shrinks its
 * pages with <b>imgscalr</b> until the resulting PDF fits under Azure
 * Document Intelligence's 4 MB {@code InvalidContentLength} limit.
 *
 * <p><b>Strategy — fixed-shape loop, no DPI guessing:</b>
 * <ol>
 *   <li>Render every page <i>once</i> via PDFBox's {@link PDFRenderer}
 *       at the caller-supplied baseline DPI, as grayscale
 *       ({@link ImageType#GRAY}) — chroma carries no signal on printed
 *       B/W financial forms and costs 3x the pixel buffer.</li>
 *   <li>Find the longest edge across all rendered pages. This is the
 *       scaling knob.</li>
 *   <li>Serialize the current image set into a PDF (JPEG-embedded at
 *       quality {@link #JPEG_QUALITY}) and measure the byte count.</li>
 *   <li>If under {@link #AZURE_SIZE_THRESHOLD} -> return.</li>
 *   <li>Otherwise shrink every page proportionally via
 *       {@link Scalr#resize(BufferedImage, int)} with the new longest
 *       edge = current longest edge x {@link #SHRINK_FACTOR}. imgscalr
 *       preserves aspect ratio for every page automatically, even if
 *       pages have different dimensions.</li>
 *   <li>Repeat until under threshold OR the longest edge would drop
 *       below {@link #MIN_LONGEST_EDGE_PX} -- the legibility floor below
 *       which Azure DI OCR degrades past the point of usefulness.</li>
 * </ol>
 *
 * <p>The smallest byte array observed across all iterations <b>and the
 * original input</b> is the return value. If nothing beats the
 * original (e.g. vector-text born-digital PDF, or a source already
 * stored as CCITT G4 that won't re-compress smaller), the original is
 * returned unchanged -- re-rendering is never allowed to bloat.
 *
 * <p>Pure function -- no Spring beans, no logging, no side effects.
 * Fully unit-testable without a database or HTTP mock.
 */
public final class PdfNormalizer {

    /**
     * Azure Document Intelligence rejects payloads strictly greater than
     * 4 MB with {@code InvalidContentLength}. 4,000,000 bytes leaves
     * headroom for HTTP framing overhead.
     */
    private static final int AZURE_SIZE_THRESHOLD = 4_000_000;

    /**
     * Multiplier applied to the longest edge each iteration. 0.85 means
     * each pass shrinks linear dimensions by 15% -> roughly 28% pixel
     * reduction per iteration, which in turn yields ~25-30% JPEG byte
     * reduction per iteration on typical printed-form content.
     * Convergence from a 10 MB baseline is ~6-8 iterations in the worst
     * case, which is well within the recovery path's time budget.
     */
    private static final double SHRINK_FACTOR = 0.85;

    /**
     * Lower bound on the longest-edge pixel count. Azure DI's printed-
     * form OCR needs roughly ~150 DPI equivalent to reliably segment
     * glyphs; on an 11-inch page that's ~1650 px. We floor at 900 px
     * to cover letter/A4/legal while refusing to degrade past OCR
     * usefulness. If the loop would cross this floor, it stops.
     */
    private static final int MIN_LONGEST_EDGE_PX = 900;

    /**
     * JPEG quality factor for page embedding. 0.65 is aggressive enough
     * to meaningfully shrink per-page bytes versus 0.75 while staying
     * above the ~0.55 threshold where block artefacts on glyph edges
     * begin to cost OCR accuracy.
     */
    private static final float JPEG_QUALITY = 0.65f;

    /**
     * Maximum number of shrink iterations. Prevents pathological
     * infinite loops if compression efficiency stalls. 12 iterations at
     * 0.85 per step brings longest edge to ~15% of baseline -- well past
     * where Azure could still read the document.
     */
    private static final int MAX_ITERATIONS = 12;

    private PdfNormalizer() {}

    /**
     * Re-renders {@code pdfBytes} at {@code baselineDpi}, then
     * iteratively shrinks until the output fits under
     * {@link #AZURE_SIZE_THRESHOLD} or the legibility floor is hit.
     * Returns the smallest byte array observed (may be the original).
     *
     * @param pdfBytes    raw PDF bytes (must not be null or empty)
     * @param baselineDpi initial render DPI before any shrinking
     *                    (must be &gt;= 1). Typical value: 300.
     * @return the smallest PDF byte array observed; never null
     * @throws IOException              if the input cannot be parsed as a PDF
     * @throws IllegalArgumentException if {@code pdfBytes} is null / empty
     *                                  or {@code baselineDpi < 1}
     */
    public static byte[] normalize(byte[] pdfBytes, int baselineDpi) throws IOException {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalArgumentException("pdfBytes must not be null or empty");
        }
        if (baselineDpi < 1) {
            throw new IllegalArgumentException("baselineDpi must be >= 1");
        }

        try (PDDocument source = Loader.loadPDF(pdfBytes)) {
            // Pre-render every page once at baselineDpi. Subsequent iterations
            // resize these BufferedImages in place rather than re-rendering
            // from the PDF, which would be far more expensive.
            List<BufferedImage> pages = renderAllPages(source, baselineDpi);
            List<PDRectangle> pageSizes = capturePageSizes(source);

            byte[] smallest = pdfBytes;

            for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
                byte[] built = buildPdf(pages, pageSizes);
                if (built.length < smallest.length) {
                    smallest = built;
                }
                if (smallest.length <= AZURE_SIZE_THRESHOLD) {
                    return smallest;
                }

                int longestEdge = longestEdgeAcrossPages(pages);
                int nextLongest = (int) Math.round(longestEdge * SHRINK_FACTOR);
                if (nextLongest < MIN_LONGEST_EDGE_PX) {
                    // Next step would cross the legibility floor -- stop.
                    return smallest;
                }

                pages = resizeAll(pages, nextLongest);
            }

            return smallest;
        }
    }

    /** Renders every page of {@code source} at {@code dpi} as grayscale. */
    private static List<BufferedImage> renderAllPages(PDDocument source, int dpi) throws IOException {
        PDFRenderer renderer = new PDFRenderer(source);
        List<BufferedImage> pages = new ArrayList<>(source.getNumberOfPages());
        for (int i = 0; i < source.getNumberOfPages(); i++) {
            pages.add(renderer.renderImageWithDPI(i, dpi, ImageType.GRAY));
        }
        return pages;
    }

    /** Captures each page's media box so the output PDF preserves the original page sizes in points. */
    private static List<PDRectangle> capturePageSizes(PDDocument source) {
        List<PDRectangle> sizes = new ArrayList<>(source.getNumberOfPages());
        for (int i = 0; i < source.getNumberOfPages(); i++) {
            PDRectangle mb = source.getPage(i).getMediaBox();
            sizes.add(new PDRectangle(mb.getWidth(), mb.getHeight()));
        }
        return sizes;
    }

    /**
     * Serializes {@code pages} into a new PDF whose pages have the
     * supplied {@code pageSizes} in points. Each image is embedded as a
     * lossy JPEG at {@link #JPEG_QUALITY}.
     */
    private static byte[] buildPdf(List<BufferedImage> pages, List<PDRectangle> pageSizes) throws IOException {
        try (PDDocument output = new PDDocument()) {
            for (int i = 0; i < pages.size(); i++) {
                PDRectangle size = pageSizes.get(i);
                PDPage page = new PDPage(new PDRectangle(size.getWidth(), size.getHeight()));
                output.addPage(page);
                PDImageXObject xImage = JPEGFactory.createFromImage(output, pages.get(i), JPEG_QUALITY);
                try (PDPageContentStream cs = new PDPageContentStream(output, page)) {
                    cs.drawImage(xImage, 0, 0, size.getWidth(), size.getHeight());
                }
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            output.save(baos);
            return baos.toByteArray();
        }
    }

    /**
     * Resizes every page proportionally so the longest edge of each
     * becomes at most {@code targetLongestEdge}. imgscalr preserves
     * aspect ratio automatically.
     */
    private static List<BufferedImage> resizeAll(List<BufferedImage> pages, int targetLongestEdge) {
        List<BufferedImage> resized = new ArrayList<>(pages.size());
        for (BufferedImage page : pages) {
            resized.add(Scalr.resize(page, Scalr.Method.QUALITY, Scalr.Mode.AUTOMATIC, targetLongestEdge));
        }
        return resized;
    }

    /** Maximum of {@code max(width, height)} across every page. */
    private static int longestEdgeAcrossPages(List<BufferedImage> pages) {
        int longest = 0;
        for (BufferedImage p : pages) {
            longest = Math.max(longest, Math.max(p.getWidth(), p.getHeight()));
        }
        return longest;
    }
}
