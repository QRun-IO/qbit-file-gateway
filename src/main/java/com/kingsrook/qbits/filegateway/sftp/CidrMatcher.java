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

package com.kingsrook.qbits.filegateway.sftp;


import java.net.InetAddress;


/*******************************************************************************
 ** Numeric IPv4 and IPv6 CIDR matching; hostnames never trigger DNS resolution.
 *******************************************************************************/
public final class CidrMatcher
{



   /*******************************************************************************
    ** Utility only.
    *******************************************************************************/
   private CidrMatcher()
   {
   }



   /*******************************************************************************
    ** Match an address against comma or whitespace separated networks.
    *******************************************************************************/
   public static boolean matches(String address, String cidrs)
   {
      if(cidrs == null || cidrs.isBlank())
      {
         return (true);
      }
      try
      {
         byte[] candidate = numeric(address);
         for(String cidr : cidrs.split("[,\\s]+"))
         {
            String[] parts = cidr.split("/", -1);
            byte[] network = numeric(parts[0]);
            int bits = parts.length == 1 ? network.length * 8 : Integer.parseInt(parts[1]);
            if(parts.length > 2 || bits < 0 || bits > network.length * 8)
            {
               return (false);
            }
            if(candidate.length != network.length)
            {
               continue;
            }
            boolean match = true;
            for(int i = 0; i < bits; i++)
            {
               int mask = 1 << (7 - i % 8);
               if((candidate[i / 8] & mask) != (network[i / 8] & mask))
               {
                  match = false;
                  break;
               }
            }
            if(match)
            {
               return (true);
            }
         }
      }
      catch(Exception e)
      {
         return (false);
      }
      return (false);
   }



   /*******************************************************************************
    ** Parse numeric addresses only.
    *******************************************************************************/
   private static byte[] numeric(String address) throws Exception
   {
      if(address == null || !address.matches("[0-9a-fA-F:.]+") || (!address.contains(":") && !address.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")))
      {
         throw (new IllegalArgumentException("Expected a numeric IP address"));
      }
      return (InetAddress.getByName(address).getAddress());
   }
}
