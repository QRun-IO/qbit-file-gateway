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


import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.util.Set;


/*******************************************************************************
 ** Virtual attributes: generated files have size zero and read-only permissions.
 *******************************************************************************/
public record GatewayFileAttributes(VirtualTree.Node node, FileTime time) implements PosixFileAttributes
{



   /*******************************************************************************
    ** Virtual attribute lastModifiedTime.
    *******************************************************************************/
   @Override
   public FileTime lastModifiedTime()
   {
      return (time);
   }



   /*******************************************************************************
    ** Virtual attribute lastAccessTime.
    *******************************************************************************/
   @Override
   public FileTime lastAccessTime()
   {
      return (time);
   }



   /*******************************************************************************
    ** Virtual attribute creationTime.
    *******************************************************************************/
   @Override
   public FileTime creationTime()
   {
      return (time);
   }



   /*******************************************************************************
    ** Virtual attribute isRegularFile.
    *******************************************************************************/
   @Override
   public boolean isRegularFile()
   {
      return (!node.directory());
   }



   /*******************************************************************************
    ** Virtual attribute isDirectory.
    *******************************************************************************/
   @Override
   public boolean isDirectory()
   {
      return (node.directory());
   }



   /*******************************************************************************
    ** Virtual attribute isSymbolicLink.
    *******************************************************************************/
   @Override
   public boolean isSymbolicLink()
   {
      return (false);
   }



   /*******************************************************************************
    ** Virtual attribute isOther.
    *******************************************************************************/
   @Override
   public boolean isOther()
   {
      return (false);
   }



   /*******************************************************************************
    ** Virtual attribute size.
    *******************************************************************************/
   @Override
   public long size()
   {
      return (0);
   }



   /*******************************************************************************
    ** Virtual attribute fileKey.
    *******************************************************************************/
   @Override
   public Object fileKey()
   {
      return (node.path());
   }



   /*******************************************************************************
    ** Virtual attribute owner.
    *******************************************************************************/
   @Override
   public UserPrincipal owner()
   {
      return (() -> "gateway");
   }



   /*******************************************************************************
    ** Virtual attribute group.
    *******************************************************************************/
   @Override
   public GroupPrincipal group()
   {
      return (() -> "gateway");
   }



   /*******************************************************************************
    ** Virtual attribute permissions.
    *******************************************************************************/
   @Override
   public Set<PosixFilePermission> permissions()
   {
      return (node.directory() ? Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE) : Set.of(PosixFilePermission.OWNER_READ));
   }
}
