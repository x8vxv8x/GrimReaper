package com.smd.grimreaper.potion;

import net.minecraft.potion.Potion;
import net.minecraft.entity.EntityLivingBase;

public class DeathDenialPotion extends Potion {
    public DeathDenialPotion() {
        super(false, 0x550000);
        setPotionName("effect.death_denial");
    }

    @Override
    public boolean isBeneficial() {
        return false;
    }

    @Override
    public boolean isReady(int duration, int amplifier) {
        return false;
    }

    @Override
    public void performEffect(EntityLivingBase entity, int amplifier) {
    }
}
