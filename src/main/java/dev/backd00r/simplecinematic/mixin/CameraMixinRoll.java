package dev.backd00r.simplecinematic.mixin;

import dev.backd00r.simplecinematic.accessor.RollAccessor;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Camera.class)
public class CameraMixinRoll implements RollAccessor {
    @Unique
    private float simplecinematic_roll = 0.0f;

    @Override
    public void setRoll(float roll) {
        this.simplecinematic_roll = roll;
    }

    @Override
    public float getRoll() {
        return this.simplecinematic_roll;
    }
}