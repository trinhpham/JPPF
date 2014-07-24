/*
 * JPPF.
 * Copyright (C) 2005-2014 JPPF Team.
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

package org.jppf.ui.monitoring.job.actions;

import java.util.*;

import org.jppf.ui.actions.AbstractUpdatableAction;
import org.jppf.ui.monitoring.job.*;

/**
 * Common super class for job actions.
 * @author Laurent Cohen
 */
public abstract class AbstractJobAction extends AbstractUpdatableAction
{
  /**
   * Constant for an empty <code>JobData</code> array.
   */
  private static final JobData[] EMPTY_JOB_DATA_ARRAY = new JobData[0];
  /**
   * The object representing the JPPF jobs in the tree table.
   */
  protected JobData[] jobDataArray = EMPTY_JOB_DATA_ARRAY;
  /**
   * The object representing the JPPF sub-jobs in the tree table.
   */
  protected JobData[] subjobDataArray = EMPTY_JOB_DATA_ARRAY;

  /**
   * Initialize this action.
   */
  public AbstractJobAction()
  {
    BASE = "org.jppf.ui.i18n.JobDataPage";
  }

  /**
   * Update this action's enabled state based on a list of selected elements.
   * @param selectedElements - a list of objects.
   * @see org.jppf.ui.actions.AbstractUpdatableAction#updateState(java.util.List)
   */
  @Override
  public void updateState(final List<Object> selectedElements)
  {
    super.updateState(selectedElements);
    List<JobData> jobList = new ArrayList<>();
    List<JobData> subjobList = new ArrayList<>();
    for (Object o: selectedElements)
    {
      JobData data = (JobData) o;
      if (JobDataType.JOB.equals(data.getType())) jobList.add(data);
      else if (JobDataType.SUB_JOB.equals(data.getType())) subjobList.add(data);
    }
    jobDataArray = jobList.toArray(new JobData[jobList.size()]);
    subjobDataArray = subjobList.toArray(new JobData[subjobList.size()]);
  }
}
