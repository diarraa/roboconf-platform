/**
 * Copyright 2014 Linagora, Université Joseph Fourier, Floralis
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

package net.roboconf.dm.rest.services.internal.resources;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import net.roboconf.core.model.beans.Application;
import net.roboconf.core.utils.Utils;
import net.roboconf.dm.management.ManagedApplication;
import net.roboconf.dm.management.Manager;
import net.roboconf.dm.management.exceptions.AlreadyExistingException;
import net.roboconf.dm.management.exceptions.InvalidApplicationException;
import net.roboconf.dm.management.exceptions.UnauthorizedActionException;
import net.roboconf.target.api.TargetException;

import com.sun.jersey.core.header.FormDataContentDisposition;

/**
 * @author Vincent Zurczak - Linagora
 */
@Path( IManagementResource.PATH )
public class ManagementResource implements IManagementResource {

	private final Logger logger = Logger.getLogger( getClass().getName());
	private final Manager manager;



	/**
	 * Constructor.
	 * @param manager
	 */
	public ManagementResource( Manager manager ) {
		this.manager = manager;
	}


	/*
	 * (non-Javadoc)
	 * @see net.roboconf.dm.internal.rest.api.IManagementWs
	 * #loadApplication(java.io.InputStream, com.sun.jersey.core.header.FormDataContentDisposition)
	 */
	@Override
	public Response loadApplication( InputStream uploadedInputStream, FormDataContentDisposition fileDetail ) {

		this.logger.fine( "Request: load application from uploaded ZIP file (" + fileDetail.getFileName() + ")." );
		File tempZipFile = new File( System.getProperty( "java.io.tmpdir" ), fileDetail.getFileName());
		File dir = null;
		Response response;
		try {
			// Copy the uploaded ZIP file on the disk
			Utils.copyStream( uploadedInputStream, tempZipFile );

			// Extract the ZIP content
			String appName = fileDetail.getFileName().replace( ".zip", "" );
			dir = new File( System.getProperty( "java.io.tmpdir" ), "roboconf/" + appName );
			Utils.extractZipArchive( tempZipFile, dir );

			// Load the application
			response = loadApplication( dir.getAbsolutePath());

		} catch( IOException e ) {
			response = Response.status( Status.NOT_ACCEPTABLE ).entity( "A ZIP file was expected. " + e.getMessage()).build();

		} finally {
			Utils.closeQuietly( uploadedInputStream );
			try {
				// We do not need the extracted application anymore.
				// In case of success, it was copied in the DM's configuration.
				Utils.deleteFilesRecursively( dir );
				if( ! tempZipFile.delete())
					tempZipFile.deleteOnExit();

			} catch( IOException e ) {
				Utils.logException( this.logger, e );
			}
		}

		return response;
	}

	/*
	 * (non-Javadoc)
	 * @see net.roboconf.dm.internal.rest.api.IManagementWs
	 * #loadApplication(net.roboconf.dm.internal.rest.json.MapHolder)
	 */
	@Override
	public Response loadApplication( String localFilePath ) {

		if( localFilePath == null )
			localFilePath = "null";

		this.logger.fine( "Request: load application from " + localFilePath + "." );
		Response response;
		try {
			this.manager.loadNewApplication( new File( localFilePath ));
			response = Response.ok().build();

		} catch( AlreadyExistingException e ) {
			response = Response.status( Status.FORBIDDEN ).entity( e.getMessage()).build();

		} catch( InvalidApplicationException e ) {
			response = Response.status( Status.NOT_ACCEPTABLE ).entity( e.getMessage()).build();

		} catch( IOException e ) {
			response = Response.status( Status.UNAUTHORIZED ).entity( e.getMessage()).build();
		}

		return response;
	}


	/* (non-Javadoc)
	 * @see net.roboconf.dm.internal.rest.client.exceptions.server.IApplicationWs
	 * #listApplications()
	 */
	@Override
	public List<Application> listApplications() {
		this.logger.fine( "Request: list all the applications." );

		List<Application> result = new ArrayList<Application> ();
		for( ManagedApplication ma : this.manager.getAppNameToManagedApplication().values())
			result.add( ma.getApplication());

		return result;
	}


	/* (non-Javadoc)
	 * @see net.roboconf.dm.internal.rest.client.exceptions.server.IApplicationWs
	 * #deleteApplication(java.lang.String)
	 */
	@Override
	public Response deleteApplication( String applicationName ) {

		this.logger.fine( "Request: delete application " + applicationName + "." );
		Response result = Response.ok().build();
		try {
			ManagedApplication ma = this.manager.getAppNameToManagedApplication().get( applicationName );
			if( ma == null )
				result = Response.status( Status.NOT_FOUND ).entity( "Application " + applicationName + " was not found." ).build();
			else
				this.manager.deleteApplication( ma );

		} catch( UnauthorizedActionException e ) {
			result = Response.status( Status.FORBIDDEN ).entity( e.getMessage()).build();

		} catch( IOException e ) {
			result = Response.status( Status.FORBIDDEN ).entity( e.getMessage()).build();
		}

		return result;
	}


	/*
	 * (non-Javadoc)
	 * @see net.roboconf.dm.internal.rest.api.IManagementWs
	 * #shutdownApplication(java.lang.String)
	 */
	@Override
	public Response shutdownApplication( String applicationName ) {

		this.logger.fine( "Request: shutdown application " + applicationName + "." );
		Response result = Response.ok().build();
		try {
			ManagedApplication ma = this.manager.getAppNameToManagedApplication().get( applicationName );
			if( ma == null )
				result = Response.status( Status.NOT_FOUND ).entity( "Application " + applicationName + " was not found." ).build();
			else
				this.manager.undeployAll( ma, null );

		} catch( IOException e ) {
			result = Response.status( Status.FORBIDDEN ).entity( e.getMessage()).build();

		} catch( TargetException e ) {
			result = Response.status( Status.FORBIDDEN ).entity( e.getMessage()).build();
		}

		return result;
	}
}