package controllers

import play.api._
import play.api.mvc._
import play.api.data.Form
import play.api.data._
import play.api.data.Forms._

object Application extends Controller {

  case class CreateUserSpaceForm(subdomain: String,certificate: String,email: String)


  val createUserSpaceForm : Form[CreateUserSpaceForm] = Form(
    mapping(
      "subdomain" -> nonEmptyText(minLength = 3),
      "certificate" -> nonEmptyText,
      "email" -> email
    )(CreateUserSpaceForm.apply)(CreateUserSpaceForm.unapply)
  )

  def index = Action {
    Ok(views.html.index(createUserSpaceForm))
  }


  def createUserSpace = Action { implicit request =>
    createUserSpaceForm.bindFromRequest.fold(
      formWithErrors => BadRequest(views.html.index(formWithErrors)),
      form => {
        Logger.info("Will try to create new subdomain: " + form)
        // TODO handle userspace creation
        val subdomainURL = plantain.hostRootSubdomain(form.subdomain)
        Redirect(subdomainURL.toString)
      }
    )
  }

}

