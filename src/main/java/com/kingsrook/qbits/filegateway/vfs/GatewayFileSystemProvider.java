/*
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2021-2025.  Kingsrook, LLC
 * 651 N Broad St Ste 205 # 6917 | Middletown DE 19709 | United States
 * contact@kingsrook.com
 * https://github.com/Kingsrook/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kingsrook.qbits.filegateway.vfs;


import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.spi.FileSystemProvider;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;


/*******************************************************************************
 ** NIO operations resolve against the virtual tree; every mutation is denied and audited.
 *******************************************************************************/
public class GatewayFileSystemProvider extends FileSystemProvider
{
   private final GatewayFileSystem fileSystem;



   /*******************************************************************************
    ** Bind one virtual filesystem.
    *******************************************************************************/
   public GatewayFileSystemProvider(GatewayFileSystem fileSystem)
   {
      this.fileSystem = fileSystem;
   }



   /*******************************************************************************
    ** Canonical virtual path in this session.
    *******************************************************************************/
   private String name(Path path)
   {
      if(path.getFileSystem() != fileSystem)
      {
         throw (new ProviderMismatchException());
      }
      return (path.toAbsolutePath().normalize().toString());
   }



   /*******************************************************************************
    ** Virtual provider getScheme.
    *******************************************************************************/
   @Override
   public String getScheme()
   {
      return ("gateway");
   }



   /*******************************************************************************
    ** Virtual provider newFileSystem.
    *******************************************************************************/
   @Override
   public FileSystem newFileSystem(URI uri, Map<String, ?> env)
   {
      throw (new FileSystemAlreadyExistsException());
   }



   /*******************************************************************************
    ** Virtual provider getFileSystem.
    *******************************************************************************/
   @Override
   public FileSystem getFileSystem(URI uri)
   {
      return (fileSystem);
   }



   /*******************************************************************************
    ** Virtual provider getPath.
    *******************************************************************************/
   @Override
   public Path getPath(URI uri)
   {
      return (fileSystem.getPath(uri.getPath()));
   }



   /*******************************************************************************
    ** Virtual provider newByteChannel.
    *******************************************************************************/
   @Override
   public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException
   {
      boolean write = options.contains(StandardOpenOption.WRITE) || options.contains(StandardOpenOption.APPEND) || options.contains(StandardOpenOption.CREATE) || options.contains(StandardOpenOption.CREATE_NEW);
      if(!write)
      {
         fileSystem.tree().stat(name(path));
      }
      fileSystem.tree().deny(name(path), write ? "UPLOAD" : "DOWNLOAD", "not yet supported");
      throw (new AssertionError());
   }



   /*******************************************************************************
    ** Virtual provider newDirectoryStream.
    *******************************************************************************/
   @Override
   public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException
   {
      List<Path> entries = new ArrayList<>();
      for(VirtualTree.Node node : fileSystem.tree().list(name(dir)))
      {
         Path path = fileSystem.getPath(node.path());
         if(filter.accept(path))
         {
            entries.add(path);
         }
      }
      return (new GatewayDirectoryStream(entries));
   }



   /*******************************************************************************
    ** Virtual provider createDirectory.
    *******************************************************************************/
   @Override
   public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException
   {
      fileSystem.tree().deny(name(dir), "UPLOAD", "mkdir is not supported");
   }



   /*******************************************************************************
    ** Virtual provider delete.
    *******************************************************************************/
   @Override
   public void delete(Path path) throws IOException
   {
      fileSystem.tree().deny(name(path), "REMOVE", "Removal is not supported");
   }



   /*******************************************************************************
    ** Virtual provider copy.
    *******************************************************************************/
   @Override
   public void copy(Path source, Path target, CopyOption... options) throws IOException
   {
      fileSystem.tree().deny(name(target), "UPLOAD", "Copy is not supported");
   }



   /*******************************************************************************
    ** Virtual provider move.
    *******************************************************************************/
   @Override
   public void move(Path source, Path target, CopyOption... options) throws IOException
   {
      fileSystem.tree().deny(name(source), "RENAME", "Rename is not supported");
   }



   /*******************************************************************************
    ** Virtual provider isSameFile.
    *******************************************************************************/
   @Override
   public boolean isSameFile(Path path, Path path2)
   {
      return (name(path).equals(name(path2)));
   }



   /*******************************************************************************
    ** Virtual provider isHidden.
    *******************************************************************************/
   @Override
   public boolean isHidden(Path path)
   {
      return (false);
   }



   /*******************************************************************************
    ** Virtual provider getFileStore.
    *******************************************************************************/
   @Override
   public FileStore getFileStore(Path path)
   {
      throw (new UnsupportedOperationException("No native store"));
   }



   /*******************************************************************************
    ** Virtual provider checkAccess.
    *******************************************************************************/
   @Override
   public void checkAccess(Path path, AccessMode... modes) throws IOException
   {
      fileSystem.tree().stat(name(path));
      for(AccessMode mode : modes)
      {
         if(mode == AccessMode.WRITE)
         {
            fileSystem.tree().deny(name(path), "UPLOAD", "Writes are not supported");
         }
      }
   }



   /*******************************************************************************
    ** Virtual provider getFileAttributeView.
    *******************************************************************************/
   @Override
   public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options)
   {
      return (null);
   }



   /*******************************************************************************
    ** Virtual provider readAttributes.
    *******************************************************************************/
   @Override
   public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options) throws IOException
   {
      return (type.cast(new GatewayFileAttributes(fileSystem.tree().stat(name(path)), FileTime.from(Instant.now()))));
   }



   /*******************************************************************************
    ** Virtual provider readAttributes.
    *******************************************************************************/
   @Override
   public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException
   {
      var attrs = readAttributes(path, GatewayFileAttributes.class, options);
      Map<String, Object> result = new HashMap<>();
      result.put("isDirectory", attrs.isDirectory());
      result.put("isRegularFile", attrs.isRegularFile());
      result.put("isSymbolicLink", false);
      result.put("isOther", false);
      result.put("size", 0L);
      result.put("fileKey", attrs.fileKey());
      result.put("lastModifiedTime", attrs.lastModifiedTime());
      result.put("lastAccessTime", attrs.lastAccessTime());
      result.put("creationTime", attrs.creationTime());
      result.put("permissions", attrs.permissions());
      result.put("owner", attrs.owner());
      result.put("group", attrs.group());
      return (result);
   }



   /*******************************************************************************
    ** Virtual provider setAttribute.
    *******************************************************************************/
   @Override
   public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException
   {
      fileSystem.tree().deny(name(path), "UPLOAD", "setattr is not supported");
   }



   /*******************************************************************************
    ** Virtual provider createSymbolicLink.
    *******************************************************************************/
   @Override
   public void createSymbolicLink(Path link, Path target, FileAttribute<?>... attrs) throws IOException
   {
      fileSystem.tree().deny(name(link), "UPLOAD", "Links are not supported");
   }



   /*******************************************************************************
    ** Virtual provider createLink.
    *******************************************************************************/
   @Override
   public void createLink(Path link, Path existing) throws IOException
   {
      fileSystem.tree().deny(name(link), "UPLOAD", "Links are not supported");
   }


   /*******************************************************************************
    ** Single-use directory snapshot held entirely in memory.
    *******************************************************************************/
   private static class GatewayDirectoryStream implements DirectoryStream<Path>
   {
      private final List<Path> entries;
      private boolean used;



      /*******************************************************************************
       ** Directory snapshot lifecycle.
       *******************************************************************************/
      GatewayDirectoryStream(List<Path> entries)
      {
         this.entries = entries;
      }



      /*******************************************************************************
       ** Directory snapshot lifecycle.
       *******************************************************************************/
      @Override
      public Iterator<Path> iterator()
      {
         if(used)
         {
            throw (new IllegalStateException("Directory stream is closed or already iterated"));
         }
         used = true;
         return (entries.iterator());
      }



      /*******************************************************************************
       ** Directory snapshot lifecycle.
       *******************************************************************************/
      @Override
      public void close()
      {
         used = true;
      }
   }
}
