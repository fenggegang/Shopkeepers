package com.nisovin.shopkeepers.shoptypes.offers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang.Validate;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import com.nisovin.shopkeepers.Log;
import com.nisovin.shopkeepers.Settings;
import com.nisovin.shopkeepers.Utils;
import com.nisovin.shopkeepers.compat.NMSManager;

/**
 * Stores information about an item stack being sold or bought for a certain price.
 */
public class PriceOffer {

	private final ItemStack item; // not null/empty
	private int price; // >= 0

	public PriceOffer(ItemStack item, int price) {
		Validate.isTrue(!Utils.isEmpty(item), "Item cannot be empty!");
		this.item = item.clone();
		this.setPrice(price);
	}

	public ItemStack getItem() {
		return item.clone();
	}

	public int getPrice() {
		return price;
	}

	public void setPrice(int price) {
		// TODO what about price of 0? maybe filter that?
		Validate.isTrue(price >= 0, "Price cannot be negative!");
		this.price = price;
	}

	// //////////
	// STATIC UTILITIES
	// //////////

	public static void saveToConfig(ConfigurationSection config, String node, Collection<PriceOffer> offers) {
		ConfigurationSection offersSection = config.createSection(node);
		int id = 0;
		for (PriceOffer offer : offers) {
			ItemStack item = offer.getItem();
			// TODO temporary, due to a bukkit bug custom head item can currently not be saved
			if (Settings.skipCustomHeadSaving && Utils.isCustomHeadItem(item)) {
				Log.warning("Skipping saving of trade involving a head item with custom texture, which cannot be saved currently due to a bukkit bug.");
				continue;
			}
			ConfigurationSection offerSection = offersSection.createSection(String.valueOf(id));
			Utils.saveItem(offerSection, "item", item);
			offerSection.set("price", offer.getPrice());
			id++;
		}
	}

	public static List<PriceOffer> loadFromConfig(ConfigurationSection config, String node) {
		List<PriceOffer> offers = new ArrayList<PriceOffer>();
		ConfigurationSection offersSection = config.getConfigurationSection(node);
		if (offersSection != null) {
			for (String id : offersSection.getKeys(false)) {
				ConfigurationSection offerSection = offersSection.getConfigurationSection(id);
				ItemStack item = Utils.loadItem(offerSection, "item");
				int price = offerSection.getInt("price");
				if (Utils.isEmpty(item) || price < 0) continue; // invalid offer
				offers.add(new PriceOffer(item, price));
			}
		}
		return offers;
	}

	// legacy:

	/*public static void saveToConfigOld(ConfigurationSection config, String node, Collection<PriceOffer> offers) {
		ConfigurationSection offersSection = config.createSection(node);
		int id = 0;
		for (PriceOffer offer : offers) {
			ItemStack item = offer.getItem();
			ConfigurationSection offerSection = offersSection.createSection(id + "");
			offerSection.set("item", item);
			String attributes = NMSManager.getProvider().saveItemAttributesToString(item);
			if (attributes != null && !attributes.isEmpty()) {
				offerSection.set("attributes", attributes);
			}
			// legacy: amount was stored separately from the item
			offerSection.set("amount", item.getAmount());
			offerSection.set("cost", offer.getPrice());
			id++;
		}
	}*/

	public static List<PriceOffer> loadFromConfigOld(ConfigurationSection config, String node) {
		List<PriceOffer> offers = new ArrayList<PriceOffer>();
		ConfigurationSection offersSection = config.getConfigurationSection(node);
		if (offersSection != null) {
			for (String key : offersSection.getKeys(false)) {
				ConfigurationSection offerSection = offersSection.getConfigurationSection(key);
				ItemStack item = offerSection.getItemStack("item");
				if (item != null) {
					// legacy: the amount was stored separately from the item
					item.setAmount(offerSection.getInt("amount", 1));
					if (offerSection.contains("attributes")) {
						String attributes = offerSection.getString("attributes");
						if (attributes != null && !attributes.isEmpty()) {
							item = NMSManager.getProvider().loadItemAttributesFromString(item, attributes);
						}
					}
				}
				int price = offerSection.getInt("cost");
				if (Utils.isEmpty(item) || price < 0) continue; // invalid offer
				offers.add(new PriceOffer(item, price));
			}
		}
		return offers;
	}
}
