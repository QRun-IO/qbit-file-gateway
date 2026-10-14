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

import com.kingsrook.qbits.filegateway.sftp.CidrMatcher;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;

/*******************************************************************************
 ** IPv4 and IPv6 boundaries, multiple networks and numeric-only fail-closed parsing.
 *******************************************************************************/
class CidrMatcherTest
{
   /*******************************************************************************
    ** Networks never require hostname resolution or accept invalid prefix lengths.
    *******************************************************************************/
   @ParameterizedTest
   @CsvSource({
      "192.0.2.255,192.0.2.0/24,true",
      "192.0.3.0,192.0.2.0/24,false",
      "127.0.0.1,0.0.0.0/0,true",
      "127.0.0.1,127.0.0.1/32,true",
      "127.0.0.1,127.0.0.1/33,false",
      "127.0.0.1,127.0.0.1/-1,false",
      "127.0.0.1,deadbeef/32,false",
      "127.0.0.1,127.0.0/24,false",
      "127.0.0.1,'192.0.2.0/24,127.0.0.0/8',true",
      "2001:db8::ffff,2001:db8::/32,true",
      "2001:db9::1,2001:db8::/32,false",
      "::1,::1/128,true",
      "::1,::1/129,false"
   })
   /*******************************************************************************
    ** Matches numeric networks.
    *******************************************************************************/
   void matchesNumericNetworks(String address, String cidrs, boolean allowed)
   {
      assertThat(CidrMatcher.matches(address, cidrs)).isEqualTo(allowed);
   }
}
