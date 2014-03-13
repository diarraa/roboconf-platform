/**
 * Copyright 2013-2014 Linagora, Université Joseph Fourier
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

package net.roboconf.plugin.logger;

import java.io.File;
import java.util.logging.Logger;

import net.roboconf.core.model.runtime.Instance;
import net.roboconf.plugin.api.ExecutionLevel;
import net.roboconf.plugin.api.PluginInterface;

/**
 * @author Vincent Zurczak - Linagora
 */
public class PluginLogger implements PluginInterface {

	private final Logger logger = Logger.getLogger( getClass().getName());



	@Override
	public void setExecutionLevel( ExecutionLevel executionLevel ) {
		this.logger.info( "The execution level is: " + executionLevel );
	}


	@Override
	public void setDumpDirectory( File dumpDirectory ) {
		this.logger.info( "The dump directory is: " + dumpDirectory );
	}


	@Override
	public void deploy( Instance instance ) throws Exception {
		this.logger.fine( "Deploying instance " + instance.getName());
	}


	@Override
	public void start( Instance instance ) throws Exception {
		this.logger.fine( "Starting instance " + instance.getName());
	}


	@Override
	public void update( Instance instance ) throws Exception {
		this.logger.fine( "Updating instance " + instance.getName());
	}


	@Override
	public void stop( Instance instance ) throws Exception {
		this.logger.fine( "Stopping instance " + instance.getName());
	}


	@Override
	public void undeploy( Instance instance ) throws Exception {
		this.logger.fine( "Undeploying instance " + instance.getName());
	}
}
