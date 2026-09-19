package com.danzy.fuelblast.network;

import com.danzy.fuelblast.client.BlastEffects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client notification that a fuel tank has just gone up. */
public record BlastEffectPacket(Vec3 pos, float power, int fuelMb, ResourceLocation fluid) {

    public static void encode(BlastEffectPacket p, FriendlyByteBuf buf) {
        buf.writeDouble(p.pos.x);
        buf.writeDouble(p.pos.y);
        buf.writeDouble(p.pos.z);
        buf.writeFloat(p.power);
        buf.writeVarInt(p.fuelMb);
        buf.writeResourceLocation(p.fluid);
    }

    public static BlastEffectPacket decode(FriendlyByteBuf buf) {
        Vec3 pos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        return new BlastEffectPacket(pos, buf.readFloat(), buf.readVarInt(), buf.readResourceLocation());
    }

    public static void handle(BlastEffectPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> BlastEffects.play(p)));
        ctx.get().setPacketHandled(true);
    }
}
