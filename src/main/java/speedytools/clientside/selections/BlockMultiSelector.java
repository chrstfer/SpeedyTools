package speedytools.clientside.selections;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.*;
import net.minecraft.world.World;
import speedytools.common.selections.FillMatcher;
import speedytools.common.utilities.ErrorLog;

import java.util.*;

/**
* Created with IntelliJ IDEA.
* User: TheGreyGhost
* Date: 28/10/13
* Time: 9:47 PM
* BlockMultiSelector is a group of methods used to select multiple blocks based on where the mouse is pointing.
*/
public class BlockMultiSelector
{
//  public enum BlockTypeToSelect {AIR_ONLY, NON_SOLID_OK, SOLID_OK}

  /**
   * Used to specify the type of behaviour when selecting the starting block
   * 0) should we perform a collision test, or just skip to placing a block in mid air?
   * 1) if no block collided, should we select a position in mid air?
   * 2) should we collide with water?
   * 3) if the collided block is water, should we pull back one to the adjacent block?
   * 4) if the collided block is non-solid (eg grass), should we pull back one to the adjacent block?
   * 5) if the collided block is solid, should we pull back one to the adjacent block?
   */
  public enum BlockSelectionBehaviour {
               WAND_STYLE( true,  true, false, false, false,  true),
                ORB_STYLE( true, false,  true, false, false, false),
        SCEPTRE_ADD_STYLE( true, false,  true,  true, false,  true),
    SCEPTRE_REPLACE_SYTLE( true, false,  true, false, false, false),
           BOUNDARY_STYLE(false,  true, false, false, false, false);

    BlockSelectionBehaviour(boolean i_performCollisionTest, boolean i_selectAirIfNoCollision, boolean i_waterCollision,
                            boolean i_waterPullback, boolean i_nonSolidPullback, boolean i_solidPullback)
    {
      performCollisionTest = i_performCollisionTest;
      selectAirIfNoCollision = i_selectAirIfNoCollision;
      waterCollision = i_waterCollision;
      waterPullback = i_waterPullback;
      nonSolidPullback = i_nonSolidPullback;
      solidPullback = i_solidPullback;
    }

    public boolean isPerformCollisionTest() { return performCollisionTest;}
    public boolean isSelectAirIfNoCollision() {
      return selectAirIfNoCollision;
    }
    public boolean isWaterCollision() {
      return waterCollision;
    }
    public boolean isWaterPullback() {
      return waterPullback;
    }
    public boolean isNonSolidPullback() {
      return nonSolidPullback;
    }
    public boolean isSolidPullback() {
      return solidPullback;
    }

    private boolean performCollisionTest;
    private boolean selectAirIfNoCollision;
    private boolean waterCollision;
    private boolean waterPullback;
    private boolean nonSolidPullback;
    private boolean solidPullback;
  }

  /**
   * selectStartingBlock is used to select a starting block based on the player's position and look
   * There are three distinct cases for the starting block:
   * (1) the mouse is not on any target: the first block selected will be the one corresponding to the line of sight from the player's head:
   *     a) which doesn't intersect the player's bounding box
   *     b) which is at least 0.5 m from the player's eyes in each of the the x, y, and z directions.
   * (2) the mouse is on a tile target: the first block selected will be according to blockSelectionBehaviour
   * (3) the mouse is on an entity: no selection.
   * The method also returns the look vector snapped to the midpoint of the face that was hit on the selected Block
   * @param mouseTarget  where the cursor is currently pointed
   * @param blockSelectionBehaviour the types of blocks that can be selected.
   * @param player       the player (used for position and look information)
   * @param partialTick  used for calculating player head position
   * @return the coordinates of the starting selection block plus the side hit plus the look vector snapped to the midpoint of
   *         side hit.  null if no selection.
   */
  public static MovingObjectPosition selectStartingBlock(MovingObjectPosition mouseTarget, BlockSelectionBehaviour blockSelectionBehaviour,
                                                         EntityPlayer player, float partialTick)
  {
    final double MINIMUMHITDISTANCE = 0.5; // minimum distance from the player's eyes (axis-aligned not oblique)
    int blockx, blocky, blockz;
    double playerOriginX = player.lastTickPosX + (player.posX - player.lastTickPosX) * (double)partialTick;
    double playerOriginY = player.lastTickPosY + (player.posY - player.lastTickPosY) * (double)partialTick;
    double playerOriginZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * (double)partialTick;

    Vec3 playerLook = player.getLook(partialTick);
    Vec3 playerEyesPos = player.getPositionEyes(partialTick); // new Vec3(playerOriginX, playerOriginY, playerOriginZ);

    if (mouseTarget == null) {   // no hit
      if (!blockSelectionBehaviour.isSelectAirIfNoCollision()) {
        return null;
      }

      // we need to find the closest [x,y,z] in the direction the player is looking in, that the player is not occupying.
      // This will depend on the yaw but also the elevation.
      // The algorithm is:
      // (1) calculated an expanded AABB around the player (all sides at least 0.5m from the eyes) and snap it to the next largest enclosing blocks.
      // (2) find the intersection of the look vector with this AABB
      // (3) the selected block is the one just beyond the intersection point

      AxisAlignedBB playerAABB = player.getEntityBoundingBox();
      double AABBminX = Math.floor(Math.min(playerAABB.minX, playerOriginX - MINIMUMHITDISTANCE));
      double AABBminY = Math.floor(Math.min(playerAABB.minY, playerOriginY - MINIMUMHITDISTANCE));
      double AABBminZ = Math.floor(Math.min(playerAABB.minZ, playerOriginZ - MINIMUMHITDISTANCE));
      double AABBmaxX = Math.ceil(Math.max(playerAABB.maxX, playerOriginX + MINIMUMHITDISTANCE));
      double AABBmaxY = Math.ceil(Math.max(playerAABB.maxY, playerOriginY + MINIMUMHITDISTANCE));
      double AABBmaxZ = Math.ceil(Math.max(playerAABB.maxZ, playerOriginZ + MINIMUMHITDISTANCE));

      AxisAlignedBB expandedAABB = new AxisAlignedBB(AABBminX, AABBminY, AABBminZ,   AABBmaxX, AABBmaxY, AABBmaxZ);

      Vec3 startVec = playerEyesPos.addVector(0, 0, 0);
      Vec3 endVec = playerEyesPos.addVector(playerLook.xCoord * 8.0, playerLook.yCoord * 8.0, playerLook.zCoord * 8.0);

      MovingObjectPosition traceResult = expandedAABB.calculateIntercept(startVec, endVec);
      if (traceResult == null) {  // shouldn't be possible
        return null;
      }

      blockx = MathHelper.floor_double(traceResult.hitVec.xCoord + playerLook.xCoord * 0.001);
      blocky = MathHelper.floor_double(traceResult.hitVec.yCoord + playerLook.yCoord * 0.001);
      blockz = MathHelper.floor_double(traceResult.hitVec.zCoord + playerLook.zCoord * 0.001);
      traceResult = new MovingObjectPosition(traceResult.hitVec, traceResult.sideHit.getOpposite(),  new BlockPos(blockx, blocky, blockz));

      traceResult.hitVec = playerLook;
//      traceResult.hitVec = snapLookToBlockFace(traceResult, playerEyesPos);

      return traceResult;

    } else if (mouseTarget.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
      World world = player.getEntityWorld();
      boolean pullback = false;
      BlockPos blockUnderCursor = mouseTarget.getBlockPos();  // get BlockPos
      switch (BlockMultiSelector.checkBlockSolidity(world, blockUnderCursor.getX(), blockUnderCursor.getY(), blockUnderCursor.getZ())) {
        case AIR: {
          pullback = false;
          break;
        }
        case WATER: {
          pullback = blockSelectionBehaviour.isWaterPullback();
          break;
        }
        case NON_SOLID: {
          pullback = blockSelectionBehaviour.isNonSolidPullback();
          break;
        }
        case SOLID: {
          pullback = blockSelectionBehaviour.isSolidPullback();
          break;
        }
        default: {
          ErrorLog.defaultLog().severe("Illegal solidity");
          break;
        }
      }

      if (pullback) {
        EnumFacing blockInFront = mouseTarget.sideHit;
        blockx = blockUnderCursor.getX() + blockInFront.getFrontOffsetX();
        blocky = blockUnderCursor.getY() + blockInFront.getFrontOffsetY();
        blockz = blockUnderCursor.getZ() + blockInFront.getFrontOffsetZ();
        mouseTarget.sideHit = mouseTarget.sideHit.getOpposite();   // if pullback, swap the sidehit to point to the solid block
      } else {
        blockx = blockUnderCursor.getX();
        blocky = blockUnderCursor.getY();
        blockz = blockUnderCursor.getZ();
      }

      mouseTarget = new MovingObjectPosition(mouseTarget.hitVec, mouseTarget.sideHit, new BlockPos(blockx, blocky, blockz));
      mouseTarget.hitVec = snapLookToBlockFace(mouseTarget, playerEyesPos);
      return mouseTarget;

    } else {  // currently only ENTITY
      return null;
    }

  }

  /**
   * selectLine is used to select a straight line of blocks, and return a list of their coordinates.
   * Starting from the startingBlock, the selection will continue in a line parallel to the direction vector, snapped to the six cardinal directions or
   *   alternatively to one of the twenty 45 degree directions (if diagonalOK == true).
   *   If stopWhenCollide == true and the snapped direction points directly into a solid block, the direction will be deflected up to lie flat along the surface
   *   Keeps going until it reaches maxLineLength, y goes outside the valid range, or hits a solid block (and stopWhenCollide is true)
   * @param startingBlock the first block in the straight line
   * @param world       the world
   * @param direction    the direction to extend the selection
   * @param maxLineLength the maximum number of blocks to select
   * @param diagonalOK    if true, diagonal 45 degree lines are allowed
   * @param stopWhenCollide if true, stops when a solid block is encountered (canCollide == true).  Otherwise, continues for maxLineLength
   * @return a list of the coordinates of all blocks in the selection, including the startingBlock.  May be zero length if the startingBlock is null
   */
  public static List<BlockPos> selectLine(BlockPos startingBlock, World world, Vec3 direction,
                                                  int maxLineLength, boolean diagonalOK, CollisionOptions stopWhenCollide)
  {
    List<BlockPos> selection = new ArrayList<BlockPos>();

    if (startingBlock == null) return selection;

    Vec3 snappedCardinalDirection = snapToCardinalDirection(direction, diagonalOK);
    if (snappedCardinalDirection == null) return selection;

    BlockPos deltaPosition = convertToDelta(snappedCardinalDirection);

    BlockPos nextCoordinate = new BlockPos(startingBlock);
    selection.add(startingBlock);
    int blocksCount = 1;
    while (blocksCount < maxLineLength) {
      nextCoordinate = nextCoordinate.add(deltaPosition);
      if (nextCoordinate.getY() < 0 || nextCoordinate.getY() >= 256) break;
      if ((stopWhenCollide == CollisionOptions.STOP_WHEN_SOLID_BLOCK_REACHED) && isBlockSolid(world, nextCoordinate)) {
        if (blocksCount > 1) break;
        deltaPosition = deflectDirectionVector(world, startingBlock, direction, deltaPosition);
        nextCoordinate = startingBlock.add(deltaPosition);
        if (isBlockSolid(world, nextCoordinate)) break;
      }
      selection.add(nextCoordinate);
      ++blocksCount;
    }

    return selection;
  }

  public static List<BlockPos> selectContourUnbounded(BlockPos startingBlockPosition, World world,
                                                              int maxBlockCount, boolean diagonalOK, FillMatcher fillMatcher, EnumFacing normalDirection)
  {
    return selectContourBounded(startingBlockPosition, world, maxBlockCount, diagonalOK, fillMatcher, normalDirection,
                                Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE);
  }

  /**
   * selectContour is used to select a contoured line of blocks, and return a list of their coordinates.
   * Starting from the block identified by mouseTarget, the selection will attempt to follow any contours in the same plane as the side hit.
   * (for example: if there is a zigzagging wall, it will select the layer of blocks that follows the top of the wall.)
   * Depending on fillMatcher, it will either select the non-solid blocks on top of the contour (to make the wall "taller"), or
   *   select the solid blocks that form the top layer of the contour (to remove the top layer of the wall).
   * depending on diagonalOK it will follow diagonals or only the cardinal directions.
   * Keeps going until it reaches maxBlockCount, y goes outside the valid range, or hits a solid block.  The search algorithm is to look for closest blocks first
   *   ("closest" meaning the shortest distance travelled along the contour being created)
   * @param startingBlockPosition the block to start from
   * @param world       the world
   * @param maxBlockCount the maximum number of blocks to select
   * @param diagonalOK    if true, diagonal 45 degree lines are allowed
   * @param fillMatcher   the matcher used to determine which blocks should be added to the fill
   * @param normalDirection specifies the plane that will be searched in (Facing directions; specifies the normal to the plane)
   * @return a list of the coordinates of all blocks in the selection, including the mouseTarget block.   Will be empty if the mouseTarget is not a tile.
   */

  public static List<BlockPos> selectContourBounded(BlockPos startingBlockPosition, World world,
                                                              int maxBlockCount, boolean diagonalOK, FillMatcher fillMatcher, EnumFacing normalDirection,
                                                              int xMin, int xMax, int yMin, int yMax, int zMin, int zMax)
  {
    // lookup table to give the possible search directions for any given search plane
    //  row index 0 = xz plane, 1 = xy plane, 2 = yz plane
    //  column index = the eight directions within the plane (even = cardinal, odd = diagonal)
    final int PLANE_XZ = 0;
    final int PLANE_XY = 1;
    final int PLANE_YZ = 2;
    final int searchDirectionsX[][] = {  {+0, -1, -1, -1, +0, +1, +1, +1},
            {+0, -1, -1, -1, +0, +1, +1, +1},
            {+0, +0, +0, +0, +0, +0, +0, +0}
    };
    final int searchDirectionsY[][] = {  {+0, +0, +0, +0, +0, +0, +0, +0},
            {+1, +1, +0, -1, -1, -1, +0, +1},
            {+1, +1, +0, -1, -1, -1, +0, +1}
    };
    final int searchDirectionsZ[][] = {  {+1, +1, +0, -1, -1, -1, +0, +1},
            {+0, +0, +0, +0, +0, +0, +0, +0},
            {+0, -1, -1, -1, +0, +1, +1, +1}
    };

    List<BlockPos> selection = new ArrayList<BlockPos>();

//    if (mouseTarget == null || mouseTarget.typeOfHit !=  MovingObjectPosition.MovingObjectType.BLOCK) return selection;

//    BlockPos startingBlock = new BlockPos();
    int searchPlane;
    switch (normalDirection) {
      case DOWN:
      case UP:
        searchPlane = PLANE_XZ;
        break;
      case EAST:
      case WEST:
        searchPlane = PLANE_XY;
        break;
      case NORTH:
      case SOUTH:
        searchPlane = PLANE_YZ;
        break;
      default: return selection;  // illegal value so return nothing
    }

    // first step is to identify the starting block depending on whether this is an additive contour or subtractive contour,

//    EnumFacing blockInFront = EnumFacing.getFront(mouseTarget.sideHit);
//    startingBlock.posX = mouseTarget.blockX;
//    startingBlock.posY = mouseTarget.blockY;
//    startingBlock.posZ = mouseTarget.blockZ;
//    if (selectAdditiveContour && isBlockSolid(world, startingBlock)) {
//      startingBlock.posX += blockInFront.getFrontOffsetX();
//      startingBlock.posY += blockInFront.getFrontOffsetY();
//      startingBlock.posZ += blockInFront.getFrontOffsetZ();
//    }
    selection.add(startingBlockPosition);

    final int INITIAL_CAPACITY = 128;
    Set<BlockPos> locationsFilled = new HashSet<BlockPos>(INITIAL_CAPACITY);                                   // locations which have been selected during the fill
    Deque<SearchPosition> currentSearchPositions = new LinkedList<SearchPosition>();
    Deque<SearchPosition> nextDepthSearchPositions = new LinkedList<SearchPosition>();
    BlockPos checkPosition = new BlockPos(0,0,0);
//    BlockPos checkPositionSupport = new BlockPos(0,0,0);

    locationsFilled.add(startingBlockPosition);
    currentSearchPositions.add(new SearchPosition(startingBlockPosition));

    // algorithm is:
    //   for each block in the list of search positions, iterate through each adjacent block to see whether it meets the criteria for expansion:
    //     a) is not solid (for additive contours) or is solid (for subtractive contours)
    //     b) hasn't been filled already during this contour search
    //     c) for additive contours: if it is "supported" by a solid block (eg in the case of sideHit = top face, then test whether the block at [x,y-1,z] is solid
    //   if the criteria are met, select the block and add it to the list of blocks to be search next round.
    //   if the criteria aren't met, keep trying other directions from the same position until all positions are searched.  Then delete the search position and move onto the next.
    //   This will ensure that the fill spreads evenly out from the starting point.

    while (!currentSearchPositions.isEmpty() && selection.size() < maxBlockCount) {
      SearchPosition currentSearchPosition = currentSearchPositions.getFirst();
      checkPosition = currentSearchPosition.chunkCoordinates.add(
                        searchDirectionsX[searchPlane][currentSearchPosition.nextSearchDirection],
                        searchDirectionsY[searchPlane][currentSearchPosition.nextSearchDirection],
                        searchDirectionsZ[searchPlane][currentSearchPosition.nextSearchDirection]
                       );
      if (checkPosition.getX() >= xMin && checkPosition.getX() <= xMax &&
          checkPosition.getY() >= yMin && checkPosition.getY() <= yMax &&
          checkPosition.getZ() >= zMin && checkPosition.getZ() <= zMax &&
          !locationsFilled.contains(checkPosition)) {
        FillMatcher.MatchResult matchResult = fillMatcher.matches(world, checkPosition.getX(), checkPosition.getY(), checkPosition.getZ());

//        if (selectAdditiveContour) {
//          if (!isBlockSolid(world, checkPosition)) {
//            checkPositionSupport.set(checkPosition.posX - blockInFront.getFrontOffsetX(),      // block behind
//                                     checkPosition.posY - blockInFront.getFrontOffsetY(),
//                                     checkPosition.posZ - blockInFront.getFrontOffsetZ()
//                                    );
//            blockIsSuitable = isBlockSolid(world, checkPositionSupport);
//          }
//        } else { // subtractive contour
//          blockIsSuitable = isBlockSolid(world, checkPosition);
//        }
        if (matchResult == FillMatcher.MatchResult.MATCH) {
          BlockPos newChunkCoordinate = new BlockPos(checkPosition);
          SearchPosition nextSearchPosition = new SearchPosition(newChunkCoordinate);
          nextDepthSearchPositions.addLast(nextSearchPosition);
          locationsFilled.add(newChunkCoordinate);
          selection.add(newChunkCoordinate);
        }
      }
      currentSearchPosition.nextSearchDirection += diagonalOK ? 1 : 2;  // no diagonals -> even numbers only
      if (currentSearchPosition.nextSearchDirection >= 8) {
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

  /**
   * selectFill is used to select a flood fill of blocks which match the starting block, and return a list of their coordinates.
   * Starting from the block identified by mouseTarget, the selection will flood fill out in three directions
   * depending on diagonalOK it will follow diagonals or only the cardinal directions.
   * Keeps going until it reaches maxBlockCount, y goes outside the valid range.  The search algorithm is to look for closest blocks first
   *   ("closest" meaning the shortest distance travelled along the blob being created)
   *
   * @param world       the world
   * @param maxBlockCount the maximum number of blocks to select
   * @param diagonalOK    if true, diagonal 45 degree lines are allowed
   * @param fillMatcher the matcher used to determine which blocks should be added to the fill
   * @param xMin  the fill will not extend below xMin.  Likewise it will not extend above xMax.  Similar for y, z.
   */
  public static List<BlockPos> selectFillBounded(BlockPos fillStartPosition, World world,
                                                         int maxBlockCount, boolean diagonalOK, FillMatcher fillMatcher,
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

    List<BlockPos> selection = new ArrayList<BlockPos>();

    BlockPos startingBlock = new BlockPos(fillStartPosition);
    if (FillMatcher.MatchResult.MATCH != fillMatcher.matches(world, fillStartPosition.getX(), fillStartPosition.getY(), fillStartPosition.getZ())) {
      return selection;
    }
    selection.add(startingBlock);

//    Block blockToReplace = world.getBlock(startingBlock.posX, startingBlock.posY, startingBlock.posZ);
//    int blockToReplaceMetadata = world.getBlockMetadata(startingBlock.posX, startingBlock.posY, startingBlock.posZ);

    final int INITIAL_CAPACITY = 128;
    Set<BlockPos> locationsFilled = new HashSet<BlockPos>(INITIAL_CAPACITY);                                   // locations which have been selected during the fill
    Deque<SearchPosition> currentSearchPositions = new LinkedList<SearchPosition>();
    Deque<SearchPosition> nextDepthSearchPositions = new LinkedList<SearchPosition>();
    BlockPos checkPosition = new BlockPos(0,0,0);
    BlockPos checkPositionSupport = new BlockPos(0,0,0);

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
      checkPosition = currentSearchPosition.chunkCoordinates.add(
                        searchDirectionsX[currentSearchPosition.nextSearchDirection],
                        searchDirectionsY[currentSearchPosition.nextSearchDirection],
                        searchDirectionsZ[currentSearchPosition.nextSearchDirection]
                        );
      if (    checkPosition.getX() >= xMin && checkPosition.getX() <= xMax
          &&  checkPosition.getY() >= yMin && checkPosition.getY() <= yMax
          &&  checkPosition.getZ() >= zMin && checkPosition.getZ() <= zMax
          && !locationsFilled.contains(checkPosition)) {
        FillMatcher.MatchResult matchResult = fillMatcher.matches(world, checkPosition.getX(), checkPosition.getY(), checkPosition.getZ());
//        Block blockToCheck = world.getBlock();
//
//        if (matchAnyNonAir && blockToCheck != Blocks.air) {
//          blockIsSuitable = true;
//        } else if (blockToCheck == blockToReplace) {
//          if (world.getBlockMetadata(checkPosition.posX, checkPosition.posY, checkPosition.posZ) == blockToReplaceMetadata) {
//            blockIsSuitable = true;
//          } else {
//            if (blockToCheck.getMaterial() == Material.lava
//                || blockToCheck.getMaterial() == Material.water) {
//              blockIsSuitable = true;
//            }
//          }
//        }

        if (matchResult == FillMatcher.MatchResult.MATCH) {
          BlockPos newChunkCoordinate = new BlockPos(checkPosition);
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

  /**
   * see selectFill above
   * @param world
   * @param maxBlockCount
   * @param diagonalOK
   * @return
   */
  public static List<BlockPos> selectFillUnbounded(BlockPos fillStartPosition, World world,
                                                           int maxBlockCount, boolean diagonalOK, FillMatcher fillMatcher)
  {
    return selectFillBounded(fillStartPosition, world, maxBlockCount, diagonalOK, fillMatcher,
            Integer.MIN_VALUE, Integer.MAX_VALUE, 0, 255, Integer.MIN_VALUE, Integer.MAX_VALUE);
  }

  /**
   * Used to create vector from the starting point to the midpoint of the specified side of the block.
   * @param movingObjectPosition the [x,y,z] of the target block, and the side.  hitVec is ignored.
   * @param startPos the origin of the vector to be created
   * @return the direction vector, or null for failure
   */
  public static Vec3 snapLookToBlockFace(MovingObjectPosition movingObjectPosition, Vec3 startPos)
  {
    // midpoint of each face based on side
    final double[] X_FACE_OFFSET = {0.5, 0.5, 0.5, 0.5, 0.0, 1.0};
    final double[] Y_FACE_OFFSET = {0.0, 1.0, 0.5, 0.5, 0.5, 0.5};
    final double[] Z_FACE_OFFSET = {0.5, 0.5, 0.0, 1.0, 0.5, 0.5};

    int sideHit = movingObjectPosition.sideHit.getIndex();
    BlockPos blockPos = movingObjectPosition.getBlockPos();
    Vec3 endPos = new Vec3(blockPos.getX() + X_FACE_OFFSET[sideHit],
                           blockPos.getY() + Y_FACE_OFFSET[sideHit],
                           blockPos.getZ() + Z_FACE_OFFSET[sideHit]);

//    return startPos.subtract(endPos);
    return endPos.subtract(startPos);
  }

  /**
   * Snaps the given vector to the closest of the six cardinal directions, or alternatively to one of the twenty "45 degree" directions (if diagonalOK == true)
   * @param vectorToSnap the vector to be snapped to a cardinal direction
   * @param diagonalOK if true, diagonal "45 degree" directions are allowed
   * @return the cardinal direction snapped to (unit length vector), or null if input vector is null or zero.
   */
  public static Vec3 snapToCardinalDirection(Vec3 vectorToSnap, boolean diagonalOK)
  {
    final float R2 = 0.707107F;  // 1 / sqrt(2)
    final float R3 = 0.577350F;  // 1 / sqrt(3)
    final float cardinal[][] =   {   {1, 0, 0},      {0, 1, 0},      {0,0,1} };
    final float cardinal45[][] = { {R2, R2, 0},   {-R2, R2, 0},   {R2, 0, R2},  {R2, 0, -R2}, {0, R2, R2}, {0, R2, -R2},
                                   {R3, R3, R3}, {R3, -R3, R3}, {R3, R3, -R3}, {R3, -R3, -R3}
                                 };
    Vec3 cardinalVector;
    Vec3 closestVector = null;
    double highestDotProduct = 0.0;

    // use the dot product to find the closest match (highest projection of vectorToSnap onto the cardinaldirection).
    // if the best match has negative dot product, it points the opposite way so reverse it

    int i;
    for (i=0; i < 3; ++i) {
      cardinalVector = new Vec3(cardinal[i][0], cardinal[i][1], cardinal[i][2]);
      double dotProduct = cardinalVector.dotProduct(vectorToSnap);
      if (Math.abs(dotProduct) > Math.abs(highestDotProduct)) {
        highestDotProduct = dotProduct;
        closestVector = cardinalVector;
      }
    }

    if (diagonalOK) {
      for (i=0; i < 10; ++i) {
        cardinalVector = new Vec3(cardinal45[i][0], cardinal45[i][1], cardinal45[i][2]);
        double dotProduct = cardinalVector.dotProduct(vectorToSnap);
        if (Math.abs(dotProduct) > Math.abs(highestDotProduct)) {
          highestDotProduct = dotProduct;
          closestVector = cardinalVector;
        }
      }
    }

    if (closestVector == null) return null;

    if (highestDotProduct < 0) {
      Vec3 nullVector = new Vec3(0, 0, 0);
      closestVector = nullVector.subtract(closestVector);
    }

    return closestVector;
  }

  /**
   * "deflects" the direction vector so that it doesn't try to penetrate a solid block.
   * for example: if the vector is [0.707, -0.707, 0] and the starting block is sitting on a flat plane:
   *    the direction vector will be "deflected" up to [0.707, 0, 0], converted to [+1, 0, 0] return value, so
   *    that the direction runs along the surface of the plane
   * @param world the world
   * @param startingBlock - the starting block, should be non-solid (isBlockSolid == false)
   * @param direction - the direction vector to be deflected.
   * @param deltaPosition - the current [deltax, deltay, deltaz] where each delta is -1, 0, or 1
   * @return a [deltax,deltay,deltaz] where each delta is -1, 0, or 1
   */
  public static BlockPos deflectDirectionVector(World world, BlockPos startingBlock, Vec3 direction, BlockPos deltaPosition)
  {
    // algorithm is:
    // if deltaPosition has two or three non-zero components:
    //     re-snap the vector to the six cardinal axes only.  If it still fails, perform further deflection as for deltaPosition with one non-zero component below
    // if deltaPosition has one non-zero component (is parallel to one of the six coordinate axes):
    //     normalise the direction vector to unit length, eliminate the deltaPosition's non-zero axis from the direction vector, verify that at least one of the other two
    //     components is at least 0.1, renormalise and snap the vector to the cardinal axes again.

    int nonZeroCount = Math.abs(deltaPosition.getX()) + Math.abs(deltaPosition.getY()) + Math.abs(deltaPosition.getZ());
    Vec3 deflectedDirection;
    BlockPos deflectedDeltaPosition;

    if (nonZeroCount >= 2) {
      deflectedDirection = snapToCardinalDirection(direction, false);
      if (deflectedDirection == null) return new BlockPos(deltaPosition);

      deflectedDeltaPosition = convertToDelta(deflectedDirection);
      BlockPos nextCoordinate = startingBlock.add(deflectedDeltaPosition);
      if (!isBlockSolid(world, nextCoordinate)) return deflectedDeltaPosition;
    } else {
      deflectedDeltaPosition = new BlockPos(deltaPosition);
    }

    deflectedDirection = direction.normalize();
    if (deflectedDeltaPosition.getX() != 0) {
      deflectedDirection = deflectedDirection.subtract(deflectedDirection.xCoord, 0, 0);
    } else if (deflectedDeltaPosition.getY() != 0) {
      deflectedDirection = deflectedDirection.subtract(0, deflectedDirection.yCoord, 0);
    } else {
      deflectedDirection = deflectedDirection.subtract(0, 0, deflectedDirection.zCoord);
    }
    deflectedDirection = deflectedDirection.normalize();
    deflectedDirection = snapToCardinalDirection(deflectedDirection, false);
    if (deflectedDirection == null) return new BlockPos(deltaPosition);

    deflectedDeltaPosition = convertToDelta(deflectedDirection);
    return deflectedDeltaPosition;
  }

  /**
   * Converts the unit vector to a [deltax,deltay,deltaz] where each delta is -1, 0, or 1
   * @param vector - valid inputs are unit length vectors parallel to [dx, dy, dz] where d{} is -1, 0, or +1
   * @return a [deltax,deltay,deltaz] where each delta is -1, 0, or 1
   */
  public static BlockPos convertToDelta(Vec3 vector)
  {
    final float EPSILON = 0.1F;
    int dx = 0;
    int dy = 0;
    int dz = 0;

    if (vector.xCoord > EPSILON) dx = 1;
    if (vector.xCoord < -EPSILON) dx = -1;
    if (vector.yCoord > EPSILON) dy = 1;
    if (vector.yCoord < -EPSILON) dy = -1;
    if (vector.zCoord > EPSILON) dz = 1;
    if (vector.zCoord < -EPSILON) dz = -1;
    return new BlockPos(dx, dy, dz);
  }

  /**
   *  returns true if the block is "solid" or is water.
   *  Non-solid appears to correlate with "doesn't interact with a piston" i.e. getMobilityFlag == 1
    * @param world  the world
   * @param blockLocation  the [x,y,z] of the block to be checked
   */
  public static boolean isBlockSolid(World world, BlockPos blockLocation)
  {
    if (blockLocation.getY() < 0 || blockLocation.getY() >= 256) return false;
    IBlockState iBlockState = world.getBlockState(blockLocation);
    Block block = iBlockState.getBlock();
    if (block == Blocks.air) {
      return false;
    }
    return (block.getMaterial() == Material.water || block.getMobilityFlag() != 1);
  }

  public enum BlockSolidity {AIR, WATER, NON_SOLID, SOLID};

  /** check how solid this block is
   * @param world
   * @return
   */
  public static BlockSolidity checkBlockSolidity(World world, int wx, int wy, int wz)
  {
    if (wy < 0 || wy >= 256) return BlockSolidity.AIR;
    Block block = world.getBlockState(new BlockPos(wx, wy, wz)).getBlock();
    if (block == Blocks.air) return BlockSolidity.AIR;
    if (block.getMaterial() == Material.water) return BlockSolidity.WATER;
    if (block.getMobilityFlag() == 1) return BlockSolidity.NON_SOLID;
    return BlockSolidity.SOLID;
  }

  /**
   * SearchPosition contains the coordinates of a block and the current direction in which to search.
   */
  public static class SearchPosition
  {
    public SearchPosition(BlockPos initBlockPos) {
      chunkCoordinates = initBlockPos;
      nextSearchDirection = 0;
    }
    public BlockPos chunkCoordinates;
    public int nextSearchDirection;
  }

  public static enum CollisionOptions {
    STOP_WHEN_SOLID_BLOCK_REACHED, CONTINUE_THROUGH_SOLID_BLOCKS
  }

}
