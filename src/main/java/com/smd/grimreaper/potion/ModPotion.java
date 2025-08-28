package com.smd.grimreaper.potion;

import net.minecraft.potion.Potion;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber
public class ModPotion {

    public static final Potion DEATH_DENIAL = new DeathDenialPotion();

    @SubscribeEvent
    public static void registerPotions(RegistryEvent.Register<Potion> event) {
        event.getRegistry().register(DEATH_DENIAL.setRegistryName("death_denial"));
    }
}
