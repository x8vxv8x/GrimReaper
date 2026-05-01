package com.smd.grimreaper.proxy;

import com.smd.grimreaper.client.render.RenderGrimReaper;
import com.smd.grimreaper.entity.EntityGrimReaper;
import com.smd.grimreaper.skill.LifeLinkManager;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

public class ClientProxy extends ServerProxy {
    @Override
    public void registerEntityRenderers() {
        RenderingRegistry.registerEntityRenderingHandler(EntityGrimReaper.class, RenderGrimReaper::new);
    }

    @Override
    public void init(FMLInitializationEvent event) {
        LifeLinkManager.init();
    }
}
