/**
 * Copyright 2015-2017 Linagora, Université Joseph Fourier, Floralis
 *
 * The present code is developed in the scope of the joint LINAGORA -
 * Université Joseph Fourier - Floralis research program and is designated
 * as a "Result" pursuant to the terms and conditions of the LINAGORA
 * - Université Joseph Fourier - Floralis research program. Each copyright
 * holder of Results enumerated here above fully & independently holds complete
 * ownership of the complete Intellectual Property rights applicable to the whole
 * of said Results, and may freely exploit it in any manner which does not infringe
 * the moral rights of the other copyright holders.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.roboconf.karaf.decanter.collector.dm;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.service.event.EventAdmin;
import org.osgi.util.tracker.ServiceTracker;

/**
 * @author Amadou Diarra - UGA
 */
public class Activator implements BundleActivator {

	private BundleDecanterCollector collector;

	@Override
	public void start(final BundleContext bundleContext) throws Exception {

		ServiceTracker tracker = new ServiceTracker(bundleContext, EventAdmin.class.getName(), null);
		EventAdmin eventAdmin = (EventAdmin) tracker.waitForService(10000);
		this.collector = new BundleDecanterCollector(eventAdmin);

	}

	@Override
	public void stop(BundleContext bundleContext) throws Exception {
		this.collector = null;
	}

}
