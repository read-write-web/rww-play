package controllers

import play.api._
import play.api.mvc._
import play.api.data.Form
import play.api.data._
import play.api.data.Forms._

object Application extends Controller {

  case class CreateUserSoaceForm(subdomain: String,certificate: String,email: String)


  val createUserSpaceForm : Form[CreateUserSoaceForm] = Form(
    mapping(
      "subdomain" -> nonEmptyText(minLength = 3),
      "certificate" -> nonEmptyText,
      "email" -> email
    )(CreateUserSoaceForm.apply)(CreateUserSoaceForm.unapply)
  )

  def index = Action {
    Ok(views.html.index(createUserSpaceForm))
  }



  def createUserSpace = Action { implicit request =>
    createUserSpaceForm.bindFromRequest.fold(
      formWithErrors => {
        Logger.warn("Form errors: " + formWithErrors)
        BadRequest(views.html.index(createUserSpaceForm))
      },
      value => doCreateUserSpace(value)
    )
  }

  def doCreateUserSpace(form: CreateUserSoaceForm): Result = {
    Logger.info("Will try to create new subdomain: " + form)
    // TODO handle userspace creation
    Redirect(s"https://${form.subdomain}.stample.io")
  }



}

