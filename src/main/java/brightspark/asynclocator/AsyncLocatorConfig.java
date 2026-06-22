package brightspark.asynclocator;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.Builder;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;

/**
 * Server-side configuration for AsyncLocator.
 *
 * <p>Each async-locate feature can be independently disabled. If the corresponding flag is
 * {@code false}, that interception path falls through to vanilla synchronous behaviour. This is
 * the primary lever for isolating performance issues or compatibility problems with other mods —
 * disable the offending feature without disabling the whole mod.</p>
 */
public final class AsyncLocatorConfig {
	public static final ForgeConfigSpec SPEC;

	// Pool sizing
	public static final ConfigValue<Integer> LOCATOR_THREADS;

	// Merchant offer handling
	public static final ConfigValue<Boolean> REMOVE_OFFER;

	// Feature toggles
	public static final ConfigValue<Boolean> DOLPHIN_TREASURE_ENABLED;
	public static final ConfigValue<Boolean> EYE_OF_ENDER_ENABLED;
	public static final ConfigValue<Boolean> EXPLORATION_MAP_ENABLED;
	public static final ConfigValue<Boolean> LOCATE_COMMAND_ENABLED;
	public static final ConfigValue<Boolean> VILLAGER_TRADE_ENABLED;

	static {
		Builder builder = new Builder();
		builder.push("General");

		LOCATOR_THREADS = builder
			.worldRestart()
			.comment(
				"Maximum number of threads in the async locator thread pool.",
				"There's no upper bound, but only raise this if you're seeing simultaneous structure",
				"lookups cause issues AND your hardware has spare cores to handle the extra threads.",
				"Default of 1 is suitable for the vast majority of servers."
			)
			.defineInRange("asyncLocatorThreads", 1, 1, Integer.MAX_VALUE);

		REMOVE_OFFER = builder
			.comment(
				"When a merchant's treasure-map offer fails to find a destination, controls whether",
				"the offer is removed entirely or just marked out-of-stock. Out-of-stock allows the",
				"villager to potentially restock the offer later (vanilla behaviour); removal makes",
				"the failure permanent."
			)
			.define("removeMerchantInvalidMapOffer", false);

		builder.pop();

		builder.push("Features");
		builder.comment(
			"Per-feature toggles. Each one independently enables/disables the async-locate path for",
			"a specific game event. Disabling a feature falls back to vanilla synchronous behaviour",
			"for that path; the rest of the mod continues to operate normally."
		);

		DOLPHIN_TREASURE_ENABLED = builder
			.comment("Async locate when a dolphin starts searching for treasure.")
			.define("dolphinTreasureEnabled", true);

		EYE_OF_ENDER_ENABLED = builder
			.comment("Async locate when a player throws an Eye of Ender.")
			.define("eyeOfEnderEnabled", true);

		EXPLORATION_MAP_ENABLED = builder
			.comment("Async locate when a treasure / exploration map is generated from a loot table.")
			.define("explorationMapEnabled", true);

		LOCATE_COMMAND_ENABLED = builder
			.comment("Async locate when a player or the server runs the /locate command.")
			.define("locateCommandEnabled", true);

		VILLAGER_TRADE_ENABLED = builder
			.comment("Async locate when a cartographer villager generates a treasure-map trade offer.")
			.define("villagerTradeEnabled", true);

		builder.pop();

		SPEC = builder.build();
	}

	private AsyncLocatorConfig() {}
}
