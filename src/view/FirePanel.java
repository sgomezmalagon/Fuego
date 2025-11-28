package view;

import javax.swing.JPanel;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Dimension;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.util.ArrayDeque;
import java.awt.RadialGradientPaint;
import java.awt.MultipleGradientPaint;
import java.awt.geom.Point2D;
import imagenes.ImageLoader;

// panel que dibuja fondo y fuego
public class FirePanel extends JPanel {
    private BufferedImage image; // buffer del fuego
    private BufferedImage background; // imagen de fondo
    private final int mScale;

    private boolean[] lastMask = null;
    private int maskWidth = 0;
    private int maskHeight = 0;
    // si el usuario fija una máscara manual, no la sobrescribimos
    private boolean manualMaskSet = false;

    public FirePanel(int width, int height, int scale) {
        this.mScale = scale;
        // crear imagen premultiplicada al tamaño real (evita artefactos al pintar)
        this.image = new BufferedImage(width * scale, height * scale, BufferedImage.TYPE_INT_ARGB_PRE);
        this.setPreferredSize(new Dimension(width * scale, height * scale));

        try {
            // Intentamos varias variantes (mayúsculas/minúsculas y extensiones comunes)
            String[] tryNames = new String[]{"chimeneaa.png", "chimeneaa.jpg", "chimenea.jpg", "Chimenea.png", "Chimenea.jpg", "chimenea.png"};
            background = null;
            for (String n : tryNames){
                try {
                    background = ImageLoader.load(n);
                    if (background != null) break;
                } catch (RuntimeException ex) {
                    // ignoramos y probamos el siguiente nombre
                }
            }
        } catch (RuntimeException ex) {
            background = null;
            System.err.println("Aviso: no se pudo cargar la imagen de fondo: " + ex.getMessage());
            ex.printStackTrace();
        }

        // Si tenemos fondo, calcular un color promedio y usarlo como fondo del panel
        if (background != null) {
            long rsum = 0, gsum = 0, bsum = 0; int samples = 0;
            int stepX = Math.max(1, background.getWidth() / 16);
            int stepY = Math.max(1, background.getHeight() / 16);
            for (int y = 0; y < background.getHeight(); y += stepY) {
                for (int x = 0; x < background.getWidth(); x += stepX) {
                    int rgb = background.getRGB(x, y);
                    rsum += (rgb >>> 16) & 0xFF;
                    gsum += (rgb >>> 8) & 0xFF;
                    bsum += rgb & 0xFF;
                    samples++;
                }
            }
            if (samples > 0) {
                int avr = (int)(rsum / samples);
                int avg = (int)(gsum / samples);
                int avb = (int)(bsum / samples);
                this.setBackground(new java.awt.Color(avr, avg, avb));
            }
        }

        // Mantener el panel opaco y dejar que super.paintComponent limpie el fondo
        this.setOpaque(true);

        try { lastMask = computeFireMask(width, height); } catch (Exception ex) { }
    }

    // detecta donde está el hueco oscuro de la chimenea
    public boolean[] computeFireMask(int width, int height) {
        // Si el usuario fijó una máscara manual, no la sobrescribimos
        if (manualMaskSet && lastMask != null && maskWidth == width && maskHeight == height) {
            return lastMask;
        }
        boolean[] mask = new boolean[width * height];
        maskWidth = width; maskHeight = height; lastMask = null;
        if (background == null) { for (int i = 0; i < mask.length; i++) mask[i] = true; lastMask = mask; return mask; }

        int bgW = background.getWidth(); int bgH = background.getHeight();
        final double BRIGHT_THRESH = 130.0; // umbral aumentado para detectar huecos más claros

        boolean[] dark = new boolean[width * height];
        for (int y = 0; y < height; y++){
            for (int x = 0; x < width; x++){
                int bx = Math.min(bgW - 1, x * bgW / width);
                int by = Math.min(bgH - 1, y * bgH / height);
                int rgb = background.getRGB(bx, by);
                int r = (rgb >>> 16) & 0xFF;
                int g = (rgb >>> 8) & 0xFF;
                int b = rgb & 0xFF;
                double bright = 0.299 * r + 0.587 * g + 0.114 * b;
                dark[y * width + x] = bright < BRIGHT_THRESH;
            }
        }

        int lastRow = height - 1; int center = width / 2; int seedRadius = Math.max(3, width / 8);
        ArrayDeque<Integer> seeds = new ArrayDeque<>();
        for (int sx = Math.max(0, center - seedRadius); sx <= Math.min(width - 1, center + seedRadius); sx++){
            int idx = lastRow * width + sx; if (dark[idx]) seeds.add(idx);
        }
        if (seeds.isEmpty()){
            for (int sy = lastRow - 1; sy >= Math.max(0, lastRow - 8) && seeds.isEmpty(); sy--){
                for (int sx = Math.max(0, center - seedRadius); sx <= Math.min(width - 1, center + seedRadius); sx++){
                    int idx = sy * width + sx; if (dark[idx]) { seeds.add(idx); break; }
                }
            }
        }

        boolean[] visited = new boolean[width * height];
        boolean[] bestRegion = new boolean[width * height]; int bestSize = 0;
        while (!seeds.isEmpty()){
            int s = seeds.poll(); if (visited[s] || !dark[s]) continue;
            ArrayDeque<Integer> q = new ArrayDeque<>(); ArrayDeque<Integer> component = new ArrayDeque<>();
            q.add(s); visited[s] = true; component.add(s);
            while (!q.isEmpty()){
                int cur = q.poll(); int cy = cur / width; int cx = cur % width;
                int[] nx = {cx-1, cx+1, cx, cx}; int[] ny = {cy, cy, cy-1, cy+1};
                for (int k = 0; k < 4; k++){
                    int nxv = nx[k]; int nyv = ny[k];
                    if (nxv < 0 || nxv >= width || nyv < 0 || nyv >= height) continue;
                    int nidx = nyv * width + nxv; if (!visited[nidx] && dark[nidx]){ visited[nidx] = true; q.add(nidx); component.add(nidx); }
                }
            }
            int compSize = component.size(); if (compSize > bestSize){ bestSize = compSize; for (int i = 0; i < bestRegion.length; i++) bestRegion[i] = false; for (int idx : component) bestRegion[idx] = true; }
        }

        if (bestSize < 30){ int marginX = Math.max(1, width / 6); int minY = Math.max(0, (int) (height * 0.45)); for (int y = minY; y < height; y++){ for (int x = marginX; x < width - marginX; x++){ bestRegion[y * width + x] = true; } } }

        int dil = 1; boolean[] dilated = new boolean[width * height];
        for (int y = 0; y < height; y++){ for (int x = 0; x < width; x++){ boolean v = false; for (int oy = -dil; oy <= dil && !v; oy++){ for (int ox = -dil; ox <= dil && !v; ox++){ int xx = x + ox; int yy = y + oy; if (xx < 0 || xx >= width || yy < 0 || yy >= height) continue; if (bestRegion[yy * width + xx]) v = true; } } dilated[y * width + x] = v; } }

        mask = dilated; lastMask = mask; return mask;
    }

    // Devuelve la máscara efectiva: manual si está fijada, si no calcula/usa la automática
    public boolean[] getMask(int width, int height) {
        if (manualMaskSet && lastMask != null && maskWidth == width && maskHeight == height) return lastMask;
        return computeFireMask(width, height);
    }

    public void updateImage(int[] pixels, int width, int height) {
        if (pixels == null) return;
        // Escalado por bloques: cada píxel del buffer fuente ocupa scale x scale en la imagen de destino
        int sw = width; int sh = height; int scale = this.mScale;
        int dw = sw * scale; int dh = sh * scale;
        int[] dest = new int[dw * dh]; // inicializados a 0 (transparent)

        // Si tenemos imagen de fondo, muestrearla (nearest) para rellenar dest y evitar transparencias
        if (background != null) {
            int bgW = background.getWidth();
            int bgH = background.getHeight();
            int bgFill = this.getBackground().getRGB();
            for (int y = 0; y < dh; y++){
                int srcY = Math.min(bgH - 1, (int)((long)y * bgH / dh));
                for (int x = 0; x < dw; x++){
                    int srcX = Math.min(bgW - 1, (int)((long)x * bgW / dw));
                    int p = background.getRGB(srcX, srcY);
                    // si el píxel de fondo es transparente (alpha == 0), usar el color de fondo calculado
                    if (((p >>> 24) & 0xFF) == 0) p = bgFill;
                    dest[y * dw + x] = p;
                }
            }
        }

         boolean[] mask = getMask(sw, sh);
         for (int sy = 0; sy < sh; sy++){
             int rowSrc = sy * sw;
             for (int sx = 0; sx < sw; sx++){
                 int srcIdx = rowSrc + sx;
                 int val = (mask != null && mask.length == pixels.length) ? (mask[srcIdx] ? pixels[srcIdx] : 0) : pixels[srcIdx];
                 if (val == 0) continue;
                 int baseY = sy * scale;
                 int baseX = sx * scale;
                 int srcA = (val >>> 24) & 0xFF;
                 int srcR = (val >>> 16) & 0xFF;
                 int srcG = (val >>> 8) & 0xFF;
                 int srcB = val & 0xFF;
                 for (int dy = 0; dy < scale; dy++){
                     int dstRow = (baseY + dy) * dw;
                     int dstIdx = dstRow + baseX;
                     for (int dx = 0; dx < scale; dx++){
                         int di = dstIdx + dx;
                         int dstRGB = dest[di];
                         int dstA = (dstRGB >>> 24) & 0xFF;
                         int dstR = (dstRGB >>> 16) & 0xFF;
                         int dstG = (dstRGB >>> 8) & 0xFF;
                         int dstB = dstRGB & 0xFF;
                         // compositing: out = src over dst
                         double a = srcA / 255.0;
                         int outR = (int)Math.round(srcR * a + dstR * (1.0 - a));
                         int outG = (int)Math.round(srcG * a + dstG * (1.0 - a));
                         int outB = (int)Math.round(srcB * a + dstB * (1.0 - a));
                         int outA = Math.min(255, srcA + dstA); // simple approximation
                         dest[di] = (outA << 24) | (outR << 16) | (outG << 8) | outB;
                     }
                 }
             }
         }

         // volcar en el BufferedImage del panel (ya del tamaño real)
         // Premultiplicar el buffer de destino si la imagen es premultiplicada
         if (image.getType() == BufferedImage.TYPE_INT_ARGB_PRE) {
             int[] prem = new int[dest.length];
             for (int i = 0; i < dest.length; i++) {
                 int argb = dest[i];
                 int a = (argb >>> 24) & 0xFF;
                 if (a == 0) { prem[i] = 0; continue; }
                 int r = (argb >>> 16) & 0xFF;
                 int g = (argb >>> 8) & 0xFF;
                 int b = argb & 0xFF;
                 int rp = (r * a + 127) / 255;
                 int gp = (g * a + 127) / 255;
                 int bp = (b * a + 127) / 255;
                 prem[i] = (a << 24) | (rp << 16) | (gp << 8) | bp;
             }
             image.setRGB(0, 0, dw, dh, prem, 0, dw);
         } else {
             image.setRGB(0, 0, dw, dh, dest, 0, dw);
         }
         repaint();
    }

    // permite establecer máscara manualmente (override de la detectada)
    public void setManualMask(boolean[] mask){
        if (mask == null) return;
        if (mask.length == maskWidth * maskHeight) { this.lastMask = mask; manualMaskSet = true; }
    }

    // limpia la máscara manual, volviendo a la detección automática
    public void clearManualMask() { manualMaskSet = false; computeFireMask(maskWidth, maskHeight); }

    @Override
    protected void paintComponent(Graphics g) {
        // Limpiar fondo siempre (evita cuadros negros detrás de la transparencia)
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            // No dibujamos el `background` aquí: `image` ya contiene el fondo muestreado.
            // Esto evita que se superponga y genere artefactos visibles (rectángulo negro).

            if (lastMask != null) {
                int w = maskWidth; int h = maskHeight; int minX = w, minY = h, maxX = 0, maxY = 0;
                for (int y = 0; y < h; y++){ for (int x = 0; x < w; x++){ if (!lastMask[y * w + x]) continue; if (x < minX) minX = x; if (y < minY) minY = y; if (x > maxX) maxX = x; if (y > maxY) maxY = y; } }
                if (minX <= maxX && minY <= maxY) {
                    int panelW = getWidth(); int panelH = getHeight(); float cx = (float)((minX + maxX) * 0.5 * panelW / w); float cy = (float)((minY + maxY) * 0.5 * panelH / h);
                    float radius = (float)(Math.max(maxX - minX, maxY - minY) * Math.max(panelW / (float)w, panelH / (float)h) * 1.8);
                    if (radius < 40) radius = 40;
                    // colores cálidos y suaves, menos saturados
                    // float[] dist = {0.0f, 0.5f, 1.0f};
                    // Color[] colors = {new Color(255, 210, 140, 220), new Color(255, 190, 120, 140), new Color(255, 180, 120, 0)};
                    // RadialGradientPaint rgp = new RadialGradientPaint(new Point2D.Float(cx, cy), radius, dist, colors, MultipleGradientPaint.CycleMethod.NO_CYCLE);
                    // g2.setPaint(rgp); g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.85f)); g2.fillOval((int)(cx - radius), (int)(cy - radius), (int)(radius * 2), (int)(radius * 2)); g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
                    // iluminación radial desactivada (provocaba artefactos/contornos)
                    // antes aplicábamos un RadialGradientPaint aquí para iluminar la cavidad;
                    // lo dejamos apagado para evitar el "cuadro negro" que comentaste.
                 }
            }

            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Dibujar la imagen ya escalada (1:1), la máscara ya fue aplicada en updateImage
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
            g2.drawImage(image, 0, 0, getWidth(), getHeight(), null);

          } finally {
              g2.dispose();
          }
      }
  }
