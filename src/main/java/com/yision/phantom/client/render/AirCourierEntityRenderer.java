package com.yision.phantom.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.yision.phantom.entity.courier.AirCourierEntity;
import com.yision.phantom.item.miniphantom.MiniPhantomItem;
import com.yision.phantom.registry.AllItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class AirCourierEntityRenderer extends EntityRenderer<AirCourierEntity> {
	private final ItemRenderer itemRenderer;

	public AirCourierEntityRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.itemRenderer = Minecraft.getInstance().getItemRenderer();
	}

	@Override
	public void render(AirCourierEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
		MultiBufferSource buffer, int packedLight) {
		if (entity.tickCount < 1) {
			super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
			return;
		}

		ItemStack renderStack = AllItems.MINI_PHANTOM.asStack();
		if (renderStack.isEmpty()) {
			return;
		}
		MiniPhantomItem.loadCargo(renderStack, entity.getPackage());

		poseStack.pushPose();
		float yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
		poseStack.mulPose(Axis.YP.rotationDegrees(yaw + 180.0f));
		float pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
		poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
		if (entity.getPhase() == AirCourierEntity.Phase.WAITING) {
			poseStack.mulPose(Axis.XP.rotationDegrees(18.0f));
		}
		poseStack.mulPose(Axis.ZP.rotationDegrees(-Mth.lerp(partialTick, entity.oldDeltaYaw, entity.newDeltaYaw) * -4.0f));

		float scale = entity.getPhase() == AirCourierEntity.Phase.CRUISE ? 0.58f : 0.66f;
		float yOffset = entity.getPhase() == AirCourierEntity.Phase.WAITING ? 0.5f : 0.08f;
		poseStack.scale(scale, scale, scale);
		poseStack.translate(0, yOffset, 0.02f);

		itemRenderer.renderStatic(renderStack, ItemDisplayContext.FIXED, packedLight, OverlayTexture.NO_OVERLAY,
			poseStack, buffer, entity.level(), entity.getId());
		poseStack.popPose();

		super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
	}

	@Override
	public ResourceLocation getTextureLocation(AirCourierEntity entity) {
		return InventoryMenu.BLOCK_ATLAS;
	}
}
