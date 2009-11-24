/*
 * Java Parallel Processing Framework.
 *  Copyright (C) 2005-2009 JPPF Team. 
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

package org.jppf.server.scheduler.bundle.proportional;

import java.util.*;

import org.apache.commons.logging.*;
import org.jppf.server.*;
import org.jppf.server.scheduler.bundle.*;

/**
 * This bundler implementation computes bundle sizes propertional to the mean execution
 * time for each node to the power of n, where n is an integer value specified in the configuration file as "proportionality factor".<br>
 * The scope of this bundler is all nodes, which means that it computes the size for all nodes,
 * unless an override is specified by the nodes.<br>
 * The mean execution time is computed as a moving average over a number of tasks, specified in the bundling
 * algorithm profile configuration as &quot;performanceCacheSize&quot;<br>
 * This algorithm is well suited for relatively small networks (a few dozen nodes at most). It generates an overhead
 * everytime the performance data for a node is updated. In the case of a small network, this overhead is not
 * large enough to impact the overall performance significantly.
 * @author Laurent Cohen
 */
public abstract class AbstractProportionalBundler extends AbstractBundler
{
	/**
	 * Logger for this class.
	 */
	private static Log log = LogFactory.getLog(AbstractProportionalBundler.class);
	/**
	 * Determines whether debugging level is set for logging.
	 */
	private static boolean debugEnabled = log.isDebugEnabled();
	/**
	 * Mapping of individual bundler to corresponding performance data.
	 */
	private static Set<AbstractProportionalBundler> bundlers = new HashSet<AbstractProportionalBundler>();
	/**
	 * Parameters of the auto-tuning algorithm, grouped as a performance analysis profile.
	 */
	protected ProportionalTuneProfile profile;
	/**
	 * Bounded memory of the past performance updates.
	 */
	protected BundleDataHolder dataHolder = null;
	/**
	 * The current bundle size.
	 */
	protected int bundleSize = 1;

	/**
	 * Creates a new instance with the initial size of bundle as the start size.
	 * @param profile the parameters of the auto-tuning algorithm,
	 * @param override true if the settings were overriden by the node, false otherwise.
	 * grouped as a performance analysis profile.
	 */
	public AbstractProportionalBundler(AutoTuneProfile profile, boolean override)
	{
		log.info("Bundler#" + bundlerNumber + ": Using Auto-Tuned bundle size");
		this.override = override;
		int bundleSize = JPPFStatsUpdater.getStaticBundleSize();
		if (bundleSize < 1) bundleSize = 1;
		log.info("Bundler#" + bundlerNumber + ": The initial size is " + bundleSize);
		this.profile = (ProportionalTuneProfile) profile;
		dataHolder = new BundleDataHolder(this.profile.getPerformanceCacheSize());
	}

	/**
	 * Get the current size of bundle.
	 * @return  the bundle size as an int value.
	 * @see org.jppf.server.scheduler.bundle.Bundler#getBundleSize()
	 */
	public int getBundleSize()
	{
		return bundleSize;
	}

	/**
	 * Set the current size of bundle.
	 * @param size the bundle size as an int value.
	 * @see org.jppf.server.scheduler.bundle.Bundler#getBundleSize()
	 */
	public void setBundleSize(int size)
	{
		bundleSize = size;
	}

	/**
	 * This method delegates the bundle size calculation to the singleton instance of <code>SimpleBundler</code>.
	 * @param bundleSize the number of tasks executed.
	 * @param totalTime the time in milliseconds it took to execute the tasks.
	 * @see org.jppf.server.scheduler.bundle.AbstractBundler#feedback(int, double)
	 */
	public void feedback(int bundleSize, double totalTime)
	{
		BundlePerformanceSample sample = new BundlePerformanceSample(totalTime, bundleSize);
		synchronized(bundlers)
		{
			dataHolder.addSample(sample);
			computeBundleSizes();
		}
	}

	/**
	 * Perform context-independant initializations.
	 * @see org.jppf.server.scheduler.bundle.AbstractBundler#setup()
	 */
	public void setup()
	{
		synchronized(bundlers)
		{
			bundlers.add(this);
		}
	}
	
	/**
	 * Release the resources used by this bundler.
	 * @see org.jppf.server.scheduler.bundle.AbstractBundler#dispose()
	 */
	public void dispose()
	{
		synchronized(bundlers)
		{
			bundlers.remove(this);
		}
	}

	/**
	 * Get the bounded memory of the past performance updates.
	 * @return a BundleDataHolder instance.
	 */
	public BundleDataHolder getDataHolder()
	{
		return dataHolder;
	}

	/**
	 * Update the bundler sizes.
	 */
	private void computeBundleSizes()
	{
		double maxMean = Double.NEGATIVE_INFINITY;
		double minMean = Double.POSITIVE_INFINITY;
		AbstractProportionalBundler minBundler = null;
		for (AbstractProportionalBundler b: bundlers)
		{
			BundleDataHolder h = b.getDataHolder();
			double m = h.getMean();
			if (m > maxMean) maxMean = m;
			if (m < minMean)
			{
				minMean = m;
				minBundler = b;
			}
		}
		BundleDataHolder minHolder = minBundler.getDataHolder();
		double diffSum = 0d;
		for (AbstractProportionalBundler b: bundlers)
		{
			double diff = maxMean / b.getDataHolder().getMean();
			diffSum += b.computeDiff(diff);
		}
		int max = maxSize();
		int sum = 0;
		for (AbstractProportionalBundler b: bundlers)
		{
			BundleDataHolder h = b.getDataHolder();
			double diff = maxMean / h.getMean();
			double d = b.computeDiff(diff) / diffSum;
			int size = Math.max(1, (int) (max * d));
			b.setBundleSize(size);
			sum += size;
		}
		if (sum < max)
		{
			int size = minBundler.getBundleSize();
			minBundler.setBundleSize(size + (max - sum));
		}
		if (debugEnabled)
		{
			StringBuilder sb = new StringBuilder();
			sb.append("bundler info: [");
			int count = 0;
			for (AbstractProportionalBundler b: bundlers)
			{
				if (count > 0) sb.append(", ");
				count++;
				sb.append("#").append(b.getBundlerNumber()).append(":").append(b.getBundleSize());
			}
			sb.append("]");
			log.debug(sb.toString());
		}
	}

	/**
	 * 
	 * @param x .
	 * @return .
	 */
	public double computeDiff(double x)
	{
		double r = 1d;
		for (int i=0; i<profile.getProportionalityFactor(); i++) r *= x;
		return r;
	}
}
