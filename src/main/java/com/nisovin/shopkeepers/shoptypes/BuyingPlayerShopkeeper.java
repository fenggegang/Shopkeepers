package com.nisovin.shopkeepers.shoptypes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.nisovin.shopkeepers.Filter;
import com.nisovin.shopkeepers.ItemCount;
import com.nisovin.shopkeepers.Settings;
import com.nisovin.shopkeepers.ShopCreationData;
import com.nisovin.shopkeepers.ShopType;
import com.nisovin.shopkeepers.ShopkeeperCreateException;
import com.nisovin.shopkeepers.Utils;
import com.nisovin.shopkeepers.shoptypes.offers.PriceOffer;
import com.nisovin.shopkeepers.ui.UIType;
import com.nisovin.shopkeepers.ui.defaults.DefaultUIs;

public class BuyingPlayerShopkeeper extends PlayerShopkeeper {

	protected static class BuyingPlayerShopEditorHandler extends PlayerShopEditorHandler {

		protected BuyingPlayerShopEditorHandler(UIType uiType, BuyingPlayerShopkeeper shopkeeper) {
			super(uiType, shopkeeper);
		}

		@Override
		public BuyingPlayerShopkeeper getShopkeeper() {
			return (BuyingPlayerShopkeeper) super.getShopkeeper();
		}

		@Override
		protected boolean openWindow(Player player) {
			final BuyingPlayerShopkeeper shopkeeper = this.getShopkeeper();
			Inventory inventory = Bukkit.createInventory(player, 27, Settings.editorTitle);

			// add the shopkeeper's offers:
			List<ItemCount> chestItems = shopkeeper.getItemsFromChest();
			for (int column = 0; column < chestItems.size() && column < 8; column++) {
				ItemCount itemCount = chestItems.get(column);
				ItemStack type = itemCount.getItem(); // this item is already a copy with amount 1
				ItemStack currencyItem = null;
				PriceOffer offer = shopkeeper.getOffer(type);

				if (offer != null) {
					if (offer.getPrice() == 0) {
						currencyItem = Settings.createZeroCurrencyItem();
					} else {
						currencyItem = Settings.createCurrencyItem(offer.getPrice());
					}
					int tradedItemAmount = offer.getItem().getAmount();
					type.setAmount(tradedItemAmount);
				} else {
					currencyItem = Settings.createZeroCurrencyItem();
				}
				assert currencyItem != null;

				// add offer to inventory:
				inventory.setItem(column, currencyItem);
				inventory.setItem(column + 18, type);
			}

			// add the special buttons:
			this.setActionButtons(inventory);
			// show editing inventory:
			player.openInventory(inventory);
			return true;
		}

		@Override
		protected void onInventoryClick(InventoryClickEvent event, Player player) {
			event.setCancelled(true);

			int slot = event.getRawSlot();
			if (slot >= 0 && slot <= 7) {
				// modifying cost:
				ItemStack tradedItem = event.getInventory().getItem(slot + 18);
				if (Utils.isEmpty(tradedItem)) return;
				this.handleUpdateTradeCostItemOnClick(event, Settings.createCurrencyItem(1), Settings.createZeroCurrencyItem());
			} else if (slot >= 18 && slot <= 25) {
				// modifying bought item quantity:
				this.handleUpdateItemAmountOnClick(event, 1);
			} else if (slot >= 9 && slot <= 16) {
			} else {
				super.onInventoryClick(event, player);
			}
		}

		@Override
		protected void saveEditor(Inventory inventory, Player player) {
			final BuyingPlayerShopkeeper shopkeeper = this.getShopkeeper();
			for (int column = 0; column < 8; column++) {
				ItemStack tradedItem = inventory.getItem(column + 18);
				if (!Utils.isEmpty(tradedItem)) {
					ItemStack priceItem = inventory.getItem(column);
					if (priceItem != null && priceItem.getType() == Settings.currencyItem && priceItem.getAmount() > 0) {
						shopkeeper.addOffer(tradedItem, priceItem.getAmount());
					} else {
						shopkeeper.removeOffer(tradedItem);
					}
				}
			}
		}
	}

	protected class BuyingPlayerShopTradingHandler extends PlayerShopTradingHandler {

		protected BuyingPlayerShopTradingHandler(UIType uiType, BuyingPlayerShopkeeper shopkeeper) {
			super(uiType, shopkeeper);
		}

		@Override
		public BuyingPlayerShopkeeper getShopkeeper() {
			return (BuyingPlayerShopkeeper) super.getShopkeeper();
		}

		@Override
		protected void onPurchaseClick(InventoryClickEvent event, Player player, ItemStack[] usedRecipe, ItemStack offered1, ItemStack offered2) {
			super.onPurchaseClick(event, player, usedRecipe, offered1, offered2);
			if (event.isCancelled()) return;
			final BuyingPlayerShopkeeper shopkeeper = this.getShopkeeper();

			// get offer for this bought item:
			ItemStack requestedItem = usedRecipe[0];
			PriceOffer offer = shopkeeper.getOffer(requestedItem);
			if (offer == null) {
				// this should not happen.. because the recipes were created based on the shopkeeper's offers
				event.setCancelled(true);
				return;
			}

			int tradedItemAmount = offer.getItem().getAmount();
			if (tradedItemAmount > requestedItem.getAmount()) {
				// this shouldn't happen .. because the recipe was created based on this offer
				event.setCancelled(true);
				return;
			}

			// get chest:
			Block chest = shopkeeper.getChest();
			if (!Utils.isChest(chest.getType())) {
				event.setCancelled(true);
				return;
			}

			// remove currency from chest:
			Inventory inventory = ((Chest) chest.getState()).getInventory();
			ItemStack[] contents = inventory.getContents();
			boolean removed = this.removeCurrencyFromChest(offer.getPrice(), contents);
			if (!removed) {
				event.setCancelled(true);
				return;
			}

			// add items to chest:
			int amount = this.getAmountAfterTaxes(tradedItemAmount);
			if (amount > 0) {
				// the item the trading player gave might slightly differ from the required item,
				// but is still accepted, depending on item comparison and settings:
				ItemStack receivedItem = offered1.clone(); // create a copy, just in case
				receivedItem.setAmount(amount);
				if (Utils.addItems(contents, receivedItem) != 0) {
					event.setCancelled(true);
					return;
				}
			}

			// save chest contents:
			inventory.setContents(contents);
		}

		protected boolean removeCurrencyFromChest(int amount, ItemStack[] contents) {
			int remaining = amount;

			// first pass - remove currency:
			int emptySlot = -1;
			for (int i = 0; i < contents.length; i++) {
				ItemStack item = contents[i];
				if (item != null) {
					if (Settings.isHighCurrencyItem(item) && remaining >= Settings.highCurrencyValue) {
						int needed = remaining / Settings.highCurrencyValue;
						int amt = item.getAmount();
						if (amt > needed) {
							item.setAmount(amt - needed);
							remaining = remaining - (needed * Settings.highCurrencyValue);
						} else {
							contents[i] = null;
							remaining = remaining - (amt * Settings.highCurrencyValue);
						}
					} else if (Settings.isCurrencyItem(item)) {
						int amt = item.getAmount();
						if (amt > remaining) {
							item.setAmount(amt - remaining);
							return true;
						} else if (amt == remaining) {
							contents[i] = null;
							return true;
						} else {
							contents[i] = null;
							remaining -= amt;
						}
					}
				} else if (emptySlot < 0) {
					emptySlot = i;
				}
				if (remaining <= 0) {
					return true;
				}
			}

			// second pass - try to make change:
			if (remaining > 0 && remaining <= Settings.highCurrencyValue && Settings.highCurrencyItem != Material.AIR && emptySlot >= 0) {
				for (int i = 0; i < contents.length; i++) {
					ItemStack item = contents[i];
					if (Settings.isHighCurrencyItem(item)) {
						if (item.getAmount() == 1) {
							contents[i] = null;
						} else {
							item.setAmount(item.getAmount() - 1);
						}
						int stackSize = Settings.highCurrencyValue - remaining;
						if (stackSize > 0) {
							contents[emptySlot] = Settings.createCurrencyItem(stackSize);
						}
						return true;
					}
				}
			}

			return false;
		}
	}

	private static final Filter<ItemStack> ITEM_FILTER = new Filter<ItemStack>() {

		@Override
		public boolean accept(ItemStack item) {
			if (Settings.isCurrencyItem(item) || Settings.isHighCurrencyItem(item)) return false;
			if (item.getType() == Material.WRITTEN_BOOK) return false;
			if (!item.getEnchantments().isEmpty()) return false; // TODO why don't allow buying of enchanted items?
			return true;
		}
	};

	// contains only one offer for a specific type of item:
	private final List<PriceOffer> offers = new ArrayList<PriceOffer>();
	private final List<PriceOffer> offersView = Collections.unmodifiableList(offers);

	/**
	 * For use in extending classes.
	 */
	protected BuyingPlayerShopkeeper() {
	}

	public BuyingPlayerShopkeeper(ConfigurationSection config) throws ShopkeeperCreateException {
		this.initOnLoad(config);
		this.onInitDone();
	}

	public BuyingPlayerShopkeeper(ShopCreationData creationData) throws ShopkeeperCreateException {
		this.initOnCreation(creationData);
		this.onInitDone();
	}

	@Override
	protected void onInitDone() {
		super.onInitDone();
		this.registerUIHandler(new BuyingPlayerShopEditorHandler(DefaultUIs.EDITOR_WINDOW, this));
		this.registerUIHandler(new BuyingPlayerShopTradingHandler(DefaultUIs.TRADING_WINDOW, this));
	}

	@Override
	protected void load(ConfigurationSection config) throws ShopkeeperCreateException {
		super.load(config);
		// load offers:
		this.clearOffers();
		// TODO remove legacy: load offers from old costs section
		this.addOffers(PriceOffer.loadFromConfigOld(config, "costs"));
		this.addOffers(PriceOffer.loadFromConfig(config, "offers"));
	}

	@Override
	protected void save(ConfigurationSection config) {
		super.save(config);
		// save offers:
		PriceOffer.saveToConfig(config, "offers", this.getOffers());
	}

	@Override
	public ShopType<?> getType() {
		return DefaultShopTypes.PLAYER_BUYING();
	}

	@Override
	public List<ItemStack[]> getRecipes() {
		List<ItemStack[]> recipes = new ArrayList<ItemStack[]>();
		List<ItemCount> chestItems = this.getItemsFromChest();
		int chestTotal = this.getCurrencyInChest();
		for (PriceOffer offer : this.getOffers()) {
			ItemStack tradedItem = offer.getItem();
			ItemCount itemCount = ItemCount.findSimilar(chestItems, tradedItem);
			if (itemCount != null) {
				if (chestTotal >= offer.getPrice()) {
					ItemStack[] recipe = new ItemStack[3];
					recipe[0] = tradedItem;
					recipe[2] = Settings.createCurrencyItem(offer.getPrice());
					recipes.add(recipe);
				}
			}
		}
		return recipes;
	}

	private List<ItemCount> getItemsFromChest() {
		return this.getItemsFromChest(ITEM_FILTER);
	}

	// OFFERS:

	public List<PriceOffer> getOffers() {
		return offersView;
	}

	public PriceOffer getOffer(ItemStack tradedItem) {
		for (PriceOffer offer : this.getOffers()) {
			if (Utils.isSimilar(offer.getItem(), tradedItem)) {
				return offer;
			}
		}
		return null;
	}

	public PriceOffer addOffer(ItemStack tradedItem, int price) {
		// create offer (also handles validation):
		PriceOffer newOffer = new PriceOffer(tradedItem, price);

		// add new offer (replacing any previous offer for the same item):
		this.addOffer(newOffer);
		return newOffer;
	}

	private void addOffer(PriceOffer offer) {
		assert offer != null;
		// remove previous offer for the same item:
		this.removeOffer(offer.getItem());
		offers.add(offer);
	}

	private void addOffers(Collection<PriceOffer> offers) {
		assert offers != null;
		for (PriceOffer offer : offers) {
			if (offer == null) continue; // skip invalid entries
			// add new offer (replacing any previous offer for the same item):
			this.addOffer(offer);
		}
	}

	public void clearOffers() {
		offers.clear();
	}

	public void removeOffer(ItemStack tradedItem) {
		Iterator<PriceOffer> iterator = offers.iterator();
		while (iterator.hasNext()) {
			if (Utils.isSimilar(iterator.next().getItem(), tradedItem)) {
				iterator.remove();
				break;
			}
		}
	}
}
