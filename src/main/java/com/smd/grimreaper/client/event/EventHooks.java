package com.smd.grimreaper.client.event;

import com.smd.grimreaper.entity.EntityGrimReaper;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.EnumDifficulty;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class EventHooks {

    @SubscribeEvent
    public void onPlayerInteractEntity(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget() instanceof EntityGrimReaper) {
            EntityPlayer player = event.getEntityPlayer();
            ItemStack stack = player.getHeldItem(event.getHand());

            if (stack != null && !stack.isEmpty()) {
                event.setCanceled(true);
                if (!event.getWorld().isRemote) {
                    player.sendMessage(new TextComponentTranslation("message.death_force_block"));
                }
            }
        }
    }

    @SubscribeEvent
    public void onLivingHeal(LivingHealEvent event) {
        if (!(event.getEntityLiving() instanceof EntityGrimReaper)) return;

        EntityGrimReaper boss = (EntityGrimReaper) event.getEntityLiving();
        float healthPercent = boss.getHealth() / boss.getMaxHealth();

        if (healthPercent < 0.25f) {
            event.setAmount(event.getAmount() * 2.5f);
        } else {
            float missingPercent = 1.0f - healthPercent;
            event.setAmount(event.getAmount() * (1.0f + missingPercent));
        }
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.world.isRemote || event.phase != TickEvent.Phase.START) return;

        for (EntityGrimReaper reaper : event.world.getEntities(EntityGrimReaper.class, e -> true)) {
            if (!reaper.isDead || reaper.canBeRemoved() || reaper.getHealth() <= 0.0F) continue;

            if (event.world.getDifficulty() == EnumDifficulty.PEACEFUL
                    && reaper.isCreatureType(EnumCreatureType.MONSTER, false)) {
                continue;
            }

            reaper.isDead = false;
            reaper.deathTime = 0;
            if (!event.world.loadedEntityList.contains(reaper)) {
                event.world.loadedEntityList.add(reaper);
            }
        }
    }
}
