/*
 *
 * ExtremeReactors.java
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

package it.zerono.mods.extremereactors;

import it.zerono.mods.extremereactors.config.Config;
import it.zerono.mods.extremereactors.gamecontent.Content;
import it.zerono.mods.extremereactors.proxy.ClientProxy;
import it.zerono.mods.extremereactors.proxy.IProxy;
import it.zerono.mods.extremereactors.proxy.ServerProxy;
import it.zerono.mods.zerocore.lib.init.IModInitializationHandler;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(value = ExtremeReactors.MOD_ID)
public class ExtremeReactors implements IModInitializationHandler {

    public static final String MOD_ID = "bigreactors";
    public static final String MOD_NAME = "Extreme Reactors 2";

    public static ExtremeReactors getInstance() {
        return s_instance;
    }

    public static IProxy getProxy() {
        return s_proxy;
    }

    public static ResourceLocation newID(final String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    public ExtremeReactors() {

        s_instance = this;

        Config.initialize();
        Content.initialize();

        s_proxy = DistExecutor.safeRunForDist(() -> ClientProxy::new, () -> ServerProxy::new);
//        Mod.EventBusSubscriber.Bus.MOD.bus().get().register(this);

        final IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        modBus.addListener(this::onPreRegistries);
        modBus.addListener(this::onCommonInit);
        modBus.addListener(this::onInterModProcess);
    }

    private void onPreRegistries(RegistryEvent.NewRegistry event) {

        // execute before registries population

    }

    /**
     * Called on both the physical client and the physical server to perform common initialization tasks
     * @param event the event
     */
    @Override
//    @SubscribeEvent
    public void onCommonInit(FMLCommonSetupEvent event) {
    }

    /**
     * Retrieve and process inter-mods messages and process them
     * <p>
     * See {@link InterModComms}
     *
     * @param event the event
     */
    @Override
//    @SubscribeEvent
    public void onInterModProcess(InterModProcessEvent event) {

        // API messages

        // - Reactor Reactants
        imcProcessAPIMessages(event, "reactant-register");
        // - Reactor Reactants mappings
        imcProcessAPIMessages(event, "mapping-register");
        // - Reactor reactions
        imcProcessAPIMessages(event, "reaction-register");
        // - Reactor Moderators
        imcProcessAPIMessages(event, "moderator-s-register");
        imcProcessAPIMessages(event, "moderator-f-register");
        imcProcessAPIMessages(event, "moderator-s-remove");
        imcProcessAPIMessages(event, "moderator-f-remove");

        // - Coolants
        imcProcessAPIMessages(event, "coolant-register");

        // - Turbine CoilMaterials
        imcProcessAPIMessages(event, "coilmaterial-register");
        imcProcessAPIMessages(event, "coilmaterial-remove");
    }

    //region internals

    private void imcProcessAPIMessages(InterModProcessEvent event, String method) {
        event.getIMCStream((method::equals)).map(imc -> (Runnable) imc.getMessageSupplier().get()).forEach(Runnable::run);
    }

    private static ExtremeReactors s_instance;
    private static IProxy s_proxy;

    //endregion
}
