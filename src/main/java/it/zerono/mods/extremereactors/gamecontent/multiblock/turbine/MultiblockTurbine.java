/*
 *
 * MultiblockTurbine.java
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

package it.zerono.mods.extremereactors.gamecontent.multiblock.turbine;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import it.zerono.mods.extremereactors.Log;
import it.zerono.mods.extremereactors.api.turbine.CoilMaterial;
import it.zerono.mods.extremereactors.api.turbine.CoilMaterialRegistry;
import it.zerono.mods.extremereactors.gamecontent.multiblock.common.*;
import it.zerono.mods.extremereactors.gamecontent.multiblock.common.part.powertap.IPowerTap;
import it.zerono.mods.extremereactors.gamecontent.multiblock.common.part.powertap.IPowerTapHandler;
import it.zerono.mods.extremereactors.gamecontent.multiblock.turbine.part.*;
import it.zerono.mods.extremereactors.gamecontent.multiblock.turbine.rotor.RotorComponentType;
import it.zerono.mods.extremereactors.gamecontent.multiblock.turbine.variant.IMultiblockTurbineVariant;
import it.zerono.mods.zerocore.lib.CodeHelper;
import it.zerono.mods.zerocore.lib.IDebugMessages;
import it.zerono.mods.zerocore.lib.IDebuggable;
import it.zerono.mods.zerocore.lib.block.ModBlock;
import it.zerono.mods.zerocore.lib.data.IoDirection;
import it.zerono.mods.zerocore.lib.data.stack.AllowedHandlerAction;
import it.zerono.mods.zerocore.lib.data.stack.OperationMode;
import it.zerono.mods.zerocore.lib.energy.EnergyBuffer;
import it.zerono.mods.zerocore.lib.multiblock.AbstractMultiblockPart;
import it.zerono.mods.zerocore.lib.multiblock.IMultiblockController;
import it.zerono.mods.zerocore.lib.multiblock.IMultiblockPart;
import it.zerono.mods.zerocore.lib.multiblock.ITickableMultiblockPart;
import it.zerono.mods.zerocore.lib.multiblock.validation.IMultiblockValidator;
import it.zerono.mods.zerocore.lib.world.WorldHelper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.profiler.IProfiler;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.LogicalSide;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class MultiblockTurbine
        extends AbstractGeneratorMultiblockController<MultiblockTurbine, IMultiblockTurbineVariant>
        implements ITurbineMachine, ITurbineEnvironment, ITurbineWriter, IDebuggable {

    public MultiblockTurbine(final World world, final IMultiblockTurbineVariant variant) {

        super(world);
        this._variant = variant;
        this._data = new TurbineData(variant);
        this._fluidContainer = new FluidContainer(FLUID_CONTAINER_ACCESS);

        // Minimum 10 RPM difference for slow updates, if change > 100 RPM, update every 5 ticks
        this._rpmUpdateTracker = new RpmUpdateTracker(100, 5, 10.0f, 100.0f);
        this._active = false;

        this._attachedTickables = Sets.newHashSet();
        this._attachedRotorBearings = Lists.newLinkedList();
        this._attachedPowerTaps = Sets.newHashSet();
        this._attachedRotorComponents = Sets.newHashSet();
        this._rotorBladesCount = 0;
        this._attachedVaporPorts = Sets.newHashSet();
        this._attachedOutgoingVaporPorts = Sets.newHashSet();
        this._validationFoundCoils = Sets.newHashSet();

        this._logic = new TurbineLogic(this, this._data, this.getEnergyBuffer());
    }

    /**
     * Reset the internal data
     * --- FOR TESTING PURPOSES ONLY ---
     */
    public void reset() {

        this.setMachineActive(false);
        this._fluidContainer.reset();
        this._data.reset();
        this.getEnergyBuffer().setEnergyStored(0);

        this.resizeFluidContainer();
    }

    public void onFluidPortChanged() {
        this.rebuildOutgoingFluidPorts();
    }

    //region active-coolant system

    @Override
    public Optional<IFluidHandler> getLiquidHandler() {
        return this.getFluidHandler(IoDirection.Output);
    }

    @Override
    public Optional<IFluidHandler> getGasHandler() {
        return this.getFluidHandler(IoDirection.Input);
    }

    @Override
    public Optional<IFluidHandler> getFluidHandler(final IoDirection portDirection) {
        return Optional.of(this._fluidContainer.getWrapper(portDirection));
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
    //region ITurbineMachine

    @Override
    public ITurbineEnvironment getEnvironment() {
        return this;
    }

    @Override
    public IFluidContainer getFluidContainer() {
        return this._fluidContainer;
    }

    /**
     * Output power/coolant to active ports
     */
    @Override
    public void performOutputCycle() {

        final IProfiler profiler = this.getWorld().getProfiler();

        // Distribute available power equally to all the Power Taps
        profiler.startSection("Power");
        this.distributeEnergyEqually();

        // Distribute available gas equally to all the Coolant Ports in output mode
        profiler.endStartSection("Coolant");
        this.distributeCoolantEqually();

        profiler.endSection();
    }

    //endregion
    //region ITurbineEnvironment

    @Override
    public boolean isSimulator() {
        return false;
    }

    /**
     * Get a CoilMaterial from the Turbine internal volume
     *
     * @param position the position to look up. Must be inside the Turbine internal volume
     * @return the CoilMaterial at the requested position, if the position is valid and a CoilMaterial is found there
     */
    @Override
    public Optional<CoilMaterial> getCoilBlock(BlockPos position) {
        return CoilMaterialRegistry.get(this.getWorld().getBlockState(position));
    }

    @Override
    public RotorComponentType getRotorComponentTypeAt(final BlockPos position) {

        final World world = this.getWorld();
        final BlockState state = world.getBlockState(position);
        final Block block = state.getBlock();

        if (state.isAir(world, position)) {

            return RotorComponentType.Ignore;

        } else if (block instanceof TurbineRotorComponentBlock) {

            final TurbineRotorComponentBlock/*<?>*/ rotorBlock = (TurbineRotorComponentBlock/*<?>*/)block;

            switch (rotorBlock.getPartType()) {

                case RotorBlade:
                    return RotorComponentType.Blade;

                case RotorShaft:
                    return RotorComponentType.Shaft;

                default:
                    return RotorComponentType.Ignore;
            }

        } else {

            return RotorComponentType.CandidateCoil;
        }
    }

    //endregion
    //region ITurbineReader

    public boolean isAssembledAndActive() {
        return this.isAssembled() && this.isMachineActive();
    }

    /**
     * @return the amount of coolant contained in the Turbine
     */
    public int getCoolantAmount() {
        return this._fluidContainer.getLiquidAmount();
    }

    /**
     * @return the amount of vapor contained in the Turbine
     */
    public int getVaporAmount() {
        return this._fluidContainer.getGasAmount();
    }

    /**
     * @return an integer representing the maximum amount of coolant and vapor, combined, the Turbine can contain
     */
    public int getCapacity() {
        return this._fluidContainer.getCapacity();
    }

    @Override
    public int getMaxIntakeRate() {
        return this._data.getMaxIntakeRate();
    }

    @Override
    public int getMaxIntakeRateHardLimit() {
        return this.getVariant().getMaxPermittedFlow();
    }

    @Override
    public double getEnergyGeneratedLastTick() {
        return this._data.getEnergyGeneratedLastTick();
    }

    @Override
    public int getFluidConsumedLastTick() {
        return this._data.getFluidConsumedLastTick();
    }

    @Override
    public float getRotorEfficiencyLastTick() {
        return this._data.getRotorEfficiencyLastTick();
    }

    public float getRotorSpeed() {

        final int blades = this.getRotorBladesCount();
        final int rotorMass = this.getRotorMass();

        if (blades <= 0 || rotorMass <= 0) {
            return 0f;
        } else {
            return this._data.getRotorEnergy() / (blades * rotorMass);
        }
    }

    public int getRotorBladesCount() {
        return this._rotorBladesCount;
    }

    @Override
    public float getMaxRotorSpeed() {
        return this.getVariant().getMaxRotorSpeed();
    }

    @Override
    public int getRotorMass() {
        return this._data.getRotorMass();
    }

    @Override
    public VentSetting getVentSetting() {
        return this._data.getVentSetting();
    }

    @Override
    public boolean isInductorEngaged() {
        return this._data.isInductorEngaged();
    }

    //endregion
    //region ITurbineWriter

    @Override
    public void setMaxIntakeRate(final int rate) {

        this._data.setMaxIntakeRate(rate);
        this.markReferenceCoordDirty();
    }

    @Override
    public void setVentSetting(final VentSetting setting) {

        this._data.setVentSetting(setting);
        this.markReferenceCoordDirty();
    }

    @Override
    public void setInductorEngaged(final boolean engaged) {

        this._data.setInductorEngaged(engaged);
        this.markReferenceCoordDirty();
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

        super.syncDataFrom(data, syncReason);

        if (data.contains("active")) {
            this._active = data.getBoolean("active");
        }

        this.syncChildDataEntityFrom(this._fluidContainer, "fluidcontainer", data, syncReason);
        this.syncChildDataEntityFrom(this._data, "internaldata", data, syncReason);

        if (syncReason.isFullSync()) {
            this._rpmUpdateTracker.setValue(this.getRotorSpeed());
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
        this.syncChildDataEntityTo(this._fluidContainer, "fluidcontainer", data, syncReason);
        this.syncChildDataEntityTo(this._data, "internaldata", data, syncReason);

        return data;
    }

    //endregion
    //region IDebuggable

    /**
     * @param side     the LogicalSide of the caller
     * @param messages add your debug messages here
     */
    @Override
    public void getDebugMessages(LogicalSide side, IDebugMessages messages) {

        if (!this.isAssembled()) {
            return;
        }

        messages.addUnlocalized("Active: %s", this.isMachineActive());

        this.getEnergyBuffer().getDebugMessages(side, messages);

        messages.add(side, this._data, "Internal data:");
        messages.add(side, this._fluidContainer, "Fluids Tanks:");
    }

    //endregion
    //region AbstractGeneratorMultiblockController


    /**
     * Marks the reference coord dirty.
     * <p>
     * On the server, this marks the reference coord's chunk as dirty; the block (and chunk)
     * will be saved to disk the next time chunks are saved. This does NOT mark it dirty for
     * a description-packet update.
     * <p>
     * On the client, does nothing.
     */
    @Override
    protected void markReferenceCoordDirty() {

        this._rpmUpdateTracker.reset();
        super.markReferenceCoordDirty();
    }

    @Override
    public IMultiblockTurbineVariant getVariant() {
        return this._variant;
    }

    @Override
    protected void sendClientUpdates() {
        this.sendUpdates();
    }

    //endregion
    //region AbstractMultiblockController

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

        final IProfiler profiler = this.getWorld().getProfiler();

        profiler.startSection("Extreme Reactors|Turbine update"); // main section

        //////////////////////////////////////////////////////////////////////////////
        // GENERATE ENERGY / COOLANT
        //////////////////////////////////////////////////////////////////////////////

        profiler.startSection("Generate");
        this._logic.update();

        //////////////////////////////////////////////////////////////////////////////
        // SEND POWER/GAS OUT
        //////////////////////////////////////////////////////////////////////////////

        profiler.endStartSection("Distribute"); // close "Generate"
        this.performOutputCycle();

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
        // ROTOR RPM TRACKER
        //////////////////////////////////////////////////////////////////////////////

        profiler.endStartSection("RpmTracker");

        if (this._rpmUpdateTracker.shouldUpdate(this.getRotorSpeed())) {
            this.markReferenceCoordDirty();
        }

        profiler.endSection(); // RpmTracker
        profiler.endSection(); // main section

        return this._data.getEnergyGeneratedLastTick() > 0 || this._data.getFluidConsumedLastTick() > 0;
    }

    @Override
    public boolean isPartCompatible(final IMultiblockPart<MultiblockTurbine> part) {
        return (part instanceof AbstractTurbineEntity) &&
                ((AbstractTurbineEntity) part).getMultiblockVariant()
                        .filter(variant -> this.getVariant() == variant)
                        .isPresent();
    }

    /**
     * Called when a new part is added to the machine. Good time to register things into lists.
     *
     * @param newPart The part being added.
     */
    @Override
    protected void onPartAdded(IMultiblockPart<MultiblockTurbine> newPart) {

        if (newPart instanceof ITickableMultiblockPart) {
            this._attachedTickables.add((ITickableMultiblockPart) newPart);
        }

        if (newPart instanceof TurbineRotorBearingEntity) {
            this._attachedRotorBearings.add((TurbineRotorBearingEntity)newPart);
        } else if (newPart instanceof TurbineRotorComponentEntity) {
            this._attachedRotorComponents.add((TurbineRotorComponentEntity)newPart);
        } else if (newPart instanceof TurbinePowerTapEntity || newPart instanceof TurbineChargingPortEntity) {
            this._attachedPowerTaps.add((IPowerTap)newPart);
        } else if (newPart instanceof TurbineFluidPortEntity) {
            this._attachedVaporPorts.add((TurbineFluidPortEntity)newPart);
        }
    }

    /**
     * Called when a part is removed from the machine. Good time to clean up lists.
     *
     * @param oldPart The part being removed.
     */
    @Override
    protected void onPartRemoved(IMultiblockPart<MultiblockTurbine> oldPart) {

        if (oldPart instanceof ITickableMultiblockPart) {
            this._attachedTickables.remove(oldPart);
        }

        if (oldPart instanceof TurbineRotorBearingEntity) {
            this._attachedRotorBearings.remove(oldPart);
        } else if (oldPart instanceof TurbineRotorComponentEntity) {
            this._attachedRotorComponents.remove(oldPart);
        } else if (oldPart instanceof TurbinePowerTapEntity || oldPart instanceof TurbineChargingPortEntity) {
            this._attachedPowerTaps.remove(oldPart);
        } else if (oldPart instanceof TurbineFluidPortEntity) {
            this._attachedVaporPorts.remove(oldPart);
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

        // how many blades?
        this._rotorBladesCount = (int)(this._attachedRotorComponents.stream().filter(c -> c.isTypeOfPart(TurbinePartType.RotorBlade)).count());

        // interior visible?
        this.setInteriorInvisible(!this.isAnyPartConnected(part -> part instanceof TurbineGlassEntity));

        // gather outgoing vapor ports
        this.rebuildOutgoingFluidPorts();

        //resize energy buffer

        this.getEnergyBuffer().setCapacity(this.getVariant().getPartEnergyCapacity() * this.getPartsCount());
        this.getEnergyBuffer().setMaxExtract(this.getVariant().getMaxEnergyExtractionRate());

        this.resizeFluidContainer();
        this.updateRotorAndCoilsParameters();

        this.callOnLogicalSide(
                this::markReferenceCoordForUpdate,
                () -> {
                    // Make sure our fuel rods re-render
//                    this.onClientFuelStatusChanged();//TODO rotor?
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

        this._data.onTurbineDisassembled();
        this._rpmUpdateTracker.setValue(0f);

        this.markMultiblockForRenderUpdate();
    }

    @Override
    protected boolean isMachineWhole(IMultiblockValidator validatorCallback) {

        if (this._attachedRotorBearings.size() != 1) {

            validatorCallback.setLastError("multiblock.validation.turbine.invalid_rotor_count");
            return false;
        }

        if (!this.isAnyPartConnected(part -> part instanceof TurbineControllerEntity)) {

            validatorCallback.setLastError("multiblock.validation.turbine.too_few_controllers");
            return false;
        }

        if (!super.isMachineWhole(validatorCallback)) {
            return false;
        }

        // Check if the the rotor is valid and cache coils positions

        if (!this.validateRotor(this._attachedRotorBearings.get(0), validatorCallback)) {
            return false;
        }

        // Check if the machine has a single power system

        if (!this.validateEnergySystems(validatorCallback)) {
            return false;
        }

        // machine is valid

        return true;
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

        // We only allow air and valid coils blocks inside a Turbine.

        BlockPos position = new BlockPos(x, y, z);

        // is it Air ?
        if (world.isAirBlock(position)) {
            return true;
        }

        // is it a valid coil block ?

        if (CoilMaterialRegistry.get(world.getBlockState(position)).isPresent()) {

            // yes, cache it's position

            _validationFoundCoils.add(position);
            return true;
        }

        // Everything else is an invalid block

        validatorCallback.setLastError(position, "multiblock.validation.turbine.invalid_block_for_interior");
        return false;
    }

    /**
     * Callback. Called after this controller assimilates all the blocks
     * from another controller.
     * Use this to absorb that controller's game data.
     *
     * @param assimilated The controller whose uniqueness was added to our own.
     */
    @Override
    protected void onAssimilate(IMultiblockController<MultiblockTurbine> assimilated) {

        if (!(assimilated instanceof MultiblockTurbine)) {

            Log.LOGGER.warn(Log.TURBINE, "[{}] Turbine @ {} is attempting to assimilate a non-Turbine machine! That machine's data will be lost!",
                    CodeHelper.getWorldSideName(this.getWorld()), this.getReferenceCoord());
            return;
        }

        this._data.onAssimilate((((MultiblockTurbine)assimilated)._data));
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
    protected void onAssimilated(IMultiblockController<MultiblockTurbine> assimilator) {

        this._attachedTickables.clear();
        this._attachedRotorBearings.clear();
        this._rotorBladesCount = 0;
        this._attachedRotorComponents.clear();
        this._attachedPowerTaps.clear();
        this._attachedVaporPorts.clear();
        this._attachedOutgoingVaporPorts.clear();
    }

    //endregion
    //region internals
    //region isMachineWhole helpers

    /**
     * isMachineWhole-helper
     * Check that we have a rotor that goes all the way up the bearing
     *
     * @param bearing the Rotor Bearing to check
     * @param validatorCallback the validator, for error reporting
     * @return true if the rotor is correctly constructed, false otherwise
     */
    private boolean validateRotor(final TurbineRotorBearingEntity bearing, final IMultiblockValidator validatorCallback) {

        // clear cache of Coils positions so it can be filled again here
        this._validationFoundCoils.clear();

        return this.mapBoundingBoxCoordinates(
                (min, max) -> validateRotor(bearing, validatorCallback, bearing.getRotorDirection(), min, max), false);
    }

    /**
     * isMachineWhole-helper
     * Check that we have a rotor that goes all the way up the bearing
     *
     * @param bearing the Rotor Bearing to check
     * @param validatorCallback the validator, for error reporting
     * @param rotorDirection the rotor direction
     * @param turbineMin the minimum coordinates of the Turbine
     * @param turbineMax the maximum coordinates of the Turbine
     * @return true if the rotor is correctly constructed, false otherwise
     */
    private boolean validateRotor(final TurbineRotorBearingEntity bearing, final IMultiblockValidator validatorCallback,
                                  final Direction rotorDirection, final BlockPos turbineMin, final BlockPos turbineMax) {

        // Figure out where the rotor ends and which directions are normal to the rotor's 4 faces (this is where blades emit from)

        BlockPos rotorCoord = bearing.getWorldPosition();
        final BlockPos endRotorCoord;

        switch (rotorDirection.getAxis()) {

            case X:
                endRotorCoord = rotorCoord.offset(rotorDirection, Math.abs(turbineMax.getX() - turbineMin.getX()) - 1);
                break;

            default:
            case Y:
                endRotorCoord = rotorCoord.offset(rotorDirection, Math.abs(turbineMax.getY() - turbineMin.getY()) - 1);
                break;

            case Z:
                endRotorCoord = rotorCoord.offset(rotorDirection, Math.abs(turbineMax.getZ() - turbineMin.getZ()) - 1);
                break;
        }

        final Set<BlockPos> shaftsPositions = this._attachedRotorComponents.stream()
//                .filter(c -> c.isTypeOfPart(TurbinePartType.RotorShaft)) // use isShaft
                .filter(TurbineRotorComponentEntity::isShaft)
                .map(AbstractMultiblockPart::getWorldPosition)
                .collect(Collectors.toSet());

        final Set<BlockPos> bladesPositions = this._attachedRotorComponents.stream()
//                .filter(c -> c.isTypeOfPart(TurbinePartType.RotorBlade)) // use isBlade
                .filter(TurbineRotorComponentEntity::isBlade)
                .map(AbstractMultiblockPart::getWorldPosition)
                .collect(Collectors.toSet());

        // Move along the length of the rotor, 1 block at a time

        final Direction.Axis rotatedAxis = rotorDirection.getAxis();
        boolean encounteredCoils = false;

        while (!shaftsPositions.isEmpty() && !rotorCoord.equals(endRotorCoord)) {

            rotorCoord = rotorCoord.offset(rotorDirection);

            // Ensure we find a rotor shaft block along the length of the entire rotor

            if (!shaftsPositions.remove(rotorCoord)) {

                validatorCallback.setLastError(rotorCoord, "multiblock.validation.turbine.block_must_be_rotor");
                return false;
            }

            // Now move out in the 4 rotor normals, looking for blades and coils

            BlockPos checkCoord;
            boolean encounteredBlades = false;

            for (final Direction bladeDirection : CodeHelper.perpendicularDirections(rotorDirection)) {

                boolean bladeFound = false;

                checkCoord = rotorCoord.offset(bladeDirection);

                // If we find one blade, we can keep moving along the normal to find more blades

                while (bladesPositions.remove(checkCoord)) {

                    // We found a coil already?! NOT ALLOWED.
                    if (encounteredCoils) {

                        validatorCallback.setLastError(checkCoord, "multiblock.validation.turbine.blades_too_far");
                        return false;
                    }

                    bladeFound = encounteredBlades = true;
                    checkCoord = checkCoord.offset(bladeDirection);
                }

                // If this block wasn't a blade, check to see if it was a coil

                if (!bladeFound) {

                    if (this._validationFoundCoils.remove(checkCoord)) {

                        encounteredCoils = true;

                        // We cannot have blades and coils intermix. This prevents intermixing, depending on eval order.

                        if (encounteredBlades) {

                            validatorCallback.setLastError(checkCoord, "multiblock.validation.turbine.metal_too_near");
                            return false;
                        }

                        // Check the two coil spots in the 'corners', which are permitted if they're connected to the main rotor coil somehow

                        BlockPos coilCheck;
                        Direction rotatedDir;

                        rotatedDir = CodeHelper.directionRotateAround(bladeDirection, rotatedAxis);
                        coilCheck = checkCoord.offset(rotatedDir);
                        this._validationFoundCoils.remove(coilCheck);

                        rotatedDir = CodeHelper.directionRotateAround(CodeHelper.directionRotateAround(rotatedDir, rotatedAxis), rotatedAxis);
                        coilCheck = checkCoord.offset(rotatedDir);
                        this._validationFoundCoils.remove(coilCheck);
                    }

                    // Else: It must have been air.
                }
            }
        }

        if (!rotorCoord.equals(endRotorCoord)) {

            validatorCallback.setLastError("multiblock.validation.turbine.shaft_too_short");
            return false;
        }

        // Ensure that we encountered all the rotor, blade and coil blocks. If not, there's loose stuff inside the turbine.

        if (!shaftsPositions.isEmpty()) {

            validatorCallback.setLastError("multiblock.validation.turbine.found_loose_rotor_blocks", shaftsPositions.size());
            return false;
        }

        if (!bladesPositions.isEmpty()) {

            validatorCallback.setLastError("multiblock.validation.turbine.found_loose_rotor_blades", bladesPositions.size());
            return false;
        }

        if (!this._validationFoundCoils.isEmpty()) {

            validatorCallback.setLastError("multiblock.validation.turbine.invalid_metals_shape", this._validationFoundCoils.size());
            return false;
        }

        if (WorldHelper.getTile(this.getWorld(), rotorCoord.offset(rotorDirection))
                .map(te -> te instanceof TurbineCasingEntity)
                .orElse(false)) {

            return true;

        } else {

            validatorCallback.setLastError("multiblock.validation.turbine.invalid_rotor_end");
            return false;
        }
    }

    /**
     * isMachineWhole-helper
     * Check if there is only one type of EnergySystems in the Reactor
     *
     * @param validatorCallback the validator, for error reporting
     * @return true if validation is passed, false otherwise
     */
    private boolean validateEnergySystems(final IMultiblockValidator validatorCallback) {

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
    //region Turbine UPDATE

    /**
     * Turbine UPDATE
     * Distribute the available energy equally between all the Active Power Taps
     */
    private void distributeEnergyEqually() {

        final EnergyBuffer energyBuffer = this.getEnergyBuffer();
        final double amountDistributed = distributeEnergyEqually(energyBuffer.getEnergyStored(), this._attachedPowerTaps);

        if (amountDistributed > 0) {
            energyBuffer.modifyEnergyStored(-amountDistributed);
        }
    }

    /**
     * Turbine UPDATE
     * Distribute the available gas equally between all the Active Coolant Ports
     */
    private void distributeCoolantEqually() {

        final int amountDistributed = distributeFluidEqually(this._fluidContainer.getStackCopy(FluidType.Liquid), this._attachedVaporPorts);

        if (amountDistributed > 0) {
            this._fluidContainer.extract(FluidType.Liquid, amountDistributed, OperationMode.Execute);
        }
    }

    //endregion

    private static boolean invalidBlockForExterior(World world, int x, int y, int z, IMultiblockValidator validatorCallback) {

        final BlockPos position = new BlockPos(x, y, z);

        validatorCallback.setLastError(position, "multiblock.validation.turbine.invalid_block_for_exterior",
                ModBlock.getNameForTranslation(world.getBlockState(position).getBlock()));
        return false;
    }

    /**
     * Recalculate rotor and coil parameters
     */
    private void updateRotorAndCoilsParameters() {
        this.forBoundingBoxCoordinates((min, max) -> this._data.update(this.getEnvironment(), min, max, this.getVariant()),
                min -> min.add(1, 1, 1), max -> max.add(-1, -1, -1));
    }

    private int calculateTurbineVolume() {
        return this.mapBoundingBoxCoordinates((min, max) -> CodeHelper.mathVolume(min.add(1, 1, 1), max.add(-1, -1, -1)), 0);
    }

    private void resizeFluidContainer() {

        final int outerVolume = this.mapBoundingBoxCoordinates(CodeHelper::mathVolume, 0) - this.calculateTurbineVolume();

        this._fluidContainer.setCapacity(MathHelper.clamp(outerVolume * this.getVariant().getPartFluidCapacity(),
                0, this.getVariant().getMaxFluidCapacity()));
    }

    private void rebuildOutgoingFluidPorts() {

        this._attachedOutgoingVaporPorts.clear();
        this._attachedVaporPorts.stream()
                .filter(port -> port.getIoDirection().isOutput())
                .collect(Collectors.toCollection(() -> this._attachedOutgoingVaporPorts));
    }

    private static final IFluidContainerAccess FLUID_CONTAINER_ACCESS = new IFluidContainerAccess() {

        @Override
        public AllowedHandlerAction getAllowedActionFor(final FluidType fluidType) {

            switch (fluidType) {

                default:
                case Gas:
                    return AllowedHandlerAction.InsertOnly;

                case Liquid:
                    return AllowedHandlerAction.ExtractOnly;
            }
        }

        @Override
        public FluidType getFluidTypeFrom(final IoDirection portDirection) {

            switch (portDirection) {

                default:
                case Input:
                    return FluidType.Gas;

                case Output:
                    return FluidType.Liquid;
            }
        }
    };

    private final TurbineData _data;
    private final TurbineLogic _logic;
    private final IMultiblockTurbineVariant _variant;
    private final FluidContainer _fluidContainer;
    private final RpmUpdateTracker _rpmUpdateTracker;
    private final Set<ITickableMultiblockPart> _attachedTickables;
    private final List<TurbineRotorBearingEntity> _attachedRotorBearings;
    private final Set<TurbineRotorComponentEntity> _attachedRotorComponents;
    private final Set<IPowerTap> _attachedPowerTaps;
    private final Set<TurbineFluidPortEntity> _attachedVaporPorts;
    private final Set<TurbineFluidPortEntity> _attachedOutgoingVaporPorts;

    private boolean _active;
    private int _rotorBladesCount;

    // Coils positions cached during validation
    private final Set<BlockPos> _validationFoundCoils;

    //endregion
}
