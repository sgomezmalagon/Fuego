// Main - arranca la aplicación
public class Main {
    public static void main(String[] args) {
        // arrancar en el hilo de Swing
        javax.swing.SwingUtilities.invokeLater(() -> {
            int ANCHO = 415; // ancho del efecto (aumentado 10% aprox)
            int ALTO = 128;  // alto del efecto
            int ZOOM = 2;    // escala para ver mejor

            // panel que pinta todo
            view.FirePanel panel = new view.FirePanel(ANCHO, ALTO, ZOOM);

            // modelo que genera la textura del fuego
            model.FireModel model = new model.FireModel(ANCHO, ALTO);
            // restaurar posición inicial: valor por defecto pequeño
            model.setRowOffset(3);

            // Habilitar interpolacion lineal y enfriamiento vertical (menos enfriamiento para que suban más)
            model.setUseLinearInterpolation(true); // suaviza colores
            model.setInterpolationAmount(0.5); // mezcla 50%
            model.setVerticalCoolingFactor(0.15); // menos enfriamiento vertical para que las llamas suban más

            // Usar la máscara automática detectada por la imagen (no máscara manual)
            // Eliminamos la máscara manual para quitar el cuadro negro
            // panel.setManualMask(manualMask);  // QUITADO

            // (antes aquí se generaba manualMask; ahora confiamos en computeFireMask)

            // Crear una máscara manual elíptica centrada para asegurar que el fuego sólo aparezca dentro del hueco
            boolean[] manualMask = new boolean[ANCHO * ALTO];
            double cx = ANCHO * 0.5;
            double cy = ALTO * 0.72; // centro vertical algo bajo
            double rx = ANCHO * 0.45; // radio x casi todo el ancho
            double ry = ALTO * 0.38;  // radio y cubre la cavidad
            double invRx2 = 1.0 / (rx * rx);
            double invRy2 = 1.0 / (ry * ry);
            for (int y = 0; y < ALTO; y++) {
                for (int x = 0; x < ANCHO; x++) {
                    double dx = x - cx;
                    double dy = y - cy;
                    double ell = (dx * dx) * invRx2 + (dy * dy) * invRy2;
                    if (ell <= 1.0) manualMask[y * ANCHO + x] = true;
                }
            }
            // aplicar la máscara manual al panel (y el controlador usará esta máscara automática cuando corresponda)
            panel.setManualMask(manualMask);

            // controlador que actualiza a 60 fps
            controller.FireController controller = new controller.FireController(model, panel, 60);

            // ventana básica
            javax.swing.JFrame frame = new javax.swing.JFrame("Fuego - MVC");
            frame.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE);
            frame.getContentPane().add(panel);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            // empezar animación
            controller.start();
        });
    }
}