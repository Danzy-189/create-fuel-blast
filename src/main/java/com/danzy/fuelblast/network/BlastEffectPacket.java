package com.danzy.fuelblast.network;

import com.danzy.fuelblast.FuelBlast;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server -> client notification that a fuel tank has just gone up. */
public record BlastEffectPacket(Vec3 pos, float power, int fuelMb, ResourceLocation fluid)
        implements CustomPacketPayload {

    public static final Type<BlastEffectPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FuelBlast.ID, "blast_effect"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BlastEffectPacket> STREAM_CODEC =
            StreamCodec.of(BlastEffectPacket::write, BlastEffectPacket::read);

    private static void write(RegistryFriendlyByteBuf buf, BlastEffectPacket p) {
        buf.writeDouble(p.pos.x);
        buf.writeDouble(p.pos.y);
        buf.writeDouble(p.pos.z);
        buf.writeFloat(p.power);
        buf.writeVarInt(p.fuelMb);
        buf.writeResourceLocation(p.fluid);
    }

    private static BlastEffectPacket read(RegistryFriendlyByteBuf buf) {
        Vec3 pos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        return new BlastEffectPacket(pos, buf.readFloat(), buf.readVarInt(), buf.readResourceLocation());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BlastEffectPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                com.danzy.fuelblast.client.BlastEffects.play(packet);
            }
        });
    }
}
