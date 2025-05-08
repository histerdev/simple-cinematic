package dev.backd00r.simplecinematic.screen;

import dev.backd00r.simplecinematic.Simplecinematic;
import dev.backd00r.simplecinematic.block.entity.CameraPointBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

public class CameraPointScreenHandler extends ScreenHandler {

    @Nullable // BE solo existe en servidor en este contexto
    private final CameraPointBlockEntity blockEntity;
    private final ScreenHandlerContext context;

    // Variables locales para sincronizar estado (cliente <-> servidor vía paquetes)
    private int channel;
    private int position;
    private float yaw;
    private float pitch;
    private double duration;
    private double stayDuration;
    private boolean rotateToNext; // <--- NUEVA VARIABLE
    private BlockPos blockPos;    // Necesario en cliente para enviar paquete UPDATE

    // Constructor del Servidor
    public CameraPointScreenHandler(int syncId, PlayerInventory playerInventory, CameraPointBlockEntity blockEntity) {
        super(ModScreenHandlers.CAMERA_POINT_SCREEN_HANDLER, syncId);
        this.blockEntity = blockEntity; // Guardar referencia (solo servidor)
        this.context = ScreenHandlerContext.create(blockEntity.getWorld(), blockEntity.getPos());

        // Inicializar valores desde el BlockEntity real
        this.channel = blockEntity.getChannel();
        this.position = blockEntity.getPosition();
        this.yaw = blockEntity.getYaw();
        this.pitch = blockEntity.getPitch();
        this.duration = blockEntity.getDuration();
        this.stayDuration = blockEntity.getStayDuration();
        this.rotateToNext = blockEntity.shouldRotateToNext(); // <--- LEER DEL BE
        this.blockPos = blockEntity.getPos(); // Guardar posición
    }

    // Constructor del Cliente
    public CameraPointScreenHandler(int syncId, PlayerInventory playerInventory, PacketByteBuf buf) {
        super(ModScreenHandlers.CAMERA_POINT_SCREEN_HANDLER, syncId);
        this.blockEntity = null; // No hay BE en cliente
        this.context = ScreenHandlerContext.EMPTY;

        // Leer los datos iniciales enviados por writeScreenOpeningData EN EL ORDEN CORRECTO
        this.blockPos = buf.readBlockPos();
        this.channel = buf.readInt();
        this.position = buf.readInt();
        this.yaw = buf.readFloat();
        this.pitch = buf.readFloat();
        this.duration = buf.readDouble();
        this.stayDuration = buf.readDouble();
        this.rotateToNext = buf.readBoolean(); // <--- LEER DEL BUFFER
    }

    // --- Getters para la Pantalla (Screen) ---
    public BlockPos getBlockPos() {
        return blockPos; // La pantalla necesita esto para enviar actualizaciones
    }
    public int getChannel() { return channel; }
    public int getPosition() { return position; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public double getDuration() { return duration; }
    public double getStayDuration() { return stayDuration; }
    public boolean shouldRotateToNext() { return rotateToNext; } // <--- NUEVO Getter

    // --- Setters (Llamados desde la Pantalla Cliente) ---
    // Actualizan el estado LOCAL del handler. La pantalla envía el paquete.
    public void setChannel(int channel) {
        this.channel = Math.max(1, channel);
    }
    public void setPosition(int position) {
        this.position = Math.max(1, position);
    }
    public void setYaw(float yaw) {
        this.yaw = yaw; // La validación/parseo la hace la Screen
    }
    public void setPitch(float pitch) {
        this.pitch = Math.max(-90.0f, Math.min(90.0f, pitch)); // Validar también aquí
    }
    public void setDuration(double duration) {
        this.duration = Math.max(0.1, duration);
    }
    public void setStayDuration(double stayDuration) {
        this.stayDuration = Math.max(0.0, stayDuration);
    }
    public void setRotateToNext(boolean rotateToNext) { // <--- NUEVO Setter
        this.rotateToNext = rotateToNext;
    }

    // --- Lógica del Screen Handler ---
    @Override
    public boolean canUse(PlayerEntity player) {
        // Comprueba si el bloque todavía existe y está cerca
        return canUse(this.context, player, Simplecinematic.CAMERA_POINT_BLOCK); // Usa el método estático de ScreenHandler
        /* // Implementación alternativa (equivalente a la línea anterior)
         return context.get((world, pos) -> {
             if (!world.getBlockState(pos).isOf(Carrozatest.CAMERA_POINT_BLOCK)) { // Asegúrate que Carrozatest.CAMERA_POINT_BLOCK es la referencia correcta
                 return false;
             }
             // Comprueba distancia cuadrada (más eficiente)
             return player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0; // 8 bloques
         }, true); // Default a true si el contexto está vacío (cliente) ?? -> Mejor usar canUse estático
        */
    }

    // No se usa para mover items
    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }

    // No se necesita un método para actualizar el BE aquí,
    // la actualización ocurre en el servidor al recibir el paquete UPDATE_CAMERA_POINT.
}