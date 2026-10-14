package com.yision.phantom.item.storagecard;

import java.util.List;
import java.util.UUID;

import org.apache.commons.lang3.tuple.Pair;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnectionHandler;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;

import net.neoforged.neoforge.client.event.ClientTickEvent;

public class StorageChannelExtensionCardClientHandler {
	public static void onClientTick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (mc.isPaused() || mc.level == null || player == null)
			return;
		ItemStack stack = player.getMainHandItem();
		if (!(stack.getItem() instanceof StorageChannelExtensionCardItem))
			return;
		UUID frequency = StorageChannelExtensionCardItem.networkFromStack(stack);
		if (frequency == null)
			return;

		int color = AnimationTickHolder.getTicks() % 16 < 8 ? 0x708DAD : 0x90ADCD;
		for (LogisticallyLinkedBehaviour behaviour : LogisticallyLinkedBehaviour.getAllPresent(frequency, false,
			true)) {
			SmartBlockEntity be = behaviour.blockEntity;
			if (!player.canInteractWithBlock(be.getBlockPos(), 64))
				continue;
			List<AABB> boxes = be.getBlockState().getShape(mc.level, be.getBlockPos()).toAabbs();
			for (int i = 0; i < boxes.size(); i++)
				showOutline(Pair.of(behaviour, i), boxes.get(i).inflate(-1 / 128f).move(be.getBlockPos()), color);
		}

		BlockPos playerPos = player.blockPosition();
		for (int x = SectionPos.blockToSectionCoord(playerPos.getX() - 64);
			 x <= SectionPos.blockToSectionCoord(playerPos.getX() + 64); x++) {
			for (int z = SectionPos.blockToSectionCoord(playerPos.getZ() - 64);
				 z <= SectionPos.blockToSectionCoord(playerPos.getZ() + 64); z++) {
				LevelChunk chunk = mc.level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
				if (chunk == null)
					continue;
				for (BlockEntity be : chunk.getBlockEntities().values()) {
					if (!(be instanceof FactoryPanelBlockEntity board)
						|| !playerPos.closerThan(board.getBlockPos(), 64))
						continue;
					for (FactoryPanelBehaviour panel : board.panels.values()) {
						if (!panel.isActive() || !frequency.equals(panel.network))
							continue;
						showOutline(panel, FactoryPanelConnectionHandler
							.getBB(board.getBlockState(), panel.getPanelPosition()).inflate(-1.5 / 128f), color);
					}
				}
			}
		}
	}

	private static void showOutline(Object key, AABB bounds, int color) {
		Outliner.getInstance().showAABB(key, bounds, 2)
			.lineWidth(1 / 32f)
			.disableLineNormals()
			.colored(color);
	}
}
