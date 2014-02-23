package controllers

import play.api.mvc._
import rww.auth.BrowserCertBehavior
import scala.concurrent.Future


object Certificates {
  import Results._

  def display() = Action.async { request=>
    import plantain.executionContext //todo: what EC should one use?
    val res = for {
      certs <- request.certs(true)
    } yield {
      Ok(<html><body>
        <p>The certificates received are:</p>
        {if (certs.length == 0) <p>None</p>
        else {
          <ul>
            {for (cert <- certs) yield <li>
            <pre>
              {cert}
            </pre>
          </li>}
          </ul>
        }
        }
      </body></html>).as("text/html")
    }
    res.recover{
      case e => Ok(e.toString)
    }
  }

}