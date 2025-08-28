package com.smd.grimreaper.skill;

import com.smd.grimreaper.potion.ModPotion;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.EnderTeleportEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.ref.WeakReference;
import java.util.*;

public enum LifeLinkManager {
    INSTANCE;

    private static class LifeLinkBinding {
        private final WeakReference<EntityLivingBase> boss;
        private final WeakReference<EntityPlayer> player;
        private final long endTime;

        public LifeLinkBinding(EntityLivingBase boss, EntityPlayer player, long duration) {
            this.boss = new WeakReference<>(boss);
            this.player = new WeakReference<>(player);
            this.endTime = boss.world.getTotalWorldTime() + duration;
        }

        public boolean isExpired(long currentTime) {
            return currentTime >= endTime;
        }

        public EntityLivingBase getBoss() {
            return boss.get();
        }

        public EntityPlayer getPlayer() {
            return player.get();
        }
    }

    private final Map<UUID, LifeLinkBinding> playerBindings = new HashMap<>();
    private final Map<UUID, UUID> bossToPlayerMap = new HashMap<>();

    public static void init() {
        MinecraftForge.EVENT_BUS.register(INSTANCE);
    }

    public void addBinding(EntityLivingBase boss, EntityPlayer player, long durationTicks) {
        UUID bossId = boss.getUniqueID();
        UUID playerId = player.getUniqueID();

        removeBindingByBoss(bossId);
        removeBindingByPlayer(playerId);

        LifeLinkBinding binding = new LifeLinkBinding(boss, player, durationTicks);
        playerBindings.put(playerId, binding);
        bossToPlayerMap.put(bossId, playerId);
    }

    public boolean hasValidBinding(EntityPlayer player, long currentTime) {
        LifeLinkBinding binding = playerBindings.get(player.getUniqueID());
        return binding != null &&
                !binding.isExpired(currentTime) &&
                binding.getBoss() != null &&
                binding.getPlayer() != null;
    }

    public EntityLivingBase getBossForPlayer(EntityPlayer player, long currentTime) {
        LifeLinkBinding binding = playerBindings.get(player.getUniqueID());
        if (binding == null) return null;

        if (binding.isExpired(currentTime) || binding.getBoss() == null || binding.getPlayer() == null) {
            removeBindingByPlayer(player.getUniqueID());
            return null;
        }

        return binding.getBoss();
    }

    public EntityPlayer getPlayerForBoss(EntityLivingBase boss) {
        UUID playerId = bossToPlayerMap.get(boss.getUniqueID());
        if (playerId == null) return null;

        LifeLinkBinding binding = playerBindings.get(playerId);
        if (binding == null || binding.getPlayer() == null) {
            removeBindingByBoss(boss.getUniqueID());
            return null;
        }

        return binding.getPlayer();
    }

    public void removeBindingByBoss(UUID bossId) {
        UUID playerId = bossToPlayerMap.remove(bossId);
        if (playerId != null) {
            playerBindings.remove(playerId);
        }
    }

    public void removeBindingByPlayer(UUID playerId) {
        LifeLinkBinding binding = playerBindings.remove(playerId);
        if (binding != null && binding.getBoss() != null) {
            bossToPlayerMap.remove(binding.getBoss().getUniqueID());
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || playerBindings.isEmpty()) return;

        long currentTime = 0;
        Iterator<Map.Entry<UUID, LifeLinkBinding>> iterator = playerBindings.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, LifeLinkBinding> entry = iterator.next();
            LifeLinkBinding binding = entry.getValue();

            EntityLivingBase boss = binding.getBoss();
            EntityPlayer player = binding.getPlayer();

            if (boss != null && currentTime == 0) {
                currentTime = boss.world.getTotalWorldTime();
            }

            if (currentTime > 0 && (binding.isExpired(currentTime) || boss == null || player == null)) {
                if (player != null) {
                    player.sendMessage(new TextComponentString("生命链接已断开"));
                }
                iterator.remove();
                if (boss != null) {
                    bossToPlayerMap.remove(boss.getUniqueID());
                }
            }
        }
    }

    @SubscribeEvent
    public void onPlayerHeal(LivingHealEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayer)) return;

        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        World world = player.world;
        if (world.isRemote || playerBindings.isEmpty()) return;

        long currentTime = world.getTotalWorldTime();
        if (!hasValidBinding(player, currentTime)) return;

        EntityLivingBase boss = getBossForPlayer(player, currentTime);
        if (boss == null || boss.isDead || boss.getHealth() >= boss.getMaxHealth()) return;

        float healAmount = event.getAmount();
        float futureHealth = player.getHealth() + healAmount;
        float maxHealth = player.getMaxHealth();

        boss.heal(healAmount);

        if (futureHealth > maxHealth) {
            PotionEffect current = player.getActivePotionEffect(ModPotion.DEATH_DENIAL);
            int amplifier = current != null ? Math.min(current.getAmplifier() + 1, 19) : 0;

            PotionEffect effect = new PotionEffect(ModPotion.DEATH_DENIAL, 600, amplifier);
            effect.setCurativeItems(new ArrayList<>());
            player.addPotionEffect(effect);

            world.playSound(null, player.posX, player.posY, player.posZ, SoundEvents.ENTITY_WITHER_HURT, SoundCategory.HOSTILE, 1.0F, 0.8F);
        }

        PotionEffect denial = player.getActivePotionEffect(ModPotion.DEATH_DENIAL);
        if (denial != null) {
            float reduction = (denial.getAmplifier() + 1) * 0.05f;
            healAmount *= (1.0f - reduction);
            event.setAmount(healAmount);
        }
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getSource().getTrueSource() instanceof EntityPlayer)) return;

        EntityPlayer player = (EntityPlayer) event.getSource().getTrueSource();
        World world = player.world;
        if (playerBindings.isEmpty()) return;

        long currentTime = world.getTotalWorldTime();
        if (!hasValidBinding(player, currentTime)) return;

        EntityLivingBase boss = getBossForPlayer(player, currentTime);
        if (boss != null && !boss.isDead && event.getAmount() < boss.getHealth()) {
            boss.heal(event.getAmount() * 0.4f);
        }
    }

    @SubscribeEvent
    public void onPlayerTeleport(EnderTeleportEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayer)) return;

        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        World world = player.world;
        if (world.isRemote || playerBindings.isEmpty()) return;

        long currentTime = world.getTotalWorldTime();
        if (!hasValidBinding(player, currentTime)) return;

        EntityLivingBase boss = getBossForPlayer(player, currentTime);
        if (boss != null && !boss.isDead) {
            event.setCanceled(true);

            double offsetX = (world.rand.nextDouble() - 0.5) * 4.0;
            double offsetZ = (world.rand.nextDouble() - 0.5) * 4.0;
            double targetX = boss.posX + offsetX;
            double targetY = boss.posY + 1.5;
            double targetZ = boss.posZ + offsetZ;

            player.setPositionAndUpdate(targetX, targetY, targetZ);
            player.sendMessage(new TextComponentString("生命链接时你无法逃离死神的掌控！"));
        }
    }

    @SubscribeEvent
    public void onPlayerTravelDimension(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof EntityPlayerMP)) return;

        EntityPlayerMP player = (EntityPlayerMP) event.getEntity();
        World world = player.world;
        if (world.isRemote || playerBindings.isEmpty()) return;

        long currentTime = world.getTotalWorldTime();
        if (!hasValidBinding(player, currentTime)) return;

        EntityLivingBase boss = getBossForPlayer(player, currentTime);
        if (boss != null && !boss.isDead) {
            event.setCanceled(true);

            double offsetX = (world.rand.nextDouble() - 0.5) * 4.0;
            double offsetZ = (world.rand.nextDouble() - 0.5) * 4.0;
            double targetX = boss.posX + offsetX;
            double targetY = boss.posY + 1.5;
            double targetZ = boss.posZ + offsetZ;

            player.setPositionAndUpdate(targetX, targetY, targetZ);
            player.sendMessage(new TextComponentString("生命链接时你无法逃离死神的维度锁定！"));
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;

        EntityPlayer player = event.player;
        if (player.isCreative() || player.isSpectator()) return;

        World world = player.world;
        if (INSTANCE.playerBindings.isEmpty()) return;

        long currentTime = world.getTotalWorldTime();
        if (!INSTANCE.hasValidBinding(player, currentTime)) return;

        EntityLivingBase boss = INSTANCE.getBossForPlayer(player, currentTime);
        if (boss != null && !boss.isDead) {
            if (player.capabilities.allowFlying || player.capabilities.isFlying) {
                player.capabilities.allowFlying = false;
                player.capabilities.isFlying = false;
                player.sendPlayerAbilities();
            }
        }
    }
}
