package utils

import play.api.templates.{Html, Txt}
import play.api.Logger
import play.api.libs.concurrent.Akka
import play.api.Play._

/**
 * @author Sebastien Lorber (lorber.sebastien@gmail.com)
 */
object Mailer {

  val DefaultFromAddress = current.configuration.getString("smtp.from").get

  def sendEmail(to: String, subject: String, body: (Txt, Html)): Unit = sendEmail(None,to,subject,(Some(body._1),Some(body._2)))

  def sendEmail(from: Option[String], to: String, subject: String, body: (Option[Txt], Option[Html])): Unit = {
    import com.typesafe.plugin._
    import scala.concurrent.duration._
    import play.api.libs.concurrent.Execution.Implicits.defaultContext
    val fromEmail = from.getOrElse(DefaultFromAddress)
    if ( Logger.isDebugEnabled ) {
      Logger.debug(s"Sending email to $to with body $body")
    }
    // TODO should we really schedule this?
    Akka.system.scheduler.scheduleOnce(1 seconds) {
      val mail = use[MailerPlugin].email
      mail.setFrom(fromEmail)
      mail.setRecipient(to)
      mail.setSubject(subject)
      // the mailer plugin handles null / empty string gracefully
      val txtEmail = body._1.map(_.body).getOrElse("")
      val htmlEmail = body._2.map(_.body).getOrElse("")
      mail.send(txtEmail,htmlEmail)
    }
  }


}
