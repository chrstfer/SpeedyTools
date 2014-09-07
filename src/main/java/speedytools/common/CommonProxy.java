package speedytools.common;

import speedytools.common.blocks.RegistryForBlocks;
import speedytools.common.items.RegistryForItems;
import speedytools.serverside.ServerSide;

/**
 * CommonProxy is used to set up the mod and start it running.  It contains all the code that should run on both the
 *   Standalone client and the dedicated server.
 */
public class CommonProxy {

  /**
   * Run before anything else. Read your config, create blocks, items, etc, and register them with the GameRegistry
   */
  public void preInit()
  {
    RegistryForItems.initialise();
    RegistryForBlocks.initialise();
  }

  /**
   * Do your mod setup. Build whatever data structures you care about. Register recipes,
   * send FMLInterModComms messages to other mods.
   */
  public void load()
  {
    ServerSide.load();
  }

  /**
   * Handle interaction with other mods, complete your setup based on this.
   */
  public void postInit()
  {
//    MinecraftForge.EVENT_BUS.register(new ServerEventHandler());
//    TickRegistry.registerTickHandler(new ServerTickHandler(), Side.SERVER);
  }

//  /**
//   * Gets the NetworkTrafficMonitor used to monitor network traffic on the current side
//   * @return
//   */
//  public NetworkTrafficMonitor getNetworkTrafficMonitorForSide()
//  {
//    if (FMLCommonHandler.instance().getSide() == Side.CLIENT) {
//      return ClientSide.getNetworkTrafficMonitor();
//    } else {
//      return ServerSide.getNetworkTrafficMonitor();
//    }
//  }

}
