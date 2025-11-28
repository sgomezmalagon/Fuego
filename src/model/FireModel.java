package model;

import java.util.Random;

// FireModel - textura del fuego
public class FireModel {
    // sizes
    private int mTamanio;
    private int mTamanioBufferEfecto;
    public int mAncho;
    public int mAlto;

    // buffers
    private int mAltoBufferEfecto;
    private int[] mBufferEfecto;
    private int[] mCopiaBufferEfecto;
    private int[] mBufferImagen;

    // cooling
    private int[] mBufferEnfriamiento;
    private int mOffsetBufferEnfriamiento;

    // palette / rng
    private int[] mPaleta;
    private Random mRandom;
    private boolean[] mMask = null;
    // desplazamiento vertical en filas para subir el fuego (por defecto 3 filas)
    private int mRowOffset = 3;
    public void setRowOffset(int rows){ this.mRowOffset = Math.max(0, rows); }
    // (sin target region) la textura se mapeará usando mRowOffset

    // alternative (off)
    private boolean mUseAltPropagation = false;
    public int fireWidth;
    public int fireHeight;
    private int[] pixelTemperature;
    public void setUseAltPropagation(boolean use){ this.mUseAltPropagation = use; }

    // Nuevo: interpolacion lineal y enfriamiento vertical
    private boolean mUseLinearInterpolation = false;
    private double mInterpolationAmount = 0.5; // 0..1, 0 = sin interpolacion, 1 = usar totalmente la segunda muestra
    private double mVerticalCoolingFactor = 0.0; // mayor -> más enfriamiento al subir

    public void setUseLinearInterpolation(boolean use){ this.mUseLinearInterpolation = use; }
    public void setInterpolationAmount(double amt){ this.mInterpolationAmount = Math.max(0.0, Math.min(1.0, amt)); }
    public void setVerticalCoolingFactor(double f){ this.mVerticalCoolingFactor = Math.max(0.0, f); }

    // set mask (null = none)
    public void setMask(boolean[] mask){ if (mask == null) { this.mMask = null; return; } if (mask.length == mTamanio) this.mMask = mask; else this.mMask = null; }

    // RGBA helper
    private int RGB(int r, int g, int b, int a){ return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF); }

    // helper: interpolar colores ARGB
    private int lerpColor(int c1, int c2, double t){
        int a1 = (c1 >> 24) & 0xFF; int r1 = (c1 >> 16) & 0xFF; int g1 = (c1 >> 8) & 0xFF; int b1 = c1 & 0xFF;
        int a2 = (c2 >> 24) & 0xFF; int r2 = (c2 >> 16) & 0xFF; int g2 = (c2 >> 8) & 0xFF; int b2 = c2 & 0xFF;
        int a = (int) Math.max(0, Math.min(255, Math.round(a1 * (1.0 - t) + a2 * t)));
        int r = (int) Math.max(0, Math.min(255, Math.round(r1 * (1.0 - t) + r2 * t)));
        int g = (int) Math.max(0, Math.min(255, Math.round(g1 * (1.0 - t) + g2 * t)));
        int b = (int) Math.max(0, Math.min(255, Math.round(b1 * (1.0 - t) + b2 * t)));
        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    // ctor
    public FireModel(int ancho, int alto){
        mRandom = new Random();
        mAncho = ancho; mAlto = alto;
        mAltoBufferEfecto = alto + 5;
        mTamanio = ancho * alto;
        mTamanioBufferEfecto = ancho * mAltoBufferEfecto;
        mBufferEfecto = new int[mTamanioBufferEfecto];
        mCopiaBufferEfecto = new int[mTamanioBufferEfecto];
        mBufferImagen = new int[mTamanio];
        mBufferEnfriamiento = new int[2 * mTamanioBufferEfecto];

        // fill cooling
        for (int i = 0 ; i < mTamanioBufferEfecto ; i++){
            mBufferEnfriamiento[i] = mRandom.nextInt(8) == 0 ? 50 + mRandom.nextInt(50) : 0;
            mBufferEnfriamiento[i + mTamanioBufferEfecto] = mBufferEnfriamiento[i];
        }
        suavizado(mBufferEnfriamiento, mAncho, 20);

        mOffsetBufferEnfriamiento = 0;

        // palette: tonos naranja -> amarillo, no muy saturados
        mPaleta = new int[112];
        int idx = 0;
        // sombras profundas (rojizos apagados)
        for (int j = 0; j < 16; j++){
            int r = 40 + j * 8; int g = 8; int b = 0; int a = 255;
            mPaleta[idx++] = RGB(r, g, b, a);
        }
        // naranjas medios
        for (int j = 0; j < 32; j++){
            int r = 255; int g = 40 + j * 4; if (g > 210) g = 210; int b = 0; int a = 255;
            mPaleta[idx++] = RGB(r, g, b, a);
        }
        // amarillos suaves
        for (int j = 0; j < 32; j++){
            int r = 255; int g = 180 + j * 2; if (g > 250) g = 250; int b = Math.min(80, j * 2); int a = 255;
            mPaleta[idx++] = RGB(r, g, b, a);
        }
        // tonos finales (claros, no saturados)
        while (idx < mPaleta.length){ mPaleta[idx++] = RGB(255, 245, 230, 255); }

        // índice 0 transparente para ver el fondo (alpha = 0)
        mPaleta[0] = RGB(0,0,0,0);

        // init buffers
        for (int i = 0; i < mTamanioBufferEfecto; i++){ mBufferEfecto[i] = 0; mCopiaBufferEfecto[i] = 0; }

        // init alt
        fireWidth = mAncho; fireHeight = mAltoBufferEfecto; pixelTemperature = new int[mTamanioBufferEfecto];
    }

    // smooth
    private void suavizado(int[] buffer, int ancho, int pasadas){
        int[] copiaBuffer = new int[buffer.length];
        int delta[] = {0, -1, 1, -ancho, ancho};
        while (pasadas > 0){
            System.arraycopy(buffer, 0, copiaBuffer, 0, buffer.length);
            for (int j = 0 ; j < buffer.length ; j++){
                int cnt = 0; int sum = 0;
                for (int k = 0 ; k < delta.length ; k++){
                    int idx = j + delta[k]; if ((idx >= 0) && (idx < buffer.length)){ sum += copiaBuffer[idx]; cnt++; }
                }
                buffer[j] = sum / cnt;
            }
            pasadas--;
        }
    }

    // generate frame
    private int[] generarTextura(){
        int p;

        // limpiar buffer de imagen para evitar restos de frames previos
        for (int ii = 0; ii < mBufferImagen.length; ii++) mBufferImagen[ii] = 0;

        // (sin limpieza forzada del buffer; comport. previo)

        if (mUseAltPropagation) {
            int i = mTamanioBufferEfecto - 2 * mAncho;
            while (i < mTamanioBufferEfecto){
                p = mRandom.nextInt(5) == 0 ? 0 : mPaleta.length - 1; int rep = mRandom.nextInt(5) + 1;
                while ((i < mTamanioBufferEfecto) && (rep > 0)){ mCopiaBufferEfecto[i] = p; rep--; i++; }
            }

            int maxPaletteIndex = Math.max(1, mPaleta.length - 1);
            for (i = 0; i < mTamanioBufferEfecto; i++) this.pixelTemperature[i] = (mCopiaBufferEfecto[i] * 1023) / maxPaletteIndex;

            int iniRow, iniBelowRow, pos, posBelow;
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

            // Mapear temperaturas a colores (resultado alternativo)
            int[] altImage = new int[mTamanio];
            for (i = 0; i < mTamanio; i++){
                int temp = pixelTemperature[i];
                if (temp < 0) temp = 0;
                if (temp > 1023) temp = 1023;
                int idx = (temp * maxPaletteIndex) / 1023;
                if (idx < 0) idx = 0; if (idx > maxPaletteIndex) idx = maxPaletteIndex;
                altImage[i] = mPaleta[idx];
            }

            // Aplicar máscara y volcar altImage a mBufferImagen, con desplazamiento vertical
            for (i = 0; i < mTamanio; i++){
                int row = i / mAncho;
                int col = i % mAncho;
                int shiftedRow = row - mRowOffset;
                if (shiftedRow >= 0){
                    int targetIdx = shiftedRow * mAncho + col;
                    if (mMask == null || (targetIdx < mMask.length && mMask[targetIdx])) mBufferImagen[targetIdx] = altImage[i];
                    else mBufferImagen[targetIdx] = 0;
                }
            }

            mOffsetBufferEnfriamiento += mAncho; mOffsetBufferEnfriamiento %= mTamanioBufferEfecto;
            return mBufferImagen;
        }

        int i = mTamanioBufferEfecto - 2 * mAncho;
        while (i < mTamanioBufferEfecto){ p = mRandom.nextInt(5) == 0 ? 0 : mPaleta.length - 1; int rep = mRandom.nextInt(5) + 1; while ((i < mTamanioBufferEfecto) && (rep > 0)){ mCopiaBufferEfecto[i] = p; rep--; i++; } }

        for (i = mAncho ; i < mTamanioBufferEfecto - mAncho ; i++){
            p = (mCopiaBufferEfecto[i] +
                 mCopiaBufferEfecto[i + 1] +
                 mCopiaBufferEfecto[i - 1] +
                 mCopiaBufferEfecto[i + mAncho] +
                 mCopiaBufferEfecto[i - mAncho]) / 5;

            p -= mBufferEnfriamiento[i + mOffsetBufferEnfriamiento];
            if (p < 0) p = 0;

            // Nuevo: enfriamiento vertical adicional proporcional a la fila (sube -> mas frio)
            if (mVerticalCoolingFactor > 0.0) {
                int row = i / mAncho;
                // normalizamos row respecto a fireHeight y aplicamos factor; escala moderada (max ~10 por defecto)
                int extraCool = (int) Math.round(mVerticalCoolingFactor * ((double)row / (double)fireHeight) * 10.0);
                p -= extraCool;
                if (p < 0) p = 0;
            }

            mBufferEfecto[i - mAncho] = p;

            if (i < mTamanio + mAncho){
               int outIdx = i - mAncho;
               if (outIdx >= 0 && outIdx < mTamanio){
                   int row = outIdx / mAncho;
                   int col = outIdx % mAncho;
                   int shiftedRow = row - mRowOffset;
                   if (shiftedRow >= 0){
                       int targetIdx = shiftedRow * mAncho + col;
                       if (mMask == null || (targetIdx < mMask.length && mMask[targetIdx])) {
                           // color normal desde la paleta
                           int colorIndex = p;
                           if (colorIndex < 0) colorIndex = 0; if (colorIndex >= mPaleta.length) colorIndex = mPaleta.length - 1;

                           if (mUseLinearInterpolation) {
                               // intentamos suavizar interpolando entre este índice y el siguiente en la paleta
                               int nextIdx = Math.min(mPaleta.length - 1, colorIndex + 1);
                               double t = mInterpolationAmount;
                               int c1 = mPaleta[colorIndex];
                               int c2 = mPaleta[nextIdx];
                               mBufferImagen[targetIdx] = lerpColor(c1, c2, t);
                           } else {
                               mBufferImagen[targetIdx] = mPaleta[colorIndex];
                           }
                       } else mBufferImagen[targetIdx] = 0;
                   }
               }
            }
        }

        mOffsetBufferEnfriamiento += mAncho; mOffsetBufferEnfriamiento %= mTamanioBufferEfecto;
        System.arraycopy(mBufferEfecto, 0, mCopiaBufferEfecto, 0, mTamanioBufferEfecto);

        // Asegurar: si hay máscara definida, limpiar cualquier píxel fuera de ella
        if (this.mMask != null) {
            int total = mTamanio;
            for (int k = 0; k < total; k++) {
                if (k < this.mMask.length) {
                    if (!this.mMask[k]) {
                        mBufferImagen[k] = 0; // transparente
                    }
                } else {
                    // si el tamaño no coincide, por seguridad limpiamos
                    mBufferImagen[k] = 0;
                }
            }
        }

        return mBufferImagen;
    }

    public int[] getImageBuffer(){ return generarTextura(); }
}
