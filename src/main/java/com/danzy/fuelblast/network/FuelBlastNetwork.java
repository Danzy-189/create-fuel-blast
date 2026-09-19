package com.danzy.fuelblast.network;

import com.danzy.fuelblast.FuelBlast;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class FuelBlastNetwork {
    private static final String VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(FuelBlast.ID, "main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    public static void register() {
        CHANNEL.messageBuilder(BlastEffectPacket.class, 0)
                .encoder(BlastEffectPacket::encode)
                .decoder(BlastEffectPacket::decode)
                .consumerMainThread(BlastEffectPacket::handle)
                .add();
    }

    private FuelBlastNetwork() {}
}
