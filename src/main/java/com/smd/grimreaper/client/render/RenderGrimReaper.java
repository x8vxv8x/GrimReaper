package com.smd.grimreaper.client.render;

import com.smd.grimreaper.client.model.ModelGrimReaper;
import com.smd.grimreaper.entity.EntityGrimReaper;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public class RenderGrimReaper<T extends EntityGrimReaper> extends RenderBiped<T> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("grimreaper:textures/entity/grimreaper.png");

    public RenderGrimReaper(RenderManager manager) {
        super(manager, new ModelGrimReaper(), 0.5F);
    }

    @Override
    protected void preRenderCallback(T entity, float partialTickTime) {
        double scale = 1.3D;
        GL11.glScaled(scale, scale, scale);
    }

    @Override
    protected ResourceLocation getEntityTexture(T entity) {
        return TEXTURE;
    }
}