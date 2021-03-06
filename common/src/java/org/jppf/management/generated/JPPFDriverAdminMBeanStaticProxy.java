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

package org.jppf.management.generated;

import java.util.Collection;
import java.util.Map;
import org.jppf.load.balancer.LoadBalancingInformation;
import org.jppf.management.AbstractMBeanStaticProxy;
import org.jppf.management.JMXConnectionWrapper;
import org.jppf.management.JPPFDriverAdminMBean;
import org.jppf.management.JPPFSystemInformation;
import org.jppf.management.NodeSelector;
import org.jppf.utils.stats.JPPFStatistics;

/**
 * Generated static proxy for the {@link org.jppf.management.JPPFDriverAdminMBean} MBean interface.
 * @author /common/src/java/org/jppf/utils/generator/MBeanStaticProxyGenerator.java
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class JPPFDriverAdminMBeanStaticProxy extends AbstractMBeanStaticProxy implements JPPFDriverAdminMBean {
  /**
   * Initialize this MBean static proxy.
   * @param connection the JMX connection used to invoke remote MBean methods.
   */
  public JPPFDriverAdminMBeanStaticProxy(final JMXConnectionWrapper connection) {
    super(connection, "org.jppf:name=admin,type=driver");
  }

  /**
   * Get the JMX object name for this MBean static proxy.
   * @return the object name as a string.
   */
  public static final String getMBeanName() {
    return "org.jppf:name=admin,type=driver";
  }

  @Override
  public boolean getBroadcasting() {
    return (boolean) getAttribute("Broadcasting");
  }

  @Override
  public Integer nbIdleNodes() {
    return (Integer) invoke("nbIdleNodes", (Object[]) null, (String[]) null);
  }

  @Override
  public Integer nbIdleNodes(final NodeSelector param0) {
    return (Integer) invoke("nbIdleNodes", new Object[] { param0 }, new String[] { "org.jppf.management.NodeSelector" });
  }

  @Override
  public Integer nbIdleNodes(final NodeSelector param0, final boolean param1) {
    return (Integer) invoke("nbIdleNodes", new Object[] { param0, param1 }, new String[] { "org.jppf.management.NodeSelector", "boolean" });
  }

  @Override
  public Integer nbNodes() {
    return (Integer) invoke("nbNodes", (Object[]) null, (String[]) null);
  }

  @Override
  public Integer nbNodes(final NodeSelector param0) {
    return (Integer) invoke("nbNodes", new Object[] { param0 }, new String[] { "org.jppf.management.NodeSelector" });
  }

  @Override
  public String changeLoadBalancerSettings(final String param0, final Map param1) {
    return (String) invoke("changeLoadBalancerSettings", new Object[] { param0, param1 }, new String[] { "java.lang.String", "java.util.Map" });
  }

  @Override
  public String restartShutdown(final Long param0, final Long param1) {
    return (String) invoke("restartShutdown", new Object[] { param0, param1 }, new String[] { "java.lang.Long", "java.lang.Long" });
  }

  @Override
  public Collection idleNodesInformation() {
    return (Collection) invoke("idleNodesInformation", (Object[]) null, (String[]) null);
  }

  @Override
  public Collection idleNodesInformation(final NodeSelector param0) {
    return (Collection) invoke("idleNodesInformation", new Object[] { param0 }, new String[] { "org.jppf.management.NodeSelector" });
  }

  @Override
  public Collection nodesInformation() {
    return (Collection) invoke("nodesInformation", (Object[]) null, (String[]) null);
  }

  @Override
  public Collection nodesInformation(final NodeSelector param0) {
    return (Collection) invoke("nodesInformation", new Object[] { param0 }, new String[] { "org.jppf.management.NodeSelector" });
  }

  @Override
  public Collection nodesInformation(final NodeSelector param0, final boolean param1) {
    return (Collection) invoke("nodesInformation", new Object[] { param0, param1 }, new String[] { "org.jppf.management.NodeSelector", "boolean" });
  }

  @Override
  public Map getActiveState(final NodeSelector param0) {
    return (Map) invoke("getActiveState", new Object[] { param0 }, new String[] { "org.jppf.management.NodeSelector" });
  }

  @Override
  public LoadBalancingInformation loadBalancerInformation() {
    return (LoadBalancingInformation) invoke("loadBalancerInformation", (Object[]) null, (String[]) null);
  }

  @Override
  public JPPFSystemInformation systemInformation() {
    return (JPPFSystemInformation) invoke("systemInformation", (Object[]) null, (String[]) null);
  }

  @Override
  public JPPFStatistics statistics() {
    return (JPPFStatistics) invoke("statistics", (Object[]) null, (String[]) null);
  }

  @Override
  public void resetStatistics() {
    invoke("resetStatistics", (Object[]) null, (String[]) null);
  }

  @Override
  public void setActiveState(final NodeSelector param0, final boolean param1) {
    invoke("setActiveState", new Object[] { param0, param1 }, new String[] { "org.jppf.management.NodeSelector", "boolean" });
  }

  @Override
  public void setBroadcasting(final boolean param0) {
    setAttribute("Broadcasting", param0);
  }

  @Override
  public void toggleActiveState(final NodeSelector param0) {
    invoke("toggleActiveState", new Object[] { param0 }, new String[] { "org.jppf.management.NodeSelector" });
  }
}
