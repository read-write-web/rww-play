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

package jena

import test.WebACLTestSuite
import org.w3.banana.jena.{JenaDiesel, Jena}
import org.www.play.rdf.jena.JenaConfig
import org.www.readwriteweb.play.IterateeLDCache

object JenaCache extends IterateeLDCache[Jena](JenaConfig.jenaAsync.graphIterateeSelector)(JenaDiesel,JenaConfig.executionContext)

class JenaWebACLTestSuite extends WebACLTestSuite[Jena](JenaCache)


