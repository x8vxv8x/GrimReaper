package com.smd.grimreaper.entity;

import com.smd.grimreaper.enums.EnumReaperAttackState;
import com.smd.grimreaper.skill.LifeLinkManager;
import com.smd.grimreaper.sound.Sounds;
import com.smd.grimreaper.util.Util;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.*;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.BossInfo;
import net.minecraft.world.BossInfoServer;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

public class EntityGrimReaper extends EntityMob {
    private static final DataParameter<Integer> ATTACK_STATE = EntityDataManager.<Integer>createKey(EntityGrimReaper.class, DataSerializers.VARINT);
    private static final DataParameter<Integer> STATE_TRANSITION_COOLDOWN = EntityDataManager.<Integer>createKey(EntityGrimReaper.class, DataSerializers.VARINT);
    private static final DataParameter<Integer> EFFECT_CLEAR_COUNT = EntityDataManager.createKey(EntityGrimReaper.class, DataSerializers.VARINT);
    private EnumReaperAttackState cachedAttackState = EnumReaperAttackState.IDLE;
    private int clearEffectsTimer = 60;
    private static final int CLEAR_EFFECTS_INTERVAL = 60; // 3秒间隔
    private int currentBlockDuration = 0;
    private int soulSwapCooldown = 0;
    private int timeDebtCooldown = 0;
    private DamageSource lastDamageSource;
    private int sameDamageCount = 0;
    private float damageReduction = 0.0f;
    private long lastDamageTime = 0;
    private int noClipTicks = 0;
    private int armorSyncTimer = 0;

    private static final DataParameter<Integer> BLOCK_COUNTER = EntityDataManager.createKey(EntityGrimReaper.class, DataSerializers.VARINT);
    private static final DataParameter<Integer> INVINCIBLE_TICKS = EntityDataManager.createKey(EntityGrimReaper.class, DataSerializers.VARINT);

    private final BossInfoServer bossInfo = (BossInfoServer) (new BossInfoServer(this.getDisplayName(), BossInfo.Color.PURPLE, BossInfo.Overlay.PROGRESS)).setDarkenSky(true);
    private EntityAINearestAttackableTarget aiNearestAttackableTarget = new EntityAINearestAttackableTarget(this, EntityPlayer.class, true);
    private int healingCooldown;
    private int timesHealed;

    private int lifeLinkCooldown = 0;
    private static final int LIFE_LINK_DURATION = 200; // 10秒
    private static final int LIFE_LINK_COOLDOWN = 450; // 25秒冷却

    private static final String ARMOR_SYNC_NAME = "SyncedArmorModifier";
    private static final String TOUGHNESS_SYNC_NAME = "SyncedToughnessModifier";

    private float floatingTicks;

    public EntityGrimReaper(World world) {
        super(world);
        setSize(1.0F, 2.6F);
        this.experienceValue = 100;

        this.tasks.addTask(1, new EntityAISwimming(this));
        this.tasks.addTask(4, new EntityAIWander(this, 1.0D));
        this.tasks.addTask(6, new EntityAIWatchClosest(this, EntityPlayer.class, 8.0F));
        this.tasks.addTask(5, new EntityAILookIdle(this));
        this.tasks.addTask(2, new EntityAIAttackMelee(this, 1.1D, false));
        this.targetTasks.addTask(1, new EntityAIHurtByTarget(this, false, new Class[0]));
        this.targetTasks.addTask(2, aiNearestAttackableTarget);
    }

    @Override
    protected final void applyEntityAttributes() {
        super.applyEntityAttributes();
        this.getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(50.0D);
        this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.20F);
        this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(4.5F);
        this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(300.0F);
        this.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
        this.getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(30.0D);
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        this.dataManager.register(ATTACK_STATE, 0);
        this.dataManager.register(STATE_TRANSITION_COOLDOWN, 0);
        this.dataManager.register(BLOCK_COUNTER, 0);
        this.dataManager.register(INVINCIBLE_TICKS, 0);
        this.dataManager.register(EFFECT_CLEAR_COUNT, 0);
    }

    public EnumReaperAttackState getAttackState() {
        return cachedAttackState;
    }

    public void setAttackState(EnumReaperAttackState state) {

        if (cachedAttackState != state) {
            cachedAttackState = state;
            this.dataManager.set(ATTACK_STATE, state.getId());

            if (state == EnumReaperAttackState.BLOCK) {
                currentBlockDuration = 60;
            }

            switch (state) {
                case PRE:
                    this.playSound(Sounds.reaper_scythe_out, 1.0F, 1.0F);
                    break;
                case POST:
                    this.playSound(Sounds.reaper_scythe_swing, 1.0F, 1.0F);
                    break;
            }
        }
    }

    @Override
    public void onStruckByLightning(EntityLightningBolt entity) {
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float damage) {

        if (source.getTrueSource() == null || !(source.getTrueSource() instanceof EntityPlayer)) {
            return false;
        }

        if (this.dataManager.get(INVINCIBLE_TICKS) > 0) {
            return false;
        }

        if (source.getTrueSource() != null && source.getTrueSource() instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) source.getTrueSource();
            if (player != null && !player.isDead && rand.nextFloat() < 0.20F) {
                Collection<PotionEffect> activeEffects = player.getActivePotionEffects();
                for (PotionEffect effect : activeEffects) {
                    if (effect.getPotion().isBeneficial()) {
                        player.removePotionEffect(effect.getPotion());
                        break;
                    }
                }
            }
        }

        if (source.getTrueSource() != null && source.getTrueSource() instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) source.getTrueSource();
            if (player != null && !player.isDead
                    && !(source.getImmediateSource() instanceof EntityArrow)
                    && rand.nextFloat() < 0.30F) {
                double distance = this.getDistance(player);

                if (distance > 5.0D) {
                    teleportTo(player.posX, player.posY + 1.5D, player.posZ);
                    player.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, 60, 1));
                    world.playSound(null, player.posX, player.posY, player.posZ, SoundEvents.ENTITY_ENDERMEN_STARE, SoundCategory.HOSTILE, 1.0F, 1.0F);
                }}}




        // Ignore wall damage and fire damage.
        if (source == DamageSource.IN_WALL || source == DamageSource.ON_FIRE || source.isExplosion() || source == DamageSource.IN_FIRE) {
            // Teleport out of any walls we may end up in.
            if (source == DamageSource.IN_WALL) {
                teleportTo(this.posX, this.posY + 3, this.posZ);
            }

            return false;
        }

        //格挡机制
        else if (!world.isRemote && this.getAttackState() == EnumReaperAttackState.BLOCK && source.getImmediateSource() instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) source.getImmediateSource();

            double deltaX = this.posX - player.posX;
            double deltaZ = this.posZ - player.posZ;

            this.playSound(Sounds.reaper_block, 1.0F, 1.0F);
            teleportTo(player.posX - (deltaX * 2), player.posY + 2, this.posZ - (deltaZ * 2));
            if (!world.isRemote) {
                EntityLightningBolt lightning = new EntityLightningBolt(
                        world,
                        player.posX,
                        player.posY,
                        player.posZ,
                        false
                );
                world.addWeatherEffect(lightning);
            }

            int newBlockCount = this.dataManager.get(BLOCK_COUNTER) + 1;
            this.dataManager.set(BLOCK_COUNTER, newBlockCount);

            if (newBlockCount % 3 == 0) {
                this.dataManager.set(INVINCIBLE_TICKS, 30);
            }

            currentBlockDuration = Math.min(currentBlockDuration + 5, 100);

            float healAmount = this.getMaxHealth() * 0.004f;
            this.setHealth(Math.min(this.getHealth() + healAmount, this.getMaxHealth()));

            setStateTransitionCooldown(0);
            return false;
        }

        else if (!world.isRemote && source.getImmediateSource() instanceof EntityPlayer && rand.nextFloat() > 0.30F) {
            EntityPlayer player = (EntityPlayer) source.getImmediateSource();

            double deltaX = this.posX - player.posX;
            double deltaZ = this.posZ - player.posZ;

            teleportTo(player.posX - (deltaX * 2), player.posY + 2, this.posZ - (deltaZ * 2));
        }

        if (source.getImmediateSource() instanceof EntityArrow) {
            EntityArrow arrow = (EntityArrow) source.getImmediateSource();

            if (arrow != null && arrow.shootingEntity instanceof EntityPlayer && getAttackState() != EnumReaperAttackState.REST) {
                EntityPlayer player = (EntityPlayer) arrow.shootingEntity;
                if (player != null && !player.isDead) {
                    double newX = player.posX + (rand.nextFloat() > 0.5F ? 2 : -2);
                    double newZ = player.posZ + (rand.nextFloat() > 0.5F ? 2 : -2);

                    teleportTo(newX, player.posY, newZ);
                }}

            arrow.setDead();
            return false;
        }

        else if (this.getAttackState() == EnumReaperAttackState.REST) {
            int mobCount = world.getEntitiesWithinAABB(EntityMob.class, this.getEntityBoundingBox().grow(10.0D)).stream()
                    .filter(mob -> mob != this && !mob.isDead)
                    .toArray().length;

            float reductionRatio = MathHelper.clamp(mobCount * 0.06F, 0.0F, 0.6F);
            damage *= (0.8F - reductionRatio);
        }

        if (source != null) {
            long currentTime = world.getTotalWorldTime();

            if (lastDamageSource != null &&
                    source.getDamageType().equals(lastDamageSource.getDamageType()) &&
                    currentTime - lastDamageTime < 100) {

                sameDamageCount++;

                if (sameDamageCount >= 2) {
                    sameDamageCount = 0;
                    damageReduction = Math.min(damageReduction + 0.1f, 0.8f);
                }
            } else {
                // 伤害类型变化或超时，重置减伤
                sameDamageCount = 0;
                damageReduction = 0.0f;
            }

            lastDamageSource = source;
            lastDamageTime = currentTime;

            damage *= (1.0f - damageReduction);
        }

        boolean result = super.attackEntityFrom(source, damage);

        if (!world.isRemote && this.getHealth() <= (this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).getBaseValue() / 2) && healingCooldown == 0) {
            setAttackState(EnumReaperAttackState.REST);
            healingCooldown = 1200;
            teleportTo(this.posX, this.posY + 8, this.posZ);
            setStateTransitionCooldown(400);
        }

        if (damage < this.getHealth()) {
            float anyDamageHeal = this.getMaxHealth() * 0.002f;
            this.setHealth(Math.min(this.getHealth() + anyDamageHeal, this.getMaxHealth()));
        }

        return result;
    }

    protected void attackEntity(Entity entity, float damage) {
        EntityLivingBase entityToAttack = this.getAttackTarget();
        if (entityToAttack == null || entityToAttack.isDead) return;

        double attackDistance = this.width * 1.5D + entityToAttack.width;
        double deltaY = Math.abs(this.posY - entityToAttack.posY);
        double distanceSq = this.getDistanceSq(entityToAttack);

        if (distanceSq <= (attackDistance * attackDistance) && deltaY <= 3.0D
                && getAttackState() == EnumReaperAttackState.PRE) {
            if (getAttackState() == EnumReaperAttackState.BLOCK) {
                int rX = this.getRNG().nextInt(10);
                int rZ = this.getRNG().nextInt(10);
                teleportTo(this.posX + 5 + rX, this.posY, this.posZ + rZ);
            } else {
                float rawDamage = this.world.getDifficulty().getId() * 5.75F;

                if (entity instanceof EntityPlayer) {
                    EntityPlayer player = (EntityPlayer) entity;

                    int foodLevel = player.getFoodStats().getFoodLevel();
                    player.getFoodStats().setFoodLevel(Math.max(foodLevel - 2, 0));
                    if (rand.nextFloat() < 0.85F) { // 30%概率
                        Collection<PotionEffect> activeEffects = player.getActivePotionEffects();
                        boolean effectRemoved = false;
                        for (PotionEffect effect : activeEffects) {
                            if (effect.getPotion().isBeneficial()) {
                                player.removePotionEffect(effect.getPotion());
                                effectRemoved = true;
                                break;
                            }
                        }

                        if (effectRemoved) {
                            int newCount = this.dataManager.get(EFFECT_CLEAR_COUNT) + 1;
                            this.dataManager.set(EFFECT_CLEAR_COUNT, newCount);
                        }
                    }

                    // 血量低于10%直接秒杀
                    if (!player.isDead && player.getHealth() <= player.getMaxHealth() * 0.1F) {
                        player.setHealth(0.0F);
                        player.sendMessage(new TextComponentString("死神收割了你的灵魂"));
                    } else {
                        // 分段伤害计算
                        float physicalDamage = rawDamage * 0.6F;  // 60%物理伤害
                        float fireDamage = rawDamage * 0.3F;     // 30%火焰伤害
                        float magicDamage = rawDamage * 0.1F;    // 10%魔法伤害

                        player.attackEntityFrom(DamageSource.causeMobDamage(this), physicalDamage);
                        player.attackEntityFrom(DamageSource.IN_FIRE, fireDamage); // 火焰伤害
                        player.attackEntityFrom(DamageSource.MAGIC, magicDamage);  // 魔法伤害
                        //攻击吸取经验
                        if (player.experienceLevel > 0 || player.experienceTotal > 0) {
                            int totalExp = player.experienceTotal;

                            int drainExp = Math.max(1, MathHelper.floor(totalExp * 0.05F));
                            drainExp = Math.min(drainExp, player.experienceTotal);
                            player.addExperience(-drainExp);
                            float healAmount = drainExp * 1.0F;
                            this.setHealth(Math.min(this.getHealth() + healAmount, this.getMaxHealth()));
                        }
                    }
                } else if (entity instanceof EntityLivingBase) {
                    entity.attackEntityFrom(DamageSource.causeMobDamage(this), rawDamage);
                }

                setAttackState(EnumReaperAttackState.POST);
                setStateTransitionCooldown(10);
            }
        }

        if (getStateTransitionCooldown() == 0 && entityToAttack != null) {
            double trackingDistance = 4.0D;
            double verticalRange = 3.0D;

            // 计算实体与目标的垂直距离
            double yDiff = Math.abs(this.posY - entityToAttack.posY);
            double horizontalDistance = this.getDistance(entityToAttack.posX, this.posY, entityToAttack.posZ);

            if (horizontalDistance <= trackingDistance && yDiff <= verticalRange) {

                if (entityToAttack instanceof EntityPlayer) {
                    EntityPlayer player = (EntityPlayer) entityToAttack;

                    if (player.isActiveItemStackBlocking()) {
                        double dX = this.posX - player.posX;
                        double dZ = this.posZ - player.posZ;

                        teleportTo(player.posX - (dX * 2), player.posY + 2, player.posZ - (dZ * 2));

                        if (!world.isRemote && rand.nextFloat() > 0.20F) {
                            int currentItem = player.inventory.currentItem;
                            int hotbarSize = InventoryPlayer.getHotbarSize();
                            if (hotbarSize <= 0) return;
                            int randomItem = rand.nextInt(hotbarSize);

                            ItemStack currentItemStack = player.inventory.mainInventory.get(currentItem);
                            ItemStack randomItemStack = player.inventory.mainInventory.get(randomItem);

                            player.inventory.mainInventory.set(currentItem, randomItemStack);
                            player.inventory.mainInventory.set(randomItem, currentItemStack);

                            player.addPotionEffect(new PotionEffect(MobEffects.BLINDNESS, this.world.getDifficulty().getId() * 40, 1));
                        }
                    } else // If the player is not blocking, ready the scythe, or randomly block their attack.
                    {
                        if (rand.nextFloat() > 0.6F && getAttackState() != EnumReaperAttackState.PRE) {
                            setStateTransitionCooldown(20);
                            setAttackState(EnumReaperAttackState.BLOCK);
                        } else {
                            setAttackState(EnumReaperAttackState.PRE);
                            setStateTransitionCooldown(20);
                        }
                    }
                }
            } else
            {
                setAttackState(EnumReaperAttackState.IDLE);
            }
        }
    }

    @Override
    public int getTalkInterval() {
        return 300;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return Sounds.reaper_idle;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return Sounds.reaper_death;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_WITHER_HURT;
    }

    @Override
    public void onUpdate() {

        //格挡无敌帧
        if (this.dataManager.get(INVINCIBLE_TICKS) > 0) {
            this.dataManager.set(INVINCIBLE_TICKS, this.dataManager.get(INVINCIBLE_TICKS) - 1);
        }

        if (!world.isRemote) {
            // 每3秒清除负面效果
            if (--clearEffectsTimer <= 0) {
                clearNegativePotionEffects();
                clearEffectsTimer = CLEAR_EFFECTS_INTERVAL;
            }
        }

        if (timeDebtCooldown > 0) {
            timeDebtCooldown--;
        }

        double prevX = this.prevPosX;
        double prevY = this.prevPosY;
        double prevZ = this.prevPosZ;

        super.onUpdate();
        extinguish();

        if (this.getAttackTarget() != null && !this.getAttackTarget().isDead) {
            if (noClipTicks <= 0) noClipTicks = 20;
        }

        if (noClipTicks > 0) {
            this.noClip = true;
            noClipTicks--;
        } else {
            this.noClip = false;
        }

        if (getAttackState() == EnumReaperAttackState.BLOCK) {
            currentBlockDuration--;
            if (currentBlockDuration <= 0) {
                setAttackState(EnumReaperAttackState.IDLE);
            }
        }
        //护甲韧性同步
        armorSyncTimer++;
        if (armorSyncTimer >= 20) { // 每 20 tick（约 1 秒）执行一次
            armorSyncTimer = 0;
            syncArmorAndToughnessFromTarget();
        }

        //生命链接
        if (!world.isRemote &&
                this.getAttackTarget() != null &&
                lifeLinkCooldown == 0) {

            EntityPlayer closest = null;
            double minDist = Double.MAX_VALUE;

            for (EntityPlayer player : world.getEntitiesWithinAABB(
                    EntityPlayer.class,
                    this.getEntityBoundingBox().grow(16.0D) // 16格范围
            )) {
                double dist = this.getDistanceSq(player);
                if (dist < minDist) {
                    minDist = dist;
                    closest = player;
                }
            }

            if (closest != null) {

                LifeLinkManager.INSTANCE.addBinding(this, closest, LIFE_LINK_DURATION);
                lifeLinkCooldown = LIFE_LINK_COOLDOWN;

                closest.sendMessage(new TextComponentString("你与死神建立了生命链接！"));
            }
        }

        // 生命链接冷却
        if (lifeLinkCooldown > 0) {
            lifeLinkCooldown--;
        }

        if (!world.isRemote && this.getHealth() > 0.0F) {
            if (world.getTotalWorldTime() % 20 == 0) {
                float healAmount = this.getMaxHealth() * 0.001f;
                this.heal(healAmount);
            }
        }


        bossInfo.setPercent(this.getHealth() / this.getMaxHealth());

        if (this.getAttackTarget() != null) {

            this.getMoveHelper().setMoveTo(
                    getAttackTarget().posX,
                    getAttackTarget().posY + 1.0,
                    getAttackTarget().posZ,
                    0.6F
            );
        }

        if (this.getAttackTarget() == null || this.getAttackTarget().isDead) {
            EntityPlayer closestPlayer = this.world.getClosestPlayerToEntity(this, 48.0D);
            if (closestPlayer != null) {
                this.setAttackTarget(closestPlayer);
            }
        }

        EntityLivingBase entityToAttack = this.getAttackTarget();
        if (entityToAttack != null && !entityToAttack.isDead
                && getAttackState() != EnumReaperAttackState.REST) {
            attackEntity(entityToAttack, 5.0F);
            this.getMoveHelper().setMoveTo(entityToAttack.posX, entityToAttack.posY, entityToAttack.posZ, 0.6F); // 提高移动速度

            Vec3d targetVec = new Vec3d(
                    entityToAttack.posX - this.posX,
                    (entityToAttack.posY + 1.0) - this.posY,
                    entityToAttack.posZ - this.posZ
            ).normalize();

            double distanceToTarget = this.getDistance(entityToAttack);
            double speedFactor = distanceToTarget < 3.0D ? 0.15D : 0.35D;

            this.motionX = targetVec.x * speedFactor;
            this.motionY = targetVec.y * speedFactor * 0.6;
            this.motionZ = targetVec.z * speedFactor;
        }

        if (world.isRemote && getAttackState() == EnumReaperAttackState.REST) {
            floatingTicks += 0.1F;
        }

        if (getAttackState() == EnumReaperAttackState.REST) {
            if (!world.isRemote && getStateTransitionCooldown() == 1) {
                setAttackState(EnumReaperAttackState.IDLE);
                timesHealed++;
            } else if (!world.isRemote && getStateTransitionCooldown() % 50 == 0) {
                float healAmount = this.getMaxHealth() * 0.03f;
                this.setHealth(Math.min(this.getHealth() + healAmount, this.getMaxHealth()));

                for (EntityPlayer player : world.getEntitiesWithinAABB(EntityPlayer.class, this.getEntityBoundingBox().grow(6.0D))) {
                    Vec3d direction = new Vec3d(player.posX - this.posX, 0, player.posZ - this.posZ).normalize();
                    double pushPower = 0.6D;

                    player.motionX += direction.x * pushPower;
                    player.motionZ += direction.z * pushPower;

                }

                int dX = rand.nextInt(8) + 4 * (rand.nextFloat() > 0.50F ? 1 : -1);
                int dZ = rand.nextInt(8) + 4 * (rand.nextFloat() > 0.50F ? 1 : -1);
                int y = Util.getSpawnSafeTopLevel(world, (int) posX + dX, 256, (int) posZ + dZ);

                EntityLightningBolt bolt = new EntityLightningBolt(world, dX, y, dZ, false);
                world.addWeatherEffect(bolt);

                // Also spawn a random skeleton or zombie.
                if (!world.isRemote) {
                    EntityMob mob = rand.nextFloat() > 0.50F ? new EntityZombie(world) : new EntitySkeleton(world);
                    mob.setPosition(posX + dX + 4, y, posZ + dZ + 4);

                    if (mob instanceof EntitySkeleton) {
                        mob.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
                    }

                    world.spawnEntity(mob);
                }
            }
        }

        if (this.getHealth() <= 0.0F) {
            motionX = 0;
            motionY = 0;
            motionZ = 0;
            return;
        }

        if (getAttackState() == EnumReaperAttackState.REST) {
            motionX = 0;
            motionY = 0;
            motionZ = 0;
        }

        fallDistance = 0.0F;

        if (motionY > 0) {
            motionY = motionY * 1.04F;
        } else {
            double yMod = Math.sqrt((motionX * motionX) + (motionZ * motionZ));
            motionY = motionY * 0.6F + yMod * 0.3F;
        }

        if (getStateTransitionCooldown() > 0) {
            setStateTransitionCooldown(getStateTransitionCooldown() - 1);
        }

        if (healingCooldown > 0) {
            healingCooldown--;
        }

        if (entityToAttack != null && entityToAttack.isDead) {
            this.setAttackTarget(null);
            setAttackState(EnumReaperAttackState.IDLE);
        }

        if (!world.isRemote && this.getAttackState() != EnumReaperAttackState.REST) {
            if (world.getTotalWorldTime() % 20 == 0) {
                for (EntityPlayer player : world.getEntitiesWithinAABB(EntityPlayer.class, this.getEntityBoundingBox().grow(4.0D))) {
                    if (!player.isDead) {
                        float damageAmount = player.getMaxHealth() * 0.02F;
                        float newHealth = player.getHealth() - damageAmount;
                        newHealth = Math.max(newHealth, 0.0F);

                        if (!player.isDead && player.getHealth() > 0.0F && newHealth <= 0.0F) {
                            player.sendMessage(new TextComponentString("你被死神的气息吞噬了。"));
                        }
                        player.setHealth(newHealth);
                    }
                }
            }
        }

        EntityPlayer nearestPlayer = world.getClosestPlayerToEntity(this, 16.0D);

        if (nearestPlayer != null && !nearestPlayer.isDead && !world.isRemote && soulSwapCooldown == 0
                && getHealth() / getMaxHealth() <= 0.2F) {

            int clearCount = this.dataManager.get(EFFECT_CLEAR_COUNT);

            if (clearCount > 0) {
                double percentBoost = clearCount * 0.015;

                IAttributeInstance healthAttr = this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH);

                UUID newUUID = UUID.randomUUID();

                AttributeModifier healthBoostModifier = new AttributeModifier(
                        newUUID,
                        "EffectClearHealthBoost_" + clearCount + "_" + System.currentTimeMillis(),
                        percentBoost,
                        2
                );

                healthAttr.applyModifier(healthBoostModifier);

                this.dataManager.set(EFFECT_CLEAR_COUNT, 0);
            }
            float reaperHealthPercent = this.getHealth() / this.getMaxHealth();
            float playerHealthPercent = nearestPlayer.getMaxHealth() > 0 ?
                    nearestPlayer.getHealth() / nearestPlayer.getMaxHealth() : 0;

            this.setHealth(playerHealthPercent * this.getMaxHealth());
            nearestPlayer.setHealth(reaperHealthPercent * nearestPlayer.getMaxHealth());
            nearestPlayer.sendMessage(new TextComponentString("死神发动了灵魂契约"));

            soulSwapCooldown = 4000;
        }

        if (soulSwapCooldown > 0) {
            soulSwapCooldown--;
        }

        if (entityToAttack != null && getAttackState() != EnumReaperAttackState.REST) {
            double verticalDiff = entityToAttack.posY - this.posY;
            double horizontalDistance = this.getDistance(entityToAttack.posX, this.posY, entityToAttack.posZ);
            float moveAmount = MathHelper.clamp((8F - (float)horizontalDistance) / 8F * 4F, 0, 2.5F);

            if (verticalDiff > 1.0F) { // 玩家在Boss上方较大距离
                motionY += 0.05F * moveAmount; // 加速上升
            } else if (verticalDiff > 0.1F) { // 玩家略高于Boss
                motionY += 0.03F * moveAmount;
            } else if (verticalDiff < -1.0F) { // 玩家在Boss下方较大距离
                motionY -= 0.05F * moveAmount; // 加速下降
            } else if (verticalDiff < -0.1F) { // 玩家略低于Boss
                motionY -= 0.03F * moveAmount;
            }
        }
    }

    @Override
    public void move(MoverType type, double x, double y, double z) {
        if (type == MoverType.SELF) {
            super.move(type, x, y, z);
        }
    }

    @Override
    public void onDeath(DamageSource source) {
        LifeLinkManager.INSTANCE.removeBindingByBoss(this.getUniqueID());

        IAttributeInstance healthAttr = this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH);
        Collection<AttributeModifier> modifiers = healthAttr.getModifiers();

        for (AttributeModifier modifier : modifiers) {
            if (modifier.getName().startsWith("EffectClearHealthBoost_")) {
                healthAttr.removeModifier(modifier);
            }
        }

        super.onDeath(source);
    }


    @Override
    public String getName() {
        return I18n.format("entity.boss.grimreaper");
    }

    @Override
    protected boolean canDespawn() {
        return true;
    }

    public int getStateTransitionCooldown() {
        return this.dataManager.get(STATE_TRANSITION_COOLDOWN);
    }

    public void setStateTransitionCooldown(int value) {
        this.dataManager.set(STATE_TRANSITION_COOLDOWN, value);
    }

    public float getFloatingTicks() {
        return floatingTicks;
    }

    private void teleportTo(double x, double y, double z) {
        if (!world.isRemote) {

            if (!world.isBlockLoaded(new BlockPos(x, y, z))) return;

            EnumReaperAttackState current = getAttackState();
            if (current != EnumReaperAttackState.TELEPORTING) {
                setAttackState(EnumReaperAttackState.TELEPORTING);
            }

            this.setPositionAndUpdate(x, y, z);

            if (rand.nextFloat() < 0.9F) {
                this.playSound(SoundEvents.ENTITY_ENDERMEN_TELEPORT, 1.5F, 1.0F);
            }

            if (current != EnumReaperAttackState.TELEPORTING) {
                setAttackState(current);
            }
        }
    }


    private void clearNegativePotionEffects() {
        Collection<PotionEffect> effects = this.getActivePotionEffects();
        if (effects.isEmpty()) return;

        new ArrayList<>(effects).forEach(effect -> {
            if (!effect.getPotion().isBeneficial()) {
                this.removePotionEffect(effect.getPotion());
            }
        });
    }

    private void syncArmorAndToughnessFromTarget() {
        EntityLivingBase target = this.getAttackTarget();
        if (target == null || target.isDead) return;

        IAttributeInstance armorAttr = this.getEntityAttribute(SharedMonsterAttributes.ARMOR);
        IAttributeInstance toughnessAttr = this.getEntityAttribute(SharedMonsterAttributes.ARMOR_TOUGHNESS);

        removeModifierByName(armorAttr, ARMOR_SYNC_NAME);
        removeModifierByName(toughnessAttr, TOUGHNESS_SYNC_NAME);

        double targetArmor = target.getEntityAttribute(SharedMonsterAttributes.ARMOR).getAttributeValue();
        double targetToughness = target.getEntityAttribute(SharedMonsterAttributes.ARMOR_TOUGHNESS).getAttributeValue();

        AttributeModifier armorMod = new AttributeModifier(UUID.randomUUID(), ARMOR_SYNC_NAME, targetArmor, 0);
        AttributeModifier toughnessMod = new AttributeModifier(UUID.randomUUID(), TOUGHNESS_SYNC_NAME, targetToughness, 0);

        armorAttr.applyModifier(armorMod);
        toughnessAttr.applyModifier(toughnessMod);
    }

    private void removeModifierByName(IAttributeInstance attr, String name) {
        for (AttributeModifier mod : attr.getModifiers()) {
            if (mod.getName().equals(name)) {
                attr.removeModifier(mod);
                break;
            }
        }
    }

    @Override
    public boolean isNonBoss() {
        return false;
    }

    /**
     * Add the given player to the list of players tracking this entity. For instance, a player may track a boss in
     * order to view its associated boss bar.
     */
    @Override
    public void addTrackingPlayer(EntityPlayerMP player) {
        super.addTrackingPlayer(player);
        this.bossInfo.addPlayer(player);
    }

    /**
     * Removes the given player from the list of players tracking this entity. See {@link Entity#addTrackingPlayer} for
     * more information on tracking.
     */
    @Override
    public void removeTrackingPlayer(EntityPlayerMP player) {
        super.removeTrackingPlayer(player);
        this.bossInfo.removePlayer(player);
    }
}