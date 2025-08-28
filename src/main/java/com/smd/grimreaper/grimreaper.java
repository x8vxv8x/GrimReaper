package com.smd.grimreaper;

import com.smd.grimreaper.client.event.EventHooks;
import com.smd.grimreaper.entity.EntityGrimReaper;
import com.smd.grimreaper.proxy.ServerProxy;
import com.smd.grimreaper.skill.LifeLinkManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.registry.EntityRegistry;

@Mod(modid = Tags.MOD_ID, name = Tags.MOD_NAME, version = Tags.VERSION)
public class grimreaper {

    @SidedProxy(clientSide = "com.smd.grimreaper.proxy.ClientProxy",
                serverSide = "com.smd.grimreaper.proxy.ServerProxy")
    public static ServerProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new EventHooks());
        proxy.registerEntityRenderers();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        EntityRegistry.registerModEntity(new ResourceLocation(Tags.MOD_ID, "GrimReaper"), EntityGrimReaper.class, EntityGrimReaper.class.getSimpleName(), 1121, this, 50, 2, true);
        LifeLinkManager.init();
    }
}
