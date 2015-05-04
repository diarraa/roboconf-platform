/**
 * Copyright 2014-2015 Linagora, Université Joseph Fourier, Floralis
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

package net.roboconf.dm.rest.services.internal.resources.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import net.roboconf.core.model.beans.Application;
import net.roboconf.core.model.beans.Component;
import net.roboconf.core.model.beans.Graphs;
import net.roboconf.core.model.beans.Instance;
import net.roboconf.core.model.beans.Instance.InstanceStatus;
import net.roboconf.core.model.comparators.InstanceComparator;
import net.roboconf.core.model.helpers.ComponentHelpers;
import net.roboconf.core.model.helpers.InstanceHelpers;
import net.roboconf.dm.management.ManagedApplication;
import net.roboconf.dm.management.Manager;
import net.roboconf.dm.management.exceptions.ImpossibleInsertionException;
import net.roboconf.dm.management.exceptions.UnauthorizedActionException;
import net.roboconf.dm.rest.services.internal.RestServicesUtils;
import net.roboconf.dm.rest.services.internal.resources.IApplicationResource;
import net.roboconf.target.api.TargetException;

/**
 * @author Vincent Zurczak - Linagora
 */
@Path( IApplicationResource.PATH )
public class ApplicationResource implements IApplicationResource {

	private final Logger logger = Logger.getLogger( getClass().getName());
	private final Manager manager;


	/**
	 * Constructor.
	 * @param manager
	 */
	public ApplicationResource( Manager manager ) {
		this.manager = manager;
	}


	/*
	 * (non-Javadoc)
	 * @see net.roboconf.dm.internal.rest.api.IApplicationWs
	 * #changeInstanceState(java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public Response changeInstanceState( String applicationName, String newState, String instancePath ) {

		this.logger.fine( "Request: change state of " + instancePath + " to '" + newState + "' in " + applicationName + "." );
		Response response = Response.ok().build();
		try {
			ManagedApplication ma;
			Instance instance;
			if( ! InstanceStatus.isValidState( newState ))
				response = Response.status( Status.FORBIDDEN ).entity( "Status '" + newState + "' does not exist." ).build();

			else if(( ma = this.manager.getNameToManagedApplication().get( applicationName )) == null )
				response = Response.status( Status.NOT_FOUND ).entity( "Application " + applicationName + " does not exist." ).build();

			else if(( instance = InstanceHelpers.findInstanceByPath( ma.getApplication(), instancePath )) == null )
				response = Response.status( Status.NOT_FOUND ).entity( "Instance " + instancePath + " was not found." ).build();

			else
				this.manager.changeInstanceState( ma, instance, InstanceStatus.whichStatus( newState ));

		} catch( IOException e ) {
			response = RestServicesUtils.handleException( this.logger, Status.FORBIDDEN, null, e ).build();

		} catch( TargetException e ) {
			response = RestServicesUtils.handleException( this.logger, Status.FORBIDDEN, null, e ).build();

		} catch( Exception e ) {
			response = RestServicesUtils.handleException( this.logger, Status.INTERNAL_SERVER_ERROR, null, e ).build();
		}

		return response;
	}


	/*
	 * (non-Javadoc)
	 * @see net.roboconf.dm.internal.rest.api.IApplicationWs
	 * #deployAndStartAll(java.lang.String, java.lang.String)
	 */
	@Override
	public Response deployAndStartAll( String applicationName, String instancePath ) {

		this.logger.fine( "Request: deploy and start instances in " + applicationName + ", from instance = " + instancePath + "." );
		Response response;
		ManagedApplication ma;
		try {
			if(( ma = this.manager.getNameToManagedApplication().get( applicationName )) == null ) {
				response = Response.status( Status.NOT_FOUND ).entity( "Application " + applicationName + " does not exist." ).build();
			} else {
				this.manager.deployAndStartAll( ma, InstanceHelpers.findInstanceByPath( ma.getApplication(), instancePath ));
				response = Response.ok().build();
			}

		} catch( Exception e ) {
			response = RestServicesUtils.handleException( this.logger, Status.FORBIDDEN, null, e ).build();
		}

		return response;
	}


	/*
	 * (non-Javadoc)
	 * @see net.roboconf.dm.internal.rest.api.IApplicationWs
	 * #stopAll(java.lang.String, java.lang.String)
	 */
	@Override
	public Response stopAll( String applicationName, String instancePath ) {

		this.logger.fine( "Request: stop instances in " + applicationName + ", from instance = " + instancePath + "." );
		Response response;
		ManagedApplication ma;
		try {
			if(( ma = this.manager.getNameToManagedApplication().get( applicationName )) == null ) {
				response = Response.status( Status.NOT_FOUND ).entity( "Application " + applicationName + " does not exist." ).build();
			} else {
				this.manager.stopAll( ma, InstanceHelpers.findInstanceByPath( ma.getApplication(), instancePath ));
				response = Response.ok().build();
			}

		} catch( Exception e ) {
			response = RestServicesUtils.handleException( this.logger, Status.FORBIDDEN, null, e ).build();
		}

		return response;
	}


	/*
	 * (non-Javadoc)
	 * @see net.roboconf.dm.internal.rest.api.IApplicationWs
	 * #undeployAll(java.lang.String, java.lang.String)
	 */
	@Override
	public Response undeployAll( String applicationName, String instancePath ) {

		this.logger.fine( "Request: deploy and start instances in " + applicationName + ", from instance = " + instancePath + "." );
		Response response;
		ManagedApplication ma;
		try {
			if(( ma = this.manager.getNameToManagedApplication().get( applicationName )) == null ) {
				response = Response.status( Status.NOT_FOUND ).entity( "Application " + applicationName + " does not exist." ).build();
			} else {
				this.manager.undeployAll( ma, InstanceHelpers.findInstanceByPath( ma.getApplication(), instancePath ));
				response = Response.ok().build();
			}

		} catch( Exception e ) {
			response = RestServicesUtils.handleException( this.logger, Status.FORBIDDEN, null, e ).build();
		}

		return response;
	}


	/*
	 * (non-Javadoc)
	 * @see net.roboconf.dm.internal.rest.api.IApplicationWs
	 * #listChildrenInstances(java.lang.String, java.lang.String, boolean)
	 */
	@Override
	public List<Instance> listChildrenInstances( String applicationName, String instancePath, boolean allChildren ) {

		List<Instance> result = new ArrayList<Instance> ();
		Application app = this.manager.findApplicationByName( applicationName );

		// Log
		if( instancePath == null )
			this.logger.fine( "Request: list " + (allChildren ? "all" : "root") + " instances for " + applicationName + "." );
		else
			this.logger.fine( "Request: list " + (allChildren ? "all" : "direct") + " children instances for " + instancePath + " in " + applicationName + "." );

		// Find the instances
		Instance inst;
		if( app != null ) {
			if( instancePath == null ) {
				if( allChildren )
					result.addAll( InstanceHelpers.getAllInstances( app ));
				else
					result.addAll( app.getRootInstances());
			}

			else if(( inst = InstanceHelpers.findInstanceByPath( app, instancePath )) != null ) {
				if( allChildren ) {
					result.addAll( InstanceHelpers.buildHierarchicalList( inst ));
					result.remove( inst );
				} else {
					result.addAll( inst.getChildren());
				}
			}
		}

		// Bug #64: sort instance paths for the clients
		Collections.sort( result, new InstanceComparator());
		return result;
	}


	/* (non-Javadoc)
	 * @see net.roboconf.dm.internal.rest.client.exceptions.server.IInstanceWs
	 * #addInstance(java.lang.String, java.lang.String, net.roboconf.core.model.beans.Instance)
	 */
	@Override
	public Response addInstance( String applicationName, String parentInstancePath, Instance instance ) {

		if( parentInstancePath == null )
			this.logger.fine( "Request: add root instance " + instance.getName() + " in " + applicationName + "." );
		else
			this.logger.fine( "Request: add instance " + instance.getName() + " under " + parentInstancePath + " in " + applicationName + "." );

		Response response;
		try {
			ManagedApplication ma;
			if(( ma = this.manager.getNameToManagedApplication().get( applicationName )) == null ) {
				response = Response.status( Status.NOT_FOUND ).entity( "Application " + applicationName + " does not exist." ).build();

			} else {
				Graphs graphs = ma.getApplication().getTemplate().getGraphs();
				String componentName = null;
				if( instance.getComponent() != null )
					componentName = instance.getComponent().getName();

				Component realComponent;
				if( componentName == null ) {
					response = Response.status( Status.NOT_FOUND ).entity( "No component was specified for the instance." ).build();

				} else if((realComponent = ComponentHelpers.findComponent( graphs, componentName )) == null ) {
					response = Response.status( Status.NOT_FOUND ).entity( "Component " + componentName + " does not exist." ).build();

				} else {
					instance.setComponent( realComponent );
					Instance parentInstance = InstanceHelpers.findInstanceByPath( ma.getApplication(), parentInstancePath );
					this.manager.addInstance( ma, parentInstance, instance );
					response = Response.ok().build();
				}
			}

		} catch( ImpossibleInsertionException e ) {
			response = RestServicesUtils.handleException( this.logger, Status.FORBIDDEN, null, e ).build();

		} catch( IOException e ) {
			response = RestServicesUtils.handleException( this.logger, Status.FORBIDDEN, null, e ).build();
		}

		return response;
	}


	/*
	 * (non-Javadoc)
	 * @see net.roboconf.dm.internal.rest.api.IApplicationWs
	 * #removeInstance(java.lang.String, java.lang.String)
	 */
	@Override
	public Response removeInstance( String applicationName, String instancePath ) {

		this.logger.fine( "Request: remove " + instancePath + " in " + applicationName + "." );
		Response response = Response.ok().build();
		Instance instance;
		try {
			ManagedApplication ma;
			if(( ma = this.manager.getNameToManagedApplication().get( applicationName )) == null )
				response = Response.status( Status.NOT_FOUND ).entity( "Application " + applicationName + " does not exist." ).build();

			else if(( instance = InstanceHelpers.findInstanceByPath( ma.getApplication(), instancePath )) == null )
				response = Response.status( Status.NOT_FOUND ).entity( "Instance " + instancePath + " was not found." ).build();

			else
				this.manager.removeInstance( ma, instance );

		} catch( UnauthorizedActionException e ) {
			response = RestServicesUtils.handleException( this.logger, Status.FORBIDDEN, null, e ).build();

		} catch( IOException e ) {
			response = RestServicesUtils.handleException( this.logger, Status.NOT_ACCEPTABLE, null, e ).build();
		}

		return response;
	}


	/*
	 * (non-Javadoc)
	 * @see net.roboconf.dm.internal.rest.api.IApplicationWs
	 * #resynchronize(java.lang.String)
	 */
	@Override
	public Response resynchronize( String applicationName ) {

		this.logger.fine( "Request: resynchronize all the agents." );
		Response response = Response.ok().build();
		try {
			ManagedApplication ma;
			if(( ma = this.manager.getNameToManagedApplication().get( applicationName )) == null )
				response = Response.status( Status.NOT_FOUND ).entity( "Application " + applicationName + " does not exist." ).build();
			else
				this.manager.resynchronizeAgents( ma );

		} catch( IOException e ) {
			response = RestServicesUtils.handleException( this.logger, Status.NOT_ACCEPTABLE, null, e ).build();
		}

		return response;
	}


	/*
	 * (non-Javadoc)
	 * @see net.roboconf.dm.internal.rest.client.exceptions.server.IGraphWs
	 * #listComponents(java.lang.String)
	 */
	@Override
	public List<Component> listComponents( String applicationName ) {

		this.logger.fine( "Request: list components for the application " + applicationName + "." );
		List<Component> result = new ArrayList<Component> ();
		Application app = this.manager.findApplicationByName( applicationName );
		if( app != null )
			result.addAll( ComponentHelpers.findAllComponents( app ));

		return result;
	}


	/*
	 * (non-Javadoc)
	 * @see net.roboconf.dm.internal.rest.api.IApplicationWs
	 * #findPossibleComponentChildren(java.lang.String, java.lang.String)
	 */
	@Override
	public List<Component> findPossibleComponentChildren( String applicationName, String instancePath ) {

		if( instancePath == null )
			this.logger.fine( "Request: list possible root instances in " + applicationName + "." );
		else
			this.logger.fine( "Request: find components that can be deployed under " + instancePath + " in " + applicationName + "." );

		Application app = this.manager.findApplicationByName( applicationName );
		Instance instance = null;
		if( app != null
				&& instancePath != null )
			instance = InstanceHelpers.findInstanceByPath( app, instancePath );

		List<Component> result = new ArrayList<Component> ();
		if( instance != null )
			result.addAll( ComponentHelpers.findAllChildren( instance.getComponent()));

		else if( app != null
				&& instancePath == null )
			result.addAll( app.getTemplate().getGraphs().getRootComponents());

		return result;
	}


	/*
	 * (non-Javadoc)
	 * @see net.roboconf.dm.internal.rest.client.exceptions.server.IGraphWs
	 * #findPossibleParentInstances(java.lang.String, java.lang.String)
	 */
	@Override
	public List<String> findPossibleParentInstances( String applicationName, String componentName ) {

		this.logger.fine( "Request: find instances where a component " + componentName + " could be deployed on, in " + applicationName + "." );
		List<String> result = new ArrayList<String> ();
		Application app = this.manager.findApplicationByName( applicationName );

		// Run through all the instances.
		// See if their component can support a child "of type componentName".
		if( app != null ) {
			for( Instance instance : InstanceHelpers.getAllInstances( app )) {
				for( Component c : ComponentHelpers.findAllChildren( instance.getComponent())) {
					if( componentName.equals( c.getName())) {
						String instancePath = InstanceHelpers.computeInstancePath( instance );
						result.add( instancePath );
					}
				}
			}
		}

		return result;
	}
}