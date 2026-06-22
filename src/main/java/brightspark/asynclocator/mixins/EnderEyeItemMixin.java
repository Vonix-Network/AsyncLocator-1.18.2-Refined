package brightspark.asynclocator.mixins;

import brightspark.asynclocator.AsyncLocatorConfig;
import brightspark.asynclocator.AsyncLocatorMod;
import brightspark.asynclocator.logic.EnderEyeItemLogic;
import net.minecraft.advancements.critereon.UsedEnderEyeTrigger;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.item.EnderEyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.feature.ConfiguredStructureFeature;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(EnderEyeItem.class)
public class EnderEyeItemMixin {
	/*
		Intercept EnderEyeItem#use call. When the feature is enabled, return BlockPos.ZERO as a
		placeholder (the real location is set later by the async task). When disabled, run the
		vanilla synchronous search so the eye still works.
	 */
	@Redirect(
		method = "use",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerLevel;findNearestMapFeature(Lnet/minecraft/tags/TagKey;Lnet/minecraft/core/BlockPos;IZ)Lnet/minecraft/core/BlockPos;"
		)
	)
	public BlockPos levelFindNearestMapFeature(
		ServerLevel serverlevel,
		TagKey<ConfiguredStructureFeature<?, ?>> pStructureTag,
		BlockPos pPos,
		int pRadius,
		boolean pSkipExistingChunks
	) {
		if (AsyncLocatorConfig.EYE_OF_ENDER_ENABLED.get()) {
			AsyncLocatorMod.logDebug("Intercepted EnderEyeItem#use call");
			return BlockPos.ZERO;
		}
		// Feature disabled - run vanilla synchronous lookup
		return serverlevel.findNearestMapFeature(pStructureTag, pPos, pRadius, pSkipExistingChunks);
	}

	// Start the async locate task here so we have the eye of ender entity for context
	@Inject(
		method = "use",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/projectile/EyeOfEnder;setItem(Lnet/minecraft/world/item/ItemStack;)V"
		),
		locals = LocalCapture.CAPTURE_FAILEXCEPTION
	)
	public void startAsyncLocateTask(
		Level pLevel,
		Player pPlayer,
		InteractionHand pHand,
		CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir,
		ItemStack itemstack,
		HitResult hitresult,
		ServerLevel serverlevel,
		BlockPos blockpos,
		EyeOfEnder eyeofender
	) {
		if (!AsyncLocatorConfig.EYE_OF_ENDER_ENABLED.get()) return;
		EnderEyeItemLogic.locateAsync(serverlevel, pPlayer, eyeofender, (EnderEyeItem) (Object) this);
	}

	@Redirect(
		method = "use",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/projectile/EyeOfEnder;signalTo(Lnet/minecraft/core/BlockPos;)V"
		)
	)
	public void eyeOfEnderSignalTo(EyeOfEnder eyeOfEnder, BlockPos blockpos) {
		if (!AsyncLocatorConfig.EYE_OF_ENDER_ENABLED.get())
			eyeOfEnder.signalTo(blockpos);
		// else: deferred until the async task completes
	}

	@Redirect(
		method = "use",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/advancements/critereon/UsedEnderEyeTrigger;trigger(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/core/BlockPos;)V"
		)
	)
	public void triggerUsedEnderEyeCriteria(UsedEnderEyeTrigger trigger, ServerPlayer player, BlockPos pos) {
		if (!AsyncLocatorConfig.EYE_OF_ENDER_ENABLED.get())
			trigger.trigger(player, pos);
		// else: deferred until the async task completes
	}

	@Redirect(
		method = "use",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/player/Player;awardStat(Lnet/minecraft/stats/Stat;)V"
		)
	)
	public void playerAwardStat(Player player, Stat<?> pStat) {
		if (!AsyncLocatorConfig.EYE_OF_ENDER_ENABLED.get())
			player.awardStat(pStat);
		// else: deferred until the async task completes
	}
}
