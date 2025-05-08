package dev.backd00r.simplecinematic.screen;

import dev.backd00r.simplecinematic.Simplecinematic;
import dev.backd00r.simplecinematic.networking.ModPackets;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.function.Consumer;
import java.util.function.Predicate;

public class CameraPointScreen extends HandledScreen<CameraPointScreenHandler> {
    // Usar la textura más alta
    private static final Identifier TEXTURE = new Identifier(Simplecinematic.MOD_ID, "textures/gui/img_2.png");
    private TextFieldWidget channelField;
    private TextFieldWidget positionField;
    private TextFieldWidget yawField;
    private TextFieldWidget pitchField;
    private TextFieldWidget durationField;
    private TextFieldWidget stayDurationField;
    private ButtonWidget rotateToNextToggle;

    // Botones +/- (sin cambios en declaración)
    private ButtonWidget increaseStayDurationButton, decreaseStayDurationButton;
    private ButtonWidget increaseChannelButton, decreaseChannelButton;
    private ButtonWidget increasePositionButton, decreasePositionButton;
    private ButtonWidget increaseYawButton, decreaseYawButton;
    private ButtonWidget increasePitchButton, decreasePitchButton;
    private ButtonWidget increaseDurationButton, decreaseDurationButton;

    // Constantes de Layout - Ajustadas para nuevo diseño
    private static final int BUTTON_WIDTH = 20;
    private static final int BUTTON_HEIGHT = 20;
    private static final int FIELD_WIDTH = 50; // Mantenido
    private static final int FIELD_HEIGHT = 18; // Altura TextField
    private static final int LABEL_COLOR = 0xFFFFFF; // Blanco
    private static final int LABEL_Y_OFFSET = -10; // Offset Y de la etiqueta RELATIVO AL CAMPO (encima)
    private static final int ROW_SPACING = 28; // Espacio vertical TOTAL entre filas de controles (incluye etiqueta+campo+espacio)
    private static final int CONTROL_SPACING_X = 3; // Espacio horizontal botón <-> campo
    private static final int TOGGLE_BUTTON_WIDTH = 60; // Ancho botón Sí/No
    private static final int TOP_MARGIN = 25; // Margen superior antes del primer control


    private final BlockPos blockPos;

    public CameraPointScreen(CameraPointScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 176;
        this.backgroundHeight = 260; // <-- USAR LA ALTURA CORRECTA DE LA TEXTURA

        BlockPos posFromHandler = handler.getBlockPos();
        if (posFromHandler == null) {
            Simplecinematic.LOGGER.error("CRITICAL: CameraPointScreen received null blockPos from handler! Using ORIGIN.");
            this.blockPos = BlockPos.ORIGIN;
        } else {
            this.blockPos = posFromHandler;
        }
        // No es necesario titleY aquí si no dibujamos el título manualmente
        // this.titleY = 6; // Ajustar si es necesario
    }


    @Override
    protected void init() {
        super.init();
        // Calcular coordenadas base
        int x = (this.width - this.backgroundWidth) / 2;
        int y = (this.height - this.backgroundHeight) / 2;

        // Calcular X para centrar el campo de texto
        int fieldX = x + this.backgroundWidth / 2 - FIELD_WIDTH / 2;
        // Calcular X para los botones +/- relativos al campo
        int decreaseButtonX = fieldX - BUTTON_WIDTH - CONTROL_SPACING_X;
        int increaseButtonX = fieldX + FIELD_WIDTH + CONTROL_SPACING_X;

        // Y inicial para la PRIMERA fila de controles
        int currentY = y + TOP_MARGIN;

        // --- Fila 1: Canal ---
        this.channelField = setupTextField(fieldX, currentY, String.valueOf(handler.getChannel()), this::validatePositiveInteger, this::onChannelChanged);
        this.decreaseChannelButton = setupDecreaseButton(decreaseButtonX, currentY, () -> updateIntValue(channelField, -1, 1));
        this.increaseChannelButton = setupIncreaseButton(increaseButtonX, currentY, () -> updateIntValue(channelField, 1, 1));
        currentY += ROW_SPACING; // Incrementar Y para la siguiente fila

        // --- Fila 2: Posición ---
        this.positionField = setupTextField(fieldX, currentY, String.valueOf(handler.getPosition()), this::validatePositiveInteger, this::onPositionChanged);
        this.decreasePositionButton = setupDecreaseButton(decreaseButtonX, currentY, () -> updateIntValue(positionField, -1, 1));
        this.increasePositionButton = setupIncreaseButton(increaseButtonX, currentY, () -> updateIntValue(positionField, 1, 1));
        currentY += ROW_SPACING;

        // --- Fila 3: Yaw ---
        this.yawField = setupTextField(fieldX, currentY, String.format("%.1f", handler.getYaw()), this::validateFloat, this::onYawChanged);
        this.decreaseYawButton = setupDecreaseButton(decreaseButtonX, currentY, () -> updateFloatValue(yawField, -15.0f, -1800.0f, 1800.0f));
        this.increaseYawButton = setupIncreaseButton(increaseButtonX, currentY, () -> updateFloatValue(yawField, 15.0f, -1800.0f, 1800.0f));
        currentY += ROW_SPACING;

        // --- Fila 4: Pitch ---
        this.pitchField = setupTextField(fieldX, currentY, String.format("%.1f", handler.getPitch()), this::validatePitch, this::onPitchChanged);
        this.decreasePitchButton = setupDecreaseButton(decreaseButtonX, currentY, () -> updateFloatValue(pitchField, -5.0f, -90.0f, 90.0f));
        this.increasePitchButton = setupIncreaseButton(increaseButtonX, currentY, () -> updateFloatValue(pitchField, 5.0f, -90.0f, 90.0f));
        currentY += ROW_SPACING;

        // --- Fila 5: Duración ---
        this.durationField = setupTextField(fieldX, currentY, String.format("%.1f", handler.getDuration()), this::validatePositiveFloatMin01, this::onDurationChanged);
        this.decreaseDurationButton = setupDecreaseButton(decreaseButtonX, currentY, () -> updateDoubleValue(durationField, -0.5, 0.1));
        this.increaseDurationButton = setupIncreaseButton(increaseButtonX, currentY, () -> updateDoubleValue(durationField, 0.5, 0.1));
        currentY += ROW_SPACING;

        // --- Fila 6: Stay Duration ---
        this.stayDurationField = setupTextField(fieldX, currentY, String.format("%.1f", handler.getStayDuration()), this::validatePositiveFloatMin0, this::onStayDurationChanged);
        this.decreaseStayDurationButton = setupDecreaseButton(decreaseButtonX, currentY, () -> updateDoubleValue(stayDurationField, -0.5, 0.0));
        this.increaseStayDurationButton = setupIncreaseButton(increaseButtonX, currentY, () -> updateDoubleValue(stayDurationField, 0.5, 0.0));
        currentY += ROW_SPACING;

        // --- Fila 7: Rotate To Next Toggle ---
        int toggleX = x + this.backgroundWidth / 2 - TOGGLE_BUTTON_WIDTH / 2; // Centrar el botón toggle
        this.rotateToNextToggle = ButtonWidget.builder(getRotateButtonText(), (btn) -> toggleRotateToNext())
                .dimensions(toggleX, currentY, TOGGLE_BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        this.addDrawableChild(this.rotateToNextToggle);
        this.addSelectableChild(this.rotateToNextToggle);

        // No es necesario añadir explícitamente los selectable child si se usa addDrawableChild
    }

    // --- Métodos auxiliares para crear widgets (sin cambios) ---
    private TextFieldWidget setupTextField(int x, int y, String initialValue, Predicate<String> predicate, Consumer<String> listener) {
        TextFieldWidget field = new TextFieldWidget(this.textRenderer, x, y, FIELD_WIDTH, FIELD_HEIGHT, Text.of("")); // Usar FIELD_HEIGHT
        field.setText(initialValue);
        field.setEditable(true);
        field.setTextPredicate(predicate);
        field.setChangedListener(listener);
        this.addDrawableChild(field);
        // this.addSelectableChild(field); // addDrawableChild generalmente lo hace seleccionable
        return field;
    }

    private ButtonWidget setupDecreaseButton(int x, int y, Runnable action) {
        ButtonWidget button = ButtonWidget.builder(Text.of("-"), (btn) -> action.run())
                .dimensions(x, y, BUTTON_WIDTH, BUTTON_HEIGHT) // Usar BUTTON_HEIGHT
                .build();
        this.addDrawableChild(button);
        // this.addSelectableChild(button);
        return button;
    }

    private ButtonWidget setupIncreaseButton(int x, int y, Runnable action) {
        ButtonWidget button = ButtonWidget.builder(Text.of("+"), (btn) -> action.run())
                .dimensions(x, y, BUTTON_WIDTH, BUTTON_HEIGHT) // Usar BUTTON_HEIGHT
                .build();
        this.addDrawableChild(button);
        // this.addSelectableChild(button);
        return button;
    }

    // --- Métodos auxiliares para actualizar valores con botones (sin cambios) ---
    private void updateIntValue(TextFieldWidget field, int change, int minValue) {
        int value = parseInt(field.getText(), minValue);
        value = Math.max(minValue, value + change);
        field.setText(String.valueOf(value));
    }

    private void updateFloatValue(TextFieldWidget field, float change, float minValue, float maxValue) {
        float value = parseFloat(field.getText(), 0.0f);
        value = MathHelper.clamp(value + change, minValue, maxValue);
        field.setText(String.format("%.1f", value));
    }

    private void updateDoubleValue(TextFieldWidget field, double change, double minValue) {
        double value = parseDouble(field.getText(), minValue);
        value = Math.max(minValue, value + change);
        field.setText(String.format("%.1f", value));
    }

    // --- Métodos para el Toggle Button (sin cambios) ---
    private Text getRotateButtonText() {
        return Text.translatable(handler.shouldRotateToNext() ?
                "gui.carrozatest.camera_point.rotate_enabled" :
                "gui.carrozatest.camera_point.rotate_disabled");
    }

    private void toggleRotateToNext() {
        boolean newValue = !handler.shouldRotateToNext();
        handler.setRotateToNext(newValue);
        rotateToNextToggle.setMessage(getRotateButtonText());
        sendValuesToServer();
    }

    // --- Listeners de cambio y envío (sin cambios en lógica) ---
    private void onChannelChanged(String text) {
        int value = parseInt(text, handler.getChannel()); // Usar valor actual como default en parseo
        if (value >= 1 && value != handler.getChannel()) {
            handler.setChannel(value);
            sendValuesToServer();
        } else if (!text.isEmpty() && !text.equals("-") && value < 1) {
            // Si el usuario escribe algo inválido, revertir al valor del handler
            channelField.setText(String.valueOf(handler.getChannel()));
        }
    }
    private void onPositionChanged(String text) {
        int value = parseInt(text, handler.getPosition());
        if (value >= 1 && value != handler.getPosition()) {
            handler.setPosition(value);
            sendValuesToServer();
        } else if (!text.isEmpty() && !text.equals("-") && value < 1) {
            positionField.setText(String.valueOf(handler.getPosition()));
        }
    }
    private void onYawChanged(String text) {
        // Permitir edición más libre, enviar el valor parseado
        try {
            float value = Float.parseFloat(text.replace(',', '.'));
            if (Math.abs(value - handler.getYaw()) > 0.01f) {
                handler.setYaw(value);
                sendValuesToServer();
            }
        } catch (NumberFormatException | NullPointerException e) {
            // Si no es un número válido (y no está vacío o parcial), no hacer nada o revertir
            if (!text.isEmpty() && !text.equals("-") && !text.endsWith(".") && !text.equals("-.")) {
                yawField.setText(String.format("%.1f", handler.getYaw()));
            }
        }
    }
    private void onPitchChanged(String text) {
        try {
            float value = Float.parseFloat(text.replace(',', '.'));
            value = MathHelper.clamp(value, -90.0f, 90.0f); // Clamp siempre
            if (Math.abs(value - handler.getPitch()) > 0.01f) {
                // Actualizar campo si se clampea antes de enviar
                if (!text.equals(String.format("%.1f", value)) && !text.equals(String.format("%.0f", value))) { // Evitar bucle si ya está formateado
                    pitchField.setText(String.format("%.1f", value));
                }
                handler.setPitch(value);
                sendValuesToServer();
            }
        } catch (NumberFormatException | NullPointerException e) {
            if (!text.isEmpty() && !text.equals("-") && !text.endsWith(".") && !text.equals("-.")) {
                pitchField.setText(String.format("%.1f", handler.getPitch()));
            }
        }
    }
    private void onDurationChanged(String text) {
        try {
            double value = Double.parseDouble(text.replace(',', '.'));
            value = Math.max(0.1, value); // Asegurar mínimo
            if (Math.abs(value - handler.getDuration()) > 0.01) {
                if (!text.equals(String.format("%.1f", value)) && !text.equals(String.format("%.0f", value))) {
                    durationField.setText(String.format("%.1f", value));
                }
                handler.setDuration(value);
                sendValuesToServer();
            }
        } catch (NumberFormatException | NullPointerException e) {
            if (!text.isEmpty() && !text.endsWith(".")) {
                durationField.setText(String.format("%.1f", handler.getDuration()));
            }
        }
    }
    private void onStayDurationChanged(String text) {
        try {
            double value = Double.parseDouble(text.replace(',', '.'));
            value = Math.max(0.0, value); // Asegurar mínimo
            if (Math.abs(value - handler.getStayDuration()) > 0.01) {
                if (!text.equals(String.format("%.1f", value)) && !text.equals(String.format("%.0f", value))) {
                    stayDurationField.setText(String.format("%.1f", value));
                }
                handler.setStayDuration(value);
                sendValuesToServer();
            }
        } catch (NumberFormatException | NullPointerException e) {
            if (!text.isEmpty() && !text.endsWith(".")) {
                stayDurationField.setText(String.format("%.1f", handler.getStayDuration()));
            }
        }
    }

    // Envía TODOS los valores actuales del handler al servidor (sin cambios)
    private void sendValuesToServer() {
        int channel = handler.getChannel();
        int position = handler.getPosition();
        float yaw = handler.getYaw();
        float pitch = handler.getPitch();
        double duration = handler.getDuration();
        double stayDuration = handler.getStayDuration();
        boolean rotateToNext = handler.shouldRotateToNext();

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(this.blockPos);
        buf.writeInt(channel);
        buf.writeInt(position);
        buf.writeFloat(yaw);
        buf.writeFloat(pitch);
        buf.writeDouble(duration);
        buf.writeDouble(stayDuration);
        buf.writeBoolean(rotateToNext);

        ClientPlayNetworking.send(ModPackets.UPDATE_CAMERA_POINT, buf);
    }

    // --- Renderizado ---
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context); // Fondo oscuro estándar
        super.render(context, mouseX, mouseY, delta); // Dibuja textura fondo, widgets, etc.
        this.drawMouseoverTooltip(context, mouseX, mouseY); // Tooltips de widgets

        // --- Dibuja las etiquetas CENTRADAS encima de los campos ---
        int x = (this.width - this.backgroundWidth) / 2;
        int labelX = x + this.backgroundWidth / 2; // X para centrar etiquetas

        // Dibujar cada etiqueta usando la posición Y del widget correspondiente
        // Es más robusto que recalcular currentY aquí
        drawCenteredLabel(context, Text.translatable("gui.carrozatest.camera_point.channel"), labelX, channelField.getY() + LABEL_Y_OFFSET);
        drawCenteredLabel(context, Text.translatable("gui.carrozatest.camera_point.position"), labelX, positionField.getY() + LABEL_Y_OFFSET);
        drawCenteredLabel(context, Text.translatable("gui.carrozatest.camera_point.yaw"), labelX, yawField.getY() + LABEL_Y_OFFSET);
        drawCenteredLabel(context, Text.translatable("gui.carrozatest.camera_point.pitch"), labelX, pitchField.getY() + LABEL_Y_OFFSET);
        drawCenteredLabel(context, Text.translatable("gui.carrozatest.camera_point.duration"), labelX, durationField.getY() + LABEL_Y_OFFSET);
        drawCenteredLabel(context, Text.translatable("gui.carrozatest.camera_point.stay_duration"), labelX, stayDurationField.getY() + LABEL_Y_OFFSET);
        drawCenteredLabel(context, Text.translatable("gui.carrozatest.camera_point.rotate_to_next"), labelX, rotateToNextToggle.getY() + LABEL_Y_OFFSET);

        // El título de la pantalla (InventoryScreen) se dibuja por defecto por super.render,
        // si quieres un título diferente o en otra posición, deshabilita eso y dibújalo aquí.
        // Ejemplo: context.drawTextWithShadow(textRenderer, title, x + 8, y + 6, 4210752);
    }

    // Método auxiliar para dibujar etiquetas centradas
    private void drawCenteredLabel(DrawContext context, Text text, int centerX, int y) {
        context.drawTextWithShadow(textRenderer, text, centerX - textRenderer.getWidth(text) / 2, y, LABEL_COLOR);
    }


    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;
        // Dibuja la textura de fondo personalizada usando las dimensiones correctas
        context.drawTexture(TEXTURE, x, y, 0, 0, this.backgroundWidth, this.backgroundHeight, this.backgroundWidth, this.backgroundHeight);
    }

    // --- Validación y Parseo (sin cambios) ---
    private boolean validatePositiveInteger(String text) {
        if (text.isEmpty() || text.equals("-")) return true;
        try {
            int value = Integer.parseInt(text);
            return value >= 1;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    private boolean validateFloat(String text) {
        if (text.isEmpty() || text.equals("-") || text.endsWith(".") || text.equals("-.")) return true;
        try {
            Float.parseFloat(text.replace(',', '.')); // Usar replace aquí también
            return true;
        } catch (NumberFormatException | NullPointerException e) { // Capturar NullPointer por si acaso
            return false;
        }
    }
    private boolean validatePitch(String text) {
        return validateFloat(text); // Clamp se hace en el listener
    }
    private boolean validatePositiveFloatMin01(String text) {
        if (text.isEmpty() || text.endsWith(".")) return true; // Permitir "." al final
        if (text.equals("-")) return false; // No permitir empezar con "-"
        try {
            double value = Double.parseDouble(text.replace(',', '.')); // Usar double para parsear
            return value >= 0.0; // Mínimo 0.0, el ajuste a 0.1 se hace en listener
        } catch (NumberFormatException | NullPointerException e) {
            return false;
        }
    }
    private boolean validatePositiveFloatMin0(String text) {
        if (text.isEmpty() || text.endsWith(".")) return true;
        if (text.equals("-")) return false;
        try {
            double value = Double.parseDouble(text.replace(',', '.'));
            return value >= 0.0;
        } catch (NumberFormatException | NullPointerException e) {
            return false;
        }
    }
    private int parseInt(String text, int defaultValue) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    private float parseFloat(String text, float defaultValue) {
        try {
            return Float.parseFloat(text.replace(',', '.'));
        } catch (NumberFormatException | NullPointerException e) {
            return defaultValue;
        }
    }
    private double parseDouble(String text, double defaultValue) {
        try {
            return Double.parseDouble(text.replace(',', '.'));
        } catch (NumberFormatException | NullPointerException e) {
            return defaultValue;
        }
    }

    // --- Otros métodos ---
    @Override
    public void close() {
        // Podrías llamar a sendValuesToServer aquí por seguridad, pero es opcional
        super.close();
    }

    @Override
    public boolean shouldPause() {
        return false; // Mantener el juego corriendo
    }

    // Opcional: Sobrescribir para quitar el título del inventario si no lo quieres
    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        // No llamar a super.drawForeground(context, mouseX, mouseY);
        // Si quieres un título personalizado, dibújalo aquí:
        // context.drawText(this.textRenderer, this.title, this.titleX, this.titleY, 4210752, false);
    }
}