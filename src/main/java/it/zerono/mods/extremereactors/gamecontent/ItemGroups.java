/*
 *
 * ItemGroups.java
 *
 * This file is part of Extreme Reactors 2 by ZeroNoRyouki, a Minecraft mod.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 *
 * DO NOT REMOVE OR EDIT THIS HEADER
 *
 */

package it.zerono.mods.extremereactors.gamecontent;

import com.google.common.collect.ImmutableList;
import it.zerono.mods.extremereactors.ExtremeReactors;
import it.zerono.mods.zerocore.lib.item.ItemHelper;
import it.zerono.mods.zerocore.lib.item.ModItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IItemProvider;

import java.util.function.Supplier;

public final class ItemGroups {

    public static final ItemGroup GENERAL = new ModItemGroup(ExtremeReactors.MOD_ID + ".general",
            () -> stack(Content.Items.YELLORITE_ORE_BLOCK),
            () -> ImmutableList.of(
                    stack(Content.Blocks.YELLORITE_ORE_BLOCK), stack(Content.Blocks.ANGLESITE_ORE_BLOCK), stack(Content.Blocks.BENITOITE_ORE_BLOCK),
                    stack(Content.Items.YELLORIUM_INGOT), stack(Content.Items.YELLORIUM_DUST), stack(Content.Blocks.YELLORIUM_BLOCK),
                    stack(Content.Items.CYANITE_INGOT), stack(Content.Items.CYANITE_DUST), stack(Content.Blocks.CYANITE_BLOCK),
                    stack(Content.Items.GRAPHITE_INGOT), stack(Content.Items.GRAPHITE_DUST), stack(Content.Blocks.GRAPHITE_BLOCK),
                    stack(Content.Items.ANGLESITE_CRYSTAL), stack(Content.Items.BENITOITE_CRYSTAL),
                    stack(Content.Items.WRENCH)
            ));

    public static final ItemGroup REACTOR = new ModItemGroup(ExtremeReactors.MOD_ID + ".reactor",
            () -> stack(Content.Blocks.REACTOR_FUELROD_BASIC),
            () -> ImmutableList.of(
                    stack(Content.Blocks.REACTOR_CONTROLLER_BASIC), stack(Content.Blocks.REACTOR_CASING_BASIC),
                    stack(Content.Blocks.REACTOR_GLASS_BASIC), stack(Content.Blocks.REACTOR_FUELROD_BASIC),
                    stack(Content.Blocks.REACTOR_CONTROLROD_BASIC), stack(Content.Blocks.REACTOR_SOLID_ACCESSPORT_BASIC),
                    stack(Content.Blocks.REACTOR_POWERTAP_FE_ACTIVE_BASIC), stack(Content.Blocks.REACTOR_POWERTAP_FE_PASSIVE_BASIC),
                    stack(Content.Blocks.REACTOR_REDSTONEPORT_BASIC),

                    stack(Content.Blocks.REACTOR_CONTROLLER_REINFORCED), stack(Content.Blocks.REACTOR_CASING_REINFORCED),
                    stack(Content.Blocks.REACTOR_GLASS_REINFORCED), stack(Content.Blocks.REACTOR_FUELROD_REINFORCED),
                    stack(Content.Blocks.REACTOR_CONTROLROD_REINFORCED), stack(Content.Blocks.REACTOR_SOLID_ACCESSPORT_REINFORCED),
                    stack(Content.Blocks.REACTOR_POWERTAP_FE_ACTIVE_REINFORCED), stack(Content.Blocks.REACTOR_POWERTAP_FE_PASSIVE_REINFORCED),
                    stack(Content.Blocks.REACTOR_REDSTONEPORT_REINFORCED), stack(Content.Blocks.REACTOR_COMPUTERPORT_REINFORCED),
                    stack(Content.Blocks.REACTOR_COOLANTPORT_FORGE_ACTIVE_REINFORCED), stack(Content.Blocks.REACTOR_COOLANTPORT_FORGE_PASSIVE_REINFORCED)
                    //, stack(Content.Blocks.REACTOR_CREATIVECOOLANTPORT_REINFORCED),
            ));

    //region internals

    private static <T extends IItemProvider> ItemStack stack(final Supplier<T> supplier) {
        return ItemHelper.stackFrom(supplier);
    }

    //endregion
}
