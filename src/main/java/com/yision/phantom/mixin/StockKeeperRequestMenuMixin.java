package com.yision.phantom.mixin;

import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestMenu;
import com.yision.phantom.content.logistics.tunablePortableTicker.StockKeeperRequestMenuAccess;
import com.yision.phantom.content.logistics.tunablePortableTicker.TunablePortableTickerMenu;
import java.util.List;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StockKeeperRequestMenu.class)
public abstract class StockKeeperRequestMenuMixin extends AbstractContainerMenu implements StockKeeperRequestMenuAccess {
	@Shadow(remap = false)
	boolean isAdmin;
	@Shadow(remap = false)
	boolean isLocked;

	protected StockKeeperRequestMenuMixin(MenuType<?> type, int id) {
		super(type, id);
	}

	@Override
	public boolean createphantom$isAdmin() {
		return isAdmin;
	}

	@Override
	public void createphantom$setAdmin(boolean admin) {
		isAdmin = admin;
	}

	@Override
	public boolean createphantom$isLocked() {
		return isLocked;
	}

	@Override
	public void createphantom$setLocked(boolean locked) {
		isLocked = locked;
	}

	@Inject(method = "initializeContents", at = @At("HEAD"))
	private void createphantom$syncPortableInventory(int stateId, List<ItemStack> items, ItemStack carried,
		CallbackInfo ci) {
		if ((Object) this instanceof TunablePortableTickerMenu) {
			super.initializeContents(stateId, items, carried);
		}
	}
}
