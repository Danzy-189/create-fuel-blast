package com.danzy.fuelblast.network;

import com.danzy.fuelblast.FuelBlast;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = FuelBlast.ID, bus = EventBusSubscriber.Bus.MOD)
public final class FuelBlastNetwork {

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToClient(BlastEffectPacket.TYPE, BlastEffectPacket.STREAM_CODEC, BlastEffectPacket::handle);
    }

    private FuelBlastNetwork() {}
}
