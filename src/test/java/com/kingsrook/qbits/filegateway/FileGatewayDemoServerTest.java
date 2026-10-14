/*
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2021-2026.  Kingsrook, LLC
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


import com.kingsrook.qbits.filegateway.metadata.FileGatewayTables;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** The demo server seeds and serves (smoke test; the demo itself is manual).
 *******************************************************************************/
class FileGatewayDemoServerTest
{
   /*******************************************************************************
    ** Reset the demo-only grant.
    *******************************************************************************/
   @AfterEach
   void reset()
   {
      FileGatewayTestServer.TestAuthentication.grantAllToUiSessions = false;
   }



   /*******************************************************************************
    ** Start on a free port, check seeded data, stop.
    *******************************************************************************/
   @Test
   void startsAndSeeds() throws Exception
   {
      try(FileGatewayTestServer server = FileGatewayDemoServer.start(0))
      {
         assertThat(server.audit.query(FileGatewayTables.ACCOUNT, new QQueryFilter())).hasSize(3);
         assertThat(server.audit.query(FileGatewayTables.UPLOAD_JOB, new QQueryFilter())).hasSize(40);
         assertThat(server.audit.query(FileGatewayTables.OUTBOX_FILE, new QQueryFilter())).hasSize(3);
      }
   }
}
