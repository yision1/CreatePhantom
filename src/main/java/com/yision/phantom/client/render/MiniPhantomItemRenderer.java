package com.yision.phantom.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;
import com.yision.phantom.CreatePhantom;
import com.yision.phantom.item.miniphantom.MiniPhantomItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class MiniPhantomItemRenderer extends CustomRenderedItemModelRenderer {
	private static final ModelResourceLocation PACKAGE_MODEL = ModelResourceLocation.standalone(
		ResourceLocation.fromNamespaceAndPath(CreatePhantom.MODID, "item/mini_phantom_package"));

	@Override
	protected void render(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
		ItemDisplayContext transformType, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
		ms.pushPose();
		applyHeadingRotation(stack, transformType, ms);
		renderer.render(getModel(stack, model), light);
		ms.popPose();
	}

	private static BakedModel getModel(ItemStack stack, CustomRenderedItemModel model) {
		if (!MiniPhantomItem.hasCargo(stack)) {
			return model.getOriginalModel();
		}
		return Minecraft.getInstance()
			.getModelManager()
			.getModel(PACKAGE_MODEL);
	}

	private static void applyHeadingRotation(ItemStack stack, ItemDisplayContext transformType, PoseStack ms) {
		if (transformType != ItemDisplayContext.FIXED && transformType != ItemDisplayContext.GROUND) {
			return;
		}
		if (!MiniPhantomItem.hasHeadingAngle(stack)) {
			return;
		}
		ms.mulPose(Axis.YP.rotationDegrees(MiniPhantomItem.getHeadingAngle(stack)));
	}
}
