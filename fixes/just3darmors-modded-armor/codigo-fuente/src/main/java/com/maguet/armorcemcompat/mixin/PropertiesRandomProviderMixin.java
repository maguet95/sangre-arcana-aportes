package com.maguet.armorcemcompat.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import traben.entity_texture_features.features.property_reading.PropertiesRandomProvider;

import java.util.UUID;

/**
 * ETF decide con entityCanUpdate(uuid) si una regla se vuelve a evaluar para una
 * entidad, y ese permiso solo se concede la primera vez que alguna regla coincide.
 * Resultado: si te pones la armadura DESPUÉS de que el modelo ya se decidió, el
 * cambio no se ve — el modelo se queda con la variante vieja.
 *
 * Para una condición basada en el equipo eso no sirve: la armadura cambia
 * constantemente en partida. Este mixin permite siempre la re-evaluación; la
 * frecuencia real la sigue gobernando EMF con modelUpdateFrequency, así que no
 * se evalúa más a menudo de lo que el usuario haya configurado.
 */
@Mixin(value = PropertiesRandomProvider.class, remap = false)
public class PropertiesRandomProviderMixin {

    @Inject(method = "entityCanUpdate", at = @At("HEAD"), cancellable = true)
    private void armorcemcompat$alwaysAllowUpdate(UUID uuid, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
    }
}
