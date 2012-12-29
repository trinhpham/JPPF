/*
 * JPPF.
 * Copyright (C) 2005-2012 JPPF Team.
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
package org.jppf.ui.monitoring.node.actions;

import java.awt.event.ActionEvent;
import java.util.List;

import javax.swing.tree.DefaultMutableTreeNode;

import org.jppf.management.*;
import org.jppf.ui.monitoring.node.*;
import org.slf4j.*;

/**
 * This action stops a node.
 */
public class ToggleNodeActiveAction extends AbstractTopologyAction
{
  /**
   * Logger for this class.
   */
  private static Logger log = LoggerFactory.getLogger(ToggleNodeActiveAction.class);
  /**
   * Determines whether debug log statements are enabled.
   */
  private static boolean debugEnabled = log.isDebugEnabled();
  /**
   * The tree table panel to which this action applies.
   */
  private final NodeDataPanel panel;

  /**
   * Initialize this action.
   * @param panel the tree table panel to which this action applies.
   */
  public ToggleNodeActiveAction(final NodeDataPanel panel)
  {
    this.panel = panel;
    setupIcon("/org/jppf/ui/resources/toggle_active.gif");
    setupNameAndTooltip("toggle.active");
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
    setEnabled(dataArray.length > 0);
  }

  /**
   * Perform the action.
   * @param event not used.
   * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
   */
  @Override
  public void actionPerformed(final ActionEvent event)
  {
    Runnable r = new Runnable() {
      @Override
      public void run() {
        for (TopologyData data: dataArray) {
          try {
            DefaultMutableTreeNode driverNode = panel.getManager().findDriverForNode(data.getUuid());
            if (driverNode == null) continue;
            TopologyData driverData = (TopologyData) driverNode.getUserObject();
            JPPFManagementInfo info = data.getNodeInformation();
            boolean b = info.isActive();
            ((JMXDriverConnectionWrapper) driverData.getJmxWrapper()).activateNode(info.getUuid(), !b);
          } catch(Exception e) {
            log.error(e.getMessage(), e);
          }
        }
      }
    };
    new Thread(r).start();
  }
}