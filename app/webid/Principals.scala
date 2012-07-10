///*
// * Copyright (c) 2011 Henry Story (bblfish.net)
// * under the MIT licence defined
// *    http://www.opensource.org/licenses/mit-license.html
// *
// * Permission is hereby granted, free of charge, to any person obtaining a copy of
// * this software and associated documentation files (the "Software"), to deal in the
// * Software without restriction, including without limitation the rights to use, copy,
// * modify, merge, publish, distribute, sublicense, and/or sell copies of the Software,
// * and to permit persons to whom the Software is furnished to do so, subject to the
// * following conditions:
// *
// * The above copyright notice and this permission notice shall be included in all
// * copies or substantial portions of the Software.
// *
// * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
// * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
// * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
// * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
// * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
// * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
// */
//
//package org.w3.webid.auth
//
//import java.security.Principal
//import com.hp.hpl.jena.rdf.model.Model
//import com.hp.hpl.jena.shared.WrappedIOException
//import scalaz.{Scalaz, Validation}
//import Scalaz._
//import java.net.{ConnectException, URL}
//import org.w3.readwriteweb.CacheControl
//import org.w3.readwriteweb.GraphCache
//import org.w3.readwriteweb.{CacheControl, GraphCache}
//import org.w3.readwriteweb.auth.SANFailure
//import org.w3.readwriteweb.auth.UnsupportedProtocol._
//import org.w3.readwriteweb.auth.URISyntaxError._
//import org.w3.readwriteweb.auth.ProfileError
//import org.w3.readwriteweb.auth.ProfileGetError
//import org.w3.readwriteweb.auth.ProfileParseError
//
///**
// * @author Henry Story from http://bblfish.net/
// * @created: 09/10/2011
// */
//
///**
// * The WebID - ie, verified identity - something like a Principal.
// * it is arguable that it should know what it was verified against.
// */
//protected object WebID {
//
//  def apply(subjectAlternativeName: String): Validation[SANFailure,WebID] =
//    toUrl(subjectAlternativeName) flatMap {  url =>
//      val protocol = url.getProtocol
//      if ("http".equals(protocol) || "https".equals(protocol)) {
//        new WebID(url).success
//      } else UnsupportedProtocol("only http and https url supported at present",subjectAlternativeName).fail
//    }
//
//
//
//  def toUrl(urlStr: String): Validation[SANFailure,URL] = {
//    try { new URL(urlStr).success } catch {
//      // oops: should be careful, not all SANs are perhaps traditionally written out as a full URL.
//      case e => URISyntaxError("unparseable Subject Alternative Name",urlStr).fail
//    }
//  }
//
//}
//
///**
// * A WebID Principal
// * Can only be constructed by the object, which does some minimal verifications
// **/
//case class WebID private (val url: URL) extends Principal {
//  import org.w3.readwriteweb.util.wrapValidation
//
//  def getName = url.toExternalForm
//
//  override def equals(that: Any) = that match {
//    case other: WebID => other.url == url
//    case _ => false
//  }
//
//  //TODO: now that we are no longer passing the GraphCache around it's questionable whether we still need this method
//  //in this class
//  def getDefiningModel(cacheControl: CacheControl.Value = CacheControl.CacheFirst): Validation[ProfileError, Model] =
//    GraphCache.resource(url).get(cacheControl) failMap {
//      case ioe: WrappedIOException => new ProfileGetError("error fetching profile", Some(ioe),url)
//      case connE : ConnectException => new ProfileGetError("error fetching profile", Some(connE),url)
//      case other => new ProfileParseError("error parsing profile", Some(other),url)
//    }
//}
//
//
//case class Anonymous() extends Principal {
//  def getName = "anonymous"
//  override def equals(that: Any) =  that match {
//    case other: Principal => other eq this
//    case _ => false
//  } //anonymous principals are equal only when they are identical. is this wise?
//  //well we don't know when two anonymous people are the same or different.
//}