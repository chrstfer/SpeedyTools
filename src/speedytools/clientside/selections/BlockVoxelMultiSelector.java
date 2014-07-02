package speedytools.clientside.selections;

import cpw.mods.fml.common.FMLLog;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.*;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;
import speedytools.common.blocks.RegistryForBlocks;
import speedytools.common.selections.VoxelSelection;
import speedytools.common.selections.VoxelSelectionWithOrigin;
import speedytools.common.utilities.Colour;
import speedytools.common.utilities.UsefulConstants;

import java.io.ByteArrayOutputStream;
import java.util.*;

/**
 * User: The Grey Ghost
 * Date: 17/02/14
 */
public class BlockVoxelMultiSelector
{
  private VoxelSelectionWithOrigin selection;
//  private VoxelSelection shadow;

  private int smallestVoxelX;
  private int largestVoxelX;
  private int smallestVoxelY;
  private int largestVoxelY;
  private int smallestVoxelZ;
  private int largestVoxelZ;

/*
  // the coordinates of the blocks that form the
  private ArrayList<ChunkCoordinates> wireFrameXnegY;
  private ArrayList<ChunkCoordinates> wireFrameXposY;
  private ArrayList<ChunkCoordinates> wireFrameZnegY;
  private ArrayList<ChunkCoordinates> wireFrameZposY;
*/
  private int xSize;
  private int ySize;
  private int zSize;
  private int wxOrigin;
  private int wyOrigin;
  private int wzOrigin;

  private int xpos;
  private int ypos;
  private int zpos;

  private boolean empty = true;

  private enum OperationInProgress {
    IDLE, ALL_IN_BOX, FILL, COMPLETE
  }
  private OperationInProgress mode;

  private int displayListSelection = 0;
  private int displayListWireFrameXY = 0;
  private int displayListWireFrameYZ = 0;
  private int displayListWireFrameXZ = 0;

  /**
   * initialise conversion of the selected box to a VoxelSelection
   * @param world
   * @param corner1 one corner of the box
   * @param corner2 opposite corner of the box
   */
  public void selectAllInBoxStart(World world, ChunkCoordinates corner1, ChunkCoordinates corner2)
  {
    initialiseSelectionSizeFromBoundary(corner1, corner2);
    xpos = 0;
    ypos = 0;
    zpos = 0;
    mode = OperationInProgress.ALL_IN_BOX;
    initialiseVoxelRange();
  }

  public float continueSelectionGeneration(World world, long maxTimeInNS)
  {
    switch (mode) {
      case ALL_IN_BOX: {
        return selectAllInBoxContinue(world, maxTimeInNS);
      }
      case FILL: {
        return selectFillContinue(world, maxTimeInNS);
      }
      default: assert false : "invalid mode " + mode + " in continueSelectionGeneration";
    }
    return 0;
  }

  /**
   * continue conversion of the selected box to a VoxelSelection.  Call repeatedly until conversion complete.
   * @param world
   * @param maxTimeInNS maximum elapsed duration before processing stops & function returns
   * @return fraction complete (0 - 1), -ve number for finished
   */
  private float selectAllInBoxContinue(World world, long maxTimeInNS)
  {
    if (mode != OperationInProgress.ALL_IN_BOX) {
      FMLLog.severe("Mode should be ALL_IN_BOX in BlockVoxelMultiSelector::selectAllInBoxContinue");
      return -1;
    }

    long startTime = System.nanoTime();

    for ( ; zpos < zSize; ++zpos, xpos = 0) {
      for ( ; xpos < xSize; ++xpos, ypos = 0) {
        for ( ; ypos < ySize; ++ypos) {
          if (System.nanoTime() - startTime >= maxTimeInNS) return (zpos / (float)zSize);
          if (world.getBlockId(xpos + wxOrigin, ypos + wyOrigin, zpos + wzOrigin) != 0) {
            selection.setVoxel(xpos, ypos, zpos);
            expandVoxelRange(xpos, ypos, zpos);
          }
        }
      }
    }
    mode = OperationInProgress.COMPLETE;
    return -1;
  }

  /**
   * initialise conversion of the selected fill to a VoxelSelection
   * From the starting block, performs a flood fill on all non-air blocks.
   * Will not fill any blocks with y less than the blockUnderCursor.
   * @param world
   * @param blockUnderCursor the block being highlighted by the cursor
   */
  public void selectUnboundFillStart(World world, ChunkCoordinates blockUnderCursor)
  {
    ChunkCoordinates corner1 = new ChunkCoordinates();
    ChunkCoordinates corner2 = new ChunkCoordinates();
    corner1.posX = blockUnderCursor.posX - VoxelSelection.MAX_X_SIZE / 2 + 1;
    corner2.posX = blockUnderCursor.posX + VoxelSelection.MAX_X_SIZE / 2 - 1;
    corner1.posY = blockUnderCursor.posY;
    corner2.posY = Math.min(255, blockUnderCursor.posY + VoxelSelection.MAX_Y_SIZE - 1);
    corner1.posZ = blockUnderCursor.posZ - VoxelSelection.MAX_Z_SIZE / 2 + 1;
    corner2.posZ = blockUnderCursor.posZ + VoxelSelection.MAX_Z_SIZE / 2 - 1;

    selectBoundFillStart(world, blockUnderCursor, corner1, corner2);
  }

  /**
   * initialise conversion of the selected fill to a VoxelSelection
   * From the starting block, performs a flood fill on all non-air blocks.
   * Will not fill any blocks outside of the box defined by corner1 and corner2
   * @param world
   * @param blockUnderCursor the block being highlighted by the cursor
   */
  public void selectBoundFillStart(World world, ChunkCoordinates blockUnderCursor, ChunkCoordinates corner1, ChunkCoordinates corner2)
  {
    initialiseSelectionSizeFromBoundary(corner1, corner2);
    assert (blockUnderCursor.posX >= wxOrigin && blockUnderCursor.posY >= wyOrigin && blockUnderCursor.posZ >= wzOrigin);
    assert (blockUnderCursor.posX < wxOrigin + xSize && blockUnderCursor.posY < wyOrigin + ySize && blockUnderCursor.posZ < wzOrigin + zSize);
    mode = OperationInProgress.FILL;
    initialiseVoxelRange();
    ChunkCoordinates startingBlockCopy = new ChunkCoordinates(blockUnderCursor.posX - wxOrigin, blockUnderCursor.posY - wyOrigin, blockUnderCursor.posZ - wzOrigin);
    currentSearchPositions.clear();
    nextDepthSearchPositions.clear();
    currentSearchPositions.add(new SearchPosition(startingBlockCopy));
    selection.setVoxel(startingBlockCopy.posX, startingBlockCopy.posY, startingBlockCopy.posZ);
    expandVoxelRange(startingBlockCopy.posX, startingBlockCopy.posY, startingBlockCopy.posZ);
    blocksAddedCount = 0;
//    checkedLocations = new VoxelSelection(xSize, ySize, zSize);
  }

  /**
   * continue conversion of the selected box to a VoxelSelection.  Call repeatedly until conversion complete.
   * @param world
   * @param maxTimeInNS maximum elapsed duration before processing stops & function returns
   * @return fraction complete (0 - 1), -ve number for finished
   */
  private float selectFillContinue(World world, long maxTimeInNS)
  {
    if (mode != OperationInProgress.FILL) {
      FMLLog.severe("Mode should be FILL in BlockVoxelMultiSelector::selectFillContinue");
      return -1;
    }

    long startTime = System.nanoTime();

    // lookup table to give the possible search directions for non-diagonal and diagonal respectively
    final int NON_DIAGONAL_DIRECTIONS = 6;
    final int ALL_DIRECTIONS = 26;
    final int searchDirectionsX[] = {+0, +0, +0, +0, -1, +1,   // non-diagonal
            +1, +0, -1, +0,   +1, +1, -1, -1,   +1, +0, -1, +0,  // top, middle, bottom "edge" blocks
            +1, +1, -1, -1,   +1, +1, -1, -1                   // top, bottom "corner" blocks
    };
    final int searchDirectionsY[] = {-1, +1, +0, +0, +0, +0,   // non-diagonal
            +1, +1, +1, +1,   +0, +0, +0, +0,   -1, -1, -1, -1,   // top, middle, bottom "edge" blocks
            +1, +1, +1, +1,   -1, -1, -1, -1                      // top, bottom "corner" blocks
    };

    final int searchDirectionsZ[] = {+0, +0, -1, +1, +0, +0,   // non-diagonal
            +0, -1, +0, +1,   +1, -1, -1, +1,   +0, -1, +0, +1,   // top, middle, bottom "edge" blocks
            +1, -1, -1, +1,   +1, -1, -1, +1
    };

//    final int INITIAL_CAPACITY = 128;
//    Set<ChunkCoordinates> locationsFilled = new HashSet<ChunkCoordinates>(INITIAL_CAPACITY);                                   // locations which have been selected during the fill
    ChunkCoordinates checkPosition = new ChunkCoordinates(0,0,0);
    ChunkCoordinates checkPositionSupport = new ChunkCoordinates(0,0,0);

    // algorithm is:
    //   for each block in the list of search positions, iterate through each adjacent block to see whether it meets the criteria for expansion:
    //     a) matches the block-to-be-replaced (if matchAnyNonAir: non-air, otherwise if blockID and metaData match.  For lava or water metadata doesn't need to match).
    //     b) hasn't been filled already during this contour search
    //   if the criteria are met, select the block and add it to the list of blocks to be search next round.
    //   if the criteria aren't met, keep trying other directions from the same position until all positions are searched.  Then delete the search position and move onto the next.
    //   This will ensure that the fill spreads evenly out from the starting point.   Check the boundary to stop fill spreading outside it.

    while (!currentSearchPositions.isEmpty()) {
      SearchPosition currentSearchPosition = currentSearchPositions.getFirst();
      checkPosition.set(currentSearchPosition.chunkCoordinates.posX + searchDirectionsX[currentSearchPosition.nextSearchDirection],
              currentSearchPosition.chunkCoordinates.posY + searchDirectionsY[currentSearchPosition.nextSearchDirection],
              currentSearchPosition.chunkCoordinates.posZ + searchDirectionsZ[currentSearchPosition.nextSearchDirection]);
      if (    checkPosition.posX >= 0 && checkPosition.posX < xSize
              &&  checkPosition.posY >= 0 && checkPosition.posY < ySize
              &&  checkPosition.posZ >= 0 && checkPosition.posZ < zSize
              && !selection.getVoxel(checkPosition.posX, checkPosition.posY, checkPosition.posZ)) {
        int blockToCheckID = world.getBlockId(checkPosition.posX + wxOrigin, checkPosition.posY + wyOrigin, checkPosition.posZ + wzOrigin);
        if (blockToCheckID != 0) {
          ChunkCoordinates newChunkCoordinate = new ChunkCoordinates(checkPosition);
          SearchPosition nextSearchPosition = new SearchPosition(newChunkCoordinate);
          nextDepthSearchPositions.addLast(nextSearchPosition);
          selection.setVoxel(checkPosition.posX, checkPosition.posY, checkPosition.posZ);
          expandVoxelRange(checkPosition.posX, checkPosition.posY, checkPosition.posZ);
          ++blocksAddedCount;
//          checkedLocations.setVoxel(checkPosition.posX, checkPosition.posY, checkPosition.posZ);
        }
      }
      ++currentSearchPosition.nextSearchDirection;
      if (currentSearchPosition.nextSearchDirection >= ALL_DIRECTIONS) {
        currentSearchPositions.removeFirst();
        if (currentSearchPositions.isEmpty()) {
          Deque<SearchPosition> temp = currentSearchPositions;
          currentSearchPositions = nextDepthSearchPositions;
          nextDepthSearchPositions = temp;
        }
      }
      if (System.nanoTime() - startTime >= maxTimeInNS) {   // completion fraction is hard to predict, so use a logarithmic function instead to provide some visual movement regardless of size
        if (blocksAddedCount == 0) return 0;
        double fillFraction = blocksAddedCount / (double)(xSize * ySize * zSize);

        final double FULL_SCALE = Math.log(1.0 / (xSize * ySize * zSize)) - 1;
        double fractionComplete = (1 - Math.log(fillFraction) / FULL_SCALE);
        return (float)fractionComplete;
      }
    }

    mode = OperationInProgress.COMPLETE;
    shrinkToSmallestEnclosingCuboid();
//    checkedLocations = null;
    return -1;
  }

  Deque<SearchPosition> currentSearchPositions = new LinkedList<SearchPosition>();
  Deque<SearchPosition> nextDepthSearchPositions = new LinkedList<SearchPosition>();
  int blocksAddedCount;
//  VoxelSelection checkedLocations;

  public static class SearchPosition
  {
    public SearchPosition(ChunkCoordinates initChunkCoordinates) {
      chunkCoordinates = initChunkCoordinates;
      nextSearchDirection = 0;
    }
    public ChunkCoordinates chunkCoordinates;
    public int nextSearchDirection;
  }

  /**
   * returns true if there are no solid pixels at all in this selection.
   * @return
   */
  public boolean isEmpty()
  {
    return empty;
  }

  /**
   * write the current selection in serialised form to a ByteArray
   * @return the byte array, or null for failure
   */
  public ByteArrayOutputStream writeToBytes()
  {
    return selection.writeToBytes();
  }

  private void initialiseVoxelRange()
  {
    smallestVoxelX = xSize;
    largestVoxelX = -1;
    smallestVoxelY = ySize;
    largestVoxelY = -1;
    smallestVoxelZ = zSize;
    largestVoxelZ = -1;
    empty = true;
  }

  private void expandVoxelRange(int x, int y, int z)
  {
    smallestVoxelX = Math.min(smallestVoxelX, x);
    smallestVoxelY = Math.min(smallestVoxelY, y);
    smallestVoxelZ = Math.min(smallestVoxelZ, z);
    largestVoxelX = Math.max(largestVoxelX, x);
    largestVoxelY = Math.max(largestVoxelY, y);
    largestVoxelZ = Math.max(largestVoxelZ, z);
    empty = false;
  }

  /**
   * shrinks the voxel selection to the minimum size needed to contain the set voxels
   */
  private void shrinkToSmallestEnclosingCuboid()
  {
    if (smallestVoxelX == 0 && smallestVoxelY == 0 && smallestVoxelZ == 0
        && largestVoxelX == xSize-1 && largestVoxelY == ySize-1 && largestVoxelZ == zSize-1) {
      return;
    }

    int newXsize = largestVoxelX - smallestVoxelX + 1;
    int newYsize = largestVoxelY - smallestVoxelY + 1;
    int newZsize = largestVoxelZ - smallestVoxelZ + 1;
    VoxelSelectionWithOrigin smallerSelection = new VoxelSelectionWithOrigin(
            wxOrigin + smallestVoxelX, wyOrigin + smallestVoxelY, wzOrigin + smallestVoxelZ,
            newXsize, newYsize, newZsize);
    for (int y = 0; y < newYsize; ++y) {
      for (int z = 0; z < newZsize; ++z) {
        for (int x = 0; x < newXsize; ++x) {
          if (selection.getVoxel(x + smallestVoxelX, y + smallestVoxelY, z + smallestVoxelZ)) {
            smallerSelection.setVoxel(x, y, z);
          }
        }
      }
    }
    selection = smallerSelection;
    wxOrigin += smallestVoxelX;
    wyOrigin += smallestVoxelY;
    wzOrigin += smallestVoxelZ;
    smallestVoxelX = 0;
    smallestVoxelY = 0;
    smallestVoxelZ = 0;
    largestVoxelX = newXsize - 1;
    largestVoxelY = newYsize - 1;
    largestVoxelZ = newZsize - 1;
    xSize = newXsize;
    ySize = newYsize;
    zSize = newZsize;
  }

  private void initialiseSelectionSizeFromBoundary(ChunkCoordinates corner1, ChunkCoordinates corner2)
  {
    wxOrigin = Math.min(corner1.posX, corner2.posX);
    wyOrigin = Math.min(corner1.posY, corner2.posY);
    wzOrigin = Math.min(corner1.posZ, corner2.posZ);
    xSize = 1 + Math.max(corner1.posX, corner2.posX) - wxOrigin;
    ySize = 1 + Math.max(corner1.posY, corner2.posY) - wyOrigin;
    zSize = 1 + Math.max(corner1.posZ, corner2.posZ) - wzOrigin;
    if (selection == null) {
      selection = new VoxelSelectionWithOrigin(wxOrigin, wyOrigin, wzOrigin, xSize, ySize, zSize);
//      shadow = new VoxelSelection(xSize, 1, zSize);
    } else {
      selection.resizeAndClear(xSize, ySize, zSize);
//      shadow.clearAll(xSize, 1, zSize);
    }
  }

  /** gets the origin for the selection in world coordinates
   * @return the origin for the selection in world coordinates
   */
  public ChunkCoordinates getWorldOrigin()
  {
    return new ChunkCoordinates(selection.getWxOrigin(), selection.getWyOrigin(), selection.getWzOrigin());
  }

  /**
   * create a render list for the current selection.
   * Quads, with lines to outline them
   * @param world
   */
  public void createRenderList(World world)
  {
    if (displayListSelection == 0) {
      displayListSelection = GLAllocation.generateDisplayLists(1);
    }
    if (displayListSelection == 0) {
      FMLLog.warning("Unable to create a displayList in BlockVoxelMultiSelector::createRenderList");
      return;
    }

    if (selection == null) {
      GL11.glNewList(displayListSelection, GL11.GL_COMPILE);
      GL11.glEndList();
      return;
    }

    final double NUDGE_DISTANCE = 0.01;
    final double FRAME_NUDGE_DISTANCE = NUDGE_DISTANCE + 0.002;

    GL11.glNewList(displayListSelection, GL11.GL_COMPILE);
    GL11.glPushAttrib(GL11.GL_ENABLE_BIT);
    GL11.glDisable(GL11.GL_CULL_FACE);
    GL11.glDisable(GL11.GL_LIGHTING);
    GL11.glEnable(GL11.GL_TEXTURE_2D);
//    GL11.glDisable(GL11.GL_BLEND);
//    GL11.glDisable(GL11.GL_COLOR_LOGIC_OP);
//    GL11.glDisable(GL11.GL_FOG);
//    GL11.glDisable(GL11.GL_COLOR_MATERIAL);
//    GL11.glDisable(GL11.GL_DITHER);
//    GL11.glDisable(GL11.GL_POLYGON_SMOOTH);
//    GL11.glShadeModel(GL11.GL_FLAT);

    Tessellator tessellator = Tessellator.instance;

//    GL11.glEnable(GL11.GL_BLEND);
//    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
//    GL11.glColor4f(Colour.PINK_100.R, Colour.PINK_100.G, Colour.PINK_100.B, Colour.WHITE_40.A); // Colour.PINK_100.A);
//    GL11.glColor4f(Colour.WHITE_40.R, Colour.WHITE_40.G, Colour.WHITE_40.B, Colour.PINK_100.A);

//      GL11.glColor4f(Colour.WHITE_40.R, Colour.WHITE_40.G, Colour.WHITE_40.B, Colour.PINK_100.A);
//    tessellateSurface(tessellator, false, NUDGE_DISTANCE);
    tessellateSurfaceWithTexture(world, tessellator, false, NUDGE_DISTANCE);

    GL11.glEnable(GL11.GL_BLEND);
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    GL11.glColor4f(Colour.BLACK_40.R, Colour.BLACK_40.G, Colour.BLACK_40.B, Colour.BLACK_40.A);
    GL11.glLineWidth(2.0F);
    GL11.glDisable(GL11.GL_TEXTURE_2D);
    GL11.glDepthMask(false);

    tessellateSurface(tessellator, true, FRAME_NUDGE_DISTANCE);

    GL11.glDepthMask(true);
    GL11.glPopAttrib();
    GL11.glEndList();

    createMeshRenderLists(world);
  }

  private void tessellateSurface(Tessellator tessellator, boolean drawingWireFrame, double nudgeDistance)
  {
    int xNegNudge, xPosNudge, yNegNudge, yPosNudge, zNegNudge, zPosNudge;

    if (!drawingWireFrame) {
      tessellator.startDrawingQuads();
    }

    // goes outside the VoxelSelection size, which always returns zero when out of bounds
    // "nudges" the quad boundaries to make solid blocks slightly larger, avoids annoying visual artifacts when overlapping with world blocks
    // three cases of nudge for each edge: internal (concave) edges = nudge inwards (-1), flat = no nudge (0), outer (convex) = nudge outwards (+1)

    for (int y = 0; y <= ySize; ++y) {
      for (int z = 0; z <= zSize; ++z) {
        for (int x = 0; x <= xSize; ++x) {
          if (selection.getVoxel(x, y, z)) {
            // xneg face
            if (!selection.getVoxel(x - 1, y, z)) {
              yNegNudge = (selection.getVoxel(x-1, y-1, z+0) ? -1 : (selection.getVoxel(x+0, y-1, z+0) ? 0 : 1) );
              yPosNudge = (selection.getVoxel(x-1, y+1, z+0) ? -1 : (selection.getVoxel(x+0, y+1, z+0) ? 0 : 1) );
              zNegNudge = (selection.getVoxel(x-1, y+0, z-1) ? -1 : (selection.getVoxel(x+0, y+0, z-1) ? 0 : 1) );
              zPosNudge = (selection.getVoxel(x-1, y+0, z+1) ? -1 : (selection.getVoxel(x+0, y+0, z+1) ? 0 : 1) );

              if (drawingWireFrame) tessellator.startDrawing(GL11.GL_LINE_LOOP);
              tessellator.addVertex(x - nudgeDistance, y     - yNegNudge * nudgeDistance, z     - zNegNudge * nudgeDistance);
              tessellator.addVertex(x - nudgeDistance, y + 1 + yPosNudge * nudgeDistance, z     - zNegNudge * nudgeDistance);
              tessellator.addVertex(x - nudgeDistance, y + 1 + yPosNudge * nudgeDistance, z + 1 + zPosNudge * nudgeDistance);
              tessellator.addVertex(x - nudgeDistance, y     - yNegNudge * nudgeDistance, z + 1 + zPosNudge * nudgeDistance);
              if (drawingWireFrame) tessellator.draw();
            }
            // xpos face
            if (!selection.getVoxel(x + 1, y, z)) {
              yNegNudge = (selection.getVoxel(x+1, y-1, z+0) ? -1 : (selection.getVoxel(x+0, y-1, z+0) ? 0 : 1) );
              yPosNudge = (selection.getVoxel(x+1, y+1, z+0) ? -1 : (selection.getVoxel(x+0, y+1, z+0) ? 0 : 1) );
              zNegNudge = (selection.getVoxel(x+1, y+0, z-1) ? -1 : (selection.getVoxel(x+0, y+0, z-1) ? 0 : 1) );
              zPosNudge = (selection.getVoxel(x+1, y+0, z+1) ? -1 : (selection.getVoxel(x+0, y+0, z+1) ? 0 : 1) );

              if (drawingWireFrame) tessellator.startDrawing(GL11.GL_LINE_LOOP);
              tessellator.addVertex(x + 1 + nudgeDistance, y     - yNegNudge * nudgeDistance, z     - zNegNudge * nudgeDistance);
              tessellator.addVertex(x + 1 + nudgeDistance, y + 1 + yPosNudge * nudgeDistance, z     - zNegNudge * nudgeDistance);
              tessellator.addVertex(x + 1 + nudgeDistance, y + 1 + yPosNudge * nudgeDistance, z + 1 + zPosNudge * nudgeDistance);
              tessellator.addVertex(x + 1 + nudgeDistance, y     - yNegNudge * nudgeDistance, z + 1 + zPosNudge * nudgeDistance);
              if (drawingWireFrame) tessellator.draw();
            }
            // yneg face
            if (!selection.getVoxel(x, y-1, z)) {
              xNegNudge = (selection.getVoxel(x-1, y-1, z+0) ? -1 : (selection.getVoxel(x-1, y+0, z+0) ? 0 : 1) );
              xPosNudge = (selection.getVoxel(x+1, y-1, z+0) ? -1 : (selection.getVoxel(x+1, y+0, z+0) ? 0 : 1) );
              zNegNudge = (selection.getVoxel(x+0, y-1, z-1) ? -1 : (selection.getVoxel(x+0, y+0, z-1) ? 0 : 1) );
              zPosNudge = (selection.getVoxel(x+0, y-1, z+1) ? -1 : (selection.getVoxel(x+0, y+0, z+1) ? 0 : 1) );

              if (drawingWireFrame) tessellator.startDrawing(GL11.GL_LINE_LOOP);
              tessellator.addVertex(x     - xNegNudge * nudgeDistance, y - nudgeDistance, z     - zNegNudge * nudgeDistance);
              tessellator.addVertex(x + 1 + xPosNudge * nudgeDistance, y - nudgeDistance, z     - zNegNudge * nudgeDistance);
              tessellator.addVertex(x + 1 + xPosNudge * nudgeDistance, y - nudgeDistance, z + 1 + zPosNudge * nudgeDistance);
              tessellator.addVertex(x     - xNegNudge * nudgeDistance, y - nudgeDistance, z + 1 + zPosNudge * nudgeDistance);
              if (drawingWireFrame) tessellator.draw();
            }
            // ypos face
            if (!selection.getVoxel(x, y+1, z)) {
              xNegNudge = (selection.getVoxel(x-1, y+1, z+0) ? -1 : (selection.getVoxel(x-1, y+0, z+0) ? 0 : 1) );
              xPosNudge = (selection.getVoxel(x+1, y+1, z+0) ? -1 : (selection.getVoxel(x+1, y+0, z+0) ? 0 : 1) );
              zNegNudge = (selection.getVoxel(x+0, y+1, z-1) ? -1 : (selection.getVoxel(x+0, y+0, z-1) ? 0 : 1) );
              zPosNudge = (selection.getVoxel(x+0, y+1, z+1) ? -1 : (selection.getVoxel(x+0, y+0, z+1) ? 0 : 1) );

              if (drawingWireFrame) tessellator.startDrawing(GL11.GL_LINE_LOOP);
              tessellator.addVertex(x     - xNegNudge * nudgeDistance, y + 1 + nudgeDistance, z     - zNegNudge * nudgeDistance);
              tessellator.addVertex(x + 1 + xPosNudge * nudgeDistance, y + 1 + nudgeDistance, z     - zNegNudge * nudgeDistance);
              tessellator.addVertex(x + 1 + xPosNudge * nudgeDistance, y + 1 + nudgeDistance, z + 1 + zPosNudge * nudgeDistance);
              tessellator.addVertex(x     - xNegNudge * nudgeDistance, y + 1 + nudgeDistance, z + 1 + zPosNudge * nudgeDistance);
              if (drawingWireFrame) tessellator.draw();
            }
            // zneg face
            if (!selection.getVoxel(x, y, z-1)) {
              xNegNudge = (selection.getVoxel(x-1, y+0, z-1) ? -1 : (selection.getVoxel(x-1, y+0, z+0) ? 0 : 1) );
              xPosNudge = (selection.getVoxel(x+1, y+0, z-1) ? -1 : (selection.getVoxel(x+1, y+0, z+0) ? 0 : 1) );
              yNegNudge = (selection.getVoxel(x+0, y-1, z-1) ? -1 : (selection.getVoxel(x+0, y-1, z+0) ? 0 : 1) );
              yPosNudge = (selection.getVoxel(x+0, y+1, z-1) ? -1 : (selection.getVoxel(x+0, y+1, z+0) ? 0 : 1) );

              if (drawingWireFrame) tessellator.startDrawing(GL11.GL_LINE_LOOP);
              tessellator.addVertex(x     - xNegNudge * nudgeDistance, y     - yNegNudge * nudgeDistance, z - nudgeDistance);
              tessellator.addVertex(x + 1 + xPosNudge * nudgeDistance, y     - yNegNudge * nudgeDistance, z - nudgeDistance);
              tessellator.addVertex(x + 1 + xPosNudge * nudgeDistance, y + 1 + yPosNudge * nudgeDistance, z - nudgeDistance);
              tessellator.addVertex(x     - xNegNudge * nudgeDistance, y + 1 + yPosNudge * nudgeDistance, z - nudgeDistance);
              if (drawingWireFrame) tessellator.draw();
            }
            // zpos face
            if (!selection.getVoxel(x, y, z+1)) {
              xNegNudge = (selection.getVoxel(x-1, y+0, z+1) ? -1 : (selection.getVoxel(x-1, y+0, z+0) ? 0 : 1) );
              xPosNudge = (selection.getVoxel(x+1, y+0, z+1) ? -1 : (selection.getVoxel(x+1, y+0, z+0) ? 0 : 1) );
              yNegNudge = (selection.getVoxel(x+0, y-1, z+1) ? -1 : (selection.getVoxel(x+0, y-1, z+0) ? 0 : 1) );
              yPosNudge = (selection.getVoxel(x+0, y+1, z+1) ? -1 : (selection.getVoxel(x+0, y+1, z+0) ? 0 : 1) );

              if (drawingWireFrame) tessellator.startDrawing(GL11.GL_LINE_LOOP);
              tessellator.addVertex(x     - xNegNudge * nudgeDistance, y     - yNegNudge * nudgeDistance, z + 1 + nudgeDistance);
              tessellator.addVertex(x + 1 + xPosNudge * nudgeDistance, y     - yNegNudge * nudgeDistance, z + 1 + nudgeDistance);
              tessellator.addVertex(x + 1 + xPosNudge * nudgeDistance, y + 1 + yPosNudge * nudgeDistance, z + 1 + nudgeDistance);
              tessellator.addVertex(x     - xNegNudge * nudgeDistance, y + 1 + yPosNudge * nudgeDistance, z + 1 + nudgeDistance);
              if (drawingWireFrame) tessellator.draw();
            }
          }
        }
      }
    }

    if (!drawingWireFrame) {
      tessellator.draw();
    }
  }

  private void tessellateSurfaceWithTexture(World world, Tessellator tessellator, boolean drawingWireFrame, double nudgeDistance)
  {
    int xNegNudge, xPosNudge, yNegNudge, yPosNudge, zNegNudge, zPosNudge;

    if (!drawingWireFrame) {
      tessellator.startDrawingQuads();
    }

    // goes outside the VoxelSelection size, which always returns zero when out of bounds
    // "nudges" the quad boundaries to make solid blocks slightly larger, avoids annoying visual artifacts when overlapping with world blocks
    // three cases of nudge for each edge: internal (concave) edges = nudge inwards (-1), flat = no nudge (0), outer (convex) = nudge outwards (+1)

//    tessellator.setBrightness(this.renderMinY > 0.0D ? l : par1Block.getMixedBrightnessForBlock(this.blockAccess, par2, par3 - 1, par4));
//    tessellator.setColorOpaque_F(1.0F, 1.0F, 1.0F);

    for (int y = 0; y <= ySize; ++y) {
      for (int z = 0; z <= zSize; ++z) {
        for (int x = 0; x <= xSize; ++x) {
          if (selection.getVoxel(x, y, z)) {
            int wx = x + wxOrigin;
            int wy = y + wyOrigin;
            int wz = z + wzOrigin;
            int blockID = world.getBlockId(wx, wy, wz);
//            int metaData = world.getBlockMetadata(wx, wy, wz);
            Block block = Block.blocksList[blockID];
            if (block == null) block = RegistryForBlocks.blockSelectionFog;

            // xneg face
            if (!selection.getVoxel(x - 1, y, z)) {
              yNegNudge = (selection.getVoxel(x-1, y-1, z+0) ? -1 : (selection.getVoxel(x+0, y-1, z+0) ? 0 : 1) );
              yPosNudge = (selection.getVoxel(x-1, y+1, z+0) ? -1 : (selection.getVoxel(x+0, y+1, z+0) ? 0 : 1) );
              zNegNudge = (selection.getVoxel(x-1, y+0, z-1) ? -1 : (selection.getVoxel(x+0, y+0, z-1) ? 0 : 1) );
              zPosNudge = (selection.getVoxel(x-1, y+0, z+1) ? -1 : (selection.getVoxel(x+0, y+0, z+1) ? 0 : 1) );
              Icon icon = block.getBlockTexture(world, wx, wy, wz, UsefulConstants.FACE_XNEG);

              if (drawingWireFrame) tessellator.startDrawing(GL11.GL_LINE_LOOP);
              tessellator.addVertexWithUV(x - nudgeDistance, y - yNegNudge * nudgeDistance, z - zNegNudge * nudgeDistance, icon.getMinU(), icon.getMaxV());
              tessellator.addVertexWithUV(x - nudgeDistance, y + 1 + yPosNudge * nudgeDistance, z - zNegNudge * nudgeDistance, icon.getMinU(), icon.getMinV());
              tessellator.addVertexWithUV(x - nudgeDistance, y + 1 + yPosNudge * nudgeDistance, z + 1 + zPosNudge * nudgeDistance, icon.getMaxU(), icon.getMinV());
              tessellator.addVertexWithUV(x - nudgeDistance, y - yNegNudge * nudgeDistance, z + 1 + zPosNudge * nudgeDistance, icon.getMaxU(), icon.getMaxV());
              if (drawingWireFrame) tessellator.draw();
            }
            // xpos face
            if (!selection.getVoxel(x + 1, y, z)) {
              yNegNudge = (selection.getVoxel(x+1, y-1, z+0) ? -1 : (selection.getVoxel(x+0, y-1, z+0) ? 0 : 1) );
              yPosNudge = (selection.getVoxel(x+1, y+1, z+0) ? -1 : (selection.getVoxel(x+0, y+1, z+0) ? 0 : 1) );
              zNegNudge = (selection.getVoxel(x+1, y+0, z-1) ? -1 : (selection.getVoxel(x+0, y+0, z-1) ? 0 : 1) );
              zPosNudge = (selection.getVoxel(x+1, y+0, z+1) ? -1 : (selection.getVoxel(x+0, y+0, z+1) ? 0 : 1) );
              Icon icon = block.getBlockTexture(world, wx, wy, wz, UsefulConstants.FACE_XPOS);

              if (drawingWireFrame) tessellator.startDrawing(GL11.GL_LINE_LOOP);
              tessellator.addVertexWithUV(x + 1 + nudgeDistance, y - yNegNudge * nudgeDistance, z - zNegNudge * nudgeDistance, icon.getMinU(), icon.getMaxV());
              tessellator.addVertexWithUV(x + 1 + nudgeDistance, y + 1 + yPosNudge * nudgeDistance, z - zNegNudge * nudgeDistance, icon.getMinU(), icon.getMinV());
              tessellator.addVertexWithUV(x + 1 + nudgeDistance, y + 1 + yPosNudge * nudgeDistance, z + 1 + zPosNudge * nudgeDistance, icon.getMaxU(), icon.getMinV());
              tessellator.addVertexWithUV(x + 1 + nudgeDistance, y - yNegNudge * nudgeDistance, z + 1 + zPosNudge * nudgeDistance, icon.getMaxU(), icon.getMaxV());
              if (drawingWireFrame) tessellator.draw();
            }
            // yneg face
            if (!selection.getVoxel(x, y-1, z)) {
              xNegNudge = (selection.getVoxel(x-1, y-1, z+0) ? -1 : (selection.getVoxel(x-1, y+0, z+0) ? 0 : 1) );
              xPosNudge = (selection.getVoxel(x+1, y-1, z+0) ? -1 : (selection.getVoxel(x+1, y+0, z+0) ? 0 : 1) );
              zNegNudge = (selection.getVoxel(x+0, y-1, z-1) ? -1 : (selection.getVoxel(x+0, y+0, z-1) ? 0 : 1) );
              zPosNudge = (selection.getVoxel(x+0, y-1, z+1) ? -1 : (selection.getVoxel(x+0, y+0, z+1) ? 0 : 1) );
              Icon icon = block.getBlockTexture(world, wx, wy, wz, UsefulConstants.FACE_YNEG);
                                                                                                                              // NB yneg face is flipped left-right in vanilla
              if (drawingWireFrame) tessellator.startDrawing(GL11.GL_LINE_LOOP);
              tessellator.addVertexWithUV(x - xNegNudge * nudgeDistance, y - nudgeDistance, z - zNegNudge * nudgeDistance, icon.getMaxU(), icon.getMaxV());
              tessellator.addVertexWithUV(x + 1 + xPosNudge * nudgeDistance, y - nudgeDistance, z - zNegNudge * nudgeDistance, icon.getMinU(), icon.getMaxV());
              tessellator.addVertexWithUV(x + 1 + xPosNudge * nudgeDistance, y - nudgeDistance, z + 1 + zPosNudge * nudgeDistance, icon.getMinU(), icon.getMinV());
              tessellator.addVertexWithUV(x - xNegNudge * nudgeDistance, y - nudgeDistance, z + 1 + zPosNudge * nudgeDistance, icon.getMaxU(), icon.getMinV());
              if (drawingWireFrame) tessellator.draw();
            }
            // ypos face
            if (!selection.getVoxel(x, y+1, z)) {
              xNegNudge = (selection.getVoxel(x-1, y+1, z+0) ? -1 : (selection.getVoxel(x-1, y+0, z+0) ? 0 : 1) );
              xPosNudge = (selection.getVoxel(x+1, y+1, z+0) ? -1 : (selection.getVoxel(x+1, y+0, z+0) ? 0 : 1) );
              zNegNudge = (selection.getVoxel(x+0, y+1, z-1) ? -1 : (selection.getVoxel(x+0, y+0, z-1) ? 0 : 1) );
              zPosNudge = (selection.getVoxel(x+0, y+1, z+1) ? -1 : (selection.getVoxel(x+0, y+0, z+1) ? 0 : 1) );
              Icon icon = block.getBlockTexture(world, wx, wy, wz, UsefulConstants.FACE_YPOS);

              if (drawingWireFrame) tessellator.startDrawing(GL11.GL_LINE_LOOP);
              tessellator.addVertexWithUV(x - xNegNudge * nudgeDistance, y + 1 + nudgeDistance, z - zNegNudge * nudgeDistance, icon.getMinU(), icon.getMaxV());
              tessellator.addVertexWithUV(x + 1 + xPosNudge * nudgeDistance, y + 1 + nudgeDistance, z - zNegNudge * nudgeDistance, icon.getMaxU(), icon.getMaxV());
              tessellator.addVertexWithUV(x + 1 + xPosNudge * nudgeDistance, y + 1 + nudgeDistance, z + 1 + zPosNudge * nudgeDistance, icon.getMaxU(), icon.getMinV());
              tessellator.addVertexWithUV(x - xNegNudge * nudgeDistance, y + 1 + nudgeDistance, z + 1 + zPosNudge * nudgeDistance, icon.getMinU(), icon.getMinV());
              if (drawingWireFrame) tessellator.draw();
            }
            // zneg face
            if (!selection.getVoxel(x, y, z-1)) {
              xNegNudge = (selection.getVoxel(x-1, y+0, z-1) ? -1 : (selection.getVoxel(x-1, y+0, z+0) ? 0 : 1) );
              xPosNudge = (selection.getVoxel(x+1, y+0, z-1) ? -1 : (selection.getVoxel(x+1, y+0, z+0) ? 0 : 1) );
              yNegNudge = (selection.getVoxel(x+0, y-1, z-1) ? -1 : (selection.getVoxel(x+0, y-1, z+0) ? 0 : 1) );
              yPosNudge = (selection.getVoxel(x+0, y+1, z-1) ? -1 : (selection.getVoxel(x+0, y+1, z+0) ? 0 : 1) );
              Icon icon = block.getBlockTexture(world, wx, wy, wz, UsefulConstants.FACE_ZNEG);

              if (drawingWireFrame) tessellator.startDrawing(GL11.GL_LINE_LOOP);
              tessellator.addVertexWithUV(x - xNegNudge * nudgeDistance, y - yNegNudge * nudgeDistance, z - nudgeDistance, icon.getMaxU(), icon.getMaxV());
              tessellator.addVertexWithUV(x + 1 + xPosNudge * nudgeDistance, y - yNegNudge * nudgeDistance, z - nudgeDistance, icon.getMinU(), icon.getMaxV());
              tessellator.addVertexWithUV(x + 1 + xPosNudge * nudgeDistance, y + 1 + yPosNudge * nudgeDistance, z - nudgeDistance, icon.getMinU(), icon.getMinV());
              tessellator.addVertexWithUV(x - xNegNudge * nudgeDistance, y + 1 + yPosNudge * nudgeDistance, z - nudgeDistance, icon.getMaxU(), icon.getMinV());
              if (drawingWireFrame) tessellator.draw();
            }
            // zpos face
            if (!selection.getVoxel(x, y, z+1)) {
              xNegNudge = (selection.getVoxel(x-1, y+0, z+1) ? -1 : (selection.getVoxel(x-1, y+0, z+0) ? 0 : 1) );
              xPosNudge = (selection.getVoxel(x+1, y+0, z+1) ? -1 : (selection.getVoxel(x+1, y+0, z+0) ? 0 : 1) );
              yNegNudge = (selection.getVoxel(x+0, y-1, z+1) ? -1 : (selection.getVoxel(x+0, y-1, z+0) ? 0 : 1) );
              yPosNudge = (selection.getVoxel(x+0, y+1, z+1) ? -1 : (selection.getVoxel(x+0, y+1, z+0) ? 0 : 1) );
              Icon icon = block.getBlockTexture(world, wx, wy, wz, UsefulConstants.FACE_ZNEG);

              if (drawingWireFrame) tessellator.startDrawing(GL11.GL_LINE_LOOP);
              tessellator.addVertexWithUV(x - xNegNudge * nudgeDistance, y - yNegNudge * nudgeDistance, z + 1 + nudgeDistance, icon.getMinU(), icon.getMaxV());
              tessellator.addVertexWithUV(x + 1 + xPosNudge * nudgeDistance, y - yNegNudge * nudgeDistance, z + 1 + nudgeDistance, icon.getMaxU(), icon.getMaxV());
              tessellator.addVertexWithUV(x + 1 + xPosNudge * nudgeDistance, y + 1 + yPosNudge * nudgeDistance, z + 1 + nudgeDistance, icon.getMaxU(), icon.getMinV());
              tessellator.addVertexWithUV(x - xNegNudge * nudgeDistance, y + 1 + yPosNudge * nudgeDistance, z + 1 + nudgeDistance, icon.getMinU(), icon.getMinV());
              if (drawingWireFrame) tessellator.draw();
            }
          }
        }
      }
    }

    if (!drawingWireFrame) {
      tessellator.draw();
    }
  }


/*
          boolean voxelIsSolid = selection.getVoxel(x, y, z);
          solidIdx = voxelIsSolid ? 0 : -1;
          emptyIdx = -1 - solidIdx;
          if (voxelIsSolid != selection.getVoxel(x-1, y, z)) {
            xPosNudge = x + (voxelIsSolid ? -NUDGE_DISTANCE : +NUDGE_DISTANCE);
            if (selection.getVoxel(x + emptyIdx, y - 1, z)) {
              yNegNudge = +NUDGE_DISTANCE;
            } else if (selection.getVoxel(x + solidIdx, y - 1, z)) {
              yPosNudge = - NUDGE_DISTANCE;
            } else {
              yNegNudge = 0;
            }
            if (selection.getVoxel(x + emptyIdx, y - 1, z)) {
              yNegNudge = +NUDGE_DISTANCE;
            } else if (selection.getVoxel(x + solidIdx, y - 1, z)) {
              yPosNudge = - NUDGE_DISTANCE;
            } else {
              yNegNudge = 0;
            }

            yPosNudge = y + 1 + (selection.getVoxel(emptyIdx, y + 1,     z) ? 0 : NUDGE_DISTANCE);
            zNegNudge = z     - (selection.getVoxel(emptyIdx,     y, z - 1) ? 0 : NUDGE_DISTANCE);
            zPosNudge = z + 1 + (selection.getVoxel(emptyIdx,     y, z + 1) ? 0 : NUDGE_DISTANCE);

            tessellator.addVertex(xPosNudge, yNegNudge, zNegNudge);
            tessellator.addVertex(xPosNudge, yPosNudge, zNegNudge);
            tessellator.addVertex(xPosNudge, yPosNudge, zPosNudge);
            tessellator.addVertex(xPosNudge, yNegNudge, zPosNudge);
          }
          if (voxelIsSolid != selection.getVoxel(x, y-1, z)) {
            emptyIdx = y     + (selection.getVoxel(x, y, z) ? -1 : 0);
            yPosNudge = y     + (selection.getVoxel(x, y, z) ? -NUDGE_DISTANCE : +NUDGE_DISTANCE);
            xNegNudge = x     - (selection.getVoxel(x - 1, emptyIdx,     z) ? 0 : NUDGE_DISTANCE);
            xPosNudge = x + 1 + (selection.getVoxel(x + 1, emptyIdx,     z) ? 0 : NUDGE_DISTANCE);
            zNegNudge = z     - (selection.getVoxel(    x, emptyIdx, z - 1) ? 0 : NUDGE_DISTANCE);
            zPosNudge = z + 1 + (selection.getVoxel(    x, emptyIdx, z + 1) ? 0 : NUDGE_DISTANCE);

            tessellator.addVertex(xNegNudge, yPosNudge, zNegNudge);
            tessellator.addVertex(xPosNudge, yPosNudge, zNegNudge);
            tessellator.addVertex(xPosNudge, yPosNudge, zPosNudge);
            tessellator.addVertex(xNegNudge, yPosNudge, zPosNudge);
         }
          if (voxelIsSolid != selection.getVoxel(x, y, z-1)) {
            emptyIdx = z     + (selection.getVoxel(x, y, z) ? -1 : 0);
            zPosNudge = z     + (selection.getVoxel(x, y, z) ? -NUDGE_DISTANCE : +NUDGE_DISTANCE);
            xNegNudge = x     - (selection.getVoxel(x - 1,     y, emptyIdx) ? 0 : NUDGE_DISTANCE);
            xPosNudge = x + 1 + (selection.getVoxel(x + 1,     y, emptyIdx) ? 0 : NUDGE_DISTANCE);
            yNegNudge = y     - (selection.getVoxel(    x, y - 1, emptyIdx) ? 0 : NUDGE_DISTANCE);
            yPosNudge = y + 1 + (selection.getVoxel(    x, y + 1, emptyIdx) ? 0 : NUDGE_DISTANCE);

            tessellator.addVertex(xNegNudge, yNegNudge, zPosNudge);
            tessellator.addVertex(xPosNudge, yNegNudge, zPosNudge);
            tessellator.addVertex(xPosNudge, yPosNudge, zPosNudge);
            tessellator.addVertex(xNegNudge, yPosNudge, zPosNudge);
          }
        }
      }
    }
    tessellator.draw();
*/

/*
    for (int y = 0; y <= ySize; ++y) {
      for (int z = 0; z <= zSize; ++z) {
        for (int x = 0; x <= xSize; ++x) {
          if (selection.getVoxel(x, y, z) != selection.getVoxel(x-1, y, z)) {
            emptyIdx = x     + (selection.getVoxel(x, y, z) ? -1 : 0);
            xPosNudge = x     + (selection.getVoxel(x, y, z) ? -FRAME_NUDGE_DISTANCE : +FRAME_NUDGE_DISTANCE);
            yNegNudge = y     - (selection.getVoxel(emptyIdx, y - 1,     z) ? 0 : FRAME_NUDGE_DISTANCE);
            yPosNudge = y + 1 + (selection.getVoxel(emptyIdx, y + 1,     z) ? 0 : FRAME_NUDGE_DISTANCE);
            zNegNudge = z     - (selection.getVoxel(emptyIdx,     y, z - 1) ? 0 : FRAME_NUDGE_DISTANCE);
            zPosNudge = z + 1 + (selection.getVoxel(emptyIdx,     y, z + 1) ? 0 : FRAME_NUDGE_DISTANCE);

            tessellator.startDrawing(GL11.GL_LINE_LOOP);
            tessellator.addVertex(xPosNudge, yNegNudge, zNegNudge);
            tessellator.addVertex(xPosNudge, yPosNudge, zNegNudge);
            tessellator.addVertex(xPosNudge, yPosNudge, zPosNudge);
            tessellator.addVertex(xPosNudge, yNegNudge, zPosNudge);
            tessellator.draw();
          }
          if (selection.getVoxel(x, y, z) != selection.getVoxel(x, y-1, z)) {
            emptyIdx = y     + (selection.getVoxel(x, y, z) ? -1 : 0);
            yPosNudge = y     + (selection.getVoxel(x, y, z) ? -FRAME_NUDGE_DISTANCE : +FRAME_NUDGE_DISTANCE);
            xNegNudge = x     - (selection.getVoxel(x - 1, emptyIdx,     z) ? 0 : FRAME_NUDGE_DISTANCE);
            xPosNudge = x + 1 + (selection.getVoxel(x + 1, emptyIdx,     z) ? 0 : FRAME_NUDGE_DISTANCE);
            zNegNudge = z     - (selection.getVoxel(    x, emptyIdx, z - 1) ? 0 : FRAME_NUDGE_DISTANCE);
            zPosNudge = z + 1 + (selection.getVoxel(    x, emptyIdx, z + 1) ? 0 : FRAME_NUDGE_DISTANCE);

            tessellator.startDrawing(GL11.GL_LINE_LOOP);
            tessellator.addVertex(xNegNudge, yPosNudge, zNegNudge);
            tessellator.addVertex(xPosNudge, yPosNudge, zNegNudge);
            tessellator.addVertex(xPosNudge, yPosNudge, zPosNudge);
            tessellator.addVertex(xNegNudge, yPosNudge, zPosNudge);
            tessellator.draw();
          }
          if (selection.getVoxel(x, y, z) != selection.getVoxel(x, y, z-1)) {
            emptyIdx = z     + (selection.getVoxel(x, y, z) ? -1 : 0);
            zPosNudge = z     + (selection.getVoxel(x, y, z) ? -FRAME_NUDGE_DISTANCE : +FRAME_NUDGE_DISTANCE);
            xNegNudge = x     - (selection.getVoxel(x - 1,     y, emptyIdx) ? 0 : FRAME_NUDGE_DISTANCE);
            xPosNudge = x + 1 + (selection.getVoxel(x + 1,     y, emptyIdx) ? 0 : FRAME_NUDGE_DISTANCE);
            yNegNudge = y     - (selection.getVoxel(    x, y - 1, emptyIdx) ? 0 : FRAME_NUDGE_DISTANCE);
            yPosNudge = y + 1 + (selection.getVoxel(    x, y + 1, emptyIdx) ? 0 : FRAME_NUDGE_DISTANCE);

            tessellator.startDrawing(GL11.GL_LINE_LOOP);
            tessellator.addVertex(xNegNudge, yNegNudge, zPosNudge);
            tessellator.addVertex(xPosNudge, yNegNudge, zPosNudge);
            tessellator.addVertex(xPosNudge, yPosNudge, zPosNudge);
            tessellator.addVertex(xNegNudge, yPosNudge, zPosNudge);
            tessellator.draw();
          }
        }
      }
    }
*/

/*
    // goes outside the VoxelSelection size, which always returns zero when out of bounds
    for (int y = 0; y <= ySize; ++y) {
      for (int z = 0; z <= zSize; ++z) {
        for (int x = 0; x <= xSize; ++x) {
          if (selection.getVoxel(x, y, z) != selection.getVoxel(x-1, y, z)) {
            double xNudge = x + (selection.getVoxel(x, y, z) ? -NUDGE_DISTANCE : +NUDGE_DISTANCE);
            tessellator.startDrawing(GL11.GL_LINE_LOOP);
            tessellator.addVertex(xNudge,   y,   z);
            tessellator.addVertex(xNudge, y+1,   z);
            tessellator.addVertex(xNudge, y+1, z+1);
            tessellator.addVertex(xNudge,   y, z+1);
            tessellator.draw();
          }
          if (selection.getVoxel(x, y, z) != selection.getVoxel(x, y-1, z)) {
            double yNudge = y + (selection.getVoxel(x, y, z) ? -NUDGE_DISTANCE : +NUDGE_DISTANCE);
            tessellator.startDrawing(GL11.GL_LINE_LOOP);
            tessellator.addVertex(  x, yNudge,   z);
            tessellator.addVertex(x+1, yNudge,   z);
            tessellator.addVertex(x+1, yNudge, z+1);
            tessellator.addVertex(  x, yNudge, z+1);
            tessellator.draw();
          }
          if (selection.getVoxel(x, y, z) != selection.getVoxel(x, y, z-1)) {
            double zNudge = z + (selection.getVoxel(x, y, z) ? -NUDGE_DISTANCE : +NUDGE_DISTANCE);
            tessellator.startDrawing(GL11.GL_LINE_LOOP);
            tessellator.addVertex(  x,   y, zNudge);
            tessellator.addVertex(  x, y+1, zNudge);
            tessellator.addVertex(x+1, y+1, zNudge);
            tessellator.addVertex(x+1,   y, zNudge);
            tessellator.draw();
          }
        }
      }
    }
*/

  /**
   * a vertical wireframe mesh made up of 1x1 squares, in the xy, yz, or xz plane
   * origin is the top (ymax) corner, i.e. the topmost square is [0,0,0], the bottommost square is +xcount, -ycount, zcount
   * one of the three must be 0 to specify which plane the mesh is in.
   */
  private void generateWireFrame2DMesh(int displayListNumber, int xcount, int ycount, int zcount)
  {
    if (displayListNumber == 0) return;
    assert (xcount >= 0 && ycount >= 0 && zcount >= 0);
    assert (xcount == 0 || ycount == 0 || zcount == 0);

    Tessellator tessellator = Tessellator.instance;
    GL11.glNewList(displayListNumber, GL11.GL_COMPILE);
    GL11.glPushAttrib(GL11.GL_ENABLE_BIT);
    GL11.glEnable(GL11.GL_BLEND);
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    GL11.glLineWidth(2.0F);
    GL11.glDisable(GL11.GL_TEXTURE_2D);
    GL11.glDepthMask(false);

    tessellator.startDrawing(GL11.GL_LINE_LOOP);
    tessellator.addVertex(0.0, 0.0, 0.0);
    if (xcount == 0) {
      tessellator.addVertex(   0.0, -ycount,    0.0);
      tessellator.addVertex(   0.0, -ycount, zcount);
      tessellator.addVertex(   0.0,       0, zcount);
    } else {
      tessellator.addVertex(xcount,     0.0,    0.0);
      tessellator.addVertex(xcount, -ycount, zcount);
      tessellator.addVertex(   0.0, -ycount, zcount);
    }
    tessellator.draw();

    for (int x = 1; x < xcount; ++x) {
      tessellator.startDrawing(GL11.GL_LINES);
      tessellator.addVertex(x,     0.0,    0.0);
      tessellator.addVertex(x, -ycount, zcount);
      tessellator.draw();
    }
    for (int y = 1; y < ycount; ++y) {
      tessellator.startDrawing(GL11.GL_LINES);
      tessellator.addVertex(   0.0, -y,    0.0);
      tessellator.addVertex(xcount, -y, zcount);
      tessellator.draw();
    }
    for (int z = 1; z < zcount; ++z) {
      tessellator.startDrawing(GL11.GL_LINES);
      tessellator.addVertex(   0.0,     0.0, z);
      tessellator.addVertex(xcount, -ycount, z);
      tessellator.draw();
    }

    GL11.glDepthMask(true);
    GL11.glPopAttrib();
    GL11.glEndList();
  }

  private void createMeshRenderLists(World world)
  {
    if (displayListWireFrameXY == 0) {
      displayListWireFrameXY = GLAllocation.generateDisplayLists(1);
    }
    if (displayListWireFrameXZ == 0) {
      displayListWireFrameXZ = GLAllocation.generateDisplayLists(1);
    }
    if (displayListWireFrameYZ == 0) {
      displayListWireFrameYZ = GLAllocation.generateDisplayLists(1);
    }
    if (displayListWireFrameXY == 0 || displayListWireFrameYZ == 0 || displayListWireFrameXZ == 0) {
      FMLLog.warning("Unable to create a displayList in BlockVoxelMultiSelector::createMeshRenderLists");
      return;
    }

    final int MESH_HEIGHT = 256;
    generateWireFrame2DMesh(displayListWireFrameXY, largestVoxelX - smallestVoxelX + 1, MESH_HEIGHT,                                  0);
    generateWireFrame2DMesh(displayListWireFrameXZ, largestVoxelX - smallestVoxelX + 1,           0, largestVoxelZ - smallestVoxelZ + 1);
    generateWireFrame2DMesh(displayListWireFrameYZ,                                  0, MESH_HEIGHT, largestVoxelZ - smallestVoxelZ + 1);
  }



/*
    int [] convexHullXneg = new int[zSize];
    int [] convexHullXpos = new int[zSize];
    int [] convexHullZneg = new int[xSize];
    int [] convexHullZpos = new int[xSize];

    // eliminate all pixels which are not directly illuminated from both x and from z (i.e. are outermost in both x and z)

    int x, y, z;
    for (x = 0; x < xSize; ++x) {
      for (z = 0; z < zSize && !shadow.getVoxel(x, 0, z); ++z) {} ;
      convexHullZneg[x] = z;
      for (z = zSize - 1; z >= 0 && !shadow.getVoxel(x, 0, z); --z) {} ;
      convexHullZpos[x] = z;
    }

    for (z = 0; z < zSize; ++z) {
      for (x = 0; x < xSize && !shadow.getVoxel(x, 0, z); ++x) {} ;
      convexHullXneg[z] = x;

      for (x = xSize-1; x >= 0 & !shadow.getVoxel(x, 0, z); --x) {};
      convexHullXpos[z] = x;
    }

    wireFrameXnegY = new ArrayList<ChunkCoordinates>();
    wireFrameXposY = new ArrayList<ChunkCoordinates>();
    wireFrameZnegY = new ArrayList<ChunkCoordinates>();
    wireFrameZposY = new ArrayList<ChunkCoordinates>();

    for (x = 0; x < xSize; ++x) {
      z = convexHullZneg[x];
      if (z < zSize
          && (convexHullXneg[z] == x || convexHullXpos[z] == x)) {
        wireFrameZnegY.add(new ChunkCoordinates(x, 0, z));
      }
      z = convexHullZpos[x];
      if (z >= 0
          && (convexHullXneg[z] == x || convexHullXpos[z] == x)) {
        wireFrameZposY.add(new ChunkCoordinates(x, 0, z));
      }
    }

    for (z = 0; z < zSize; ++z) {
      x = convexHullXneg[z];
      if (x < xSize
              && (convexHullZneg[x] == z || convexHullZpos[x] == z)) {
        wireFrameXnegY.add(new ChunkCoordinates(x, 0, z));
      }
      x = convexHullXpos[z];
      if (x >= 0
              && (convexHullZneg[x] == z || convexHullZpos[x] == z)) {
        wireFrameXposY.add(new ChunkCoordinates(x, 0, z));
      }
    }

    for (ChunkCoordinates coordinates : wireFrameXnegY) {
      x = coordinates.posX;
      z = coordinates.posZ;
      for (y = 0; y < ySize && !selection.getVoxel(x, y, z); ++y) {};
      coordinates.posY = y;
    }

    for (ChunkCoordinates coordinates : wireFrameZY) {
      x = coordinates.posX;
      z = coordinates.posZ;
      for (y = 0; y < ySize && !selection.getVoxel(x, y, z); ++y) {};
      coordinates.posY = y;
    }
*/
//  static int firsttime = 0;

  /**
   * render the current selection (must have called createRenderList previously).  Caller should set gLTranslatef so that the render starts in the
   *   correct spot  (the min[x,y,z] corner of the VoxelSelection will be drawn at [0,0,0])
   *   relativePlayerPosition is the position of the player's eyes relative to the minimum [x,y,z] corner of the VoxelSelection
   */
  public void renderSelection(Vec3 relativePlayerPosition, Vec3 playerLook)
  {
    if (displayListSelection == 0) {
      return;
    }

//    if (firsttime++ == 0) {
//      OpenGLdebugging.dumpAllIsEnabled();
//    }

    GL11.glPushMatrix();
/*
    final float NUDGE_DISTANCE = 0.01F;  // nudge the selection towards the viewer to prevent direct overlap of selection on existing blocks, which is visually distracting
    float nudgeX = (playerLook.xCoord < smallestVoxelX) ? +NUDGE_DISTANCE : -NUDGE_DISTANCE;
    float nudgeY = (playerLook.yCoord < smallestVoxelY) ? +NUDGE_DISTANCE : -NUDGE_DISTANCE;
    float nudgeZ = (playerLook.zCoord < smallestVoxelZ) ? +NUDGE_DISTANCE : -NUDGE_DISTANCE;
    GL11.glTranslatef(nudgeX, nudgeY, nudgeZ);
*/
    GL11.glPushMatrix();
    GL11.glPushAttrib(GL11.GL_ENABLE_BIT);
//    GL11.glDisable(GL11.GL_COLOR_MATERIAL);
//    GL11.glDisable(GL11.GL_ALPHA_TEST);
    GL11.glCallList(displayListSelection);
    GL11.glPopAttrib();
    GL11.glPopMatrix();

    // cull the back faces of the grid:
    // only draw a face if you can see the front of it
    //   eg for xpos face - if player is to the right, for xneg - if player is to the left.  if between don't draw either one.
    // exception: if inside the cube, render all faces.  Yneg is always drawn.
    boolean inside =    (relativePlayerPosition.xCoord >= smallestVoxelX && relativePlayerPosition.xCoord <= largestVoxelX + 1)
                     && (                                                   relativePlayerPosition.yCoord <= largestVoxelY + 1)
                     && (relativePlayerPosition.zCoord >= smallestVoxelZ && relativePlayerPosition.zCoord <= largestVoxelZ + 1);

    GL11.glColor4f(Colour.BLACK_40.R, Colour.BLACK_40.G, Colour.BLACK_40.B, Colour.BLACK_40.A);

    if (inside || relativePlayerPosition.xCoord < smallestVoxelX) {
      GL11.glPushMatrix();
      GL11.glTranslated(smallestVoxelX, largestVoxelY + 1, smallestVoxelZ);
      GL11.glCallList(displayListWireFrameYZ);
      GL11.glPopMatrix();
    }

    if (inside || relativePlayerPosition.xCoord > largestVoxelX + 1) {
      GL11.glPushMatrix();
      GL11.glTranslated(largestVoxelX + 1, largestVoxelY + 1, smallestVoxelZ);
      GL11.glCallList(displayListWireFrameYZ);
      GL11.glPopMatrix();
    }

    GL11.glColor4f(Colour.WHITE_40.R, Colour.WHITE_40.G, Colour.WHITE_40.B, Colour.WHITE_40.A);

    if (true) {
      GL11.glPushMatrix();
      GL11.glTranslated(smallestVoxelX, smallestVoxelY, smallestVoxelZ);
      GL11.glCallList(displayListWireFrameXZ);
      GL11.glPopMatrix();
    }

    GL11.glColor4f(Colour.BLACK_40.R, Colour.BLACK_40.G, Colour.BLACK_40.B, Colour.BLACK_40.A);

    if (inside || relativePlayerPosition.yCoord > largestVoxelY + 1) {
      GL11.glPushMatrix();
      GL11.glTranslated(smallestVoxelX, largestVoxelY + 1, smallestVoxelZ);
      GL11.glCallList(displayListWireFrameXZ);
      GL11.glPopMatrix();
    }

    if (inside || relativePlayerPosition.zCoord < smallestVoxelZ) {
      GL11.glPushMatrix();
      GL11.glTranslated(smallestVoxelX, largestVoxelY + 1, smallestVoxelZ);
      GL11.glCallList(displayListWireFrameXY);
      GL11.glPopMatrix();
    }

    if (inside || relativePlayerPosition.zCoord > largestVoxelZ + 1) {
      GL11.glPushMatrix();
      GL11.glTranslated(smallestVoxelX, largestVoxelY + 1, largestVoxelZ + 1);
      GL11.glCallList(displayListWireFrameXY);
      GL11.glPopMatrix();
    }
    GL11.glPopMatrix();
  }

  /**
   * selectFill is used to select a flood fill of blocks which match the starting block, and return a list of their coordinates.
   * Starting from the block identified by mouseTarget, the selection will flood fill out in three directions
   * depending on diagonalOK it will follow diagonals or only the cardinal directions.
   * Keeps going until it reaches maxBlockCount, y goes outside the valid range.  The search algorithm is to look for closest blocks first
   *   ("closest" meaning the shortest distance travelled along the blob being created)
   *
   * @param mouseTarget the block under the player's cursor.  Uses [x,y,z]
   * @param world       the world
   * @param maxBlockCount the maximum number of blocks to select
   * @param diagonalOK    if true, diagonal 45 degree lines are allowed
   * @param matchAnyNonAir
   * @param xMin  the fill will not extend below xMin.  Likewise it will not extend above xMax.  Similar for y, z.
   */
/*
  public static List<ChunkCoordinates> selectFill(MovingObjectPosition mouseTarget, World world,
                                                  int maxBlockCount, boolean diagonalOK, boolean matchAnyNonAir,
                                                  int xMin, int xMax, int yMin, int yMax, int zMin, int zMax)
  {

    // lookup table to give the possible search directions for non-diagonal and diagonal respectively
    final int NON_DIAGONAL_DIRECTIONS = 6;
    final int ALL_DIRECTIONS = 26;
    final int searchDirectionsX[] = {+0, +0, +0, +0, -1, +1,   // non-diagonal
            +1, +0, -1, +0,   +1, +1, -1, -1,   +1, +0, -1, +0,  // top, middle, bottom "edge" blocks
            +1, +1, -1, -1,   +1, +1, -1, -1                   // top, bottom "corner" blocks
    };
    final int searchDirectionsY[] = {-1, +1, +0, +0, +0, +0,   // non-diagonal
            +1, +1, +1, +1,   +0, +0, +0, +0,   -1, -1, -1, -1,   // top, middle, bottom "edge" blocks
            +1, +1, +1, +1,   -1, -1, -1, -1                      // top, bottom "corner" blocks
    };

    final int searchDirectionsZ[] = {+0, +0, -1, +1, +0, +0,   // non-diagonal
            +0, -1, +0, +1,   +1, -1, -1, +1,   +0, -1, +0, +1,   // top, middle, bottom "edge" blocks
            +1, -1, -1, +1,   +1, -1, -1, +1
    };

    List<ChunkCoordinates> selection = new ArrayList<ChunkCoordinates>();

    if (mouseTarget == null || mouseTarget.typeOfHit != EnumMovingObjectType.TILE) return selection;

    ChunkCoordinates startingBlock = new ChunkCoordinates();
    startingBlock.posX = mouseTarget.blockX;
    startingBlock.posY = mouseTarget.blockY;
    startingBlock.posZ = mouseTarget.blockZ;
    selection.add(startingBlock);

    int blockToReplaceID = world.getBlockId(startingBlock.posX, startingBlock.posY, startingBlock.posZ);
    int blockToReplaceMetadata = world.getBlockMetadata(startingBlock.posX, startingBlock.posY, startingBlock.posZ);

    final int INITIAL_CAPACITY = 128;
    Set<ChunkCoordinates> locationsFilled = new HashSet<ChunkCoordinates>(INITIAL_CAPACITY);                                   // locations which have been selected during the fill
    Deque<SearchPosition> currentSearchPositions = new LinkedList<SearchPosition>();
    Deque<SearchPosition> nextDepthSearchPositions = new LinkedList<SearchPosition>();
    ChunkCoordinates checkPosition = new ChunkCoordinates(0,0,0);
    ChunkCoordinates checkPositionSupport = new ChunkCoordinates(0,0,0);

    locationsFilled.add(startingBlock);
    currentSearchPositions.add(new SearchPosition(startingBlock));

    // algorithm is:
    //   for each block in the list of search positions, iterate through each adjacent block to see whether it meets the criteria for expansion:
    //     a) matches the block-to-be-replaced (if matchAnyNonAir: non-air, otherwise if blockID and metaData match.  For lava or water metadata doesn't need to match).
    //     b) hasn't been filled already during this contour search
    //   if the criteria are met, select the block and add it to the list of blocks to be search next round.
    //   if the criteria aren't met, keep trying other directions from the same position until all positions are searched.  Then delete the search position and move onto the next.
    //   This will ensure that the fill spreads evenly out from the starting point.   Check the boundary to stop fill spreading outside it.

    while (!currentSearchPositions.isEmpty() && selection.size() < maxBlockCount) {
      SearchPosition currentSearchPosition = currentSearchPositions.getFirst();
      checkPosition.set(currentSearchPosition.chunkCoordinates.posX + searchDirectionsX[currentSearchPosition.nextSearchDirection],
              currentSearchPosition.chunkCoordinates.posY + searchDirectionsY[currentSearchPosition.nextSearchDirection],
              currentSearchPosition.chunkCoordinates.posZ + searchDirectionsZ[currentSearchPosition.nextSearchDirection]
      );
      if (    checkPosition.posX >= xMin && checkPosition.posX <= xMax
              &&  checkPosition.posY >= yMin && checkPosition.posY <= yMax
              &&  checkPosition.posZ >= zMin && checkPosition.posZ <= zMax
              && !locationsFilled.contains(checkPosition)) {
        boolean blockIsSuitable = false;

        int blockToCheckID = world.getBlockId(checkPosition.posX, checkPosition.posY, checkPosition.posZ);

        if (matchAnyNonAir && blockToCheckID != 0) {
          blockIsSuitable = true;
        } else if (blockToCheckID == blockToReplaceID) {
          if (world.getBlockMetadata(checkPosition.posX, checkPosition.posY, checkPosition.posZ) == blockToReplaceMetadata) {
            blockIsSuitable = true;
          } else {
            if (Block.blocksList[blockToCheckID].blockMaterial == Material.lava
                    || Block.blocksList[blockToCheckID].blockMaterial == Material.water) {
              blockIsSuitable = true;
            }
          }
        }

        if (blockIsSuitable) {
          ChunkCoordinates newChunkCoordinate = new ChunkCoordinates(checkPosition);
          SearchPosition nextSearchPosition = new SearchPosition(newChunkCoordinate);
          nextDepthSearchPositions.addLast(nextSearchPosition);
          locationsFilled.add(newChunkCoordinate);
          selection.add(newChunkCoordinate);
        }
      }
      ++currentSearchPosition.nextSearchDirection;
      if (currentSearchPosition.nextSearchDirection >= (diagonalOK ? ALL_DIRECTIONS : NON_DIAGONAL_DIRECTIONS)) {
        currentSearchPositions.removeFirst();
        if (currentSearchPositions.isEmpty()) {
          Deque<SearchPosition> temp = currentSearchPositions;
          currentSearchPositions = nextDepthSearchPositions;
          nextDepthSearchPositions = temp;
        }
      }
    }

    return selection;
  }
*/
  /**
   * see selectFill above
   * @param mouseTarget
   * @param world
   * @param maxBlockCount
   * @param diagonalOK
   * @return
   */
/*
  public static List<ChunkCoordinates> selectFill(MovingObjectPosition mouseTarget, World world,
                                                  int maxBlockCount, boolean diagonalOK)
  {
    return selectFill(mouseTarget, world, maxBlockCount, diagonalOK, false,
            Integer.MIN_VALUE, Integer.MAX_VALUE, 0, 255, Integer.MIN_VALUE, Integer.MAX_VALUE);
  }
*/

}
