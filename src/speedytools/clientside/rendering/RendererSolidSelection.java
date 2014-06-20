package speedytools.clientside.rendering;

import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;
import speedytools.clientside.selections.BlockVoxelMultiSelector;

/**
 * User: The Grey Ghost
 * Date: 18/04/2014
 *  * This class is used to render the boundary field (translucent cuboid)
 * Usage:
 * (1) Call the constructor, providing a BoundaryFieldRenderInfoUpdateLink:
 *     This interface is used to fill the supplied BoundaryFieldRenderInfo with the requested information for a render.
 * (2) When ready to render, call .render.

 */
public class RendererSolidSelection implements RendererElement
{
  public RendererSolidSelection(SolidSelectionRenderInfoUpdateLink i_infoProvider)
  {
    infoProvider = i_infoProvider;
    renderInfo = new SolidSelectionRenderInfo();
  }
  @Override
  public boolean renderInThisPhase(RenderPhase renderPhase)
  {
    return (renderPhase == RenderPhase.WORLD);
  }

  @Override
  public void renderOverlay(RenderPhase renderPhase, ScaledResolution scaledResolution, int animationTickCount, float partialTick)
  {
    assert false : "invalid render phase: " + renderPhase;
  }

  /**
   * render the boundary field if there is one selected
   * @param player
   * @param animationTickCount
   * @param partialTick
   */
  @Override
  public void renderWorld(RenderPhase renderPhase, EntityPlayer player, int animationTickCount, float partialTick)
  {
    boolean shouldIRender = infoProvider.refreshRenderInfo(renderInfo, player, partialTick);
    if (!shouldIRender) return;

    Vec3 playerLook = player.getLook(partialTick);
    Vec3 playerOrigin = player.getPosition(partialTick);

    try {
      GL11.glPushMatrix();
      GL11.glTranslated(renderInfo.draggedSelectionOriginX - playerOrigin.xCoord,
                        renderInfo.draggedSelectionOriginY - playerOrigin.yCoord,
                        renderInfo.draggedSelectionOriginZ - playerOrigin.zCoord);
      Vec3 playerRelativeToSelectionOrigin = playerOrigin.addVector(-renderInfo.draggedSelectionOriginX,
                                                                    -renderInfo.draggedSelectionOriginY,
                                                                    -renderInfo.draggedSelectionOriginZ);
      GL11.glPushAttrib(GL11.GL_ENABLE_BIT);
      if (renderInfo.opaque) {
        GL11.glDisable(GL11.GL_BLEND);
      } else {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
      }

      renderInfo.blockVoxelMultiSelector.renderSelection(playerRelativeToSelectionOrigin, playerLook);
      GL11.glPopAttrib();
    } finally {
      GL11.glPopMatrix();
    }
  }

  /**  The SolidSelectionRenderInfoUpdateLink and SolidSelectionRenderInfo are used to retrieve the necessary information for rendering from the current tool
   *  If refreshRenderInfo returns false, no render is performed.
   */
  public interface SolidSelectionRenderInfoUpdateLink
  {
    public boolean refreshRenderInfo(SolidSelectionRenderInfo infoToUpdate, EntityPlayer player, float partialTick);
  }

  public static class SolidSelectionRenderInfo
  {
    public BlockVoxelMultiSelector blockVoxelMultiSelector;     // the voxel selection to be rendered
    public double draggedSelectionOriginX;                      // the coordinates of the selection origin, after it has been dragged from its starting point
    public double draggedSelectionOriginY;
    public double draggedSelectionOriginZ;
    public boolean opaque;                    // if false, make partially transparent
  }

  SolidSelectionRenderInfoUpdateLink infoProvider;
  SolidSelectionRenderInfo renderInfo;
}
