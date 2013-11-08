package speedytools.items;

import cpw.mods.fml.common.network.PacketDispatcher;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.Packet250CustomPayload;
import net.minecraft.util.ChunkCoordinates;
import speedytools.SpeedyToolsMod;
import speedytools.clientserversynch.Packet250SpeedyToolUse;

import java.io.IOException;
import java.util.List;

/**
 * User: The Grey Ghost
 * Date: 2/11/13
 */
public abstract class ItemSpeedyTool extends Item
{
  public ItemSpeedyTool(int id) {
    super(id);
    setCreativeTab(CreativeTabs.tabTools);
    setMaxDamage(-1);                         // not damageable
  }

  public static boolean isAspeedyTool(int itemID)
  {
    return (   itemID == SpeedyToolsMod.itemSpeedyStripStrong.itemID
            || itemID == SpeedyToolsMod.itemSpeedyStripWeak.itemID    );
  }

  /**
   * if true, this tool does not destroy "solid" blocks such as stone, only "non-solid" blocks such as air, grass, etc
   * @param itemID
   * @return
   */
  public static boolean leavesSolidBlocksIntact(int itemID)
  {
    return (itemID == SpeedyToolsMod.itemSpeedyStripWeak.itemID);
  }

  /**
   * For the given ItemStack, returns the corresponding Block that will be placed by the tool
   *   eg ItemCloth will give the Block cloth
   * @param itemToBePlaced - the Item to be placed, or null for none.
   * @return the Block corresponding to the item, or null for none.
   */
  public static Block getPlacedBlockFromItemStack(ItemStack itemToBePlaced)
  {

  }


  /**
   * Sets the current multiple-block selection for the currently-held speedy tool
   * @param currentTool the currently-held speedy tool
   * @param setCurrentBlockToPlace the Block to be used for filling the selection
   * @param currentSelection list of coordinates of blocks in the current selection
   */

  @SideOnly(Side.CLIENT)
  public static void setCurrentToolSelection(Item currentTool, Block setCurrentBlockToPlace, int setCurrentMetadataToPlace, List<ChunkCoordinates> currentSelection)
  {
    currentlySelectedTool = currentTool;
    currentlySelectedBlocks = currentSelection;
    currentBlockToPlace = setCurrentBlockToPlace;
    currentMetadataToPlace = setCurrentMetadataToPlace;
  }

  /**
   * called when the user presses the attackButton (Left Mouse)
   */
  @SideOnly(Side.CLIENT)
  public static void attackButtonClicked()
  {
    buttonClicked(0);
  }

  /**
   * called when the user presses the use button (right mouse)
   */
  @SideOnly(Side.CLIENT)
  public static void useButtonClicked()
  {
     buttonClicked(1);
  }

//  @SideOnly(Side.SERVER)
  public static void performServerAction(int toolItemID, int buttonClicked, List<ChunkCoordinates> blockSelection)
  {
//    System.out.println("performServerAction: ID, button = " + toolItemID + ", " + buttonClicked);
  }

  @SideOnly(Side.CLIENT)
  public static void buttonClicked(int buttonClicked)
  {
    if (currentlySelectedTool == null) return;
    int blockToPlaceID = (currentBlockToPlace == null) ? 0 : currentBlockToPlace.blockID;

    Packet250SpeedyToolUse packet = null;
    try {
      packet = new Packet250SpeedyToolUse(currentlySelectedTool.itemID, buttonClicked, blockToPlaceID, currentlySelectedBlocks);
    } catch (IOException e) {
      Minecraft.getMinecraft().getLogAgent().logWarning("Could not create Packet250SpeedyToolUse for itemID " + currentlySelectedTool.itemID);
      return;
    }
    PacketDispatcher.sendPacketToServer(packet);
  }

    // these keep track of the currently selected blocks, for when the tool is used
  @SideOnly(Side.CLIENT)
  protected static List<ChunkCoordinates> currentlySelectedBlocks = null;
  @SideOnly(Side.CLIENT)
  protected static Item currentlySelectedTool = null;
  protected static Block currentBlockToPlace = null;
  protected static int   currentMetadataToPlace = 0;

  @SideOnly(Side.SERVER)
  int i;  // dummy
}
