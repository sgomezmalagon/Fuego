# Fuego - descripción del proyecto

Este proyecto genera una animación de fuego usando Swing (patrón MVC). Aquí tienes qué hace cada parte y cómo ajustar el comportamiento del fuego.

Estructura principal
- `src/Main.java` — Inicializa la aplicación, crea el `FireModel`, el `FirePanel` y el `FireController`. Aquí es donde se fijan parámetros globales (ancho/alto, zoom, fps) y se puede crear una máscara manual si se desea.
- `src/model/FireModel.java` — Lógica que genera la textura del fuego (temperaturas por píxel, interpolación, enfriamiento, generación de la máscara automática). Métodos importantes:
  - `setRowOffset(int)` — Ajusta el desplazamiento vertical inicial del fuego (mueve la fuente de calor hacia arriba/abajo).
  - `setUseLinearInterpolation(boolean)` — Habilita/deshabilita interpolación lineal entre colores.
  - `setInterpolationAmount(double)` — Controla cuánto mezcla la interpolación (0..1).
  - `setVerticalCoolingFactor(double)` — Factor de enfriamiento vertical: valores más bajos permiten que las llamas suban más.
  - `computeFireMask()` — Calcula una máscara automática a partir de la imagen de fondo (detección del hueco). Si no detecta correctamente, puedes aumentar el umbral BRIGHT_THRESH.

- `src/view/FirePanel.java` — Componente Swing que pinta la textura del fuego y la imagen de fondo. Métodos relevantes:
  - `updateImage(int[] buffer, int ancho, int alto)` — Recibe el buffer generado por el modelo y lo pinta.
  - `setManualMask(boolean[] mask)` — Permite pasar una máscara manual para forzar dónde debe aparecer el fuego.
  - `lastMask` — Campo interno que almacena la máscara actual (manual o calculada).

- `src/controller/FireController.java` — Timer que llama periódicamente al modelo y actualiza el `FirePanel`. Atención: NO desactivar la máscara en el controlador (no llamar a `model.setMask(null)` cada frame), porque eso hace que el fuego se dibuje en toda la imagen.

- `src/imagenes/ImageLoader.java` y `src/imagenes/chimeneaa.png` — Carga de recursos; la imagen de la chimenea se usa como fondo para detectar la cavidad.

Ejecución (PowerShell)

Compilar:

javac -d out src\Main.java src\model\FireModel.java src\view\FirePanel.java src\controller\FireController.java src\imagenes\ImageLoader.java

Ejecutar:

java -cp out Main

Consejos para ajustar el fuego / resolución de problemas

- Si el fuego aparece fuera del hueco o en toda la imagen:
  - Asegúrate de que `FireController` no esté llamando a `model.setMask(null)` en el constructor ni en cada `step()`.
  - `computeFireMask()` depende del brillo: si la cavidad no es lo bastante oscura, incrementa el umbral. Busca la constante `BRIGHT_THRESH` en `FireModel` y prueba valores más altos (p. ej. 130).
  - Si prefieres control manual, usa `FirePanel.setManualMask(boolean[])`. En `Main` puedes crear una elipse o rectángulo y pasarla al panel; así el fuego solo aparecerá dentro de esa área.

- Para mover el fuego verticalmente o cambiar su anchura:
  - `model.setRowOffset(int)` mueve la fuente verticalmente (valores positivos suben o bajan según implementación; prueba valores pequeños y ajusta).
  - Cambios en la anchura se suelen hacer modificando la máscara (manualMask) o los parámetros de la elipse usada para generarla.

- Para que las llamas suban más o lleguen más alto:
  - Reduce `setVerticalCoolingFactor(double)` (menos enfriamiento → llamas suben más).
  - Ajusta `setInterpolationAmount(double)` y `setUseLinearInterpolation(true)` para suavizar colores y sensibilidad.
  - Ten en cuenta también `rowOffset` y el tamaño de la máscara.

- Para evitar un rectángulo visible ("cuadro marrón/negro") en la imagen:
  - Revisa si en `Main` estás pintando una máscara visible o un fondo de máscara. Lo habitual es que la máscara solo controle dónde se renderiza el fuego, sin pintar un rectángulo extra.
  - Si el rectángulo proviene de un `manualMask` dibujado como fondo, elimina esa pintura o pasa una máscara que solo permita dibujar el fuego.

Fragmento crítico del cálculo de temperatura

El bucle de difusión/atenuación del fuego es importante y debe mantenerse tal cual si otras partes dependen de él. En `FireModel` hay un fragmento como este (simplificado aquí para referencia):

for (int actualRow = this.fireHeight - 2; (actualRow > 4); actualRow--) {
    iniRow = this.fireWidth * actualRow;
    iniBelowRow = iniRow + this.fireWidth;
    for (int actualCol = 2; (actualCol < this.fireWidth - 2); actualCol++) {
        pos = iniRow + actualCol;
        posBelow = iniBelowRow + actualCol;
        this.pixelTemperature[pos]
            = ((int) ((pixelTemperature[pos - 1] * 1.2D
            + pixelTemperature[pos] * 1.5D
            + pixelTemperature[pos + 1] * 1.2D
            + pixelTemperature[posBelow - 1] * 0.7D
            + pixelTemperature[posBelow] * 0.7D
            + pixelTemperature[posBelow + 1] * 0.7D) / 5.98569
            - 1.8D));
        if (this.pixelTemperature[pos] < 0) {
            pixelTemperature[pos] = 0;
        } else if (pixelTemperature[pos] > 1023) {
            pixelTemperature[pos] = 1023;
        }
    }
}

No modifiques este bloque a menos que entiendas el efecto de cada constante.


