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

import java.util.HashMap;

import org.osgi.framework.BundleEvent;
import org.osgi.framework.SynchronousBundleListener;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;

/**
 * @author Amadou Diarra - UGA
 */
public class BundleDecanterCollector implements SynchronousBundleListener {

	private final EventAdmin eventAdmin;

	/**
	 * Constructs a bundle to collect informations.
	 * */
	public BundleDecanterCollector(EventAdmin eventAdmin) {
		this.eventAdmin = eventAdmin;
	}

	@Override
	public void bundleChanged(BundleEvent bunbleEvent) {
		HashMap<String, Object> data = new HashMap<>();
		data.put("type", "bundle");
		data.put("change", bunbleEvent.getType());
		data.put("id", bunbleEvent.getBundle().getBundleId());
		data.put("location", bunbleEvent.getBundle().getLocation());
		data.put("symbolicName", bunbleEvent.getBundle().getSymbolicName());
		Event event = new Event("decanter/collect/bundle", data);
		this.eventAdmin.postEvent(event);
	}

}
