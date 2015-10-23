package utils

import play.api.libs.mailer._
import play.twirl.api.{Html, Txt}
import play.api.Logger
import play.api.libs.concurrent.Akka
import play.api.Play._

/**
 * @author Sebastien Lorber (lorber.sebastien@gmail.com)
 */
class Mailer(mailerClient: MailerAPI) {

  val DefaultFromAddress = current.configuration.getString("smtp.from").get

  def sendEmail(to: String, subject: String, body: (Txt, Html)): Unit = sendEmail(None,to,subject,(Some(body._1),Some(body._2)))

  def sendEmail(from: Option[String], to: String, subject: String, body: (Option[Txt], Option[Html])): Unit = {
    import scala.concurrent.duration._
    import play.api.libs.concurrent.Execution.Implicits.defaultContext
    val fromEmail = from.getOrElse(DefaultFromAddress)
    if ( Logger.isDebugEnabled ) {
      Logger.debug(s"Sending email to $to with body $body")
    }
    // TODO should we really schedule this?
    Akka.system.scheduler.scheduleOnce(1.seconds) {
      val mail = Email(subject,fromEmail,Seq(to),body._1.map(_.body),body._2.map(_.body))
      mailerClient.send(mail)
    }
  }


}
