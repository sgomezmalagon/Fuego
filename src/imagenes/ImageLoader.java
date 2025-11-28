package imagenes;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * ImageLoader - utilidad simple para cargar imágenes desde el paquete /imagenes
 *
 * Uso:
 * - Coloca tus archivos de imagen en la carpeta `src/imagenes` (por ejemplo src/imagenes/fuego.png)
 * - Llama a: BufferedImage img = ImageLoader.load("fuego.png");
 *
 * Nota: intenta primero cargar como recurso (dentro del classpath). Si no lo
 * encuentra (por ejemplo al ejecutar desde el IDE sin copiar recursos), prueba
 * a cargar desde disco en las rutas comunes (src/imagenes, imagenes, ruta relativa).
 */
public class ImageLoader {

    /**
     * Carga una imagen desde el paquete `imagenes` o desde el sistema de archivos.
     * @param name nombre del archivo, por ejemplo "Chimenea.png"
     * @return BufferedImage cargada
     * @throws RuntimeException si no se encuentra o hay un error de lectura
     */
    public static BufferedImage load(String name) {
        // Normalizamos el nombre
        if (name == null) throw new IllegalArgumentException("El nombre no puede ser null");
        String resourcePath = name.startsWith("/") ? name : ("/imagenes/" + name);

        // 1) Intentamos cargar como recurso empaquetado en el classpath
        try (InputStream is = ImageLoader.class.getResourceAsStream(resourcePath)) {
            if (is != null) {
                BufferedImage img = ImageIO.read(is);
                if (img != null) return img;
            }
        } catch (IOException e) {
            // ignoramos y seguiremos con otros intentos
        }

        // 2) Intentamos cargar desde rutas comunes en el sistema de archivos
        String[] tryPaths = new String[] {
            "src/imagenes/" + name,
            "imagenes/" + name,
            name
        };

        for (String p : tryPaths) {
            File f = new File(p);
            if (f.exists() && f.isFile()) {
                try {
                    BufferedImage img = ImageIO.read(f);
                    if (img != null) return img;
                } catch (IOException e) {
                    throw new RuntimeException("Error leyendo la imagen desde fichero: " + f.getAbsolutePath(), e);
                }
            }
        }

        // Si llegamos aquí no se ha podido cargar la imagen
        StringBuilder sb = new StringBuilder();
        sb.append("Imagen no encontrada: ").append(name).append(". Rutas intentadas: ");
        sb.append(resourcePath);
        for (String p : tryPaths) sb.append(", ").append(p);
        throw new RuntimeException(sb.toString());
    }
}
