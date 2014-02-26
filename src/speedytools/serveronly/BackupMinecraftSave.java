package speedytools.serveronly;

/*
 * Copyright (c) 2008, 2010, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import net.minecraft.nbt.NBTTagCompound;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;

/**
 * Sample code that copies files in a similar manner to the cp(1) program.
 */

public class BackupMinecraftSave
{

  /**
   * Copy source file to target location. If {@code prompt} is true then
   * prompt user to overwrite target if it exists. The {@code preserve}
   * parameter determines if file attributes should be copied/preserved.
   */
  static void copyFile(Path source, Path target) {
    if (Files.notExists(target)) {
      try {
        Files.copy(source, target);
      } catch (IOException x) {
        System.err.format("Unable to copy: %s: %s%n", source, x);
      }
    }
  }


  static class TreeCopier implements FileVisitor<Path>
  {
    private final Path source;
    private final Path target;

    TreeCopier(Path source, Path target, boolean prompt, boolean preserve) {
      this.source = source;
      this.target = target;
    }

    private final String PATH_TAG = "PATH";
    private final String FILE_SIZE_TAG = "SIZE";
    private final String FILE_CREATED_TAG = "CREATED";
    private final String FILE_MODIFIED_TAG = "MODIFIED";

    private void addFileInfoEntry(File file, NBTTagCompound fileRecord) throws IOException
    {
      BasicFileAttributes attributes = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
      fileRecord.setString(PATH_TAG, file.getPath());
      fileRecord.setLong(FILE_SIZE_TAG, attributes.size());
      fileRecord.setLong(FILE_CREATED_TAG, attributes.lastModifiedTime().toMillis());
      fileRecord.setLong(FILE_MODIFIED_TAG, attributes.lastModifiedTime().toMillis());
    }

    private void junk()
    {
      try
      {
        par1NBTTagCompound.setTag("Pos", this.newDoubleNBTList(new double[] {this.posX, this.posY + (double)this.ySize, this.posZ}));
        par1NBTTagCompound.setTag("Motion", this.newDoubleNBTList(new double[] {this.motionX, this.motionY, this.motionZ}));
        par1NBTTagCompound.setTag("Rotation", this.newFloatNBTList(new float[] {this.rotationYaw, this.rotationPitch}));
        par1NBTTagCompound.setFloat("FallDistance", this.fallDistance);
        par1NBTTagCompound.setShort("Fire", (short)this.fire);
        par1NBTTagCompound.setShort("Air", (short)this.getAir());
        par1NBTTagCompound.setBoolean("OnGround", this.onGround);
        par1NBTTagCompound.setInteger("Dimension", this.dimension);
        par1NBTTagCompound.setBoolean("Invulnerable", this.invulnerable);
        par1NBTTagCompound.setInteger("PortalCooldown", this.timeUntilPortal);
        par1NBTTagCompound.setLong("UUIDMost", this.entityUniqueID.getMostSignificantBits());
        par1NBTTagCompound.setLong("UUIDLeast", this.entityUniqueID.getLeastSignificantBits());
        if (customEntityData != null)
        {
          par1NBTTagCompound.setCompoundTag("ForgeData", customEntityData);
        }

        for (String identifier : this.extendedProperties.keySet()){
          try{
            IExtendedEntityProperties props = this.extendedProperties.get(identifier);
            props.saveNBTData(par1NBTTagCompound);
          }catch (Throwable t){
            FMLLog.severe("Failed to save extended properties for %s.  This is a mod issue.", identifier);
            t.printStackTrace();
          }
        }

        this.writeEntityToNBT(par1NBTTagCompound);

        if (this.ridingEntity != null)
        {
          NBTTagCompound nbttagcompound1 = new NBTTagCompound("Riding");

          if (this.ridingEntity.writeMountToNBT(nbttagcompound1))
          {
            par1NBTTagCompound.setTag("Riding", nbttagcompound1);
          }
        }
      }
      catch (Throwable throwable)
      {
        CrashReport crashreport = CrashReport.makeCrashReport(throwable, "Saving entity NBT");
        CrashReportCategory crashreportcategory = crashreport.makeCategory("Entity being saved");
        this.addEntityCrashInfo(crashreportcategory);
        throw new ReportedException(crashreport);
      }

      try
      {
        NBTTagCompound nbttagcompound = new NBTTagCompound();
        par1EntityPlayer.writeToNBT(nbttagcompound);
        File file1 = new File(this.playersDirectory, par1EntityPlayer.getCommandSenderName() + ".dat.tmp");
        File file2 = new File(this.playersDirectory, par1EntityPlayer.getCommandSenderName() + ".dat");
        CompressedStreamTools.writeCompressed(nbttagcompound, new FileOutputStream(file1));

        if (file2.exists())
        {
          file2.delete();
        }

        file1.renameTo(file2);
      }
      catch (Exception exception)
      {
        MinecraftServer.getServer().getLogAgent().logWarning("Failed to save player data for " + par1EntityPlayer.getCommandSenderName());
      }
      DataOutputStream dataoutputstream = RegionFileCache.getChunkOutputStream(this.chunkSaveLocation, par1AnvilChunkLoaderPending.chunkCoordinate.chunkXPos, par1AnvilChunkLoaderPending.chunkCoordinate.chunkZPos);
      CompressedStreamTools.write(par1AnvilChunkLoaderPending.nbtTags, dataoutputstream);
      dataoutputstream.close();

    }


    public NBTTagCompound getPlayerData(String par1Str)
    {
      try
      {
        File file1 = new File(this.playersDirectory, par1Str + ".dat");

        if (file1.exists())
        {
          return CompressedStreamTools.readCompressed(new FileInputStream(file1));
        }
      }
      catch (Exception exception)
      {
        MinecraftServer.getServer().getLogAgent().logWarning("Failed to load player data for " + par1Str);
      }

      return null;
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
      // before visiting entries in a directory we copy the directory
      // (okay if directory already exists).

      Path newdir = target.resolve(source.relativize(dir));
      try {
         System.out.println("creating new directory: " + newdir.toString());
//        Files.copy(dir, newdir, options);
      } catch (FileAlreadyExistsException x) {
        // ignore
      } catch (IOException x) {
        System.err.format("Unable to create: %s: %s%n", newdir, x);
        return FileVisitResult.TERMINATE;
      }
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
//      copyFile(file, target.resolve(source.relativize(file)),
//              prompt, preserve);
      System.out.println("copying file " + file.toString() + " to " + target.resolve(source.relativize(file)));
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
 /*
      // fix up modification time of directory when done
      if (exc == null && preserve) {
        Path newdir = target.resolve(source.relativize(dir));
        try {
          FileTime time = Files.getLastModifiedTime(dir);
          Files.setLastModifiedTime(newdir, time);
        } catch (IOException x) {
          System.err.format("Unable to copy all attributes to: %s: %s%n", newdir, x);
        }
      }
*/
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc) {
      if (exc instanceof FileSystemLoopException) {
        System.err.println("cycle detected: " + file);
      } else {
        System.err.format("Unable to copy: %s: %s%n", file, exc);
      }
      return FileVisitResult.TERMINATE;
    }
  }

  public static void main(String[] args) throws IOException {

    // remaining arguments are the source files(s) and the target location
    Path target = Paths.get(args[argi]);

    // check if target is a directory
    boolean isDir = Files.isDirectory(target);

    // copy each source file/directory to target
    for (i=0; i<source.length; i++) {
      Path dest = (isDir) ? target.resolve(source[i].getFileName()) : target;

      if (recursive) {
        // follow links when copying files
        EnumSet<FileVisitOption> opts = EnumSet.of(FileVisitOption.FOLLOW_LINKS);
        TreeCopier tc = new TreeCopier(source[i], dest, prompt, preserve);
        Files.walkFileTree(source[i], opts, Integer.MAX_VALUE, tc);
      } else {
        // not recursive so source must not be a directory
        if (Files.isDirectory(source[i])) {
          System.err.format("%s: is a directory%n", source[i]);
          continue;
        }
        copyFile(source[i], dest, prompt, preserve);
      }
    }
  }
}