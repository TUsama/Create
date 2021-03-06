package com.simibubi.create.content.contraptions.components.structureMovement;
import org.apache.commons.lang3.mutable.MutableObject;

import com.simibubi.create.content.contraptions.components.structureMovement.sync.ContraptionInteractionPacket;
import com.simibubi.create.foundation.networking.AllPackets;
import com.simibubi.create.foundation.utility.RaycastHelper;
import com.simibubi.create.foundation.utility.RaycastHelper.PredicateTraceResult;

import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.world.gen.feature.template.Template.BlockInfo;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent.ClickInputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber
public class ContraptionInteractionHandler {

	@SubscribeEvent
	@OnlyIn(Dist.CLIENT)
	public static void rightClickingOnContraptionsGetsHandledLocally(ClickInputEvent event) {
		Minecraft mc = Minecraft.getInstance();
		ClientPlayerEntity player = mc.player;
		if (player == null)
			return;
		if (mc.world == null)
			return;
		if (!event.isUseItem())
			return;
		Vec3d origin = RaycastHelper.getTraceOrigin(player);
		
		double reach = mc.playerController.getBlockReachDistance();
		if (mc.objectMouseOver != null && mc.objectMouseOver.getHitVec() != null) 
			reach = Math.min(mc.objectMouseOver.getHitVec().distanceTo(origin), reach);
		
		Vec3d target = RaycastHelper.getTraceTarget(player, reach, origin);
		for (ContraptionEntity contraptionEntity : mc.world.getEntitiesWithinAABB(ContraptionEntity.class,
			new AxisAlignedBB(origin, target))) {

			Vec3d localOrigin = contraptionEntity.toLocalVector(origin);
			Vec3d localTarget = contraptionEntity.toLocalVector(target);
			Contraption contraption = contraptionEntity.getContraption();

			MutableObject<BlockRayTraceResult> mutableResult = new MutableObject<>();
			PredicateTraceResult predicateResult = RaycastHelper.rayTraceUntil(localOrigin, localTarget, p -> {
				BlockInfo blockInfo = contraption.blocks.get(p);
				if (blockInfo == null)
					return false;
				BlockState state = blockInfo.state;
				VoxelShape raytraceShape = state.getShape(Minecraft.getInstance().world, BlockPos.ZERO.down());
				if (raytraceShape.isEmpty())
					return false;
				BlockRayTraceResult rayTrace = raytraceShape.rayTrace(localOrigin, localTarget, p);
				if (rayTrace != null) {
					mutableResult.setValue(rayTrace);
					return true;
				}
				return false;
			});

			if (predicateResult == null || predicateResult.missed())
				return;

			BlockRayTraceResult rayTraceResult = mutableResult.getValue();
			Hand hand = event.getHand();
			Direction face = rayTraceResult.getFace();
			BlockPos pos = rayTraceResult.getPos();
			
			if (!contraptionEntity.handlePlayerInteraction(player, pos, face, hand))
				return;
			AllPackets.channel.sendToServer(new ContraptionInteractionPacket(contraptionEntity, hand,
				pos, face));
			event.setCanceled(true);
			event.setSwingHand(false);
		}
	}

}
