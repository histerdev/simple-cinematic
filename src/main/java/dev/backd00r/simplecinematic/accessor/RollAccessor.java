package dev.backd00r.simplecinematic.mixin;

import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;


public interface RollAccessor {
    void setRoll(float roll);
    float getRoll();
}