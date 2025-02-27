/*
 *
 * MultiblockReactor.java
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

package it.zerono.mods.extremereactors.gamecontent.multiblock.reactor;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import it.zerono.mods.extremereactors.ExtremeReactors;
import it.zerono.mods.extremereactors.Log;
import it.zerono.mods.extremereactors.api.radiation.RadiationPacket;
import it.zerono.mods.extremereactors.api.reactor.*;
import it.zerono.mods.extremereactors.api.reactor.radiation.EnergyConversion;
import it.zerono.mods.extremereactors.api.reactor.radiation.IRadiationModerator;
import it.zerono.mods.extremereactors.api.reactor.radiation.IrradiationData;
import it.zerono.mods.extremereactors.gamecontent.multiblock.common.*;
import it.zerono.mods.extremereactors.gamecontent.multiblock.common.part.powertap.IPowerTap;
import it.zerono.mods.extremereactors.gamecontent.multiblock.common.part.powertap.IPowerTapHandler;
import it.zerono.mods.extremereactors.gamecontent.multiblock.reactor.client.ClientFuelRodsLayout;
import it.zerono.mods.extremereactors.gamecontent.multiblock.reactor.part.*;
import it.zerono.mods.extremereactors.gamecontent.multiblock.reactor.variant.IMultiblockReactorVariant;
import it.zerono.mods.zerocore.lib.CodeHelper;
import it.zerono.mods.zerocore.lib.IDebugMessages;
import it.zerono.mods.zerocore.lib.IDebuggable;
import it.zerono.mods.zerocore.lib.block.ModBlock;
import it.zerono.mods.zerocore.lib.block.multiblock.IMultiblockPartTypeProvider;
import it.zerono.mods.zerocore.lib.data.IoDirection;
import it.zerono.mods.zerocore.lib.data.IteratorTracker;
import it.zerono.mods.zerocore.lib.data.stack.AllowedHandlerAction;
import it.zerono.mods.zerocore.lib.data.stack.OperationMode;
import it.zerono.mods.zerocore.lib.energy.EnergyBuffer;
import it.zerono.mods.zerocore.lib.multiblock.IMultiblockController;
import it.zerono.mods.zerocore.lib.multiblock.IMultiblockPart;
import it.zerono.mods.zerocore.lib.multiblock.ITickableMultiblockPart;
import it.zerono.mods.zerocore.lib.multiblock.cuboid.AbstractCuboidMultiblockPart;
import it.zerono.mods.zerocore.lib.multiblock.validation.IMultiblockValidator;
import it.zerono.mods.zerocore.lib.world.WorldHelper;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.profiler.IProfiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.LogicalSide;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MultiblockReactor
        extends AbstractGeneratorMultiblockController<MultiblockReactor, IMultiblockReactorVariant>
        implements IReactorMachine, IReactorEnvironment, IReactorWriter, IDebuggable {

    public MultiblockReactor(final World world, final IMultiblockReactorVariant variant) {

        super(world);
        this._variant = variant;

        this._fuelContainer = new FuelContainer();
        this._fluidContainer = new FluidContainer(FLUID_CONTAINER_ACCESS);
        this._fuelHeat = new Heat();
        this._reactorHeat = new Heat();
        this._fuelRodsLayout = FuelRodsLayout.DEFAULT;
        this._uiStats = new Stats(this._fuelContainer);

        this._active = false;
        this._mode = OperationalMode.Passive;
        this._wasteEjectionSetting = WasteEjectionSetting.Automatic;
        this._reactorVolume = 0;
        this._fuelToReactorHeatTransferCoefficient = 0f;
        this._reactorToCoolantSystemHeatTransferCoefficient = 0f;
        this._reactorHeatLossCoefficient = 0f;

        this._attachedTickables = Sets.newHashSet();
        this._attachedControlRods = Lists.newArrayList();
        this._attachedFuelRods = Sets.newHashSet();
        this._attachedSolidAccessPorts = Sets.newHashSet();
        this._attachedPowerTaps = Sets.newHashSet();
        this._attachedFluidPorts = Sets.newHashSet();
        this._attachedOutgoingFluidPorts = Sets.newHashSet();

        this._irradiationSourceTracker = new IteratorTracker<>(this._attachedFuelRods::iterator);
        this._logic = new ReactorLogic(this, this.getEnergyBuffer());
    }

    /**
     * Reset the internal data
     * --- FOR TESTING PURPOSES ONLY ---
     */
    public void reset() {

        this.setMachineActive(false);
        this._fuelContainer.reset();
        this._fluidContainer.reset();
        this._fuelHeat.set(0);
        this._reactorHeat.set(0);
        this._uiStats.setAmountGeneratedLastTick(0);
        this._uiStats.setFuelConsumedLastTick(0);
        this._fuelToReactorHeatTransferCoefficient = 0f;
        this._reactorToCoolantSystemHeatTransferCoefficient = 0f;
        this._reactorHeatLossCoefficient = 0f;
        this._irradiationSourceTracker.reset();
        this._logic.reset();
        this.getEnergyBuffer().setEnergyStored(0);

        this.resizeFuelContainer();
        this.calculateReactorVolume();
        this.updateFuelToReactorHeatTransferCoefficient();
        this.updateReactorToCoolantSystemHeatTransferCoefficient();
        this.updateReactorHeatLossCoefficient();
        this.resizeFluidContainer();
    }

    public Optional<ReactorControlRodEntity> getControlRodByIndex(int index) {

        if (index < 0 || index > this.getControlRodsCount()) {
            return Optional.empty();
        } else {
            return Optional.of(this._attachedControlRods.get(index));
        }
    }

    public void onFluidPortChanged() {
        this.rebuildOutgoingFluidPorts();
    }

    //region active-coolant system

    @Override
    public Optional<IFluidHandler> getLiquidHandler() {
        return this.getFluidHandler(IoDirection.Input);
    }

    @Override
    public Optional<IFluidHandler> getGasHandler() {
        return this.getFluidHandler(IoDirection.Output);
    }

    @Override
    public Optional<IFluidHandler> getFluidHandler(final IoDirection portDirection) {
        return this.getOperationalMode().isActive() ? Optional.of(this._fluidContainer.getWrapper(portDirection)) : Optional.empty();
    }

    //endregion
    //region IActivableMachine

    /**
     * @return true if the machine is active, false otherwise
     */
    @Override
    public boolean isMachineActive() {
        return this._active;
    }

    /**
     * Change the state of the machine
     *
     * @param active if true, activate the machine; if false, deactivate it
     */
    @Override
    public void setMachineActive(boolean active) {

        if (this.isMachineActive() == active) {
            return;
        }

        this._active = active;

        if (active) {
            this.getConnectedParts().forEach(IMultiblockPart::onMachineActivated);
        } else {
            this.getConnectedParts().forEach(IMultiblockPart::onMachineDeactivated);
        }

        this.callOnLogicalServer(this::markReferenceCoordForUpdate);
    }

    //endregion
    //region IReactorMachine

    @Override
    public IReactorEnvironment getEnvironment() {
        return this;
    }

    @Override
    public IHeat getFuelHeat() {
        return this._fuelHeat;
    }

    @Override
    public IFuelContainer getFuelContainer() {
        return this._fuelContainer;
    }

    @Override
    public IFluidContainer getFluidContainer() {
        return this._fluidContainer;
    }

    @Override
    public Stats getUiStats() {
        return this._uiStats;
    }

    /**
     * Perform a refueling cycle, ejecting waste and inserting new fuel into the Reactor
     */
    @Override
    public void performRefuelingCycle() {

        if (this.getWasteEjectionMode().isAutomatic()) {
            this.ejectWaste(false);
        }

        //TODO liquid fuel (do it first, so solid fuel could be used as a backup)

        this.refuelSolid();
    }

    /**
     * Output power/gas to active ports
     */
    @Override
    public void performOutputCycle() {

        final IProfiler profiler = this.getWorld().getProfiler();

        if (this.getOperationalMode().isPassive()) {

            // Distribute available power equally to all the Power Taps
            profiler.startSection("Power");
            this.distributeEnergyEqually();

        } else {

            // Distribute available gas equally to all the Coolant Ports in output mode
            profiler.startSection("Gas");
            this.distributeGasEqually();
        }

        profiler.endSection();
    }

    //endregion
    //region IReactorEnvironment

    @Override
    public boolean isSimulator() {
        return false;
    }

    @Override
    public IHeat getReactorHeat() {
        return this._reactorHeat;
    }

    @Override
    public int getReactorVolume() {
        return this._reactorVolume;
    }

    @Override
    public float getFuelToReactorHeatTransferCoefficient() {
        return this._fuelToReactorHeatTransferCoefficient;
    }

    @Override
    public float getReactorToCoolantSystemHeatTransferCoefficient() {
        return this._reactorToCoolantSystemHeatTransferCoefficient;
    }

    @Override
    public float getReactorHeatLossCoefficient() {
        return this._reactorHeatLossCoefficient;
    }

    @Override
    public Optional<IIrradiationSource> getNextIrradiationSource() {
        return this._irradiationSourceTracker.next()
                .map(s -> s);
    }

    @SuppressWarnings("unchecked")
    @Override
    public int getPartsCount(ReactorPartType type) {

        switch (type) {

            case ControlRod:
                return this._attachedControlRods.size();

            case FuelRod:
                return this._attachedFuelRods.size();

            case SolidAccessPort:
                return this._attachedSolidAccessPorts.size();

            case ActivePowerTapFE:
            case PassivePowerTapFE:
            case ChargingPortFE:
                return this._attachedPowerTaps.size();

            case ActiveFluidPortForge:
            case PassiveFluidPortForge:
                return this._attachedFluidPorts.size();

            default:
                return this.getPartsCount(part -> part instanceof IMultiblockPartTypeProvider &&
                        ((IMultiblockPartTypeProvider<MultiblockReactor, ReactorPartType>)part).getPartType()
                                .filter(partType -> partType == type)
                                .isPresent());
        }
    }


    /**
     * Get a Moderator from the Reactor internal volume
     *
     * @param position the position to look up. Must be inside the Reactor internal volume
     * @return the Moderator at the requested position, if the position is valid and a Moderator is found there
     */
    @Override
    public Optional<IRadiationModerator> getModerator(BlockPos position) {

        if (!this.containPosition(position)) {
            return Optional.empty();
        }

        final World world = this.getWorld();
        final BlockState blockState = world.getBlockState(position);

        if (blockState.hasTileEntity()) {

            return WorldHelper.getTile(world, position)
                    .filter(te -> te instanceof IRadiationModerator)
                    .map(te -> (IRadiationModerator)te);

        } else {

            final IRadiationModerator moderator;

            if (blockState.isAir()) {

                moderator = (data, packet) -> applyModerator(data, packet, Moderator.AIR);

            } else {

                moderator = (data, packet) -> applyModerator(data, packet, ModeratorsRegistry.getFrom(blockState).orElse(Moderator.AIR));
            }

            return Optional.of(moderator);
        }
    }

    private static void applyModerator(final IrradiationData data, final RadiationPacket radiation, final Moderator moderator) {

        final float radiationAbsorbed = radiation.intensity * moderator.getAbsorption() * (1f - radiation.hardness);

        radiation.intensity = Math.max(0f, radiation.intensity - radiationAbsorbed);
        radiation.hardness /= moderator.getModeration();
        data.environmentEnergyAbsorption += moderator.getHeatEfficiency() * radiationAbsorbed * EnergyConversion.ENERGY_PER_RADIATION_UNIT;
    }

    /**
     * Perform a refueling cycle (eject waste, push new fuel in, etc)
     */
    @Override
    public void refuel() {
        // ??????????????????????
    }

    //endregion
    //region IReactorReader

    @Override
    public OperationalMode getOperationalMode() {
        return this._mode;
    }

    @Override
    public int getFuelAmount() {
        return this._fuelContainer.getFuelAmount();
    }

    @Override
    public int getWasteAmount() {
        return this._fuelContainer.getWasteAmount();
    }

    @Override
    public int getCapacity() {

        if (this.calledByLogicalClient() && !this.isAssembled()) {
            return this.getFuelRodsCount() * ReactorFuelRodEntity.FUEL_CAPACITY_PER_FUEL_ROD;
        } else {
            return this._fuelContainer.getCapacity();
        }
    }

    @Override
    public Optional<Reactant> getFuel() {
        return this._fuelContainer.getFuel();
    }

    @Override
    public Optional<Reactant> getWaste() {
        return this._fuelContainer.getWaste();
    }

    @Override
    public float getFuelFertility() {
        return this._logic.getFertility();
    }

    @Override
    public Supplier<Double> getFuelHeatValue() {
        return this._fuelHeat;
    }

    @Override
    public Supplier<Double> getReactorHeatValue() {
        return this._reactorHeat;
    }

    @Override
    public WasteEjectionSetting getWasteEjectionMode() {
        return this._wasteEjectionSetting;
    }

    public Optional<FuelRodsLayout> getFuelRodsLayout() {
        return Optional.ofNullable(this._fuelRodsLayout);
    }

    @Override
    public int getFuelRodsCount() {
        return this._attachedFuelRods.size();
    }

    @Override
    public int getControlRodsCount() {
        return this._attachedControlRods.size();
    }

    @Override
    public int getPowerTapsCount() {
        return this._attachedPowerTaps.size();
    }

    //TODO used???
    public List<BlockPos> getControlRodLocations() {
        return this._attachedControlRods.stream()
                .map(IMultiblockPart::getWorldPosition)
                .collect(Collectors.toList());
    }

    //endregion
    //region IReactorWriter

    /**
     * Directly set the waste ejection setting
     *
     * @param newSetting the new waste ejection setting.
     */
    @Override
    public void setWasteEjectionMode(WasteEjectionSetting newSetting) {

        if (this.getWasteEjectionMode() != newSetting) {

            this._wasteEjectionSetting = newSetting;
            this.markReferenceCoordDirty();
        }
    }

    @Override
    public void setControlRodsInsertionRatio(final int newRatio) {
        if (this.isAssembled()) {
            ReactorControlRodEntity.setInsertionRatio(this._attachedControlRods, MathHelper.clamp(newRatio, 0, 100));
        }
    }

    @Override
    public void changeControlRodsInsertionRatio(int delta) {

        if (this.isAssembled()) {
            ReactorControlRodEntity.changeInsertionRatio(this._attachedControlRods, delta);
        }
    }

    /**
     * Attempt to eject fuel contained in the Reactor
     */
    @Override
    public void ejectFuel() {
        this.ejectFuel(false);
    }

    /**
     * Attempt to eject fuel contained in the Reactor
     *
     * @param voidLeftover if true, any remaining fuel will be voided
     */
    @Override
    public void ejectFuel(boolean voidLeftover) {

        if (ReactantHelper.ejectSolidReactant(ReactantType.Fuel, this._fuelContainer, voidLeftover, this.getInputSolidAccessPorts())) {

            this.markReferenceCoordForUpdate();
            this.markReferenceCoordDirty();
        }
    }

    /**
     * Attempt to eject fuel contained in the Reactor
     *
     * @param voidLeftover if true, any remaining fuel will be voided
     * @param portPosition coordinates of the Access Port to witch distribute fuel
     */
    @Override
    public void ejectFuel(boolean voidLeftover, BlockPos portPosition) {

        if (ReactantHelper.ejectSolidReactant(ReactantType.Fuel, this._fuelContainer, voidLeftover,
                this.getInputSolidAccessPorts().filter(port -> portPosition.equals(port.getWorldPosition())))) {

            this.markReferenceCoordForUpdate();
            this.markReferenceCoordDirty();
        }
    }

    /**
     * Attempt to eject waste contained in the Reactor
     */
    @Override
    public void ejectWaste() {
        this.ejectWaste(false);
    }

    /**
     * Attempt to eject waste contained in the Reactor
     *
     * @param voidLeftover if true, any remaining waste will be voided
     */
    @Override
    public void ejectWaste(boolean voidLeftover) {

        if (ReactantHelper.ejectSolidReactant(ReactantType.Waste, this._fuelContainer, voidLeftover,
                this.getOutputSolidAccessPorts())) {

            this.markReferenceCoordForUpdate();
            this.markReferenceCoordDirty();
        }
    }

    /**
     * Attempt to eject waste contained in the Reactor
     *
     * @param voidLeftover if true, any remaining waste will be voided
     * @param portPosition coordinates of the Access Port to witch distribute waste
     */
    @Override
    public void ejectWaste(boolean voidLeftover, BlockPos portPosition) {

        if (ReactantHelper.ejectSolidReactant(ReactantType.Waste, this._fuelContainer, voidLeftover,
                this.getOutputSolidAccessPorts().filter(port -> portPosition.equals(port.getWorldPosition())))) {

            this.markReferenceCoordForUpdate();
            this.markReferenceCoordDirty();
        }
    }

    //endregion
    //region ISyncableEntity

    /**
     * Sync the entity data from the given NBT compound
     *
     * @param data       the data
     * @param syncReason the reason why the synchronization is necessary
     */
    @Override
    public void syncDataFrom(CompoundNBT data, SyncReason syncReason) {

//        Log.LOGGER.info("REACTOR SYNC-FROM - {} on {}", syncReason, Thread.currentThread().getName());

        super.syncDataFrom(data, syncReason);

        if (data.contains("active")) {
            this._active = data.getBoolean("active");
        }

        if (data.contains("wasteeject")) {
            this._wasteEjectionSetting = WasteEjectionSetting.read(data, "wasteeject", WasteEjectionSetting.Automatic);
        }

        this._logic.syncDataFrom(data, syncReason);

        this.syncChildDataEntityFrom(this._fuelContainer, "fuelcontainer", data, syncReason);
        this.syncChildDataEntityFrom(this._fluidContainer, "fluidcontainer", data, syncReason);
        this.syncChildDataEntityFrom(this._fuelHeat, "fuelheat", data, syncReason);
        this.syncChildDataEntityFrom(this._reactorHeat, "reactorheat", data, syncReason);

        if (syncReason.isNetworkUpdate()) {

            this.syncChildDataEntityFrom(this._uiStats, "stats", data, syncReason);
            this.onClientFuelStatusChanged();
        }
    }

    /**
     * Sync the entity data to the given NBT compound
     *
     * @param data       the data
     * @param syncReason the reason why the synchronization is necessary
     */
    @Override
    public CompoundNBT syncDataTo(CompoundNBT data, SyncReason syncReason) {

        super.syncDataTo(data, syncReason);

        data.putBoolean("active", this.isMachineActive());
        WasteEjectionSetting.write(data, "wasteeject", this.getWasteEjectionMode());

        this._logic.syncDataTo(data, syncReason);

        this.syncChildDataEntityTo(this._fuelContainer, "fuelcontainer", data, syncReason);
        this.syncChildDataEntityTo(this._fluidContainer, "fluidcontainer", data, syncReason);
        this.syncChildDataEntityTo(this._fuelHeat, "fuelheat", data, syncReason);
        this.syncChildDataEntityTo(this._reactorHeat, "reactorheat", data, syncReason);

        if (syncReason.isNetworkUpdate()) {
            this.syncChildDataEntityTo(this._uiStats, "stats", data, syncReason);
        }

        return data;
    }

    //endregion
    //region AbstractGeneratorMultiblockController

    @Override
    public IMultiblockReactorVariant getVariant() {
        return this._variant;
    }

    @Override
    protected void sendClientUpdates() {

        final IProfiler profiler = this.getWorld().getProfiler();

        profiler.startSection("updateFuelAssembliesQuota");
        this.updateFuelRodsLayout();
        profiler.endStartSection("sendTickUpdate");
        this.sendUpdates();
        profiler.endSection();
    }

    //endregion
    //region AbstractMultiblockController

    @Override
    public boolean isPartCompatible(final IMultiblockPart<MultiblockReactor> part) {
        return (part instanceof AbstractReactorEntity) &&
                ((AbstractReactorEntity) part).getMultiblockVariant()
                        .filter(variant -> this.getVariant() == variant)
                        .isPresent();
    }

    /**
     * Called when a new part is added to the machine. Good time to register things into lists.
     *
     * @param newPart The part being added.
     */
    @Override
    protected void onPartAdded(IMultiblockPart<MultiblockReactor> newPart) {

        if (newPart instanceof ITickableMultiblockPart) {
            this._attachedTickables.add((ITickableMultiblockPart) newPart);
        }

        if (newPart instanceof ReactorControlRodEntity) {
            this._attachedControlRods.add((ReactorControlRodEntity) newPart);
        } else if (newPart instanceof ReactorFuelRodEntity) {
            this._attachedFuelRods.add((ReactorFuelRodEntity) newPart);
        } else if (newPart instanceof ReactorSolidAccessPortEntity) {
            this._attachedSolidAccessPorts.add((ReactorSolidAccessPortEntity) newPart);
        } else if (newPart instanceof ReactorPowerTapEntity || newPart instanceof ReactorChargingPortEntity) {
            this._attachedPowerTaps.add((IPowerTap) newPart);
        } else if (newPart instanceof ReactorFluidPortEntity) {
            this._attachedFluidPorts.add((ReactorFluidPortEntity) newPart);
        }
    }

    /**
     * Called when a part is removed from the machine. Good time to clean up lists.
     *
     * @param oldPart The part being removed.
     */
    @Override
    protected void onPartRemoved(IMultiblockPart<MultiblockReactor> oldPart) {

        if (oldPart instanceof ITickableMultiblockPart) {
            this._attachedTickables.remove(oldPart);
        }

        if (oldPart instanceof ReactorControlRodEntity) {
            this._attachedControlRods.remove(oldPart);
        } else if (oldPart instanceof ReactorFuelRodEntity) {
            this._attachedFuelRods.remove(oldPart);
        } else if (oldPart instanceof ReactorSolidAccessPortEntity) {
            this._attachedSolidAccessPorts.remove(oldPart);
        } else if (oldPart instanceof ReactorPowerTapEntity || oldPart instanceof ReactorChargingPortEntity) {
            this._attachedPowerTaps.remove(oldPart);
        } else if (oldPart instanceof ReactorFluidPortEntity) {
            this._attachedFluidPorts.remove(oldPart);
        }
    }

    /**
     * Called when a machine is assembled from a disassembled state.
     */
    @Override
    protected void onMachineAssembled() {

        // set the output EnergySystem
        if (this._attachedPowerTaps.isEmpty()) {
            this.setOutputEnergySystem(INTERNAL_ENERGY_SYSTEM);
        } else {
            CodeHelper.optionalIfPresentOrThrow(this._attachedPowerTaps.stream()
                            .map(IPowerTap::getPowerTapHandler)
                            .map(IPowerTapHandler::getEnergySystem)
                            .findFirst(),
                    this::setOutputEnergySystem);
        }

        // operation mode : if any coolant ports is present we are in active mode
        this._mode = this.isAnyPartConnected(part -> part instanceof ReactorFluidPortEntity) ?
                OperationalMode.Active : OperationalMode.Passive;

        // interior visible?
        this.setInteriorInvisible(!this.isAnyPartConnected(part -> part instanceof ReactorGlassEntity));

        // build a new fuel rods layout and link all the fuel rods to their control rods
        this._fuelRodsLayout = this.createFuelRodsLayout();
        this._attachedControlRods.forEach(rod -> rod.linkToFuelRods(this._fuelRodsLayout.getRodLength()));

        // set fuel rods occlusion status
        this._fuelRodsLayout.updateFuelRodsOcclusion(this._attachedFuelRods);

        // gather outgoing coolant ports
        this.rebuildOutgoingFluidPorts();

        // update internal data


        //resize energy buffer

        this.getEnergyBuffer().setCapacity(this.getVariant().getPartEnergyCapacity() * this.getPartsCount());
        this.getEnergyBuffer().setMaxExtract(this.getVariant().getMaxEnergyExtractionRate());

        //TODO check/fix

        this.resizeFuelContainer();
        this.calculateReactorVolume();
        this.updateFuelToReactorHeatTransferCoefficient();
        this.updateReactorToCoolantSystemHeatTransferCoefficient();
        this.updateReactorHeatLossCoefficient();
        this.resizeFluidContainer();

        // re-render the whole reactor
//        if (CodeHelper.calledByLogicalClient(this.getWorld())) {
//
//            // Make sure our fuel rods re-render
//            this.onClientFuelStatusChanged();
//            this.markMultiblockForRenderUpdate();
//
//        } else {
//
//            // Force an update of the client's multiblock information
//            this.markReferenceCoordForUpdate();
//        }
        this.callOnLogicalSide(
                this::markReferenceCoordForUpdate,
                () -> {
                    // Make sure our fuel rods re-render
                    this.onClientFuelStatusChanged();
                    this.markMultiblockForRenderUpdate();
                }
        );
    }

    /**
     * Called when a machine is disassembled from an assembled state.
     * This happens due to user or in-game actions (e.g. explosions)
     */
    @Override
    protected void onMachineDisassembled() {

        this.setMachineActive(false);

        // do not call setMachineActive() here
        this._active = false;

        this.markMultiblockForRenderUpdate();
    }

    @Override
    protected boolean isMachineWhole(IMultiblockValidator validatorCallback) {

        // Ensure that there is at least one Controller and one Control Rod attached

        if (this.getControlRodsCount() < 1) {

            validatorCallback.setLastError("multiblock.validation.reactor.too_few_rods");
            return false;
        }

        if (!this.isAnyPartConnected(part -> part instanceof ReactorControllerEntity)) {

            validatorCallback.setLastError("multiblock.validation.reactor.too_few_controllers");
            return false;
        }

        // Validate Control Rods orientation and Fuel Assemblies

        if (!this.validateFuelAssemblies(validatorCallback)) {
            return false;
        }

        // Check if the machine has a single power system

        if (!this.validateEnergySystems(validatorCallback)) {
            return false;
        }

        // Perform base checks...

        return super.isMachineWhole(validatorCallback);
    }

    /**
     * Callback. Called after this controller assimilates all the blocks
     * from another controller.
     * Use this to absorb that controller's game data.
     *
     * @param assimilated The controller whose uniqueness was added to our own.
     */
    @Override
    protected void onAssimilate(IMultiblockController<MultiblockReactor> assimilated) {

        if (!(assimilated instanceof MultiblockReactor)) {

            Log.LOGGER.warn(Log.REACTOR, "[{}] Reactor @ {} is attempting to assimilate a non-Reactor machine! That machine's data will be lost!",
                    CodeHelper.getWorldSideName(this.getWorld()), this.getReferenceCoord());
            return;
        }

        final MultiblockReactor otherReactor = (MultiblockReactor)assimilated;

        if (otherReactor._reactorHeat.get() > this._reactorHeat.get()) {
            this._reactorHeat.set(otherReactor._reactorHeat.get());
        }

        if (otherReactor._fuelHeat.get() > this._fuelHeat.get()) {
            this._fuelHeat.set(otherReactor._fuelHeat.get());
        }

        if (otherReactor.getEnergyBuffer().getEnergyStored(INTERNAL_ENERGY_SYSTEM) > this.getEnergyBuffer().getEnergyStored(INTERNAL_ENERGY_SYSTEM)) {
            this.getEnergyBuffer().setEnergyStored(otherReactor.getEnergyBuffer().getEnergyStored(INTERNAL_ENERGY_SYSTEM));
        }

        this._logic.syncDataFrom(otherReactor._logic);
        this._fuelContainer.syncDataFrom(otherReactor._fuelContainer);
        this._fluidContainer.syncDataFrom(otherReactor._fluidContainer);
    }

    /**
     * Callback. Called after this controller is assimilated into another controller.
     * All blocks have been stripped out of this object and handed over to the
     * other controller.
     * This is intended primarily for cleanup.
     *
     * @param assimilator The controller which has assimilated this controller.
     */
    @Override
    protected void onAssimilated(IMultiblockController<MultiblockReactor> assimilator) {

        this._attachedTickables.clear();
        this._attachedControlRods.clear();
        this._attachedFuelRods.clear();
        this._attachedSolidAccessPorts.clear();
        this._attachedPowerTaps.clear();
        this._attachedFluidPorts.clear();
        this._fuelRodsLayout = null;
    }

    /**
     * The server-side update loop! Use this similarly to a TileEntity's update loop.
     * You do not need to call your superclass' update() if you're directly
     * derived from AbstractMultiblockController. This is a callback.
     * Note that this will only be called when the machine is assembled.
     *
     * @return True if the multiblock should save data, i.e. its internal game state has changed. False otherwise.
     */
    @Override
    protected boolean updateServer() {

        //TODO variants

        final IProfiler profiler = this.getWorld().getProfiler();

        profiler.startSection("Extreme Reactors|Reactor update"); // main section

        profiler.startSection("Generate");
        final boolean updateResult = this._logic.update();

        //////////////////////////////////////////////////////////////////////////////
        // TICKABLES
        //////////////////////////////////////////////////////////////////////////////

        profiler.endStartSection("Tickables");
        this._attachedTickables.forEach(ITickableMultiblockPart::onMultiblockServerTick);

        //////////////////////////////////////////////////////////////////////////////
        // SEND CLIENT UPDATES
        //////////////////////////////////////////////////////////////////////////////

        profiler.endStartSection("Updates");
        this.checkAndSendClientUpdates();

        //////////////////////////////////////////////////////////////////////////////
        // UPDATE REFERENCE COORDINATES
        //////////////////////////////////////////////////////////////////////////////

        profiler.endStartSection("Mark4Update");

        if (!this.isInteriorInvisible() && this._fuelContainer.shouldUpdate()) {
            this.markReferenceCoordForUpdate();
        }

        profiler.endSection(); // Mark4Update
        profiler.endSection(); // main section
        return updateResult;
    }

    /**
     * The "frame" consists of the outer edges of the machine, plus the corners.
     *
     * @param world             World object for the world in which this controller is located.
     * @param x                 X coordinate of the block being tested
     * @param y                 Y coordinate of the block being tested
     * @param z                 Z coordinate of the block being tested
     * @param validatorCallback the validator, for error reporting
     */
    @Override
    protected boolean isBlockGoodForFrame(World world, int x, int y, int z, IMultiblockValidator validatorCallback) {
        return invalidBlockForExterior(world, x, y, z, validatorCallback);
    }

    /**
     * The top consists of the top face, minus the edges.
     *
     * @param world             World object for the world in which this controller is located.
     * @param x                 X coordinate of the block being tested
     * @param y                 Y coordinate of the block being tested
     * @param z                 Z coordinate of the block being tested
     * @param validatorCallback the validator, for error reporting
     */
    @Override
    protected boolean isBlockGoodForTop(World world, int x, int y, int z, IMultiblockValidator validatorCallback) {
        return invalidBlockForExterior(world, x, y, z, validatorCallback);
    }

    /**
     * The bottom consists of the bottom face, minus the edges.
     *
     * @param world             World object for the world in which this controller is located.
     * @param x                 X coordinate of the block being tested
     * @param y                 Y coordinate of the block being tested
     * @param z                 Z coordinate of the block being tested
     * @param validatorCallback the validator, for error reporting
     */
    @Override
    protected boolean isBlockGoodForBottom(World world, int x, int y, int z, IMultiblockValidator validatorCallback) {
        return invalidBlockForExterior(world, x, y, z, validatorCallback);
    }

    /**
     * The sides consists of the N/E/S/W-facing faces, minus the edges.
     *
     * @param world             World object for the world in which this controller is located.
     * @param x                 X coordinate of the block being tested
     * @param y                 Y coordinate of the block being tested
     * @param z                 Z coordinate of the block being tested
     * @param validatorCallback the validator, for error reporting
     */
    @Override
    protected boolean isBlockGoodForSides(World world, int x, int y, int z, IMultiblockValidator validatorCallback) {
        return invalidBlockForExterior(world, x, y, z, validatorCallback);
    }

    /**
     * The interior is any block that does not touch blocks outside the machine.
     *
     * @param world             World object for the world in which this controller is located.
     * @param x                 X coordinate of the block being tested
     * @param y                 Y coordinate of the block being tested
     * @param z                 Z coordinate of the block being tested
     * @param validatorCallback the validator, for error reporting
     */
    @Override
    protected boolean isBlockGoodForInterior(World world, int x, int y, int z, IMultiblockValidator validatorCallback) {

        final BlockPos position = new BlockPos(x, y, z);
        final BlockState blockState = world.getBlockState(position);

        if (blockState.isAir(world, position)) {
            // Air is OK
            return true;
        }

        // Check against registered moderators
        if (ModeratorsRegistry.getFrom(blockState).isPresent()) {
            return true;
        }

        // Give up ...
        validatorCallback.setLastError(position, "multiblock.validation.reactor.invalid_block_for_interior",
                ModBlock.getNameForTranslation(blockState.getBlock()));
        return false;
    }

    //endregion
    //region IDebuggable

    @Override
    public void getDebugMessages(final LogicalSide side, final IDebugMessages messages) {

        if (!this.isAssembled()) {
            return;
        }

        messages.addUnlocalized("Active: %s", this.isMachineActive());

        this.getEnergyBuffer().getDebugMessages(side, messages);
        messages.addUnlocalized("Casing Heat: %1$.4f C; Fuel Heat: %2$.4f C", this._reactorHeat.get(), this._fuelHeat.get());
        messages.add(side, this._fuelContainer, "Reactant Tanks:");

        final boolean activeCooling = this.getOperationalMode().isActive();

        messages.addUnlocalized("Actively Cooled: %1$s", activeCooling);

        if (activeCooling) {

            messages.addUnlocalized("Coolant Tanks:");
            this._fluidContainer.getDebugMessages(side, messages);
        }
    }

    //endregion
    //region internals

    private Stream<IFuelSource<ItemStack>> getInputSolidAccessPorts() {
        return this._attachedSolidAccessPorts.stream()
                .filter(port -> null != port && port.isConnected() && port.getIoDirection().isInput())
                .map(port -> port);
    }

    private Stream<IFuelSource<ItemStack>> getOutputSolidAccessPorts() {
        return this._attachedSolidAccessPorts.stream()
                .filter(port -> null != port && port.isConnected() && port.getIoDirection().isOutput())
                .map(port -> port);
    }

    private void rebuildOutgoingFluidPorts() {

        this._attachedOutgoingFluidPorts.clear();
        this._attachedFluidPorts.stream()
                .filter(port -> port.getIoDirection().isOutput())
                .collect(Collectors.toCollection(() -> this._attachedOutgoingFluidPorts));
    }

    private FuelRodsLayout createFuelRodsLayout() {

        final Direction direction = this.getControlRodByIndex(0)
                .flatMap(AbstractCuboidMultiblockPart::getOutwardDirection)
                .orElse(Direction.UP);

        final int length;

        switch (direction.getAxis()) {

            default:
            case X:
                length = this.mapBoundingBoxCoordinates((min, max) -> Math.max(1, max.getX() - min.getX() - 1), 0);
                break;

            case Y:
                length = this.mapBoundingBoxCoordinates((min, max) -> Math.max(1, max.getY() - min.getY() - 1), 0);
                break;

            case Z:
                length = this.mapBoundingBoxCoordinates((min, max) -> Math.max(1, max.getZ() - min.getZ() - 1), 0);
                break;
        }

        return ExtremeReactors.getProxy().createFuelRodsLayout(direction, length);
    }

    private void updateFuelRodsLayout() {
        this.getFuelRodsLayout()
                .ifPresent(layout -> layout.updateFuelData(this._fuelContainer, this.getFuelRodsCount()));
    }

    // called only on the Client side
    private void onClientFuelStatusChanged() {

        if (this.calledByLogicalClient() && this.isAssembled()) {

            this.updateFuelRodsLayout();

            // re-render all the fuel rod blocks when the fuel status changes

            this.getFuelRodsLayout()
                    .filter(layout -> layout instanceof ClientFuelRodsLayout)
                    .map(layout -> (ClientFuelRodsLayout) layout)
                    .ifPresent(clientLayout -> this._attachedFuelRods.stream()
                            .filter(rod -> clientLayout.isFuelDataChanged(rod.getFuelRodIndex()))
                            .forEach(rod -> rod.notifyBlockUpdate())
                    );
        }
    }

    private static boolean invalidBlockForExterior(World world, int x, int y, int z, IMultiblockValidator validatorCallback) {

        final BlockPos position = new BlockPos(x, y, z);

        validatorCallback.setLastError(position, "multiblock.validation.reactor.invalid_block_for_exterior",
                ModBlock.getNameForTranslation(world.getBlockState(position).getBlock()));
        return false;
    }

    //region Reactor UPDATE helpers

    /**
     * Reactor UPDATE
     * Inject new solid fuel into the Reactor
     */
    private void refuelSolid() {

        // is there any space for fuel?
        if (this._fuelContainer.getFreeSpace(ReactantType.Fuel) < ReactantMappingsRegistry.STANDARD_SOLID_REACTANT_AMOUNT) {
            return;
        }

        final boolean wasEmpty = this._fuelContainer.getFuelAmount() <= 0;

        if (ReactantHelper.refuelSolid(this._fuelContainer, this.getInputSolidAccessPorts(), this.getVariant())) {

            if (wasEmpty) {
                this.updateFuelRodsLayout();
            }

            this.markReferenceCoordForUpdate();
            this.markReferenceCoordDirty();
        }
    }

    /**
     * Reactor UPDATE
     * Distribute the available energy equally between all the Active Power Taps
     */
    private void distributeEnergyEqually() {

        final EnergyBuffer energyBuffer = this.getEnergyBuffer();
        final double amountDistributed = distributeEnergyEqually(energyBuffer.getEnergyStored(),
                this._attachedPowerTaps);

        if (amountDistributed > 0) {
            energyBuffer.modifyEnergyStored(-amountDistributed);
        }
    }

    /**
     * Reactor UPDATE
     * Distribute the available gas equally between all the Active Coolant Ports
     */
    private void distributeGasEqually() {

        final int amountDistributed = distributeFluidEqually(this._fluidContainer.getStackCopy(FluidType.Gas), this._attachedFluidPorts);

        if (amountDistributed > 0) {
            this._fluidContainer.extract(FluidType.Gas, amountDistributed, OperationMode.Execute);
        }
    }

//    /**
//     * Reactor UPDATE
//     * Distribute the available gas between all the Coolant Ports in output mode
//     */
//    private void distributeGas() {
//
//        final IFluidHandler gasSource = this.getFluidContainer().getWrapper(FluidType.Gas);
//        int outputCount = this._attachedOutgoingCoolantPorts.size();
//        int gasAmount = this.getFluidContainer().getGasAmount();
//        int consumedAmount = 0;
//
//        if (gasAmount > 0) {
//
//            for (final ReactorCoolantPortEntity port : this._attachedOutgoingCoolantPorts) {
//
//                final FluidStack transferred = this.distributeGasTo(gasSource, port, gasAmount / outputCount);
//
//                if (!transferred.isEmpty()) {
//
//                    gasAmount -= transferred.getAmount();
//                    consumedAmount += transferred.getAmount();
//                }
//
//                --outputCount;
//
//                if (gasAmount <= 0) {
//                    break;
//                }
//            }
//
//            this.getFluidContainer().extract(FluidType.Gas, consumedAmount, OperationMode.Execute);
//        }
//    }
//
//    /**
//     * Reactor UPDATE
//     * Distribute the specified amount of gas to the provided Coolant Port
//     *
//     * @param gasSource the gas source
//     * @param port      the destination Coolant Port
//     * @param maxAmount the maximum amount of gas to transfer
//     */
//    private FluidStack distributeGasTo(IFluidHandler gasSource, ReactorCoolantPortEntity port, int maxAmount) {
//        return port.getOutwardDirection()
//                .map(direction -> FluidHelper.tryFluidTransfer(gasSource, port.getPartWorldOrFail(),
//                        port.getWorldPosition().offset(direction),
//                        direction.getOpposite(), maxAmount, IFluidHandler.FluidAction.EXECUTE))
//                .orElse(FluidStack.EMPTY);
//    }

    //endregion
    //region internal data update

    private void resizeFuelContainer() {
        // Recalculate size of the fuel/waste container tank via fuel rods
        this._fuelContainer.setCapacity(this.getFuelRodsCount() * ReactorFuelRodEntity.FUEL_CAPACITY_PER_FUEL_ROD);
    }

    private void resizeFluidContainer() {

        if (this.getOperationalMode().isActive()) {

            final int outerVolume = this.mapBoundingBoxCoordinates(CodeHelper::mathVolume, 0) - this.getReactorVolume();

            this._fluidContainer.setCapacity(MathHelper.clamp(outerVolume * this.getVariant().getPartFluidCapacity(),
                    0, this.getVariant().getMaxFluidCapacity()));

        } else {

            this._fluidContainer.setCapacity(0);
        }
    }

    private void calculateReactorVolume() {
        this._reactorVolume = this.mapBoundingBoxCoordinates(CodeHelper::mathVolume, 0, min -> min.add(1, 1, 1), max -> max.add(-1, -1, -1));
    }

    private void updateFuelToReactorHeatTransferCoefficient() {
        // Calculate heat transfer based on fuel rod environment
        this._fuelToReactorHeatTransferCoefficient = (float)this._attachedFuelRods.stream()
                .mapToDouble(ReactorFuelRodEntity::getHeatTransferRate)
                .sum();
    }

    private void updateReactorToCoolantSystemHeatTransferCoefficient() {
        // Calculate heat transfer to coolant system based on reactor interior surface area.
        // This is pretty simple to start with - surface area of the rectangular prism defining the interior.
        this._reactorToCoolantSystemHeatTransferCoefficient = IHeatEntity.CONDUCTIVITY_IRON *
                this.mapBoundingBoxCoordinates(MultiblockReactor::internalSurfaceArea, 0);
    }

    private void updateReactorHeatLossCoefficient() {
        // Calculate passive heat loss to external surface area
        this._reactorHeatLossCoefficient = REACTOR_HEAT_LOSS_CONDUCTIVITY *
                this.mapBoundingBoxCoordinates(MultiblockReactor::externalSurfaceArea, 0);
    }

    private static int internalSurfaceArea(final BlockPos min, final BlockPos max) {

        int xSize = max.getX() - min.getX() - 1;
        int ySize = max.getY() - min.getY() - 1;
        int zSize = max.getZ() - min.getZ() - 1;

        return  2 * (xSize * ySize + xSize * zSize + ySize * zSize);
    }

    private static int externalSurfaceArea(final BlockPos min, final BlockPos max) {

        int xSize = max.getX() - min.getX() + 1;
        int ySize = max.getY() - min.getY() + 1;
        int zSize = max.getZ() - min.getZ() + 1;

        return  2 * (xSize * ySize + xSize * zSize + ySize * zSize);
    }

    //endregion
    //region isMachineWhole-helpers

    /**
     * isMachineWhole-helper
     * Check that all Control Rods are on the same face of the Reactor
     *
     * @param firstDirection the direction to check all the Control Rods against
     * @return true if the Control Rods face in the same direction, false otherwise
     */
    private boolean validateControlRodsOrientation(Direction firstDirection) {
        return this._attachedControlRods.stream()
                .map(ReactorControlRodEntity::getOutwardFacingFromWorldPosition)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .allMatch(direction -> firstDirection == direction);
    }

    /**
     * isMachineWhole-helper
     * Validate a single Fuel Assembly
     *
     * @param validatorCallback the validator, for error reporting
     * @param world the World of the Reactor
     * @param controlRodPosition the coordinate of the Control Rod of the Fuel Assembly to check
     * @param scanDirection the direction to follow while walking the Fuel Assembly
     * @param validFuelRods collect all the valid Fuel Rods found
     * @return true if the Fuel Assembly is correctly constructed, false otherwise
     */
    private static boolean validateFuelAssembly(IMultiblockValidator validatorCallback, World world, BlockPos controlRodPosition,
                                                Direction scanDirection, List<ReactorFuelRodEntity> validFuelRods) {

        BlockPos scanPosition = controlRodPosition;

        while (true) {

            scanPosition = scanPosition.offset(scanDirection);

            final TileEntity te = world.getTileEntity(scanPosition);

            if (te instanceof ReactorFuelRodEntity) {

                // found a valid Fuel Rod
                validFuelRods.add((ReactorFuelRodEntity) te);

            } else if (te instanceof AbstractReactorEntity) {

                // we hit some other Reactor parts on the walls

                if (ReactorPartType.Casing != ((AbstractReactorEntity)te).getPartType().orElse(ReactorPartType.Glass)) {

                    // a Reactor Casing is the only valid base for a fuel assembly

                    validatorCallback.setLastError(scanPosition, "multiblock.validation.reactor.invalid_base_for_fuel_assembly");
                    return false;
                }

                // found a Reactor Casing: stop the scan
                break;

            } else {

                // found an invalid tile entity (or no tile entity at all)
                validatorCallback.setLastError(scanPosition, "multiblock.validation.reactor.invalid_block_in_fuel_assembly");
                return false;
            }
        }

        return true;
    }

    /**
     * isMachineWhole-helper
     * Validate a single Fuel Assembly
     *
     * @param validatorCallback the validator, for error reporting
     * @param scanDirection the direction to follow while walking the Fuel Assembly
     * @param controlRod the Control Rod of the Fuel Assembly to check
     * @param validFuelRods collect all the valid Fuel Rods found
     * @return true if the Fuel Assembly is correctly constructed, false otherwise
     */
    private boolean validateFuelAssembly(IMultiblockValidator validatorCallback, Direction scanDirection,
                                         ReactorControlRodEntity controlRod, List<ReactorFuelRodEntity> validFuelRods) {
        return validateFuelAssembly(validatorCallback, this.getWorld(), controlRod.getWorldPosition(), scanDirection, validFuelRods);
    }

    /**
     * isMachineWhole-helper
     * Check if all Fuel Assemblies are correctly constructed
     *
     * @param validatorCallback the validator, for error reporting
     * @return true if all Fuel Assemblies are correctly constructed, false otherwise
     */
    private boolean validateFuelAssemblies(IMultiblockValidator validatorCallback) {

        // ensure that Control Rods are placed only on one face of the Reactor ...

        final Optional<Direction> firstDirection = this._attachedControlRods.get(0).getOutwardFacingFromWorldPosition();

        if (!firstDirection.isPresent()) {

//            Log.LOGGER.warn(Log.REACTOR, "Found a Control Rod with no OutwardFacing while validating: giving up");

            validatorCallback.setLastError("multiblock.validation.reactor.invalid_control_side");
            return false;

        }

        if (!firstDirection.map(this::validateControlRodsOrientation).orElse(false)) {

            validatorCallback.setLastError("multiblock.validation.reactor.invalid_control_side");
            return false;
        }

        // ... and that Fuel Rods follow the orientation of Control Rods

        final List<ReactorFuelRodEntity> validFuelRods = Lists.newArrayListWithCapacity(this.getFuelRodsCount());
        final Direction scanDirection = firstDirection.get().getOpposite();

        for (ReactorControlRodEntity controlRod : this._attachedControlRods) {
            if (!this.validateFuelAssembly(validatorCallback, scanDirection, controlRod, validFuelRods)) {
                return false;
            }
        }

        if (this.getFuelRodsCount() != validFuelRods.size()) {

            validatorCallback.setLastError("multiblock.validation.reactor.invalid_fuel_rods");
            return false;
        }

        // all OK ...

        return true;
    }

    /**
     * isMachineWhole-helper
     * Check if there is only one type of EnergySystems in the Reactor
     *
     * @param validatorCallback the validator, for error reporting
     * @return true if validation is passed, false otherwise
     */
    private boolean validateEnergySystems(IMultiblockValidator validatorCallback) {

        if (!this._attachedPowerTaps.isEmpty()) {

            if (1 != this._attachedPowerTaps.stream()
                    .map(IPowerTap::getPowerTapHandler)
                    .map(IPowerTapHandler::getEnergySystem)
                    .distinct()
                    .limit(2)
                    .count()) {

                // there must be only one output energy system for each Reactor
                validatorCallback.setLastError("multiblock.validation.reactor.mixed_power_systems");
                return false;
            }
        }

        return true;
    }

    //endregion

    private static final IFluidContainerAccess FLUID_CONTAINER_ACCESS = new IFluidContainerAccess() {

        @Override
        public AllowedHandlerAction getAllowedActionFor(final FluidType fluidType) {

            switch (fluidType) {

                default:
                case Gas:
                    return AllowedHandlerAction.ExtractOnly;

                case Liquid:
                    return AllowedHandlerAction.InsertOnly;
            }
        }

        @Override
        public FluidType getFluidTypeFrom(final IoDirection portDirection) {

            switch (portDirection) {

                default:
                case Input:
                    return FluidType.Liquid;

                case Output:
                    return FluidType.Gas;
            }
        }
    };

    // circa 1FE per tick per external surface block
    private static final float REACTOR_HEAT_LOSS_CONDUCTIVITY = 0.001f;

    private final ReactorLogic _logic;
    private final IMultiblockReactorVariant _variant;
    private final Heat _fuelHeat;
    private final Heat _reactorHeat;
    private final FuelContainer _fuelContainer;
    private final FluidContainer _fluidContainer;
    private final IteratorTracker<ReactorFuelRodEntity> _irradiationSourceTracker;
    private final Stats _uiStats;

    private FuelRodsLayout _fuelRodsLayout;
    private WasteEjectionSetting _wasteEjectionSetting;
    private OperationalMode _mode;
    private boolean _active;
    private int _reactorVolume;
    private float _fuelToReactorHeatTransferCoefficient;
    private float _reactorToCoolantSystemHeatTransferCoefficient;
    private float _reactorHeatLossCoefficient;

    private final Set<ITickableMultiblockPart> _attachedTickables;
    private final List<ReactorControlRodEntity> _attachedControlRods;
    private final Set<ReactorFuelRodEntity> _attachedFuelRods;
    private final Set<ReactorSolidAccessPortEntity> _attachedSolidAccessPorts;
    private final Set<IPowerTap> _attachedPowerTaps;
    private final Set<ReactorFluidPortEntity> _attachedFluidPorts;
    private final Set<ReactorFluidPortEntity> _attachedOutgoingFluidPorts;

    //endregion
}
