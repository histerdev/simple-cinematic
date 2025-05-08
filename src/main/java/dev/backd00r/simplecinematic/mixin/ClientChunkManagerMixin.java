package dev.backd00r.simplecinematic.mixin;

import dev.backd00r.simplecinematic.client.camera.CinematicCameraState;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ClientChunkManager.class)
public class ClientChunkManagerMixin {

    @ModifyVariable(method = "setChunkMapCenter", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private int modifyChunkCenterX(int x) {
        if (CinematicCameraState.isCinematicActive() && CinematicCameraState.getCameraBlockPos() != null) {
            return CinematicCameraState.getCameraBlockPos().getX() >> 4;
        }
        return x;
    }

    @ModifyVariable(method = "setChunkMapCenter", at = @At("HEAD"), ordinal = 1, argsOnly = true)
    private int modifyChunkCenterZ(int z) {
        if (CinematicCameraState.isCinematicActive() && CinematicCameraState.getCameraBlockPos() != null) {
            return CinematicCameraState.getCameraBlockPos().getZ() >> 4;
        }
        return z;
    }
}