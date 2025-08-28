package com.smd.grimreaper.client.event;

import com.smd.grimreaper.entity.EntityGrimReaper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

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
    public void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntityLiving() instanceof EntityGrimReaper) {
            EntityGrimReaper boss = (EntityGrimReaper) event.getEntityLiving();

            float maxAllowedDamage = boss.getMaxHealth() * 0.25F;
            float finalDamage = event.getAmount();

            if (finalDamage > maxAllowedDamage) {
                event.setAmount(maxAllowedDamage);
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
}
