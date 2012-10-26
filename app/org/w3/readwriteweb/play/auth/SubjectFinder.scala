package org.w3.readwriteweb.play.auth

import play.api.mvc.RequestHeader
import org.w3.play.auth.{WebIDVerifier, WebIDAuthN}
import concurrent.Future
import org.w3.banana.RDF


trait SubjectFinder {
  def subject: Future[Subject]
}

class WebIDSubjectFinder[Rdf<:RDF](request: RequestHeader)(implicit verifier: WebIDVerifier[Rdf]) extends SubjectFinder {
  def subject = authn(request)

  private def authn = new WebIDAuthN[Rdf]()
}