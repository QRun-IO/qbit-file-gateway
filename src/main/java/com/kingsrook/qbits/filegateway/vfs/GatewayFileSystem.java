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


import java.nio.file.ClosedFileSystemException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.List;
import java.util.Set;


/*******************************************************************************
 ** Session-private NIO filesystem over a UI API virtual tree, with no native mounts.
 *******************************************************************************/
public class GatewayFileSystem extends FileSystem
{
   private final VirtualTree tree;
   private final GatewayFileSystemProvider provider;
   private boolean open = true;



   /*******************************************************************************
    ** Create a purely virtual filesystem.
    *******************************************************************************/
   public GatewayFileSystem(VirtualTree tree)
   {
      this.tree = tree;
      this.provider = new GatewayFileSystemProvider(this);
   }



   /*******************************************************************************
    ** Access the live virtual tree.
    *******************************************************************************/
   public VirtualTree tree()
   {
      if(!open)
      {
         throw (new ClosedFileSystemException());
      }
      return (tree);
   }



   /*******************************************************************************
    ** Virtual filesystem provider.
    *******************************************************************************/
   @Override
   public FileSystemProvider provider()
   {
      return (provider);
   }



   /*******************************************************************************
    ** Virtual filesystem close.
    *******************************************************************************/
   @Override
   public void close()
   {
      open = false;
   }



   /*******************************************************************************
    ** Virtual filesystem isOpen.
    *******************************************************************************/
   @Override
   public boolean isOpen()
   {
      return (open);
   }



   /*******************************************************************************
    ** Virtual filesystem isReadOnly.
    *******************************************************************************/
   @Override
   public boolean isReadOnly()
   {
      return (true);
   }



   /*******************************************************************************
    ** Virtual filesystem getSeparator.
    *******************************************************************************/
   @Override
   public String getSeparator()
   {
      return ("/");
   }



   /*******************************************************************************
    ** Virtual filesystem getRootDirectories.
    *******************************************************************************/
   @Override
   public Iterable<Path> getRootDirectories()
   {
      return (List.of(getPath("/")));
   }



   /*******************************************************************************
    ** Virtual filesystem getFileStores.
    *******************************************************************************/
   @Override
   public Iterable<FileStore> getFileStores()
   {
      return (List.of());
   }



   /*******************************************************************************
    ** Virtual filesystem supportedFileAttributeViews.
    *******************************************************************************/
   @Override
   public Set<String> supportedFileAttributeViews()
   {
      return (Set.of("basic", "posix"));
   }



   /*******************************************************************************
    ** Virtual filesystem getPath.
    *******************************************************************************/
   @Override
   public Path getPath(String first, String... more)
   {
      return (new GatewayPath(this, first + (more.length == 0 ? "" : "/" + String.join("/", more))));
   }



   /*******************************************************************************
    ** Virtual filesystem getPathMatcher.
    *******************************************************************************/
   @Override
   public PathMatcher getPathMatcher(String syntaxAndPattern)
   {
      throw (new UnsupportedOperationException("Path matching is unavailable"));
   }



   /*******************************************************************************
    ** Virtual filesystem getUserPrincipalLookupService.
    *******************************************************************************/
   @Override
   public UserPrincipalLookupService getUserPrincipalLookupService()
   {
      throw (new UnsupportedOperationException("Native users are unavailable"));
   }



   /*******************************************************************************
    ** Virtual filesystem newWatchService.
    *******************************************************************************/
   @Override
   public WatchService newWatchService()
   {
      throw (new UnsupportedOperationException("Watching is unavailable"));
   }
}
