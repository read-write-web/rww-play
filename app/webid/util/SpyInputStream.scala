/*
 * Copyright (c) 2011 Henry Story (bblfish.net)
 * under the MIT licence defined at
 *    http://www.opensource.org/licenses/mit-license.html
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of 
 * this software and associated documentation files (the "Software"), to deal in the
 * Software without restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so, subject to the
 * following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
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