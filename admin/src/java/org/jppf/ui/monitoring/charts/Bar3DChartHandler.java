/*
 * JPPF.
 * Copyright (C) 2005-2017 JPPF Team.
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
package org.jppf.ui.monitoring.charts;

import static org.jppf.utils.ReflectionHelper.*;

import java.awt.*;
import java.lang.reflect.Proxy;
import java.util.Map;

import org.jppf.ui.monitoring.charts.StackedAreaChartHandler.CategoryItemLabelGeneratorInvocationHandler;
import org.jppf.ui.monitoring.charts.config.ChartConfiguration;
import org.jppf.ui.monitoring.data.*;

/**
 * Instances of this class are used to create and update 3D bar charts with an horizontal orientation.
 * @author Laurent Cohen
 */
public class Bar3DChartHandler implements ChartHandler {
  /**
   * The stats formatter that provides the data.
   */
  private StatsHandler statsHandler = null;

  /**
   * Initialize this chart handler with a specified stats formatter.
   * @param statsHandler the stats formatter that provides the data.
   */
  public Bar3DChartHandler(final StatsHandler statsHandler) {
    this.statsHandler = statsHandler;
  }

  /**
   * Create a plot XY chart based on a chart configuration.
   * @param config holds the configuration parameters for the chart created, modified by this method.
   * @return a <code>ChartConfiguration</code> instance.
   * @see org.jppf.ui.monitoring.charts.ChartHandler#createChart(org.jppf.ui.monitoring.charts.config.ChartConfiguration)
   */
  @Override
  public ChartConfiguration createChart(final ChartConfiguration config) {
    final Object ds = createDataset(config);
    if (ds == null) return null;
    //JFreeChart chart = ChartFactory.createBarChart3D(null, null, config.name, ds, PlotOrientation.HORIZONTAL, false, true, false);
    final Object chart = invokeMethod(getClass0("org.jfree.chart.ChartFactory"), null, "createBarChart3D", config.name, null, null, ds,
      getField(getClass0("org.jfree.chart.plot.PlotOrientation"), null, "HORIZONTAL"), false, true, false);
    //CategoryPlot plot = chart.getCategoryPlot();
    final Object plot = invokeMethod(chart.getClass(), chart, "getCategoryPlot");
    //plot.setForegroundAlpha(1.0f);
    invokeMethod(plot.getClass(), plot, "setForegroundAlpha", 1.0f);
    //CategoryAxis axis = plot.getDomainAxis();
    final Object axis = invokeMethod(plot.getClass(), plot, "getDomainAxis");
    //axis.setTickLabelsVisible(false);
    invokeMethod(axis.getClass(), axis, "setTickLabelsVisible", false);
    final Color c1 = new Color(255, 255, 0, 224);
    //Color c2 = new Color(128, 128, 255, 26);
    final Color c2 = new Color(160, 160, 255);
    //plot.setBackgroundPaint(c2);
    invokeMethod(plot.getClass(), plot, "setBackgroundPaint", c2);
    //plot.setBackgroundAlpha(0.1f);
    invokeMethod(plot.getClass(), plot, "setBackgroundAlpha", 0.1f);
    //BarRenderer3D rend = (BarRenderer3D) plot.getRenderer();
    final Object rend = invokeMethod(plot.getClass(), plot, "getRenderer");
    final Color c3 = new Color(255, 255, 192, 255);
    //rend.setWallPaint(c3);
    invokeMethod(rend.getClass(), rend, "setWallPaint", c3);
    //rend.setSeriesPaint(0, c1);
    invokeMethod(rend.getClass(), rend, "setSeriesPaint", new Class[] { Integer.TYPE, Paint.class }, 0, c1);
    //rend.setBaseItemLabelGenerator(new LabelGenerator(config.unit, config.precision));
    final Object labelGenerator = Proxy.newProxyInstance(getCurrentClassLoader(), getClasses("org.jfree.chart.labels.CategoryItemLabelGenerator"),
      new CategoryItemLabelGeneratorInvocationHandler(config.unit, config.precision));
    invokeMethod(rend.getClass(), rend, "setBaseItemLabelGenerator", labelGenerator);

    //ItemLabelPosition labelPos = new ItemLabelPosition(ItemLabelAnchor.CENTER, TextAnchor.BOTTOM_CENTER);
    final Class<?> itemLabelAnchorClass = getClass0("org.jfree.chart.labels.ItemLabelAnchor");
    final Class<?> textAnchorClass = getClass0("org.jfree.ui.TextAnchor");
    final Object labelPos = invokeConstructor(getClass0("org.jfree.chart.labels.ItemLabelPosition"), new Class[] { itemLabelAnchorClass, textAnchorClass }, getField(itemLabelAnchorClass, null, "CENTER"),
      getField(textAnchorClass, null, "BOTTOM_CENTER"));
    //rend.setBasePositiveItemLabelPosition(labelPos);
    final Class<?> labelPositionClass = getClass0("org.jfree.chart.labels.ItemLabelPosition");
    invokeMethod(rend.getClass(), rend, "setBasePositiveItemLabelPosition", new Class[] { labelPositionClass }, labelPos);
    //ItemLabelPosition labelPos2 = new ItemLabelPosition(ItemLabelAnchor.CENTER, TextAnchor.BOTTOM_LEFT);
    final Object labelPos2 = invokeConstructor(getClass0("org.jfree.chart.labels.ItemLabelPosition"), new Class[] { itemLabelAnchorClass, textAnchorClass }, getField(itemLabelAnchorClass, null, "CENTER"),
      getField(textAnchorClass, null, "BOTTOM_LEFT"));
    //rend.setPositiveItemLabelPositionFallback(labelPos2);
    invokeMethod(rend.getClass(), rend, "setPositiveItemLabelPositionFallback", new Class[] { labelPositionClass }, labelPos2);
    //rend.setBaseItemLabelsVisible(true);
    invokeMethod(rend.getClass(), rend, "setBaseItemLabelsVisible", new Class[] { Boolean.class }, true);
    config.chart = chart;
    return config;
  }

  /**
   * Create and populate a dataset with the values of the specified fields.
   * @param config the names of the fields whose values populate the dataset.
   * @return a <code>DefaultCategoryDataset</code> instance.
   */
  private Object createDataset(final ChartConfiguration config) {
    //DefaultCategoryDataset ds = new DefaultCategoryDataset();
    final Object ds = newInstance("org.jfree.data.category.DefaultCategoryDataset");
    config.dataset = ds;
    populateDataset(config);
    return ds;
  }

  /**
   * Populate a dataset based on a chart configuration.
   * @param config the chart configuration containing the dataset to populate.
   * @return a <code>ChartConfiguration</code> instance.
   * @see org.jppf.ui.monitoring.charts.ChartHandler#populateDataset(org.jppf.ui.monitoring.charts.config.ChartConfiguration)
   */
  @Override
  public ChartConfiguration populateDataset(final ChartConfiguration config) {
    //((DefaultCategoryDataset) config.dataset).clear();
    if (config.dataset == null) return config;
    invokeMethod(config.dataset.getClass(), config.dataset, "clear");
    return updateDataset(config);
  }

  /**
   * Update a dataset based on a chart configuration.
   * @param config the chart configuration containing the dataset to update.
   * @return a <code>ChartConfiguration</code> instance.
   * @see org.jppf.ui.monitoring.charts.ChartHandler#updateDataset(org.jppf.ui.monitoring.charts.config.ChartConfiguration)
   */
  @Override
  public ChartConfiguration updateDataset(final ChartConfiguration config) {
    final Object ds = config.dataset;
    if (ds == null) return config;
    final Map<Fields, Double> valueMap = statsHandler.getLatestDoubleValues();
    if (valueMap != null) {
      for (Fields key: config.fields) {
        //ds.setValue(valueMap.get(key), "0", key);
        invokeMethod(ds.getClass(), ds, "setValue", valueMap.get(key), "0", key);
      }
    }
    return config;
  }
}
