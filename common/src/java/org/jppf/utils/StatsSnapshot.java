/*
 * JPPF.
 * Copyright (C) 2005-2011 JPPF Team.
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
package org.jppf.utils;

import java.io.Serializable;

/**
 * Convenience class for collecting time statistics.
 */
public class StatsSnapshot implements Serializable
{
  /**
   * Explicit serialVersionUID.
   */
  private static final long serialVersionUID = 1L;
  /**
   * Title for this snapshot, used in the {@link #toString()} method.
   */
  public String title = "";
  /**
   * The total cumulated time.
   */
  private long total = 0L;
  /**
   * The most recent time.
   */
  private long latest = 0L;
  /**
   * The minimum time.
   */
  private long min = Long.MAX_VALUE;
  /**
   * The maximum task execution time.
   */
  private long max = 0L;
  /**
   * The average time.
   */
  private double avg = 0d;

  /**
   * Initialize this time snapshot with a blank title.
   */
  public StatsSnapshot()
  {
  }

  /**
   * Initialize this time snapshot with a specified title.
   * @param title the title for this snapshot.
   */
  public StatsSnapshot(final String title)
  {
    this.title = title;
  }

  /**
   * Called when a new time has been collected.
   * @param time the new time used to compute the new statistics of this time snapshot.
   * @param count the unit count to which the time applies.
   * @param totalCount the total unit count to which the time applies.
   */
  public void newValues(final long time, final long count, final long totalCount)
  {
    total += time;
    if (count > 0)
    {
      latest = time/count;
      if (latest > max) max = latest;
      if (latest < min) min = latest;
      if (totalCount > 0) avg = (double) total / (double) totalCount;
    }
  }

  /**
   * Called when a new time has been collected.
   * @param updateCount the number of units in the update.
   * @param totalUpdates the total number of updates since the start, not including the current update.
   */
  public void newValues(final long updateCount, final long totalUpdates)
  {
    total += updateCount;
    latest = updateCount;
    if (latest > max) max = latest;
    if (latest < min) min = latest;
    avg = (double) total / (double) (totalUpdates + 1L);
  }

  /**
   * Make a copy of this time snapshot object.
   * @return a <code>TimeSnapshot</code> instance.
   */
  public StatsSnapshot makeCopy()
  {
    StatsSnapshot ts = new StatsSnapshot(title);
    ts.setTotal(total);
    ts.setLatest(latest);
    ts.setMin(min);
    ts.setMax(max);
    ts.setAvg(avg);
    return ts;
  }

  /**
   * Get a string representation of this stats object.
   * @return a string display the various stats values.
   * @see java.lang.Object#toString()
   */
  @Override
  public String toString()
  {
    StringBuilder sb = new StringBuilder();
    sb.append(title).append(" total : ").append(total).append('\n');
    sb.append(title).append(" latest : ").append(latest).append('\n');
    sb.append(title).append(" min : ").append(min).append('\n');
    sb.append(title).append(" max : ").append(max).append('\n');
    sb.append(title).append(" avg : ").append(avg).append('\n');
    return sb.toString();
  }

  /**
   * Set the total cumulated time.
   * @param total the total time as a long value.
   */
  public void setTotal(final long total)
  {
    this.total = total;
  }

  /**
   * Get the total cumulated time.
   * @return the total time as a long value.
   */
  public long getTotal()
  {
    return total;
  }

  /**
   * Set the most recent time observed.
   * @param latest the most recent time as a long value.
   */
  public void setLatest(final long latest)
  {
    this.latest = latest;
  }

  /**
   * Get the minimum time observed.
   * @return the minimum time as a long value.
   */
  public long getLatest()
  {
    return latest;
  }

  /**
   * Set the smallest time observed.
   * @param min the minimum time as a long value.
   */
  public void setMin(final long min)
  {
    this.min = min;
  }

  /**
   * Get the smallest time observed.
   * @return the minimum time as a long value.
   */
  public long getMin()
  {
    return min;
  }

  /**
   * Set the peak time.
   * @param max the maximum time as a long value.
   */
  public void setMax(final long max)
  {
    this.max = max;
  }

  /**
   * Get the peak time.
   * @return the maximum time as a long value.
   */
  public long getMax()
  {
    return max;
  }

  /**
   * Set the average time.
   * @param avg the average time as a double value.
   */
  public void setAvg(final double avg)
  {
    this.avg = avg;
  }

  /**
   * Get the average time.
   * @return the average time as a double value.
   */
  public double getAvg()
  {
    return avg;
  }
}