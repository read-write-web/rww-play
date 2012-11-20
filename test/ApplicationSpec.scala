///*
//* Copyright 2012 Henry Story, http://bblfish.net/
//*
//* Licensed under the Apache License, Version 2.0 (the "License");
//* you may not use this file except in compliance with the License.
//* You may obtain a copy of the License at
//*
//*   http://www.apache.org/licenses/LICENSE-2.0
//*
//* Unless required by applicable law or agreed to in writing, software
//* distributed under the License is distributed on an "AS IS" BASIS,
//* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//* See the License for the specific language governing permissions and
//* limitations under the License.
//
//package test
//
//import org.specs2.mutable._
//
//import play.api.test._
//import play.api.test.Helpers._
//
//*
//* Add your spec here.
//* You can mock out a whole application including requests, plugins etc.
//* For more information, consult the wiki.
//
//class ApplicationSpec extends Specification {
//
//  "Application" should {
//
//    "send 404 on a bad request" in {
//      running(FakeApplication()) {
//        routeAndCall(FakeRequest(GET, "/boum")) must beNone
//      }
//    }
//
//    "render the index page" in {
//      running(FakeApplication()) {
//        val home = routeAndCall(FakeRequest(GET, "/")).get
//
//        status(home) must equalTo(OK)
//        contentType(home) must beSome.which(_ == "text/html")
//        contentAsString(home) must contain ("Your new application is ready.")
//      }
//    }
//  }
//}