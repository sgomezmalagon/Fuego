package controller;

import model.FireModel;
import view.FirePanel;

import javax.swing.Timer;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

// controlador simple que actualiza modelo y vista
public class FireController {
    private final FireModel model;
    private final FirePanel panel;
    private final Timer timer;

    public FireController(FireModel model, FirePanel panel, int fps) {
        this.model = model;
        this.panel = panel;
        int delay = 1000 / fps;

        // NO BORRAR LA MÁSCARA desde aquí
        this.timer = new Timer(delay, e -> step());
    }

    public void start() { timer.start(); }
    public void stop() { timer.stop(); }

    // un frame: pedimos la máscara al panel y se la damos al modelo antes de generar
    private void step() {
        try {
            boolean[] mask = panel.getMask(model.mAncho, model.mAlto);
            if (mask != null) {
                // Restringir máscara al 50% central (rectángulo) para que el fuego ocupe la mitad del ancho
                int w = model.mAncho;
                int h = model.mAlto;
                boolean[] narrowMask = new boolean[mask.length];
                int startX = w / 4; // 25% margen izquierdo
                int endX = startX + w / 2; // 50% ancho centrado
                for (int y = 0; y < h; y++){
                    int rowStart = y * w;
                    for (int x = startX; x < endX && x < w; x++){
                        int idx = rowStart + x; if (idx >= 0 && idx < mask.length && mask[idx]) narrowMask[idx] = true;
                    }
                }

                // aplicar la máscara reducida al modelo
                model.setMask(narrowMask);
                // También pasar la misma máscara al panel para que la visualización use la misma región
                panel.setManualMask(narrowMask);

                // calcular fila superior/inferior del hueco (cavityTop, cavityBottom) usando la máscara reducida
                int cavityTop = -1;
                int cavityBottom = -1;

                // top
                for (int y = 0; y < h; y++){
                    int rowStart = y * w;
                    for (int x = 0; x < w; x++){
                        if (narrowMask[rowStart + x]) { cavityTop = y; break; }
                    }
                    if (cavityTop != -1) break;
                }
                // bottom
                for (int y = h - 1; y >= 0; y--) {
                    int rowStart = y * w;
                    for (int x = 0; x < w; x++) {
                        if (narrowMask[rowStart + x]) { cavityBottom = y; break; }
                    }
                    if (cavityBottom != -1) break;
                }

                if (cavityTop != -1 && cavityBottom != -1 && cavityBottom >= cavityTop) {
                    int cavityHeight = cavityBottom - cavityTop + 1;
                    // subir el fuego un 10%: usar ~20% de la altura de la cavidad como lift (antes 10%)
                    int lift = Math.max(2, (int)(cavityHeight * 0.2));
                    int desiredTargetRow = cavityBottom - lift;
                    // Ajuste extra: subir el fuego un 10% adicional de la altura de la cavidad (sube otro 5% respecto a antes)
                    int extraUp = (int)Math.round(cavityHeight * 0.10);
                    desiredTargetRow -= extraUp; // mover hacia arriba (filas menores)
                    if (desiredTargetRow < cavityTop) desiredTargetRow = cavityTop;
                    int rowOffset = (model.mAlto - 1) - desiredTargetRow;
                    if (rowOffset < 0) rowOffset = 0;
                    model.setRowOffset(rowOffset);
                }
            }
        } catch (Exception ex) {
            // ignorar si algo falla al obtener la máscara
        }

        int[] buffer = model.getImageBuffer();
        panel.updateImage(buffer, model.mAncho, model.mAlto);
    }
}
