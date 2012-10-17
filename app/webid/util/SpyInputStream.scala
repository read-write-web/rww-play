/*
 * Copyright 2012 Henry Story, http://bblfish.net/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.w3.readwriteweb.util

import java.io.{IOException, OutputStream, InputStream}


/**
 * Wrap an inputstream and write everything that comes in here
 * @author hjs
 * @created: 30/10/2011
 */

class SpyInputStream(val in: InputStream, val out: OutputStream) extends InputStream {
  var stopOut = false

  def read() ={

    val i = try {
      in.read()
    } catch {
      case ioe: IOException => {
        out.flush()
        out.close()
        stopOut=true
        throw ioe;
      }
    }
    if (!stopOut) try {
      out.write(i)
    } catch {
      case ioe: IOException => {
        stopOut = true
      }
    }
    i
  }
}