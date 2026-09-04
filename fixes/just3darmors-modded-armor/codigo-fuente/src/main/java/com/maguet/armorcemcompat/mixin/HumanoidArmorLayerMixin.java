package com.maguet.armorcemcompat.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import traben.entity_model_features.models.animation.state.EMFEntityRenderState;
import traben.entity_model_features.models.parts.EMFModelPartRoot;

/**
 * EMF sustituye el modelo de armadura cuando Minecraft crea los renderers (al cargar
 * recursos). A partir de ahí, HumanoidArmorLayer se queda con ese objeto y no lo vuelve a
 * pedir, así que aunque EMF recalcule bien la variante en cada frame, lo que se dibuja sigue
 * siendo el modelo del estado con el que se creó.
 *
 * Síntoma: se ve la armadura que llevabas al entrar al mundo. Se corrige sola con F3+T
 * (recarga recursos) o al abrir el inventario, porque ambos fuerzan que se vuelva a pedir.
 *
 * Aquí se fuerza esa comprobación justo cuando el layer pide el modelo, que es el único
 * punto donde se sabe qué entidad y qué slot se están renderizando.
 */
@Mixin(HumanoidArmorLayer.class)
public class HumanoidArmorLayerMixin {

    @Inject(method = "getArmorModel", at = @At("RETURN"))
    private void armorcemcompat$refreshVariant(HumanoidRenderState state, EquipmentSlot slot,
                                               CallbackInfoReturnable<HumanoidModel<?>> cir) {
        try {
            HumanoidModel<?> model = cir.getReturnValue();
            if (model == null) return;
            ModelPart root = model.root();
            if (!(root instanceof EMFModelPartRoot emfRoot)) return;
            // El HumanoidRenderState de Minecraft no implementa la interfaz de EMF;
            // hay que pedirle a EMF su propio estado a partir del vanilla.
            EMFEntityRenderState emfState = EMFEntityRenderState.from(state);
            if (emfState != null) {
                emfRoot.doVariantCheck(emfState);
            }
        } catch (Throwable ignored) {
            // Nunca romper el render por esto.
        }
    }
}
