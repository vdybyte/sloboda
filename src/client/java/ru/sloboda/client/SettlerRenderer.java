package ru.sloboda.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import ru.sloboda.Sloboda;
import ru.sloboda.entity.SettlerEntity;

/** Поселенец рисуется человеческой моделью со своей текстурой (рубаха с красным поясом, лапти). */
public class SettlerRenderer extends HumanoidMobRenderer<SettlerEntity, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
    /** Собственный слой модели, чтобы не зависеть от ванильных зомби-слоёв, которые менялись в 26.x. */
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Sloboda.id("settler"), "main");

    private static final Identifier TEXTURE = Sloboda.id("textures/entity/settler.png");

    public SettlerRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(LAYER)), 0.5F);
    }

    public static LayerDefinition createLayer() {
        return LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F), 64, 64);
    }

    @Override
    public HumanoidRenderState createRenderState() {
        return new HumanoidRenderState();
    }

    @Override
    public Identifier getTextureLocation(HumanoidRenderState state) {
        return TEXTURE;
    }
}
