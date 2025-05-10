package dev.backd00r.simplecinematic.accessor;

public interface CameraMixinAccessor {
    /**
     * Establece el roll de la cámara.
     * @param roll El valor del roll en grados.
     */
    void setRoll(float roll);

    /**
     * Obtiene el roll actual de la cámara.
     * @return El valor actual del roll en grados.
     */
    float getRoll();
}
