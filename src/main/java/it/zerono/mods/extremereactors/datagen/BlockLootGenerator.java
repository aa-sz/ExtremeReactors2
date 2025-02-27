/*
 *
 * BlockLootGenerator.java
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

package it.zerono.mods.extremereactors.datagen;

import it.zerono.mods.extremereactors.ExtremeReactors;
import it.zerono.mods.extremereactors.gamecontent.Content;
import it.zerono.mods.zerocore.lib.datagen.provider.BaseBlockLootTableProvider;
import net.minecraft.data.DataGenerator;

public class BlockLootGenerator
        extends BaseBlockLootTableProvider {

    public BlockLootGenerator(DataGenerator dataGenerator) {
        super(dataGenerator);
    }

    //region IDataProvider

    @Override
    protected void generateTables() {

        this.addDrop(Content.Blocks.YELLORIUM_BLOCK, Content.Blocks.CYANITE_BLOCK, Content.Blocks.GRAPHITE_BLOCK,
                Content.Blocks.YELLORITE_ORE_BLOCK);

        this.addSilkDrop(Content.Blocks.ANGLESITE_ORE_BLOCK, Content.Items.ANGLESITE_CRYSTAL, 1, 3, Content.Blocks.ANGLESITE_ORE_BLOCK);
        this.addSilkDrop(Content.Blocks.BENITOITE_ORE_BLOCK, Content.Items.BENITOITE_CRYSTAL, 1, 3, Content.Blocks.BENITOITE_ORE_BLOCK);

        this.addDrop(Content.Blocks.REACTOR_CASING_BASIC, Content.Blocks.REACTOR_GLASS_BASIC,
                Content.Blocks.REACTOR_CONTROLLER_BASIC, Content.Blocks.REACTOR_FUELROD_BASIC,
                Content.Blocks.REACTOR_CONTROLROD_BASIC, Content.Blocks.REACTOR_SOLID_ACCESSPORT_BASIC,
                Content.Blocks.REACTOR_POWERTAP_FE_ACTIVE_BASIC,  Content.Blocks.REACTOR_POWERTAP_FE_PASSIVE_BASIC,
                Content.Blocks.REACTOR_CHARGINGPORT_FE_BASIC, Content.Blocks.REACTOR_REDSTONEPORT_BASIC);

        this.addDrop(Content.Blocks.REACTOR_CASING_REINFORCED, Content.Blocks.REACTOR_GLASS_REINFORCED,
                Content.Blocks.REACTOR_CONTROLLER_REINFORCED, Content.Blocks.REACTOR_FUELROD_REINFORCED,
                Content.Blocks.REACTOR_CONTROLROD_REINFORCED, Content.Blocks.REACTOR_SOLID_ACCESSPORT_REINFORCED,
                Content.Blocks.REACTOR_POWERTAP_FE_ACTIVE_REINFORCED,  Content.Blocks.REACTOR_POWERTAP_FE_PASSIVE_REINFORCED,
                Content.Blocks.REACTOR_CHARGINGPORT_FE_REINFORCED, Content.Blocks.REACTOR_CREATIVE_WATER_GENERATOR_REINFORCED,
                Content.Blocks.REACTOR_FLUIDPORT_FORGE_PASSIVE_REINFORCED, Content.Blocks.REACTOR_FLUIDTPORT_FORGE_ACTIVE_REINFORCED,
                Content.Blocks.REACTOR_REDSTONEPORT_REINFORCED, Content.Blocks.REACTOR_COMPUTERPORT_REINFORCED);

        this.addDrop(Content.Blocks.TURBINE_CASING_BASIC, Content.Blocks.TURBINE_GLASS_BASIC,
                Content.Blocks.TURBINE_CONTROLLER_BASIC, Content.Blocks.TURBINE_ROTORBEARING_BASIC,
                Content.Blocks.TURBINE_ROTORSHAFT_BASIC, Content.Blocks.TURBINE_ROTORBLADE_BASIC,
                Content.Blocks.TURBINE_POWERTAP_FE_ACTIVE_BASIC, Content.Blocks.TURBINE_POWERTAP_FE_PASSIVE_BASIC,
                Content.Blocks.TURBINE_CHARGINGPORT_FE_BASIC,
                Content.Blocks.TURBINE_FLUIDPORT_FORGE_ACTIVE_BASIC, Content.Blocks.TURBINE_FLUIDPORT_FORGE_PASSIVE_BASIC,
                Content.Blocks.TURBINE_CREATIVE_STEAM_GENERATOR_BASIC
                /*,
                Content.Blocks.TURBINE_REDSTONEPORT_BASIC*/);

        this.addDrop(Content.Blocks.TURBINE_CASING_REINFORCED, Content.Blocks.TURBINE_GLASS_REINFORCED,
                Content.Blocks.TURBINE_CONTROLLER_REINFORCED, Content.Blocks.TURBINE_ROTORBEARING_REINFORCED,
                Content.Blocks.TURBINE_ROTORSHAFT_REINFORCED, Content.Blocks.TURBINE_ROTORBLADE_REINFORCED,
                Content.Blocks.TURBINE_POWERTAP_FE_ACTIVE_REINFORCED, Content.Blocks.TURBINE_POWERTAP_FE_PASSIVE_REINFORCED,
                Content.Blocks.TURBINE_CHARGINGPORT_FE_REINFORCED,
                Content.Blocks.TURBINE_FLUIDPORT_FORGE_ACTIVE_REINFORCED, Content.Blocks.TURBINE_FLUIDPORT_FORGE_PASSIVE_REINFORCED,
                Content.Blocks.TURBINE_CREATIVE_STEAM_GENERATOR_REINFORCED,
                /*Content.Blocks.TURBINE_REDSTONEPORT_REINFORCED,*/ Content.Blocks.TURBINE_COMPUTERPORT_REINFORCED);
    }

    @Override
    public String getName() {
        return ExtremeReactors.MOD_NAME + " loot tables (blocks)";
    }

    //endregion
}
