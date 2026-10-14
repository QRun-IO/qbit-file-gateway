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


import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;


/*******************************************************************************
 ** A path made solely from virtual name segments; never delegates to a native filesystem.
 *******************************************************************************/
public final class GatewayPath implements Path
{
   private final GatewayFileSystem fileSystem;
   private final String value;



   /*******************************************************************************
    ** Construct a virtual path.
    *******************************************************************************/
   public GatewayPath(GatewayFileSystem fileSystem, String value)
   {
      this.fileSystem = fileSystem;
      this.value = value.replaceAll("/+", "/").replaceAll("(?<=.)/$", "");
   }



   /*******************************************************************************
    ** Individual name segments.
    *******************************************************************************/
   private List<String> parts()
   {
      return (value.isEmpty() || "/".equals(value) ? List.of() : Arrays.asList(value.replaceFirst("^/", "").split("/")));
   }



   /*******************************************************************************
    ** Virtual path getFileSystem.
    *******************************************************************************/
   @Override
   public FileSystem getFileSystem()
   {
      return (fileSystem);
   }



   /*******************************************************************************
    ** Virtual path isAbsolute.
    *******************************************************************************/
   @Override
   public boolean isAbsolute()
   {
      return (value.startsWith("/"));
   }



   /*******************************************************************************
    ** Virtual path getRoot.
    *******************************************************************************/
   @Override
   public Path getRoot()
   {
      return (isAbsolute() ? new GatewayPath(fileSystem, "/") : null);
   }



   /*******************************************************************************
    ** Virtual path getFileName.
    *******************************************************************************/
   @Override
   public Path getFileName()
   {
      List<String> parts = parts();
      return (parts.isEmpty() ? null : new GatewayPath(fileSystem, parts.getLast()));
   }



   /*******************************************************************************
    ** Virtual path getParent.
    *******************************************************************************/
   @Override
   public Path getParent()
   {
      int slash = value.lastIndexOf('/');
      return (slash < 0 || "/".equals(value) ? null : new GatewayPath(fileSystem, slash == 0 ? "/" : value.substring(0, slash)));
   }



   /*******************************************************************************
    ** Virtual path getNameCount.
    *******************************************************************************/
   @Override
   public int getNameCount()
   {
      return (parts().size());
   }



   /*******************************************************************************
    ** Virtual path getName.
    *******************************************************************************/
   @Override
   public Path getName(int index)
   {
      return (new GatewayPath(fileSystem, parts().get(index)));
   }



   /*******************************************************************************
    ** Virtual path subpath.
    *******************************************************************************/
   @Override
   public Path subpath(int beginIndex, int endIndex)
   {
      return (new GatewayPath(fileSystem, String.join("/", parts().subList(beginIndex, endIndex))));
   }



   /*******************************************************************************
    ** Virtual path startsWith.
    *******************************************************************************/
   @Override
   public boolean startsWith(Path other)
   {
      return (other instanceof GatewayPath p && p.fileSystem == fileSystem && (value.equals(p.value) || value.startsWith(p.value.endsWith("/") ? p.value : p.value + "/")));
   }



   /*******************************************************************************
    ** Virtual path endsWith.
    *******************************************************************************/
   @Override
   public boolean endsWith(Path other)
   {
      return (other instanceof GatewayPath p && p.fileSystem == fileSystem && (value.equals(p.value) || (!p.isAbsolute() && value.endsWith("/" + p.value))));
   }



   /*******************************************************************************
    ** Virtual path normalize.
    *******************************************************************************/
   @Override
   public Path normalize()
   {
      Deque<String> names = new ArrayDeque<>();
      for(String part : parts())
      {
         if(part.equals(".") || part.isEmpty())
         {
            continue;
         }
         if(part.equals("..") && !names.isEmpty() && !names.getLast().equals(".."))
         {
            names.removeLast();
         }
         else if(!part.equals("..") || !isAbsolute())
         {
            names.addLast(part);
         }
      }
      return (new GatewayPath(fileSystem, (isAbsolute() ? "/" : "") + String.join("/", names)));
   }



   /*******************************************************************************
    ** Virtual path resolve.
    *******************************************************************************/
   @Override
   public Path resolve(Path other)
   {
      if(!(other instanceof GatewayPath p) || p.fileSystem != fileSystem)
      {
         throw (new ProviderMismatchException());
      }
      return (other.isAbsolute() ? other : new GatewayPath(fileSystem, value + (value.endsWith("/") || value.isEmpty() ? "" : "/") + other));
   }



   /*******************************************************************************
    ** Virtual path relativize.
    *******************************************************************************/
   @Override
   public Path relativize(Path other)
   {
      if(!(other instanceof GatewayPath p) || p.fileSystem != fileSystem || isAbsolute() != p.isAbsolute())
      {
         throw (new IllegalArgumentException("Incompatible virtual paths"));
      }
      List<String> left = ((GatewayPath) normalize()).parts();
      List<String> right = ((GatewayPath) p.normalize()).parts();
      int common = 0;
      while(common < left.size() && common < right.size() && left.get(common).equals(right.get(common)))
      {
         common++;
      }
      List<String> result = new ArrayList<>();
      for(int i = common; i < left.size(); i++)
      {
         result.add("..");
      }
      result.addAll(right.subList(common, right.size()));
      return (new GatewayPath(fileSystem, String.join("/", result)));
   }



   /*******************************************************************************
    ** Virtual path toUri.
    *******************************************************************************/
   @Override
   public URI toUri()
   {
      try
      {
         return (new URI("gateway", null, toAbsolutePath().toString(), null));
      }
      catch(java.net.URISyntaxException e)
      {
         throw (new IllegalArgumentException(e));
      }
   }



   /*******************************************************************************
    ** Virtual path toAbsolutePath.
    *******************************************************************************/
   @Override
   public Path toAbsolutePath()
   {
      return (isAbsolute() ? this : new GatewayPath(fileSystem, "/" + value));
   }



   /*******************************************************************************
    ** Virtual path toRealPath.
    *******************************************************************************/
   @Override
   public Path toRealPath(LinkOption... options) throws IOException
   {
      Path path = toAbsolutePath().normalize();
      fileSystem.tree().stat(path.toString());
      return (path);
   }



   /*******************************************************************************
    ** Virtual path toFile.
    *******************************************************************************/
   @Override
   public File toFile()
   {
      throw (new UnsupportedOperationException("Virtual paths have no native file"));
   }



   /*******************************************************************************
    ** Virtual path register.
    *******************************************************************************/
   @Override
   public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers)
   {
      throw (new UnsupportedOperationException("Virtual paths cannot be watched"));
   }



   /*******************************************************************************
    ** Virtual path iterator.
    *******************************************************************************/
   @Override
   public Iterator<Path> iterator()
   {
      return (parts().stream().map(name -> (Path) new GatewayPath(fileSystem, name)).iterator());
   }



   /*******************************************************************************
    ** Virtual path compareTo.
    *******************************************************************************/
   @Override
   public int compareTo(Path other)
   {
      return (value.compareTo(other.toString()));
   }



   /*******************************************************************************
    ** Virtual path equals.
    *******************************************************************************/
   @Override
   public boolean equals(Object other)
   {
      return (other instanceof GatewayPath path && path.fileSystem == fileSystem && path.value.equals(value));
   }



   /*******************************************************************************
    ** Virtual path hashCode.
    *******************************************************************************/
   @Override
   public int hashCode()
   {
      return (31 * System.identityHashCode(fileSystem) + value.hashCode());
   }



   /*******************************************************************************
    ** Virtual path toString.
    *******************************************************************************/
   @Override
   public String toString()
   {
      return (value);
   }
}
