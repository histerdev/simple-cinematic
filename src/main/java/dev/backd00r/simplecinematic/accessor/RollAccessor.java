package dev.backd00r.simplecinematic.accessor;

import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;


public interface RollAccessor {
    void setRoll(float roll);
    float getRoll();
}