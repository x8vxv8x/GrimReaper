package com.smd.grimreaper.proxy;

import com.smd.grimreaper.client.render.RenderReaperFactory;
import com.smd.grimreaper.entity.EntityGrimReaper;
import net.minecraftforge.fml.client.registry.RenderingRegistry;

public class ClientProxy extends ServerProxy {
    @Override
    public void registerEntityRenderers() {
        RenderingRegistry.registerEntityRenderingHandler(EntityGrimReaper.class, RenderReaperFactory.INSTANCE);
    }
}
