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

package com.kingsrook.qbits.filegateway;


import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Snapshots real disk boundaries around every SFTP test; target build products are excluded.
 *******************************************************************************/
public class NoDiskExtension implements BeforeEachCallback, AfterEachCallback
{
   private Map<String, FileTime> before;

   /*******************************************************************************
    ** Before each.
    *******************************************************************************/
   @Override
   public void beforeEach(ExtensionContext context) throws Exception
   {
      Files.createDirectories(Path.of(System.getProperty("java.io.tmpdir")));
      Files.createDirectories(Path.of(System.getProperty("user.home")));
      before = snapshot();
   }



   /*******************************************************************************
    ** After each.
    *******************************************************************************/
   @Override
   public void afterEach(ExtensionContext context) throws Exception
   {
      Map<String, FileTime> after = snapshot();
      assertThat(after.keySet()).as("Files created or removed outside the temporary area").isEqualTo(before.keySet());
      after.forEach((path, time) -> assertThat(time).as("Modified: %s", path).isEqualTo(before.get(path)));
      try(var files = Files.list(Path.of(System.getProperty("java.io.tmpdir"))))
      {
         assertThat(files.toList()).isEmpty();
      }
   }

   private Map<String, FileTime> snapshot() throws Exception
   {
      Map<String, FileTime> result = new TreeMap<>();
      Path working = Path.of("").toAbsolutePath();
      try(var files = Files.walk(working))
      {
         for(Path file : files.filter(p -> !p.startsWith(working.resolve("target"))).toList())
         {
            result.put(file.toString(), Files.getLastModifiedTime(file));
         }
      }
      try(var files = Files.list(Path.of(System.getProperty("user.home"))))
      {
         for(Path file : files.toList())
         {
            result.put(file.toString(), Files.getLastModifiedTime(file));
         }
      }
      return (result);
   }
}
