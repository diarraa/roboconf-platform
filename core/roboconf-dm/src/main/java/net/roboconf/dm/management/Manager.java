/**
 * Copyright 2013-2015 Linagora, Université Joseph Fourier, Floralis
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

package net.roboconf.dm.management;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.roboconf.core.Constants;
import net.roboconf.core.ErrorCode;
import net.roboconf.core.RoboconfError;
import net.roboconf.core.model.beans.Application;
import net.roboconf.core.model.beans.ApplicationTemplate;
import net.roboconf.core.model.beans.Instance;
import net.roboconf.core.model.beans.Instance.InstanceStatus;
import net.roboconf.core.model.helpers.InstanceHelpers;
import net.roboconf.core.utils.IconUtils;
import net.roboconf.core.utils.Utils;
import net.roboconf.dm.internal.delegates.ApplicationMngrDelegate;
import net.roboconf.dm.internal.delegates.ApplicationTemplateMngrDelegate;
import net.roboconf.dm.internal.delegates.InstanceMngrDelegate;
import net.roboconf.dm.internal.environment.messaging.DmMessageProcessor;
import net.roboconf.dm.internal.environment.messaging.RCDm;
import net.roboconf.dm.internal.tasks.CheckerHeartbeatsTask;
import net.roboconf.dm.internal.tasks.CheckerMessagesTask;
import net.roboconf.dm.internal.utils.ConfigurationUtils;
import net.roboconf.dm.internal.utils.ManagerUtils;
import net.roboconf.dm.management.events.EventType;
import net.roboconf.dm.management.events.IDmListener;
import net.roboconf.dm.management.exceptions.AlreadyExistingException;
import net.roboconf.dm.management.exceptions.ImpossibleInsertionException;
import net.roboconf.dm.management.exceptions.InvalidApplicationException;
import net.roboconf.dm.management.exceptions.UnauthorizedActionException;
import net.roboconf.messaging.api.client.ListenerCommand;
import net.roboconf.messaging.api.messages.Message;
import net.roboconf.messaging.api.messages.from_dm_to_agent.MsgCmdResynchronize;
import net.roboconf.messaging.api.messages.from_dm_to_dm.MsgEcho;
import net.roboconf.messaging.api.reconfigurables.ReconfigurableClientDm;
import net.roboconf.target.api.TargetException;
import net.roboconf.target.api.TargetHandler;

/**
 * A class to manage a collection of applications.
 * <p>
 * This class acts as an interface to communicate with the agents.
 * It does not manage anything real (like life cycles). It only
 * handles some kind of cache (cache for application files, cache for
 * instance states).
 * </p>
 * <p>
 * The agents are the most well-placed to manage life cycles and the states
 * of instances. Therefore, the DM does the minimal set of actions on instances.
 * </p>
 * <p>
 * This class is designed to work with OSGi, iPojo and Admin Config.<br>
 * But it can also be used programmatically.
 * </p>
 * <pre><code>
 * // Configure
 * Manager manager = new Manager();
 * manager.setMessagingType( "rabbitmq" );
 *
 * // Change the way we resolve handlers for deployment targets
 * manager.setTargetResolver( ... );
 *
 * // Connect to the messaging server
 * manager.start();
 * </code></pre>
 *
 * @author Noël - LIG
 * @author Pierre-Yves Gibello - Linagora
 * @author Vincent Zurczak - Linagora
 * @author Pierre Bourret - Université Joseph Fourier
 */
public class Manager {

	// Constants
	private static final long TIMER_PERIOD = 6000;

	// Injected by iPojo or Admin Config
	protected final List<IDmListener> dmListeners = new ArrayList<> ();
	protected final List<TargetHandler> targetHandlers = new ArrayList<> ();
	protected String messagingType;

	// Internal fields
	protected final Logger logger = Logger.getLogger( getClass().getName());
	protected final ApplicationTemplateMngrDelegate templateManager;
	protected final ApplicationMngrDelegate appManager;
	protected final InstanceMngrDelegate instanceManager;
	protected String configurationDirectoryLocation;
	protected Timer timer;

	private RCDm messagingClient;
	File configurationDirectory;


	/**
	 * Constructor.
	 */
	public Manager() {
		super();
		this.templateManager = new ApplicationTemplateMngrDelegate();
		this.appManager = new ApplicationMngrDelegate();
		this.instanceManager = new InstanceMngrDelegate( this );
	}


	/**
	 * Starts the manager.
	 * <p>
	 * It is invoked by iPojo when an instance becomes VALID.
	 * </p>
	 */
	public void start() {

		this.logger.info( "The DM is about to be launched." );

		// Start the messaging
		DmMessageProcessor messageProcessor = new DmMessageProcessor( this, this.appManager );
		this.messagingClient = new RCDm( this.appManager );
		this.messagingClient.associateMessageProcessor( messageProcessor );

		// Run the timer
		this.timer = new Timer( "Roboconf's Management Timer", false );
		this.timer.scheduleAtFixedRate( new CheckerMessagesTask( this.appManager, this.messagingClient ), 0, TIMER_PERIOD );
		this.timer.scheduleAtFixedRate( new CheckerHeartbeatsTask( this.appManager ), 0, Constants.HEARTBEAT_PERIOD );

		// Initialize the directory configuration
		initializeDirectory();

		// Configure the messaging
		reconfigure();
		this.logger.info( "The DM was launched." );
	}


	/**
	 * Stops the manager.
	 * <p>
	 * It is invoked by iPojo when an instance becomes INVALID.
	 * </p>
	 */
	public void stop() {

		this.logger.info( "The DM is about to be stopped." );
		if( this.timer != null ) {
			this.timer.cancel();
			this.timer =  null;
		}

		if( this.messagingClient != null ) {

			// Stops listening to the debug queue.
			try {
				this.messagingClient.listenToTheDm( ListenerCommand.STOP );
			} catch ( IOException e ) {
				this.logger.log( Level.WARNING, "Cannot stop to listen to the debug queue", e );
			}

			this.messagingClient.getMessageProcessor().stopProcessor();
			this.messagingClient.getMessageProcessor().interrupt();
			try {
				this.messagingClient.closeConnection();

			} catch( IOException e ) {
				this.logger.warning( "The messaging client could not be terminated correctly. " + e.getMessage());
				Utils.logException( this.logger, e );
			}
		}

		this.logger.info( "The DM was stopped." );
	}


	/**
	 * A method that initializes the DM's internal directory.
	 * <p>
	 * This method is invoked in {@link #start()}. However, it can be better to invoke
	 * this method directly in tests. Indeed, {@link #start()} also starts timers, while this
	 * method does not.
	 * </p>
	 */
	public void initializeDirectory() {

		// Find the configuration directory
		String karafData = System.getProperty( "karaf.data" );
		if( ! Utils.isEmptyOrWhitespaces( this.configurationDirectoryLocation ))
			this.configurationDirectory = new File( this.configurationDirectoryLocation );
		else if( Utils.isEmptyOrWhitespaces( karafData ))
			this.configurationDirectory = new File( System.getProperty( "java.io.tmpdir" ), "roboconf-dm" );
		else
			this.configurationDirectory = new File( karafData, "roboconf" );

		try {
			// Create the directory, if necessary
			Utils.createDirectory( this.configurationDirectory );

			// Restore applications
			this.templateManager.restoreTemplates( this.configurationDirectory );
			this.appManager.restoreApplications( this.configurationDirectory, this.templateManager );

		} catch( IOException e ) {
			this.logger.severe( "The DM's configuration directory could not be found and/or created." );
			Utils.logException( this.logger, e );
		}
	}


	/**
	 * This method is invoked by iPojo every time a new target handler appears.
	 * @param targetItf the appearing target handler
	 */
	public void targetAppears( TargetHandler targetItf ) {

		if( targetItf != null ) {
			this.logger.info( "Target handler '" + targetItf.getTargetId() + "' is now available in Roboconf's DM." );
			this.targetHandlers.add( targetItf );
			ManagerUtils.listTargets( this.targetHandlers, this.logger );
		}
	}


	/**
	 * This method is invoked by iPojo every time a target handler disappears.
	 * @param targetItf the disappearing target handler
	 */
	public void targetDisappears( TargetHandler targetItf ) {

		// May happen if a target could not be instantiated
		// (iPojo uses proxies). In this case, it results in a NPE here.
		if( targetItf == null ) {
			this.logger.info( "An invalid target handler is removed." );
		} else {
			this.targetHandlers.remove( targetItf );
			this.logger.info( "Target handler '" + targetItf.getTargetId() + "' is not available anymore in Roboconf's DM." );
		}

		ManagerUtils.listTargets( this.targetHandlers, this.logger );
	}


	/**
	 * This method is invoked by iPojo every time a DM listener appears.
	 * @param targetItf the appearing listener
	 */
	public void listenerAppears( IDmListener listener ) {

		if( listener != null ) {
			this.logger.info( "A new listener '" + listener.getId() + "' is now available in Roboconf's DM." );
			synchronized( this.dmListeners ) {
				this.dmListeners.add( listener );
			}

			ManagerUtils.listListeners( this.dmListeners, this.logger );
		}
	}


	/**
	 * This method is invoked by iPojo every time a DM listener disappears.
	 * @param listener the disappearing listener
	 */
	public void listenerDisappears( IDmListener listener ) {

		// May happen if a target could not be instantiated
		// (iPojo uses proxies). In this case, it results in a NPE here.
		if( listener == null ) {
			this.logger.info( "An invalid listener is removed." );

		} else {
			synchronized( this.dmListeners ) {
				this.dmListeners.remove( listener );
			}

			this.logger.info( "The listener '" + listener.getId() + "' is not available anymore in Roboconf's DM." );
		}

		ManagerUtils.listListeners( this.dmListeners, this.logger );
	}


	/**
	 * @param configurationDirectoryLocation the configurationDirectoryLocation to set
	 */
	public void setConfigurationDirectoryLocation( String configurationDirectoryLocation ) {
		this.configurationDirectoryLocation = configurationDirectoryLocation;
	}


	/**
	 * @return the target handlers
	 */
	public List<TargetHandler> getTargetHandlers() {
		return Collections.unmodifiableList( this.targetHandlers );
	}


	/**
	 * @return the DM listeners
	 */
	public List<IDmListener> getDmListeners() {
		return Collections.unmodifiableList( this.dmListeners );
	}


	/**
	 * @param messagingType the messagingType to set
	 */
	public void setMessagingType( String messagingType ) {
		this.messagingType = messagingType;
	}


	/**
	 * @return the configurationDirectoryLocation
	 */
	public String getConfigurationDirectoryLocation() {
		return this.configurationDirectoryLocation;
	}


	/**
	 * This method reconfigures the manager.
	 * <p>
	 * It is invoked by iPojo when the configuration changes.
	 * It may be invoked before the start() method is.
	 * </p>
	 */
	public void reconfigure() {

		// Update the messaging client
		if( this.messagingClient != null ) {
			this.messagingClient.switchMessagingType(this.messagingType);
			try {
				if( this.messagingClient.isConnected())
					this.messagingClient.listenToTheDm( ListenerCommand.START );

			} catch ( IOException e ) {
				this.logger.log( Level.WARNING, "Cannot start to listen to the debug queue", e );
			}
		}

		// We must update instance states after we switched the messaging configuration.
		for( ManagedApplication ma : this.appManager.getManagedApplications())
			this.instanceManager.restoreInstanceStates( ma );

		this.logger.info( "The DM was successfully (re)configured." );
	}


	/**
	 * Finds an application by name.
	 * @param applicationName an application name (not null)
	 * @return the associated application, or null if it was not found
	 */
	public Application findApplicationByName( String applicationName ) {
		return this.appManager.findApplicationByName( applicationName );
	}


	/**
	 * @return a non-null map (key = application name, value = managed application)
	 */
	public Map<String,ManagedApplication> getNameToManagedApplication() {
		return this.appManager.getNameToManagedApplication();
	}


	/**
	 * @return a non-null set of all the application templates
	 */
	public Set<ApplicationTemplate> getApplicationTemplates() {
		return this.templateManager.getAllTemplates();
	}


	/**
	 * @return the raw templates (never null)
	 */
	public Map<ApplicationTemplate,Boolean> getRawApplicationTemplates() {
		return this.templateManager.getRawTemplates();
	}


	/**
	 * @throws IOException if the configuration is invalid
	 */
	public void checkConfiguration() throws IOException {

		String msg = null;
		if( this.messagingClient == null )
			msg = "The DM was not started.";
		else if( ! this.messagingClient.hasValidClient())
			msg = "The DM's configuration is invalid. Please, review the messaging settings.";

		if( msg != null ) {
			this.logger.warning( msg );
			throw new IOException( msg );
		}
	}


	/**
	 * @return the messagingClient
	 */
	public ReconfigurableClientDm getMessagingClient() {
		return this.messagingClient;
	}


	/**
	 * Loads a new application template.
	 * @see ApplicationTemplateMngrDelegate#loadApplicationTemplate(File, File)
	 */
	public ApplicationTemplate loadApplicationTemplate( File applicationFilesDirectory )
	throws AlreadyExistingException, InvalidApplicationException, IOException {

		checkConfiguration();
		ApplicationTemplate tpl = this.templateManager.loadApplicationTemplate( applicationFilesDirectory, this.configurationDirectory );

		synchronized( this.dmListeners ) {
			for( IDmListener listener : this.dmListeners )
				listener.applicationTemplate( tpl, EventType.CREATED );
		}

		return tpl;
	}


	/**
	 * Deletes an application template.
	 */
	public void deleteApplicationTemplate( String tplName, String tplQualifier )
	throws UnauthorizedActionException, InvalidApplicationException, IOException {

		checkConfiguration();
		ApplicationTemplate tpl = this.templateManager.findTemplate( tplName, tplQualifier );
		if( tpl == null )
			throw new InvalidApplicationException( new RoboconfError( ErrorCode.PROJ_APPLICATION_TEMPLATE_NOT_FOUND ));

		if( this.appManager.isTemplateUsed( tpl )) {
			throw new UnauthorizedActionException( tplName + " (" + tplQualifier + ") is still used by applications. It cannot be deleted." );

		} else {
			this.templateManager.deleteApplicationTemplate( tpl, this.configurationDirectory );
			synchronized( this.dmListeners ) {
				for( IDmListener listener : this.dmListeners )
					listener.applicationTemplate( tpl, EventType.DELETED );
			}
		}
	}


	/**
	 * Creates a new application from a template.
	 * @return a managed application (never null)
	 * @throws IOException
	 * @throws AlreadyExistingException
	 * @throws InvalidApplicationException
	 */
	public ManagedApplication createApplication( String name, String description, String tplName, String tplQualifier )
	throws IOException, AlreadyExistingException, InvalidApplicationException {

		// Always verify the configuration first
		checkConfiguration();

		// Create the application
		ApplicationTemplate tpl = this.templateManager.findTemplate( tplName, tplQualifier );
		if( tpl == null )
			throw new InvalidApplicationException( new RoboconfError( ErrorCode.PROJ_APPLICATION_TEMPLATE_NOT_FOUND ));

		return createApplication( name, description, tpl );
	}


	/**
	 * Creates a new application from a template.
	 * @return a managed application (never null)
	 * @throws IOException
	 * @throws AlreadyExistingException
	 */
	public ManagedApplication createApplication( String name, String description, ApplicationTemplate tpl )
	throws IOException, AlreadyExistingException {

		// Always verify the configuration first
		checkConfiguration();

		// Create the application
		ManagedApplication ma = this.appManager.createApplication( name, description, tpl, this.configurationDirectory );

		// Start listening to messages
		this.messagingClient.listenToAgentMessages( ma.getApplication(), ListenerCommand.START );

		// Notify listeners
		synchronized( this.dmListeners ) {
			for( IDmListener listener : this.dmListeners )
				listener.application( ma.getApplication(), EventType.CREATED );
		}

		this.logger.fine( "Application " + ma.getApplication().getName() + " was successfully loaded and added." );
		return ma;
	}


	/**
	 * Updates an application with a new description.
	 * @param app the application to update
	 * @param newDesc the new description
	 * @throws IOException
	 */
	public void updateApplication( ManagedApplication ma, String newDesc ) throws IOException {

		// Basic checks
		checkConfiguration();
		this.appManager.updateApplication( ma.getApplication(), newDesc );

		// Notify listeners
		synchronized( this.dmListeners ) {
			for( IDmListener listener : this.dmListeners )
				listener.application( ma.getApplication(), EventType.CHANGED );
		}

		this.logger.fine( "The description of application " + ma.getApplication().getName() + " was successfully updated." );
	}


	/**
	 * Deletes an application.
	 * @param ma the managed application
	 * @throws UnauthorizedActionException if parts of the application are still running
	 * @throws IOException if errors occurred with the messaging or the removal of resources
	 */
	public void deleteApplication( ManagedApplication ma )
	throws UnauthorizedActionException, IOException {

		// What really matters is that there is no agent running.
		// If all the root instances are not deployed, then nothing is deployed at all.
		String applicationName = ma.getApplication().getName();
		for( Instance rootInstance : ma.getApplication().getRootInstances()) {
			if( rootInstance.getStatus() != InstanceStatus.NOT_DEPLOYED )
				throw new UnauthorizedActionException( applicationName + " contains instances that are still deployed." );
		}

		// Stop listening to messages first
		try {
			checkConfiguration();
			this.messagingClient.listenToAgentMessages( ma.getApplication(), ListenerCommand.STOP );
			this.messagingClient.deleteMessagingServerArtifacts( ma.getApplication());

		} catch( IOException e ) {
			Utils.logException( this.logger, e );
		}

		// Notify listeners
		synchronized( this.dmListeners ) {
			for( IDmListener listener : this.dmListeners )
				listener.application( ma.getApplication(), EventType.DELETED );
		}

		// Delete artifacts
		this.appManager.deleteApplication( ma.getApplication(), this.configurationDirectory );
	}


	/**
	 * Adds an instance.
	 * @see InstanceMngrDelegate#addInstance(ManagedApplication, Instance, Instance)
	 */
	public void addInstance( ManagedApplication ma, Instance parentInstance, Instance instance )
	throws ImpossibleInsertionException, IOException {

		checkConfiguration();
		this.instanceManager.addInstance( ma, parentInstance, instance );
		ConfigurationUtils.saveInstances( ma, this.configurationDirectory );

		synchronized( this.dmListeners ) {
			for( IDmListener listener : this.dmListeners )
				listener.instance( instance, ma.getApplication(), EventType.CREATED );
		}
	}


	/**
	 * Invoked when an instance was modified and that we need to propagate these changes outside the DM.
	 * @param instance an instance (not null)
	 * @param ma the associated application
	 */
	public void instanceWasUpdated( Instance instance, ManagedApplication ma ) {

		synchronized( this.dmListeners ) {
			for( IDmListener listener : this.dmListeners )
				listener.instance( instance, ma.getApplication(), EventType.CHANGED );
		}

		ConfigurationUtils.saveInstances( ma, this.configurationDirectory );
	}


	/**
	 * Removes an instance.
	 * @see InstanceMngrDelegate#removeInstance(ManagedApplication, Instance)
	 */
	public void removeInstance( ManagedApplication ma, Instance instance )
	throws UnauthorizedActionException, IOException {

		checkConfiguration();
		this.instanceManager.removeInstance( ma, instance );
		ConfigurationUtils.saveInstances( ma, this.configurationDirectory );

		synchronized( this.dmListeners ) {
			for( IDmListener listener : this.dmListeners )
				listener.instance( instance, ma.getApplication(), EventType.DELETED );
		}
	}


	/**
	 * Notifies all the agents they must re-export their variables.
	 * <p>
	 * Such an operation can be used when the messaging server was down and
	 * that messages were lost.
	 * </p>
	 * @throws IOException
	 */
	public void resynchronizeAgents( ManagedApplication ma ) throws IOException {

		checkConfiguration();
		this.logger.fine( "Resynchronizing agents in " + ma.getName() + "..." );
		for( Instance rootInstance : ma.getApplication().getRootInstances()) {
			if( rootInstance.getStatus() == InstanceStatus.DEPLOYED_STARTED )
				send ( ma, new MsgCmdResynchronize(), rootInstance );
		}

		this.logger.fine( "Requests were sent to resynchronize agents in " + ma.getName() + "." );
	}


	/**
	 * Changes the state of an instance.
	 * @see InstanceMngrDelegate#changeInstanceState(ManagedApplication, Instance, InstanceStatus)
	 */
	public void changeInstanceState( ManagedApplication ma, Instance instance, InstanceStatus newStatus )
	throws IOException, TargetException {

		checkConfiguration();
		this.instanceManager.changeInstanceState( ma, instance, newStatus );
	}


	/**
	 * Deploys and starts all the instances of an application.
	 * @see InstanceMngrDelegate#deployAndStartAll(ManagedApplication, Instance)
	 */
	public void deployAndStartAll( ManagedApplication ma, Instance instance ) throws IOException {

		checkConfiguration();
		this.instanceManager.deployAndStartAll( ma, instance );
	}


	/**
	 * Stops all the started instances of an application.
	 * @see InstanceMngrDelegate#stopAll(ManagedApplication, Instance)
	 */
	public void stopAll( ManagedApplication ma, Instance instance ) throws IOException {

		checkConfiguration();
		this.instanceManager.stopAll( ma, instance );
	}


	/**
	 * Undeploys all the instances of an application.
	 * @see InstanceMngrDelegate#undeployAll(ManagedApplication, Instance)
	 */
	public void undeployAll( ManagedApplication ma, Instance instance ) throws IOException {

		checkConfiguration();
		this.instanceManager.undeployAll( ma, instance );
	}


	/**
	 * Sends a message, or stores it if the targetHandlers machine is not yet online.
	 * @param ma the managed application
	 * @param message the message to send (or store)
	 * @param instance the targetHandlers instance
	 * @throws IOException if an error occurred with the messaging
	 */
	public void send( ManagedApplication ma, Message message, Instance instance ) throws IOException {

		if( this.messagingClient != null
				&& this.messagingClient.isConnected()) {

			// We do NOT send directly a message!
			ma.storeAwaitingMessage( instance, message );

			// If the message has been stored, let's try to send all the stored messages.
			// This preserves message ordering (FIFO).

			// If the VM is online, process awaiting messages to prevent waiting.
			// This can work concurrently with the messages timer.
			Instance scopedInstance = InstanceHelpers.findScopedInstance( instance );
			if( scopedInstance.getStatus() == InstanceStatus.DEPLOYED_STARTED ) {

				List<Message> messages = ma.removeAwaitingMessages( instance );
				String path = InstanceHelpers.computeInstancePath( scopedInstance );
				this.logger.fine( "Forcing the sending of " + messages.size() + " awaiting message(s) for " + path + "." );

				for( Message msg : messages ) {
					try {
						this.messagingClient.sendMessageToAgent( ma.getApplication(), scopedInstance, msg );

					} catch( IOException e ) {
						this.logger.severe( "Error while sending a stored message. " + e.getMessage());
						Utils.logException( this.logger, e );
					}
				}
			}

		} else {
			this.logger.severe( "The connection with the messaging server was badly initialized. Message dropped." );
		}
	}


	/**
	 * @param targetResolver the target resolver to set
	 */
	public void setTargetResolver( ITargetResolver targetResolver ) {
		this.instanceManager.setTargetResolver( targetResolver );
	}


	/**
	 * Pings the DM through the messaging queue.
	 * @param message the content of the Echo message to send
	 * @throws java.io.IOException if something bad happened
	 */
	public void pingMessageQueue( String message ) throws IOException {

		final MsgEcho sentMessage = new MsgEcho( message );
		this.messagingClient.sendMessageToTheDm( sentMessage );
		this.logger.fine( "Sent Echo message on debug queue. Message=" + message + ", UUID=" + sentMessage.getUuid());
	}


	/**
	 * Pings an agent.
	 * @param app the application
	 * @param scopedInstance the scoped instance
	 * @param message the echo messages's content
	 * @throws java.io.IOException if something bad happened
	 */
	public void pingAgent( Application app, Instance scopedInstance, String message ) throws IOException {

		MsgEcho ping = new MsgEcho( "PING:" + message );
		this.messagingClient.sendMessageToAgent( app, scopedInstance, ping );
		this.logger.fine( "Sent PING request message=" + message + " to application=" + app + ", agent=" + scopedInstance );
	}


	/**
	 * Invokes when an ECHO message was received.
	 * @param message an ECHO message
	 */
	public void notifyMsgEchoReceived( MsgEcho message ) {

		synchronized( this.dmListeners ) {
			for( IDmListener listener : this.dmListeners )
				listener.raw( message.getContent());
		}
	}


	/**
	 * @return the messaging configuration
	 */
	public Map<String,String> getMessagingConfiguration() {
		return this.messagingClient.getConfiguration();
	}


	/**
	 * Finds an icon from an URL path.
	 * <p>
	 * Notice that this method may fail to return a result during
	 * reconfigurations.
	 * </p>
	 *
	 * @param urlPath an icon path
	 * @return an existing image file, or null if none was found
	 */
	public File findIconFromPath( String urlPath ) {
		Map.Entry<String,String> entry = IconUtils.decodeIconUrl( urlPath );
		return ConfigurationUtils.findIcon( entry.getKey(), entry.getValue(), this.configurationDirectory );
	}
}
