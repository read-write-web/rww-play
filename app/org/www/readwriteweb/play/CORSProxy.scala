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

package org.www.readwriteweb.play

import play.api.mvc._
import akka.util.Timeout
import scala.concurrent.{ExecutionContext, Future, Promise}
import akka.actor.ActorSystem
import play.api.libs.iteratee._
import play.api.libs.ws.WS
import play.api.mvc.SimpleResult
import play.api.mvc.ResponseHeader

/**
* A <a href="http://www.w3.org/TR/cors/">CORS</a> proxy that allows a client to fetch remote RDF
* resources that do not have the required CORS headers.
*
* Currently this only permits GET operations. It is unclear what PUT, POST, DELETE operations would
* look like for a CORS proxy.
*
* note: graphSelector used to be an IterateeSelector[Rdf#Graph], should try to get write non blocking parsers
*/
class CORSProxy extends Controller {

  implicit val system = ActorSystem("MySystem")
  implicit val executionContext = scala.concurrent.ExecutionContext.global //todo: make sure this is right
  // turn a header map into an (att,val) sequence
  private implicit def sequentialise(headers: Map[String,Seq[String]]) = headers.toSeq.flatMap(pair=>pair._2.map(v=>(pair._1,v)))

  def get(url: String) = EssentialAction { request =>
    implicit val timeout = Timeout(10 * 1000)
    val res: Iteratee[Array[Byte],SimpleResult] = Iteratee.ignore[Array[Byte]].mapM { Unit =>
      val resultPromise = Promise[SimpleResult]()
      WS.url(url) //todo: develop a WS for each client, so that one can separate their views
        .withHeaders(request.headers.toSimpleMap.toSeq: _*) //todo: this looses headers, must fix WS.withHeaders method
        .get {  response =>
          val code = if (response.status == 200) 203
          else response.status
          val hdrs = response.headers.map {
            //todo: we loose info here too!
            case (key, value) => (key, value.head)
          }
          val corsHeaders = if (!hdrs.contains("Access-Control-Allow-Origin")) {
            hdrs + ("Access-Control-Allow-Origin" -> request.headers.get("Origin").getOrElse("*"))
          } else {
            hdrs
          }

          //tie an interatee and an enum together in a pipe <- key to getting this to work
          val (getIteratee, actionResultEnum) = joined[Array[Byte]]

          //todo: this does not work with chunked docs such as http://www.httpwatch.com/httpgallery/chunked/
          val result = if (response.headers.get("Transfer-Encoding").exists(_.exists(_.trim.equalsIgnoreCase("chunked")))) {
            actionResultEnum &> Results.chunk
          } else actionResultEnum

          resultPromise.trySuccess(SimpleResult(ResponseHeader(code, corsHeaders), result))
          getIteratee.map{Unit=>Done(Input.EOF)}
      }
      resultPromise.future
    }
    res
  }

  /**
   * Create a joined iteratee enumerator pair.
   * found in http://jazzy.id.au/default/2013/06/12/call_response_websockets_in_play_framework.html
   *
   * When the enumerator is applied to an iteratee, the iteratee subsequently consumes whatever the iteratee in the pair
   * is applied to.  Consequently the enumerator is "one shot", applying it to subsequent iteratees will throw an
   * exception.
   */
  def joined[A]: (Iteratee[A, Unit], Enumerator[A]) = {
    val promisedIteratee = Promise[Iteratee[A, Unit]]()
    val enumerator = new Enumerator[A] {
      def apply[B](finalIteratee: Iteratee[A, B]): Future[Iteratee[A,B]] = {
        val doneIteratee = Promise[Iteratee[A, B]]()

        // Equivalent to map, but allows us to handle failures
        def wrap(delegate: Iteratee[A, B]): Iteratee[A, B] = new Iteratee[A, B] {
          def fold[C](folder: (Step[A, B]) => Future[C])(implicit ec: ExecutionContext): Future[C] = {
            val toReturn = delegate.fold {
              case done @ Step.Done(a, in) => {
                doneIteratee.success(done.it)
                folder(done)
              }
              case Step.Cont(k) => {
                folder(Step.Cont(k.andThen(wrap)))
              }
              case err @ Step.Error(msg,in)=> {
                doneIteratee.failure(new Exception(msg)) //todo: this may not be right, for if the code is parsed and can backtrack
                folder(err)
              }
            }
            toReturn.onFailure {
              case e => doneIteratee.failure(e)
            }
            toReturn
          }
        }

        if (promisedIteratee.trySuccess(wrap(finalIteratee).map(_ => ()))) {
          doneIteratee.future
        } else {
          throw new IllegalStateException("Joined enumerator may only be applied once")
        }
      }
    }
    (Iteratee.flatten(promisedIteratee.future), enumerator)
  }

}
