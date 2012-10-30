package org.www.readwriteweb.play.auth

import play.api.mvc.RequestHeader
import org.www.play.auth.{WebIDVerifier, WebIDAuthN}
import concurrent.Future
import org.w3.banana.RDF


trait SubjectFinder {
  def subject: Future[Subject]
}

class WebIDSubjectFinder[Rdf<:RDF](request: RequestHeader)(implicit verifier: WebIDVerifier[Rdf]) extends SubjectFinder {
  val subject: Future[Subject] = authn(request)

  private def authn = new WebIDAuthN[Rdf]()
}