package com.smd.grimreaper.util;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class Util {

    public static int getSpawnSafeTopLevel(World world, int x, int y, int z) {
        BlockPos pos;
        IBlockState state;

        while (y > 0) {
            pos = new BlockPos(x, y, z);
            state = world.getBlockState(pos);
            if (!state.getBlock().isAir(state, world, pos)) {
                break;
            }
            y--;
        }
        return y + 1;
    }
}
