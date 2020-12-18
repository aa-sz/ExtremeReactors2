/*
 *
 * TurbineChargingPortEntity.java
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

package it.zerono.mods.extremereactors.gamecontent.multiblock.turbine.part;

import it.zerono.mods.extremereactors.gamecontent.Content;
import it.zerono.mods.extremereactors.gamecontent.multiblock.common.container.ChargingPortContainer;
import it.zerono.mods.extremereactors.gamecontent.multiblock.common.part.powertap.chargingport.IChargingPort;
import it.zerono.mods.extremereactors.gamecontent.multiblock.common.part.powertap.chargingport.IChargingPortHandler;
import it.zerono.mods.zerocore.lib.energy.EnergySystem;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.INamedContainerProvider;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;

public class TurbineChargingPortEntity
        extends AbstractTurbinePowerTapEntity
        implements IChargingPort, INamedContainerProvider {

    public TurbineChargingPortEntity(final EnergySystem system, final TileEntityType<?> entityType) {

        super(system, entityType);
        this.setHandler(IChargingPortHandler.create(system, this));
    }

    //region IChargingPort

    @Override
    public IChargingPortHandler getChargingPortHandler() {
        return (IChargingPortHandler)this.getPowerTapHandler();
    }

    //endregion
    //region ISyncableEntity

    @Override
    public void syncDataFrom(CompoundNBT data, SyncReason syncReason) {

        super.syncDataFrom(data, syncReason);
        this.getChargingPortHandler().syncDataFrom(data, syncReason);
    }

    @Override
    public CompoundNBT syncDataTo(CompoundNBT data, SyncReason syncReason) {

        super.syncDataTo(data, syncReason);
        this.getChargingPortHandler().syncDataTo(data, syncReason);
        return data;
    }

    //endregion
    //region INamedContainerProvider

    /**
     * Create the SERVER-side container for this TileEntity
     * @param windowId  the window id
     * @param inventory the player inventory
     * @param player    the player
     * @return the container to use on the server
     */
    @Nullable
    @Override
    public Container createMenu(final int windowId, final PlayerInventory inventory, final PlayerEntity player) {
        return new ChargingPortContainer<>(windowId, Content.ContainerTypes.TURBINE_CHARGINGPORT.get(), inventory, this);
    }

    //endregion
    //region AbstractModBlockEntity

    /**
     * Check if the tile entity has a GUI or not
     * Override in derived classes to return true if your tile entity got a GUI
     *
     * @param world
     * @param position
     * @param state
     */
    @Override
    public boolean canOpenGui(World world, BlockPos position, BlockState state) {
        return true;
    }

    //endregion
}
