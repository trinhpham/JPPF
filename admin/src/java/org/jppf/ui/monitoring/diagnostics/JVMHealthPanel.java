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

package org.jppf.ui.monitoring.diagnostics;

import java.util.*;
import java.util.prefs.Preferences;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;

import org.jppf.client.monitoring.topology.*;
import org.jppf.ui.actions.*;
import org.jppf.ui.monitoring.data.StatsHandler;
import org.jppf.ui.monitoring.node.actions.*;
import org.jppf.ui.options.factory.OptionsHandler;
import org.jppf.ui.treetable.*;
import org.slf4j.*;

/**
 * Panel displaying the tree of all driver connections and attached nodes.
 * @author Laurent Cohen
 */
public class JVMHealthPanel extends AbstractTreeTableOption implements TopologyListener {
  /**
   * Logger for this class.
   */
  static Logger log = LoggerFactory.getLogger(JVMHealthPanel.class);
  /**
   * Determines whether debug log statements are enabled.
   */
  static boolean debugEnabled = log.isDebugEnabled();
  /**
   * Manages the topology updates.
   */
  private final TopologyManager manager;

  /**
   * Initialize this panel with the specified information.
   */
  public JVMHealthPanel() {
    BASE = "org.jppf.ui.i18n.JVMHealthPage";
    if (debugEnabled) log.debug("initializing JVMHealthPanel");
    manager = StatsHandler.getInstance().getTopologyManager();
    createTreeTableModel();
  }

  /**
   * Create and initialize the tree table model holding the drivers and nodes data.
   */
  private void createTreeTableModel() {
    treeTableRoot = new DefaultMutableTreeNode(localize("tree.root.name"));
    model = new JVMHealthTreeTableModel(treeTableRoot);
  }

  /**
   * Initialize the refresh of tree structure.
   */
  public void init() {
    populate();
    manager.addTopologyListener(this);
  }

  /**
   * Create and initialize the tree table model holding the drivers and nodes data.
   */
  public void populate() {
    for (TopologyDriver driver: manager.getDrivers()) {
      driverAdded(new TopologyEvent(manager, driver, null));
      for (AbstractTopologyComponent child: driver.getChildren()) {
        if (child.isPeer()) continue;
        TopologyNode node = (TopologyNode) child;
        log.debug("adding node " + node+ " to driver " + driver);
        nodeAdded(new TopologyEvent(manager, driver, node));
      }
    }
    treeTable.expandAll();
    repaintTreeTable();
  }

  @Override
  public void createUI() {
    treeTable = new JPPFTreeTable(model);
    treeTable.getTree().setLargeModel(true);
    treeTable.getTree().setRootVisible(false);
    treeTable.getTree().setShowsRootHandles(true);
    treeTable.expandAll();
    treeTable.getColumnModel().getColumn(0).setPreferredWidth(300);
    treeTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
    treeTable.doLayout();
    treeTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    treeTable.getTree().setCellRenderer(new HealthTreeCellRenderer());
    treeTable.setDefaultRenderer(Object.class, new HealthTableCellRenderer(this));
    JScrollPane sp = new JScrollPane(treeTable);
    setUIComponent(sp);
    treeTable.expandAll();
  }

  /**
   * Repaint the tree table area.
   */
  void repaintTreeTable() {
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        if (treeTable != null) {
          treeTable.invalidate();
          treeTable.repaint();
        }
      }
    });
  }

  /**
   * Initialize all actions used in the panel.
   */
  public void setupActions() {
    actionHandler = new JTreeTableActionHandler(treeTable);
    actionHandler.putAction("health.gc", new GCAction());
    actionHandler.putAction("health.heap.dump", new HeapDumpAction());
    actionHandler.putAction("health.thread.dump", new ThreadDumpAction());
    actionHandler.putAction("health.select.drivers", new SelectDriversAction(this));
    actionHandler.putAction("health.select.nodes", new SelectNodesAction(this));
    actionHandler.putAction("health.select.all", new SelectAllAction(this));
    actionHandler.putAction("health.update.thresholds", new ThresholdSettingsAction(this));
    actionHandler.updateActions();
    treeTable.addMouseListener(new JVMHealthTreeTableMouseListener(actionHandler));
    //
    Runnable r = new ActionsInitializer(this, "/health.toolbar");
    new Thread(r).start();
  }

  @Override
  public synchronized void driverAdded(final TopologyEvent event) {
    if (debugEnabled) log.debug("adding driver " + event.getDriver());
    TopologyDriver driverData = event.getDriver();
    DefaultMutableTreeNode driver = findDriver(driverData.getUuid());
    if (driver != null) return;
    driver = new DefaultMutableTreeNode(driverData);
    int n = treeTableRoot.getChildCount();
    model.insertNodeInto(driver, treeTableRoot, n);
    if (n == 0) treeTable.expand(treeTableRoot);
  }

  @Override
  public synchronized void driverRemoved(final TopologyEvent event) {
    if (debugEnabled) log.debug("removing driver " + event.getDriver());
    DefaultMutableTreeNode driver = findDriver(event.getDriver().getUuid());
    if (driver != null) model.removeNodeFromParent(driver);
  }

  @Override
  public synchronized void driverUpdated(final TopologyEvent event) {
    DefaultMutableTreeNode driver = findDriver(event.getDriver().getUuid());
    if (driver != null) model.changeNode(driver);
  }

  @Override
  public synchronized void nodeAdded(final TopologyEvent event) {
    if (debugEnabled) log.debug("adding node " + event.getNodeOrPeer() + " to driver " + event.getDriver());
    if (event.getNodeOrPeer().isPeer()) return;
    DefaultMutableTreeNode driver = findDriver(event.getDriver().getUuid());
    if (driver == null) return;
    DefaultMutableTreeNode node = findNode(driver, event.getNodeOrPeer().getUuid());
    if (node != null) return;
    node = new DefaultMutableTreeNode(event.getNodeOrPeer());
    int n = driver.getChildCount();
    model.insertNodeInto(node, driver, n);
    if (n == 0) treeTable.expand(driver);
  }

  @Override
  public synchronized void nodeRemoved(final TopologyEvent event) {
    if (debugEnabled) log.debug("removing node " + event.getNodeOrPeer() + " from driver " + event.getDriver());
    if (event.getNodeOrPeer().isPeer()) return;
    DefaultMutableTreeNode driver = findDriver(event.getDriver().getUuid());
    if (driver == null) return;
    DefaultMutableTreeNode node = findNode(driver, event.getNodeOrPeer().getUuid());
    if (node != null) {
      model.removeNodeFromParent(node);
      repaintTreeTable();
    }
  }

  @Override
  public synchronized void nodeUpdated(final TopologyEvent event) {
    DefaultMutableTreeNode driver = findDriver(event.getDriver().getUuid());
    if (driver != null) {
      DefaultMutableTreeNode node = findNode(driver, event.getNodeOrPeer().getUuid());
      if (node != null) model.changeNode(node);
    }
  }

  /**
   * Find the driver tree node with the specified driver name.
   * @param driverUuid name of the driver to find.
   * @return a <code>DefaultMutableTreeNode</code> or null if the driver could not be found.
   */
  DefaultMutableTreeNode findDriver(final String driverUuid) {
    for (int i=0; i<treeTableRoot.getChildCount(); i++) {
      DefaultMutableTreeNode driverNode = (DefaultMutableTreeNode) treeTableRoot.getChildAt(i);
      TopologyDriver data = (TopologyDriver) driverNode.getUserObject();
      if (data.getUuid().equals(driverUuid)) return driverNode;
    }
    return null;
  }

  /**
   * Find the node tree node with the specified driver name and node information.
   * @param driver name the parent of the node to find.
   * @param nodeUuid the name of the node to find.
   * @return a <code>DefaultMutableTreeNode</code> or null if the driver could not be found.
   */
  DefaultMutableTreeNode findNode(final DefaultMutableTreeNode driver, final String nodeUuid) {
    for (int i=0; i<driver.getChildCount(); i++) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode) driver.getChildAt(i);
      TopologyNode nodeData = (TopologyNode) node.getUserObject();
      if (nodeUuid.equals(nodeData.getUuid())) return node;
    }
    return null;
  }

  /**
   * Save the threshold values to the preferences store.
   */
  public void saveThresholds() {
    Preferences pref = OptionsHandler.getPreferences().node("thresholds");
    for (Map.Entry<Thresholds.Name, Double> entry: getThresholds().getValues().entrySet()) pref.putDouble(entry.getKey().toString().toLowerCase(), entry.getValue());
  }

  /**
   * Load the threshold values from the preferences store.
   */
  public void loadThresholds() {
    Preferences pref = OptionsHandler.getPreferences().node("thresholds");
    Map<Thresholds.Name, Double> values = getThresholds().getValues();
    List<Thresholds.Name> list = new ArrayList<>(values.keySet());
    for (Thresholds.Name name: list) {
      Double value = pref.getDouble(name.toString().toLowerCase(), -1d);
      if (value <= 0d) continue;
      values.put(name, value);
    }
  }

  /**
   * Get the threshold values.
   * @return a {@link Thresholds} object.
   */
  public Thresholds getThresholds() {
    return StatsHandler.getInstance().getClientHandler().getThresholds();
  }
}
