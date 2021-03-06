/*
 * JPPF.
 * Copyright (C) 2005-2015 JPPF Team.
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

using System;
using System.Threading;
using org.jppf.dotnet;

namespace org.jppf.dotnet.demo {
  /// <summary>An example of a task which can be added to a .Net JPPFJob
  /// <para>It has a field <</para></summary>
  [Serializable()]
  public class MyDotnetTask : BaseDotnetTask {
    private int myField = 0;
    private int waitTime;

    /// <summary>Initialize this task with the specified wait time in millis</summary>
    /// <param name="theWaitTime">the time during which this task will wait before completing</param>
    public MyDotnetTask(int theWaitTime) {
      waitTime = theWaitTime;
      myField = 1;
    }

    /// <summary>Execute this task</summary>
    public override void Execute() {
      //throw new Exception("deliberate exception");
      Console.WriteLine("[.Net] waiting " + waitTime + " ms");
      Thread.Sleep(waitTime);
      myField = 2;
      string s = "[.Net] Hello I am a real .NET task !!! (myField = " + myField + ")";
      Console.WriteLine(s);
      Result = s;
    }

    /// <summary>Print a message when the cancellation of this task is requested</summary>
    public override void OnCancel() {
      Console.WriteLine("[.Net] this task has been cancelled");
    }

    /// <summary>Print a message when this task times out</summary>
    public override void OnTimeout() {
      string s = "[.Net] this task has timed out";
      Console.WriteLine(s);
      Result = s;
    }

    public int GetMyField() {
      return myField;
    }
  }
}
