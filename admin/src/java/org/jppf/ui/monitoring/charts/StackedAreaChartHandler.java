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

import java.lang.reflect.*;
import java.text.NumberFormat;
import java.util.Map;

import org.jppf.ui.monitoring.charts.config.ChartConfiguration;
import org.jppf.ui.monitoring.data.*;

/**
 * Instances of this class are used to create and update line charts with an horizontal orientation.
 * @author Laurent Cohen
 * @since 5.0
 */
public class StackedAreaChartHandler implements ChartHandler {
  /**
   * The stats formatter that provides the data.
   */
  private StatsHandler statsHandler = null;

  /**
   * Initialize this chart handler with a specified stats formatter.
   * @param statsHandler the stats formatter that provides the data.
   */
  public StackedAreaChartHandler(final StatsHandler statsHandler) {
    this.statsHandler = statsHandler;
  }

  /**
   * Create a plot XY chart based on a chart configuration.
   * @param config holds the configuration parameters for the chart created, modified by this method.
   * @return a <code>ChartConfiguration</code> instance.
   */
  @Override
  public ChartConfiguration createChart(final ChartConfiguration config) {
    final Object ds = createDataset(config);
    String s = config.name;
    if (config.unit != null) s += " (" + config.unit+ ')';
    //JFreeChart chart = ChartFactory.createStackedAreaChart(s, null, null, ds, PlotOrientation.VERTICAL, true, true, false);
    final Object chart = invokeMethod(getClass0("org.jfree.chart.ChartFactory"), null, "createStackedAreaChart",
      s, null, null, ds, getField("org.jfree.chart.plot.PlotOrientation", "VERTICAL"), true, true, false);
    final Object plot = invokeMethod(chart.getClass(), chart, "getCategoryPlot");
    //CategoryAxis axis = plot.getDomainAxis();
    final Object axis = invokeMethod(plot.getClass(), plot, "getDomainAxis");
    //axis.setTickLabelsVisible(false);
    invokeMethod(axis.getClass(), axis, "setTickLabelsVisible", false);
    config.chart = chart;
    return config;
  }

  /**
   * Create and populate a dataset with the values of the specified fields.
   * @param config the names of the fields whose values populate the dataset.
   * @return a <code>DefaultCategoryDataset</code> instance.
   */
  private Object createDataset(final ChartConfiguration config) {
    final Object ds = newDataset(config);
    populateDataset(config);
    return ds;
  }

  /**
   * Create a new empty dataset for this chart handler.
   * @param config the configuration holding the new dataset.
   * @return the dataset.
   */
  private static Object newDataset(final ChartConfiguration config) {
    //DefaultCategoryDataset ds = new DefaultCategoryDataset();
    final Object ds = newInstance("org.jfree.data.category.DefaultCategoryDataset");
    config.dataset = ds;
    return ds;
  }

  /**
   * Populate a dataset based on a chart configuration.
   * @param config the chart configuration containing the dataset to populate.
   * @return a <code>ChartConfiguration</code> instance.
   */
  @Override
  public ChartConfiguration populateDataset(final ChartConfiguration config) {
    if (config.dataset == null) newDataset(config);
    final Object ds = config.dataset;
    //ds.clear();
    invokeMethod(ds.getClass(), ds, "clear");
    final int start = Math.max(0, statsHandler.getTickCount() - statsHandler.getStatsCount());
    for (int j=0; j<statsHandler.getStatsCount(); j++) {
      final Map<Fields, Double> valueMap = statsHandler.getDoubleValues(j);
      for (final Fields key: config.fields) {
        //ds.setValue(valueMap.get(key), key, Integer.valueOf(j + start));
        invokeMethod(ds.getClass(), ds, "setValue", valueMap.get(key), key, Integer.valueOf(j + start));
      }
    }
    return config;
  }

  /**
   * Update a dataset based on a chart configuration.
   * @param config the chart configuration containing the dataset to update.
   * @return a <code>ChartConfiguration</code> instance.
   */
  @Override
  public ChartConfiguration updateDataset(final ChartConfiguration config) {
    final Object ds = config.dataset;
    final Map<Fields, Double> valueMap = statsHandler.getLatestDoubleValues();
    if (valueMap != null) {
      for (final Fields key: config.fields) {
        //ds.setValue(valueMap.get(key), key, Integer.valueOf(statsHandler.getTickCount()));
        invokeMethod(ds.getClass(), ds, "setValue", valueMap.get(key), key, Integer.valueOf(statsHandler.getTickCount()));
      }
    }
    //if (ds.getColumnCount() > statsHandler.getRolloverPosition())
    if ((Integer) invokeMethod(ds.getClass(), ds, "getColumnCount") > statsHandler.getRolloverPosition()) {
      //ds.removeRow(0);
      invokeMethod(ds.getClass(), ds, "removeColumn", new Class[] {int.class}, 0);
    }
    return config;
  }

  /**
   * Invocation handler for a dynamic proxy to a <code>org.jppf.ui.monitoring.charts.PlotXYChartHandler.LegendLabelGenerator</code> implementation.
   */
  public static class CategoryItemLabelGeneratorInvocationHandler implements InvocationHandler {
    /**
     * Number format that formats double values in <i>##...##0.00</i> format.
     */
    private NumberFormat nf = NumberFormat.getInstance();
    /**
     * Name of the unit to display in the labels.
     */
    private String unit = null;
    /**
     * The default label generator.
     */
    private Object stdGenerator = newInstance("org.jfree.chart.labels.StandardCategoryItemLabelGenerator");

    /**
     * Initialize this label generator by configuring the NumberFormat instance it uses.
     * @param unit the unit to display for the values.
     * @param precision the number of fraction digits to display for the values.
     */
    public CategoryItemLabelGeneratorInvocationHandler(final String unit, final int precision) {
      this.unit = unit;
      nf.setGroupingUsed(true);
      nf.setMinimumIntegerDigits(1);
      nf.setMinimumFractionDigits(precision);
      nf.setMaximumFractionDigits(precision);
    }

    /**
     * Invoke a specified method on the specified proxy.
     * @param proxy the dynamic proxy to invoke the method on.
     * @param method the method to invoke.
     * @param args the method parameters values.
     * @return the result of the method invocation.
     * @throws Throwable if any error occurs.
     * @see java.lang.reflect.InvocationHandler#invoke(java.lang.Object, java.lang.reflect.Method, java.lang.Object[])
     */
    @Override
    public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
      if ("".equals(method.getName())) {
        //double val = dataset.getValue(row, col).doubleValue();
        final double val = (Double) invokeMethod(args[0].getClass(), args[0], "getValue", args[1], args[2]);
        //Object key = dataset.getColumnKey(col);
        final Object key = invokeMethod(args[0].getClass(), args[0], "getColumnKey", args[2]);
        final StringBuilder sb = new StringBuilder(String.valueOf(key)).append(" : ").append(nf.format(val));
        if (unit != null) sb.append(' ').append(unit);
        return sb.toString();
      } else {
        return invokeMethod(stdGenerator.getClass(), stdGenerator, method.getName(), args);
      }
    }
  }
}
