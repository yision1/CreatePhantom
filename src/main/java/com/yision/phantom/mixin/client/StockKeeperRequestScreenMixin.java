package com.yision.phantom.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.logistics.AddressEditBox;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen;
import com.simibubi.create.foundation.utility.CreateLang;
import com.yision.phantom.content.logistics.tunablePortableTicker.TunablePortableTickerScreen;
import com.yision.phantom.content.logistics.tunablePortableTicker.StockKeeperRequestScreenAccess;
import com.yision.phantom.content.logistics.tunablePortableTicker.TunablePortableTickerLockPacket;
import com.yision.phantom.content.logistics.tunablePortableTicker.ClientScreenStorage;
import com.simibubi.create.content.logistics.BigItemStack;
import java.util.List;
import java.util.Set;
import net.createmod.catnip.lang.LangBuilder;
import net.createmod.catnip.platform.services.NetworkHelper;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = StockKeeperRequestScreen.class, priority = 900, remap = false)
public abstract class StockKeeperRequestScreenMixin implements StockKeeperRequestScreenAccess {
	@Shadow
	@Final
	private Set<Integer> hiddenCategories;

	@Override
	public Set<Integer> createphantom$getHiddenCategories() {
		return hiddenCategories;
	}

	@Override
	@Invoker("renderItemEntry")
	public abstract void createphantom$renderItemEntry(GuiGraphics graphics, float scale, BigItemStack entry,
		boolean hovered, boolean renderingOrders);

	@WrapOperation(method = "<init>", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/player/Player;getMainHandItem()Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack createphantom$portableMode(Player player, Operation<ItemStack> original) {
		return (Object) this instanceof TunablePortableTickerScreen ? ItemStack.EMPTY : original.call(player);
	}

	@WrapOperation(method = "<init>", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
	private <T extends Entity> List<T> createphantom$skipKeeperLookup(Level level, Class<T> type, AABB bounds,
		Operation<List<T>> original) {
		return (Object) this instanceof TunablePortableTickerScreen ? List.of() : original.call(level, type, bounds);
	}

	@WrapOperation(method = "<init>", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"))
	private BlockEntity createphantom$skipBlazeLookup(Level level, BlockPos pos, Operation<BlockEntity> original) {
		return (Object) this instanceof TunablePortableTickerScreen ? null : original.call(level, pos);
	}

	@WrapOperation(method = "init", at = @At(value = "NEW",
		target = "(Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/client/gui/Font;IIIIZ)Lcom/simibubi/create/content/logistics/AddressEditBox;"))
	private AddressEditBox createphantom$portableAddress(Screen screen, Font font, int x, int y, int width, int height,
		boolean anchorToBottom, Operation<AddressEditBox> original) {
		if ((Object) this instanceof TunablePortableTickerScreen portable)
			return portable.createAddressBox(font, x, y, width, height, anchorToBottom);
		return original.call(screen, font, x, y, width, height, anchorToBottom);
	}

	@WrapOperation(method = "containerTick", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/player/Player;closeContainer()V"))
	private void createphantom$keepPortableOpen(Player player, Operation<Void> original) {
		if (!((Object) this instanceof TunablePortableTickerScreen))
			original.call(player);
	}

	@ModifyExpressionValue(method = {"renderBg", "renderForeground", "mouseClicked"}, at = @At(value = "FIELD",
		target = "Lcom/simibubi/create/content/logistics/stockTicker/StockKeeperRequestScreen;isAdmin:Z",
		opcode = Opcodes.GETFIELD))
	private boolean createphantom$currentAdmin(boolean original) {
		return (Object) this instanceof TunablePortableTickerScreen portable ? portable.getMenu().isAdmin() : original;
	}

	@ModifyExpressionValue(method = {"renderBg", "renderForeground", "mouseClicked"}, at = @At(value = "FIELD",
		target = "Lcom/simibubi/create/content/logistics/stockTicker/StockKeeperRequestScreen;isLocked:Z",
		opcode = Opcodes.GETFIELD))
	private boolean createphantom$currentLock(boolean original) {
		return (Object) this instanceof TunablePortableTickerScreen portable ? portable.getMenu().isLocked() : original;
	}

	@WrapOperation(method = "mouseClicked", at = @At(value = "FIELD",
		target = "Lcom/simibubi/create/content/logistics/stockTicker/StockKeeperRequestScreen;isLocked:Z",
		opcode = Opcodes.PUTFIELD))
	private void createphantom$updatePortableLock(StockKeeperRequestScreen screen, boolean locked, Operation<Void> original) {
		original.call(screen, locked);
		if (screen instanceof TunablePortableTickerScreen portable)
			portable.getMenu().setNetworkState(portable.getMenu().isAdmin(), locked);
	}

	@WrapOperation(method = "renderBg", at = @At(value = "INVOKE",
		target = "Lcom/simibubi/create/foundation/utility/CreateLang;translate(Ljava/lang/String;[Ljava/lang/Object;)Lnet/createmod/catnip/lang/LangBuilder;"))
	private LangBuilder createphantom$portableTitle(String key, Object[] args, Operation<LangBuilder> original) {
		if ((Object) this instanceof TunablePortableTickerScreen && key.equals("gui.stock_keeper.title"))
			return CreateLang.builder().add(Component.translatable("item.createphantom.tunable_portable_ticker.screen_title"));
		return original.call(key, args);
	}

	@WrapOperation(method = "getTroubleshootingMessage", at = @At(value = "INVOKE",
		target = "Ljava/util/List;isEmpty()Z"))
	private boolean createphantom$emptyPortableStock(List<?> stock, Operation<Boolean> original) {
		boolean empty = original.call(stock);
		return (Object) this instanceof TunablePortableTickerScreen portable && stock == portable.currentItemSource
			? portable.getMenu().getStockData().getLastClientsideStockSnapshotAsSummary().isEmpty() : empty;
	}

	@WrapMethod(method = {"sendIt", "removed"})
	private void createphantom$portableTransport(Operation<Void> original) {
		if ((Object) this instanceof TunablePortableTickerScreen portable)
			ClientScreenStorage.send(portable.getMenu(), () -> original.call());
		else
			original.call();
	}

	@WrapOperation(method = "mouseClicked", at = @At(value = "INVOKE",
		target = "Lnet/createmod/catnip/platform/services/NetworkHelper;sendToServer(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V"))
	private void createphantom$sendPortableLock(NetworkHelper network, CustomPacketPayload payload, Operation<Void> original) {
		if ((Object) this instanceof TunablePortableTickerScreen portable) {
			var menu = portable.getMenu();
			original.call(network, new TunablePortableTickerLockPacket(
				menu.getLocator(), menu.getChannel(), menu.getSessionNetwork(), menu.isLocked()));
		} else {
			original.call(network, payload);
		}
	}
}
