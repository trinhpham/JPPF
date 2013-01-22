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

import java.awt.event.*;
import java.util.*;

import javax.swing.*;

import org.jppf.management.*;
import org.jppf.ui.monitoring.node.*;
import org.jppf.utils.*;
import org.slf4j.*;

/**
 * This action displays the driver or node environment information in a separate frame.
 */
public class SystemInformationAction extends AbstractTopologyAction
{
  /**
   * Logger for this class.
   */
  private static Logger log = LoggerFactory.getLogger(SystemInformationAction.class);
  /**
   * Determines whether debug log statements are enabled.
   */
  private static boolean debugEnabled = log.isDebugEnabled();

  /**
   * Initialize this action.
   */
  public SystemInformationAction()
  {
    setupIcon("/org/jppf/ui/resources/info.gif");
    setupNameAndTooltip("show.information");
  }

  /**
   * Update this action's enabled state based on a list of selected elements.
   * @param selectedElements a list of objects.
   * @see org.jppf.ui.actions.AbstractUpdatableAction#updateState(java.util.List)
   */
  @Override
  public void updateState(final List<Object> selectedElements)
  {
    this.selectedElements = selectedElements;
    dataArray = new TopologyData[selectedElements.size()];
    int count = 0;
    for (Object o: selectedElements) dataArray[count++] = (TopologyData) o;
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
    String s = null;
    try
    {
      JPPFSystemInformation info = retrieveInfo(dataArray[0]);
      boolean isNode = dataArray[0].getType().equals(TopologyDataType.NODE);
      PropertiesTableFormat format = new HTMLPropertiesTableFormat("information for " + (isNode ? "node " : "driver ") + dataArray[0].getUuid());
      format.start();
      if (info == null)
      {
        format.print("<p><b>No information was found</b>");
      }
      else
      {
        format.formatTable(info.getUuid(), "UUID");
        format.formatTable(info.getSystem(), "System Properties");
        format.formatTable(info.getEnv(), "Environment Variables");
        format.formatTable(info.getRuntime(), "Runtime Information");
        format.formatTable(info.getJppf(), "JPPF configuration");
        format.formatTable(info.getNetwork(), "Network configuration");
        format.formatTable(info.getStorage(), "Storage Information");
      }
      format.end();
      s = format.getText();
    }
    catch(Exception e)
    {
      s = ExceptionUtils.getStackTrace(e).replace("\n", "<br>");
    }
    final JFrame frame = new JFrame("System Information");
    frame.setIconImage(((ImageIcon) getValue(SMALL_ICON)).getImage());
    frame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
    frame.addWindowListener(new WindowAdapter()
    {
      @Override
      public void windowClosing(final WindowEvent e)
      {
        frame.dispose();
      }
    });
    JEditorPane textArea = new JEditorPane("text/html", s);
    AbstractButton btn = (AbstractButton) event.getSource();
    if (btn.isShowing()) location = btn.getLocationOnScreen();
    textArea.setEditable(false);
    textArea.setOpaque(false);
    frame.getContentPane().add(new JScrollPane(textArea));
    frame.setLocationRelativeTo(null);
    frame.setLocation(location);
    frame.setSize(400, 400);
    frame.setVisible(true);
  }

  /**
   * Retrieve the system information for the specified topology object.
   * @param data the topology object for which to get the information.
   * @return a {@link JPPFSystemInformation} or <code>null</code> if the information could not be retrieved.
   */
  private JPPFSystemInformation retrieveInfo(final TopologyData data)
  {
    JPPFSystemInformation info = null;
    try
    {
      if (TopologyDataType.NODE == data.getType())
      {
        TopologyData parent = data.getParent();
        Map<String, Object> result = parent.getNodeForwarder().systemInformation(new NodeSelector.UuidSelector(data.getUuid()));
        Object o = result.get(data.getUuid());
        if (o instanceof JPPFSystemInformation) info = (JPPFSystemInformation) o;
      }
      else info = data.getJmxWrapper().systemInformation();
    }
    catch (Exception e)
    {
      if (debugEnabled) log.debug(e.getMessage(), e);
    }
    return info;
  }
}
