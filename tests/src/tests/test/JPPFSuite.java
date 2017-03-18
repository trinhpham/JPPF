/*
 * JPPF.
 * Copyright (C) 2005-2016 JPPF Team.
 * http://www.jppf.org
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

package test;

import org.junit.runner.RunWith;

import test.org.jppf.client.TestConnectionPool;
import test.org.jppf.test.runner.RepeatingSuite;


/**
 * A suite of JUnit tests.
 * @author Laurent Cohen
 */
@RunWith(RepeatingSuite.class)
@RepeatingSuite.RepeatingSuiteClasses(repeat=1000, shuffleClasses=false, shuffleMethods=true, classes = { TestConnectionPool.class })
public class JPPFSuite {
}
