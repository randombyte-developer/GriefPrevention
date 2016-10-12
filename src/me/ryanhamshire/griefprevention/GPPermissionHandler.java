/*
 * This file is part of GriefPrevention, licensed under the MIT License (MIT).
 *
 * Copyright (c) bloodmc
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package me.ryanhamshire.griefprevention;

import me.ryanhamshire.griefprevention.claim.Claim;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.item.ItemBlock;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.Item;
import org.spongepowered.api.entity.living.Living;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.item.ItemType;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.service.context.Context;
import org.spongepowered.api.util.Tristate;
import org.spongepowered.common.SpongeImplHooks;

import java.util.HashSet;
import java.util.Set;

public class GPPermissionHandler {

    public static Tristate getClaimPermission(Claim claim, String flagPermission, Object source, Object target, User user) {
        if (claim == null) {
            return Tristate.UNDEFINED;
        }
        if (user != null) {
            PlayerData playerData = GriefPrevention.instance.dataStore.getPlayerData(claim.world, user.getUniqueId());
            if (playerData.ignoreClaims) {
                return Tristate.TRUE;
            }
        }

        String sourceId = null;
        if (source != null) {
            if (source instanceof Entity) {
                Entity sourceEntity = (Entity) source;
                sourceId = sourceEntity.getType().getId();
                if (sourceEntity instanceof Item) {
                    sourceId = ((Item) sourceEntity).getItemType().getId();
                }
            } else if (source instanceof BlockType) {
                sourceId = ((BlockType) source).getId();
            } else if (source instanceof BlockState) {
                BlockState sourceBlock = (BlockState) source;
                sourceId = sourceBlock.getId().replace("[", ".[");
            } else if (source instanceof ItemStack) {
                ItemStack itemstack = (ItemStack) source;
                sourceId = itemstack.getItem().getId();
            } else if (source instanceof PluginContainer) {
                sourceId = ((PluginContainer) source).getId();
            }
        }

        String targetPermission = flagPermission;
        if (target != null) {
            if (target instanceof Entity) {
                Entity targetEntity = (Entity) target;
                net.minecraft.entity.Entity mcEntity = (net.minecraft.entity.Entity) targetEntity;
                String targetId = "";
                if (targetEntity.getType() != null) {
                    targetId = targetEntity.getType().getId();
                }
                // Workaround for pixelmon using same class for most entities.
                // In this circumstance, we will use the entity name instead
                if (targetId.equals("pixelmon:pixelmon")) {
                    targetId = "pixelmon:" + mcEntity.getName().toLowerCase();
                }
                if (targetEntity instanceof Living) {
                    for (EnumCreatureType type : EnumCreatureType.values()) {
                        if (SpongeImplHooks.isCreatureOfType(mcEntity, type)) {
                            String[] parts = targetId.split(":");
                            targetId =  parts[0] + "." + GPFlags.SPAWN_TYPES.inverse().get(type) + "." + parts[1];
                            break;
                        }
                    }
                }

                targetPermission += "." + targetId.toLowerCase();
                if (targetEntity instanceof Item) {
                    targetId = ((Item) targetEntity).getItemType().getId();
                }
            } else if (target instanceof BlockType) {
                targetPermission += "." + ((BlockType) target).getId();
            } else if (target instanceof BlockState) {
                BlockState targetBlock = (BlockState) target;
                String targetId = targetBlock.getId().replace("[", ".[");
                targetPermission += "." + targetId.toLowerCase();
            } else if (target instanceof ItemStack) {
                ItemStack itemstack = (ItemStack) target;
                String targetId = "";
                if (itemstack.getItem() instanceof ItemBlock) {
                    ItemBlock itemBlock = (ItemBlock) itemstack.getItem();
                    net.minecraft.item.ItemStack nmsStack = (net.minecraft.item.ItemStack)(Object) itemstack;
                    BlockState blockState = ((BlockState) itemBlock.getBlock().getStateFromMeta(nmsStack.getItemDamage()));
                    targetId = blockState.getId().replace("[", ".[");
                } else {
                    targetId = itemstack.getItem().getId();
                }
                targetPermission += "." + targetId.toLowerCase();
            } else if (target instanceof ItemType) {
                targetPermission += "." + ((ItemType) target).getId().toLowerCase();
            } else if (target instanceof String) {
                targetPermission += "." + target;
            }
        }

        // first check source for deny
        Context sourceContext = GriefPrevention.CUSTOM_CONTEXTS.get(sourceId);

        targetPermission = targetPermission.replace(":", ".");
        // First check for claim flag overrides
        Tristate override = getFlagOverride(claim, targetPermission);
        if (override != Tristate.UNDEFINED) {
            return override;
        }

        if (user != null) {
            if (claim.hasFullAccess(user)) {
                return Tristate.TRUE;
            }
            Tristate value = Tristate.UNDEFINED;
            if (sourceContext != null) {
                value = getUserPermission(user, claim, targetPermission, sourceContext);
                if (value == Tristate.FALSE) {
                    return value;
                }
            }

            value = getUserPermission(user, claim, targetPermission, null);
            if (value != Tristate.UNDEFINED) {
                return value;
            }
        }

        if (sourceContext != null && getClaimFlagPermission(claim, targetPermission, sourceContext) == Tristate.FALSE) {
            return Tristate.FALSE;
        }

        return getClaimFlagPermission(claim, targetPermission, null);
    }

    public static Tristate getUserPermission(User user, Claim claim, String permission, Context sourceContext) {
        Set<Context> contexts = new HashSet<>();
        contexts.add(claim.getContext());

        if (sourceContext != null) {
            contexts.add(sourceContext);
        }

        Tristate value = user.getPermissionValue(contexts, permission);
        if (value == Tristate.UNDEFINED && claim.parent != null && claim.inheritParent) {
            // check subdivision's parent
            contexts.remove(claim.getContext());
            contexts.add(claim.parent.getContext());
            return user.getPermissionValue(contexts, permission);
        }

        return value;
    }

    // helper method for boolean flags that have no targets
    public static Tristate getClaimBooleanFlagPermission(Claim claim, String permission) {
        // First check for claim flag overrides
        Tristate value = getFlagOverride(claim, permission);
        if (value != Tristate.UNDEFINED) {
            return value;
        }

        return getClaimFlagPermission(claim, permission, null);
    }

    public static Tristate getClaimFlagPermission(Claim claim, String permission, Context sourceContext) {
        Tristate value = Tristate.UNDEFINED;
        // Strip base permission node to keep it simpler locally
        Set<Context> contexts = new HashSet<>();
        if (sourceContext != null) {
            contexts.add(sourceContext);
        }

        contexts.add(claim.getContext());
        // This is required since permissions will check each context separately
        if (!GriefPrevention.GLOBAL_SUBJECT.getSubjectData().getPermissions(contexts).isEmpty() || !GriefPrevention.GLOBAL_SUBJECT.getSubjectData().getParents(contexts).isEmpty()) {
            if (claim.parent != null && claim.inheritParent) {
                // first check subdivision context
                value = GriefPrevention.GLOBAL_SUBJECT.getPermissionValue(contexts, permission);
                if (value != Tristate.UNDEFINED) {
                    return value;
                }
                // check parent context
                contexts.remove(claim.getContext());
                contexts.add(claim.parent.getContext());
                value = GriefPrevention.GLOBAL_SUBJECT.getPermissionValue(contexts, permission);
                if (value != Tristate.UNDEFINED) {
                    return value;
                }
            } else {
                // check parent
                value = GriefPrevention.GLOBAL_SUBJECT.getPermissionValue(contexts, permission);
                if (value != Tristate.UNDEFINED) {
                    return value;
                }
            }
        }

        if (sourceContext != null) {
            return Tristate.UNDEFINED;
        }
        // Fallback to defaults
        contexts = new HashSet<>();
        if (claim.isAdminClaim()) {
            contexts.add(GriefPrevention.ADMIN_CLAIM_FLAG_DEFAULT_CONTEXT);
        } else if (claim.isBasicClaim() || claim.isSubdivision()) {
            contexts.add(GriefPrevention.BASIC_CLAIM_FLAG_DEFAULT_CONTEXT);
        } else { // wilderness
            contexts.add(GriefPrevention.WILDERNESS_CLAIM_FLAG_DEFAULT_CONTEXT);
        }

        contexts.add(claim.world.getContext());
        // check persisted default data
        if (!GriefPrevention.GLOBAL_SUBJECT.getSubjectData().getPermissions(contexts).isEmpty() || !GriefPrevention.GLOBAL_SUBJECT.getSubjectData().getParents(contexts).isEmpty()) {
            value = GriefPrevention.GLOBAL_SUBJECT.getPermissionValue(contexts, permission);
            if (value != Tristate.UNDEFINED) {
                return value;
            }
        }

        // finally, check non-persisted default data
        if (!GriefPrevention.GLOBAL_SUBJECT.getTransientSubjectData().getPermissions(contexts).isEmpty() || !GriefPrevention.GLOBAL_SUBJECT.getTransientSubjectData().getParents(contexts).isEmpty()) {
            value = GriefPrevention.GLOBAL_SUBJECT.getPermissionValue(contexts, permission);
            if (value != Tristate.UNDEFINED) {
                return value;
            }
        }

        return Tristate.UNDEFINED;
    }

    public static Tristate getFlagOverride(Claim claim, String permission) {
        // First check for claim flag overrides
        if (!claim.isWildernessClaim()) {
            Set<Context> contexts = new HashSet<>();
            if (claim.isAdminClaim()) {
                contexts.add(GriefPrevention.ADMIN_CLAIM_FLAG_OVERRIDE_CONTEXT);
            } else {
                contexts.add(GriefPrevention.BASIC_CLAIM_FLAG_OVERRIDE_CONTEXT);
            }

            contexts.add(claim.world.getContext());
            if (!GriefPrevention.GLOBAL_SUBJECT.getSubjectData().getPermissions(contexts).isEmpty() || !GriefPrevention.GLOBAL_SUBJECT.getSubjectData().getParents(contexts).isEmpty()) {
                Tristate override = GriefPrevention.GLOBAL_SUBJECT.getPermissionValue(contexts, permission);
                if (override != Tristate.UNDEFINED) {
                    return override;
                }
            }
        }

        return Tristate.UNDEFINED;
    }

    public static String getBlockStateIdWithMeta(BlockState state) {
        String blockTypeId = state.getType().getId();
        Block block = (net.minecraft.block.Block) state.getType();
        int meta = block.getMetaFromState((IBlockState)state);
        return blockTypeId + ":" + meta;
    }

    public static String getBlockStateId(BlockState state) {
        String blockTypeId = state.getType().getId();
        return blockTypeId;
    }
}
