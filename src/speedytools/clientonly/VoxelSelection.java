package speedytools.clientonly;

import cpw.mods.fml.common.FMLLog;

import java.util.BitSet;

/**
 * User: The Grey Ghost
 * Date: 15/02/14
 */
public class VoxelSelection
{
  public static final int MAX_X_SIZE = 256;
  public static final int MAX_Y_SIZE = 256;
  public static final int MAX_Z_SIZE = 256;

  public VoxelSelection(int x, int y, int z)
  {
    resize(x, y, z);
  }

  public void clearAll(int x, int y, int z)
  {
    resize(x, y, z);
  }

  public void setVoxel(int x, int y, int z)
  {
   x = Math.min(Math.max(0, x), MAX_X_SIZE-1);
   y = Math.min(Math.max(0, y), MAX_Y_SIZE-1);
   z = Math.min(Math.max(0, z), MAX_Z_SIZE-1);

   voxels.set(x + MAX_X_SIZE*(y + MAX_Y_SIZE * z) );
  }

  public void clearVoxel(int x, int y, int z)
  {
    x = Math.min(Math.max(0, x), MAX_X_SIZE-1);
    y = Math.min(Math.max(0, y), MAX_Y_SIZE-1);
    z = Math.min(Math.max(0, z), MAX_Z_SIZE-1);

    voxels.clear(x + MAX_X_SIZE * (y + MAX_Y_SIZE * z));
  }

  public boolean getVoxel(int x, int y, int z)
  {
    x = Math.min(Math.max(0, x), MAX_X_SIZE-1);
    y = Math.min(Math.max(0, y), MAX_Y_SIZE-1);
    z = Math.min(Math.max(0, z), MAX_Z_SIZE-1);

    return voxels.get(x + MAX_X_SIZE*(y + MAX_Y_SIZE * z) );
  }

  private void resize(int x, int y, int z)
  {
    if (   x <= 0 || x > MAX_X_SIZE
            || y <= 0 || y > MAX_Y_SIZE
            || z <= 0 || z > MAX_Z_SIZE ) {
      FMLLog.severe("Out-of-range [x,y,z] in VoxelSelection constructor: [%d, %d, %d]", x, y, z);
      x = 1;
      y = 1;
      z = 1;
    }
    xmax = x;
    ymax = y;
    zmax = z;
    if (voxels == null) {
      voxels = new BitSet(xmax * ymax * zmax);     // default to all false
    } else {
      voxels.clear();
    }
  }

  private BitSet voxels;
  private int xmax;
  private int ymax;
  private int zmax;
}