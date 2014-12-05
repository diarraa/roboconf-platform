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

package net.roboconf.messaging.internal;

import java.util.ArrayList;
import java.util.List;

import junit.framework.Assert;
import net.roboconf.core.model.beans.Application;
import net.roboconf.core.model.beans.Component;
import net.roboconf.core.model.beans.Instance;
import net.roboconf.core.model.beans.Instance.InstanceStatus;
import net.roboconf.messaging.client.IAgentClient;
import net.roboconf.messaging.client.IClient.ListenerCommand;
import net.roboconf.messaging.client.IDmClient;
import net.roboconf.messaging.messages.Message;
import net.roboconf.messaging.messages.from_agent_to_agent.MsgCmdAddImport;
import net.roboconf.messaging.messages.from_agent_to_agent.MsgCmdRemoveImport;
import net.roboconf.messaging.messages.from_agent_to_agent.MsgCmdRequestImport;
import net.roboconf.messaging.messages.from_agent_to_dm.MsgNotifHeartbeat;
import net.roboconf.messaging.messages.from_agent_to_dm.MsgNotifMachineDown;
import net.roboconf.messaging.messages.from_dm_to_agent.MsgCmdChangeInstanceState;
import net.roboconf.messaging.messages.from_dm_to_agent.MsgCmdRemoveInstance;
import net.roboconf.messaging.messages.from_dm_to_agent.MsgCmdSetRootInstance;
import net.roboconf.messaging.processors.AbstractMessageProcessor;
import net.roboconf.messaging.reconfigurables.ReconfigurableClient;
import net.roboconf.messaging.reconfigurables.ReconfigurableClientAgent;
import net.roboconf.messaging.reconfigurables.ReconfigurableClientDm;

import org.junit.After;

/**
 * This class defines messaging tests, independently of the implementation.
 * <p>
 * They should work with RabbitMQ or any other messaging server that is
 * supported by Roboconf.
 * </p>
 *
 * @author Vincent Zurczak - Linagora
 */
public abstract class AbstractMessagingTest {

	private static final long DELAY = 700;
	private final List<ReconfigurableClient<?>> clients = new ArrayList<ReconfigurableClient<?>> ();


	@After
	public void releaseClients() throws Exception {

		for( ReconfigurableClient<?> client : this.clients ) {
			client.getMessageProcessor().stopProcessor();
			client.getMessageProcessor().interrupt();
			client.closeConnection();
		}

		this.clients.clear();
	}


	/**
	 * Tests synchronous exchanges between the DM and an agent.
	 * @throws Exception
	 */
	public void testExchangesBetweenTheDmAndOneAgent() throws Exception {

		// Initialize everything
		Application app = new Application( "app" );
		Instance rootInstance = new Instance( "root" );

		List<Message> dmMessages = new ArrayList<Message> ();
		List<Message> agentMessages = new ArrayList<Message> ();

		ReconfigurableClientDm dmClient = new ReconfigurableClientDm();
		dmClient.associateMessageProcessor( createDmProcessor( dmMessages ));
		dmClient.switchMessagingClient( getMessagingIp(), getMessagingUsername(), getMessagingPassword(), getMessagingFactoryName());
		this.clients.add( dmClient );

		ReconfigurableClientAgent agentClient = new ReconfigurableClientAgent();
		agentClient.associateMessageProcessor( createAgentProcessor( agentMessages ));
		agentClient.setApplicationName( app.getName());
		agentClient.setRootInstanceName( rootInstance.getName());
		agentClient.switchMessagingClient( getMessagingIp(), getMessagingUsername(), getMessagingPassword(), getMessagingFactoryName());
		this.clients.add( agentClient );

		// No message yet
		Thread.sleep( DELAY );
		Assert.assertEquals( 0, dmMessages.size());
		Assert.assertEquals( 0, agentMessages.size());

		// The agent is already listening to the DM.
		dmClient.sendMessageToAgent( app, rootInstance, new MsgCmdSetRootInstance( rootInstance ));
		Thread.sleep( DELAY );
		Assert.assertEquals( 1, agentMessages.size());
		Assert.assertEquals( MsgCmdSetRootInstance.class, agentMessages.get( 0 ).getClass());

		agentClient.listenToTheDm( ListenerCommand.START );
		dmClient.sendMessageToAgent( app, rootInstance, new MsgCmdRemoveInstance( rootInstance ));
		Thread.sleep( DELAY );
		Assert.assertEquals( 2, agentMessages.size());
		Assert.assertEquals( MsgCmdSetRootInstance.class, agentMessages.get( 0 ).getClass());
		Assert.assertEquals( MsgCmdRemoveInstance.class, agentMessages.get( 1 ).getClass());

		// The agent sends a message to the DM
		Assert.assertEquals( 0, dmMessages.size());
		agentClient.sendMessageToTheDm( new MsgNotifHeartbeat( app.getName(), rootInstance, "192.168.1.45" ));
		Thread.sleep( DELAY );
		Assert.assertEquals( 0, dmMessages.size());

		dmClient.listenToAgentMessages( app, ListenerCommand.START );
		agentClient.sendMessageToTheDm( new MsgNotifMachineDown( app.getName(), rootInstance ));
		Thread.sleep( DELAY );
		Assert.assertEquals( 1, dmMessages.size());
		Assert.assertEquals( MsgNotifMachineDown.class, dmMessages.get( 0 ).getClass());

		// The DM sends another message
		dmClient.sendMessageToAgent( app, rootInstance, new MsgCmdChangeInstanceState( rootInstance, InstanceStatus.DEPLOYED_STARTED ));
		Thread.sleep( DELAY );
		Assert.assertEquals( 3, agentMessages.size());
		Assert.assertEquals( MsgCmdSetRootInstance.class, agentMessages.get( 0 ).getClass());
		Assert.assertEquals( MsgCmdRemoveInstance.class, agentMessages.get( 1 ).getClass());
		Assert.assertEquals( MsgCmdChangeInstanceState.class, agentMessages.get( 2 ).getClass());

		// The agent stops listening the DM
		agentClient.listenToTheDm( ListenerCommand.STOP );

		// The agent is not listening to the DM anymore.
		// With RabbitMQ, the next invocation will result in a NO_ROUTE error in the channel.
		dmClient.sendMessageToAgent( app, rootInstance, new MsgCmdChangeInstanceState( rootInstance, InstanceStatus.DEPLOYED_STARTED ));
		Thread.sleep( DELAY );
		Assert.assertEquals( 3, agentMessages.size());
		Thread.sleep( DELAY );
		Assert.assertEquals( 3, agentMessages.size());
		Thread.sleep( DELAY );
		Assert.assertEquals( 3, agentMessages.size());

		// The DM stops listening the agent
		dmClient.listenToAgentMessages( app, ListenerCommand.STOP );
		agentClient.sendMessageToTheDm( new MsgNotifHeartbeat( app.getName(), rootInstance, "192.168.1.47" ));
		Thread.sleep( DELAY );
		Assert.assertEquals( 1, dmMessages.size());
		Thread.sleep( DELAY );
		Assert.assertEquals( 1, dmMessages.size());
		Thread.sleep( DELAY );
		Assert.assertEquals( 1, dmMessages.size());
	}


	/**
	 * Makes sure messages go to the right agent.
	 * <p>
	 * This is about messages routing.
	 * </p>
	 *
	 * @throws Exception
	 */
	public void testExchangesBetweenTheDmAndThreeAgents() throws Exception {

		// Initialize everything
		// 1 DM, 2 agents (root1 and root2) for application app1 and 1 agent (root) for app2.
		Application app1 = new Application( "app1" );
		Application app2 = new Application( "app2" );
		Instance app1_root1 = new Instance( "root1" );
		Instance app1_root2 = new Instance( "root2" );
		Instance app2_root = new Instance( "root" );

		List<Message> dmMessages = new ArrayList<Message> ();
		ReconfigurableClientDm dmClient = new ReconfigurableClientDm();
		dmClient.associateMessageProcessor( createDmProcessor( dmMessages ));
		dmClient.switchMessagingClient( getMessagingIp(), getMessagingUsername(), getMessagingPassword(), getMessagingFactoryName());
		this.clients.add( dmClient );

		List<Message> agentMessages_11 = new ArrayList<Message> ();
		ReconfigurableClientAgent agentClient_11 = new ReconfigurableClientAgent();
		agentClient_11.associateMessageProcessor( createAgentProcessor( agentMessages_11 ));
		agentClient_11.setApplicationName( app1.getName());
		agentClient_11.setRootInstanceName( app1_root1.getName());
		agentClient_11.switchMessagingClient( getMessagingIp(), getMessagingUsername(), getMessagingPassword(), getMessagingFactoryName());
		this.clients.add( agentClient_11 );

		List<Message> agentMessages_12 = new ArrayList<Message> ();
		ReconfigurableClientAgent agentClient_12 = new ReconfigurableClientAgent();
		agentClient_12.associateMessageProcessor( createAgentProcessor( agentMessages_12 ));
		agentClient_12.setApplicationName( app1.getName());
		agentClient_12.setRootInstanceName( app1_root2.getName());
		agentClient_12.switchMessagingClient( getMessagingIp(), getMessagingUsername(), getMessagingPassword(), getMessagingFactoryName());
		this.clients.add( agentClient_12 );

		List<Message> agentMessages_2 = new ArrayList<Message> ();
		ReconfigurableClientAgent agentClient_2 = new ReconfigurableClientAgent();
		agentClient_2.associateMessageProcessor( createAgentProcessor( agentMessages_2 ));
		agentClient_2.setApplicationName( app2.getName());
		agentClient_2.setRootInstanceName( app2_root.getName());
		agentClient_2.switchMessagingClient( getMessagingIp(), getMessagingUsername(), getMessagingPassword(), getMessagingFactoryName());
		this.clients.add( agentClient_2 );

		// Everybody starts listening...
		agentClient_11.listenToTheDm( ListenerCommand.START );
		agentClient_12.listenToTheDm( ListenerCommand.START );
		agentClient_2.listenToTheDm( ListenerCommand.START );

		// The DM sends messages
		dmClient.sendMessageToAgent( app1, app1_root1, new MsgCmdSetRootInstance( app1_root1 ));
		dmClient.sendMessageToAgent( app2, app2_root, new MsgCmdSetRootInstance( app2_root ));
		dmClient.sendMessageToAgent( app1, app1_root2, new MsgCmdSetRootInstance( app1_root2 ));
		dmClient.sendMessageToAgent( app2, app2_root, new MsgCmdRemoveInstance( app2_root ));
		dmClient.sendMessageToAgent( app2, app2_root, new MsgCmdChangeInstanceState( app2_root, InstanceStatus.DEPLOYED_STOPPED ));
		dmClient.sendMessageToAgent( app1, app1_root2, new MsgCmdRemoveInstance( app1_root2 ));
		dmClient.sendMessageToAgent( app1, app1_root2, new MsgCmdRemoveInstance( app1_root2 ));
		dmClient.sendMessageToAgent( app1, app1_root2, new MsgCmdChangeInstanceState( app1_root2, InstanceStatus.NOT_DEPLOYED ));
		dmClient.sendMessageToAgent( app1, app1_root1, new MsgCmdRemoveInstance( app1_root1 ));
		dmClient.sendMessageToAgent( app1, app1_root1, new MsgCmdSetRootInstance( app1_root1 ));

		// Check what was received
		Thread.sleep( DELAY );

		Assert.assertEquals( 3, agentMessages_11.size());
		Assert.assertEquals( MsgCmdSetRootInstance.class, agentMessages_11.get( 0 ).getClass());
		Assert.assertEquals( MsgCmdRemoveInstance.class, agentMessages_11.get( 1 ).getClass());
		Assert.assertEquals( MsgCmdSetRootInstance.class, agentMessages_11.get( 2 ).getClass());

		Assert.assertEquals( 4, agentMessages_12.size());
		Assert.assertEquals( MsgCmdSetRootInstance.class, agentMessages_12.get( 0 ).getClass());
		Assert.assertEquals( MsgCmdRemoveInstance.class, agentMessages_12.get( 1 ).getClass());
		Assert.assertEquals( MsgCmdRemoveInstance.class, agentMessages_12.get( 2 ).getClass());
		Assert.assertEquals( MsgCmdChangeInstanceState.class, agentMessages_12.get( 3 ).getClass());

		Assert.assertEquals( 3, agentMessages_2.size());
		Assert.assertEquals( MsgCmdSetRootInstance.class, agentMessages_2.get( 0 ).getClass());
		Assert.assertEquals( MsgCmdRemoveInstance.class, agentMessages_2.get( 1 ).getClass());
		Assert.assertEquals( MsgCmdChangeInstanceState.class, agentMessages_2.get( 2 ).getClass());
	}


	/**
	 * Makes sure exports are exchanged correctly between agents.
	 * @throws Exception
	 */
	public void testExportsBetweenAgents() throws Exception {

		// 3 agents (tomcat, mysql, apache) for application app1 and 1 agent (root) for app2.
		// This last one should not receive anything!
		Application app1 = new Application( "app1" );
		Application app2 = new Application( "app2" );

		Component tomcatComponent = new Component( "Tomcat" );
		tomcatComponent.getExportedVariables().put( "Tomcat.ip", "localhost" );
		tomcatComponent.getExportedVariables().put( "Tomcat.port", "8080" );
		tomcatComponent.getImportedVariables().put( "MySQL.port", Boolean.FALSE );
		tomcatComponent.getImportedVariables().put( "MySQL.ip", Boolean.FALSE );
		Instance tomcat = new Instance( "tomcat" ).component( tomcatComponent );

		Component mysqlComponent = new Component( "MySQL" );
		mysqlComponent.getExportedVariables().put( "MySQL.port", "3306" );
		mysqlComponent.getExportedVariables().put( "MySQL.ip", "192.168.1.15" );
		Instance mysql = new Instance( "mysql" ).component( mysqlComponent );

		Component apacheComponent = new Component( "Apache" );
		apacheComponent.getExportedVariables().put( "Apache.ip", "apache.roboconf.net" );
		apacheComponent.getImportedVariables().put( "Tomcat.port", Boolean.FALSE );
		apacheComponent.getImportedVariables().put( "Tomcat.ip", Boolean.FALSE );
		Instance apache = new Instance( "apache" ).component( apacheComponent );

		// This one is a good candidate to receive something when others publish something.
		// Except it is not in the same application.
		Component otherComponent = new Component( "other" );
		apacheComponent.getImportedVariables().put( "Tomcat.port", Boolean.FALSE );
		apacheComponent.getImportedVariables().put( "Tomcat.ip", Boolean.FALSE );
		apacheComponent.getImportedVariables().put( "Mongo.ip", Boolean.FALSE );
		mysqlComponent.getExportedVariables().put( "MySQL.port", "3306" );
		mysqlComponent.getExportedVariables().put( "MySQL.ip", "192.168.1.15" );
		Instance other = new Instance( "other" ).component( otherComponent );

		// Initialize the messaging
		List<Message> tomcatMessages = new ArrayList<Message> ();
		ReconfigurableClientAgent tomcatClient = new ReconfigurableClientAgent();
		tomcatClient.associateMessageProcessor( createAgentProcessor( tomcatMessages ));
		tomcatClient.setApplicationName( app1.getName());
		tomcatClient.setRootInstanceName( tomcat.getName());
		tomcatClient.switchMessagingClient( getMessagingIp(), getMessagingUsername(), getMessagingPassword(), getMessagingFactoryName());
		this.clients.add( tomcatClient );

		List<Message> apacheMessages = new ArrayList<Message> ();
		ReconfigurableClientAgent apacheClient = new ReconfigurableClientAgent();
		apacheClient.associateMessageProcessor( createAgentProcessor( apacheMessages ));
		apacheClient.setApplicationName( app1.getName());
		apacheClient.setRootInstanceName( apache.getName());
		apacheClient.switchMessagingClient( getMessagingIp(), getMessagingUsername(), getMessagingPassword(), getMessagingFactoryName());
		this.clients.add( apacheClient );

		List<Message> mySqlMessages = new ArrayList<Message> ();
		ReconfigurableClientAgent mySqlClient = new ReconfigurableClientAgent();
		mySqlClient.associateMessageProcessor( createAgentProcessor( mySqlMessages ));
		mySqlClient.setApplicationName( app1.getName());
		mySqlClient.setRootInstanceName( mysql.getName());
		mySqlClient.switchMessagingClient( getMessagingIp(), getMessagingUsername(), getMessagingPassword(), getMessagingFactoryName());
		this.clients.add( mySqlClient );

		List<Message> otherMessages = new ArrayList<Message> ();
		ReconfigurableClientAgent otherClient = new ReconfigurableClientAgent();
		otherClient.associateMessageProcessor( createAgentProcessor( otherMessages ));
		otherClient.setApplicationName( app2.getName());
		otherClient.setRootInstanceName( other.getName());
		otherClient.switchMessagingClient( getMessagingIp(), getMessagingUsername(), getMessagingPassword(), getMessagingFactoryName());
		this.clients.add( otherClient );

		// OK, let's start.
		// MySQL publishes its exports but nobody is listening.
		mySqlClient.publishExports( mysql );
		Thread.sleep( DELAY );
		Assert.assertEquals( 0, mySqlMessages.size());
		Assert.assertEquals( 0, apacheMessages.size());
		Assert.assertEquals( 0, tomcatMessages.size());
		Assert.assertEquals( 0, otherMessages.size());

		// Tomcat and Other are now listening.
		// Let's re-export MySQL.
		otherClient.listenToExportsFromOtherAgents( ListenerCommand.START, other );
		tomcatClient.listenToExportsFromOtherAgents( ListenerCommand.START, tomcat );
		mySqlClient.publishExports( mysql );
		Thread.sleep( DELAY );

		Assert.assertEquals( 0, mySqlMessages.size());
		Assert.assertEquals( 0, apacheMessages.size());
		Assert.assertEquals( 0, otherMessages.size());
		Assert.assertEquals( 1, tomcatMessages.size());
		Assert.assertEquals( MsgCmdAddImport.class, tomcatMessages.get( 0 ).getClass());

		MsgCmdAddImport msg = (MsgCmdAddImport) tomcatMessages.get( 0 );
		Assert.assertEquals( "MySQL", msg.getComponentOrFacetName());
		Assert.assertEquals( "/mysql", msg.getAddedInstancePath());
		Assert.assertEquals( 2, msg.getExportedVariables().size());
		Assert.assertTrue( msg.getExportedVariables().containsKey( "MySQL.port" ));
		Assert.assertTrue( msg.getExportedVariables().containsKey( "MySQL.ip" ));

		// Let's publish an unknown facet. Nobody should receive it.
		mySqlClient.publishExports( mysql, "an-unknown-facet-or-component-name" );

		Assert.assertEquals( 0, mySqlMessages.size());
		Assert.assertEquals( 0, apacheMessages.size());
		Assert.assertEquals( 0, otherMessages.size());
		Assert.assertEquals( 1, tomcatMessages.size());

		// Other publishes its exports.
		// Tomcat is not supposed to receive it.
		otherClient.publishExports( other );
		Thread.sleep( DELAY );

		Assert.assertEquals( 0, mySqlMessages.size());
		Assert.assertEquals( 0, apacheMessages.size());
		Assert.assertEquals( 0, otherMessages.size());
		Assert.assertEquals( 1, tomcatMessages.size());
		Assert.assertEquals( MsgCmdAddImport.class, tomcatMessages.get( 0 ).getClass());

		// Everybody is listening...
		// Tomcat publishes its exports.
		apacheClient.listenToExportsFromOtherAgents( ListenerCommand.START, apache );
		mySqlClient.listenToExportsFromOtherAgents( ListenerCommand.START, mysql );
		tomcatClient.publishExports( tomcat );
		Thread.sleep( DELAY );

		Assert.assertEquals( 0, mySqlMessages.size());
		Assert.assertEquals( 0, otherMessages.size());
		Assert.assertEquals( 1, tomcatMessages.size());
		Assert.assertEquals( 1, apacheMessages.size());
		Assert.assertEquals( MsgCmdAddImport.class, apacheMessages.get( 0 ).getClass());

		msg = (MsgCmdAddImport) apacheMessages.get( 0 );
		Assert.assertEquals( "Tomcat", msg.getComponentOrFacetName());
		Assert.assertEquals( "/tomcat", msg.getAddedInstancePath());
		Assert.assertEquals( 2, msg.getExportedVariables().size());
		Assert.assertTrue( msg.getExportedVariables().containsKey( "Tomcat.port" ));
		Assert.assertTrue( msg.getExportedVariables().containsKey( "Tomcat.ip" ));

		// MySQL publishes (again) its exports
		mySqlClient.publishExports( mysql );
		Thread.sleep( DELAY );

		Assert.assertEquals( 0, mySqlMessages.size());
		Assert.assertEquals( 0, otherMessages.size());
		Assert.assertEquals( 1, apacheMessages.size());
		Assert.assertEquals( 2, tomcatMessages.size());
		Assert.assertEquals( MsgCmdAddImport.class, tomcatMessages.get( 0 ).getClass());
		Assert.assertEquals( MsgCmdAddImport.class, tomcatMessages.get( 1 ).getClass());

		msg = (MsgCmdAddImport) tomcatMessages.get( 1 );
		Assert.assertEquals( "MySQL", msg.getComponentOrFacetName());
		Assert.assertEquals( "/mysql", msg.getAddedInstancePath());
		Assert.assertEquals( 2, msg.getExportedVariables().size());
		Assert.assertTrue( msg.getExportedVariables().containsKey( "MySQL.port" ));
		Assert.assertTrue( msg.getExportedVariables().containsKey( "MySQL.ip" ));

		// MySQL un-publishes its exports
		mySqlClient.unpublishExports( mysql );
		Thread.sleep( DELAY );

		Assert.assertEquals( 0, mySqlMessages.size());
		Assert.assertEquals( 0, otherMessages.size());
		Assert.assertEquals( 1, apacheMessages.size());
		Assert.assertEquals( 3, tomcatMessages.size());
		Assert.assertEquals( MsgCmdAddImport.class, tomcatMessages.get( 0 ).getClass());
		Assert.assertEquals( MsgCmdAddImport.class, tomcatMessages.get( 1 ).getClass());
		Assert.assertEquals( MsgCmdRemoveImport.class, tomcatMessages.get( 2 ).getClass());

		MsgCmdRemoveImport newMsg = (MsgCmdRemoveImport) tomcatMessages.get( 2 );
		Assert.assertEquals( "MySQL", newMsg.getComponentOrFacetName());
		Assert.assertEquals( "/mysql", newMsg.getRemovedInstancePath());

		// MySQL publishes (again) its exports
		// But this time, Tomcat does not listen anymore
		tomcatClient.listenToExportsFromOtherAgents( ListenerCommand.STOP, tomcat );
		mySqlClient.publishExports( mysql );
		Thread.sleep( DELAY );

		Assert.assertEquals( 0, mySqlMessages.size());
		Assert.assertEquals( 0, otherMessages.size());
		Assert.assertEquals( 1, apacheMessages.size());
		Assert.assertEquals( 3, tomcatMessages.size());
	}


	/**
	 * Makes sure exports requests are exchanged correctly between agents.
	 * @throws Exception
	 */
	public void testExportsRequestsBetweenAgents() throws Exception {

		// 3 agents (tomcat, mysql, apache) for application app1 and 1 agent (root) for app2.
		// This last one should not receive anything!
		Application app1 = new Application( "app1" );
		Application app2 = new Application( "app2" );

		Component tomcatComponent = new Component( "Tomcat" );
		tomcatComponent.getExportedVariables().put( "Tomcat.ip", "localhost" );
		tomcatComponent.getExportedVariables().put( "Tomcat.port", "8080" );
		tomcatComponent.getImportedVariables().put( "MySQL.port", Boolean.FALSE );
		tomcatComponent.getImportedVariables().put( "MySQL.ip", Boolean.FALSE );
		Instance tomcat = new Instance( "tomcat" ).component( tomcatComponent );

		Component mysqlComponent = new Component( "MySQL" );
		mysqlComponent.getExportedVariables().put( "MySQL.port", "3306" );
		mysqlComponent.getExportedVariables().put( "MySQL.ip", "192.168.1.15" );
		Instance mysql = new Instance( "mysql" ).component( mysqlComponent );

		Component apacheComponent = new Component( "Apache" );
		apacheComponent.getExportedVariables().put( "Apache.ip", "apache.roboconf.net" );
		apacheComponent.getImportedVariables().put( "Tomcat.port", Boolean.FALSE );
		apacheComponent.getImportedVariables().put( "Tomcat.ip", Boolean.FALSE );
		Instance apache = new Instance( "apache" ).component( apacheComponent );

		// This one is a good candidate to receive something when others publish something.
		// Except it is not in the same application.
		Component otherComponent = new Component( "other" );
		apacheComponent.getImportedVariables().put( "Tomcat.port", Boolean.FALSE );
		apacheComponent.getImportedVariables().put( "Tomcat.ip", Boolean.FALSE );
		apacheComponent.getImportedVariables().put( "Mongo.ip", Boolean.FALSE );
		mysqlComponent.getExportedVariables().put( "MySQL.port", "3306" );
		mysqlComponent.getExportedVariables().put( "MySQL.ip", "192.168.1.15" );
		Instance other = new Instance( "other" ).component( otherComponent );

		// Initialize the messaging
		List<Message> tomcatMessages = new ArrayList<Message> ();
		ReconfigurableClientAgent tomcatClient = new ReconfigurableClientAgent();
		tomcatClient.associateMessageProcessor( createAgentProcessor( tomcatMessages ));
		tomcatClient.setApplicationName( app1.getName());
		tomcatClient.setRootInstanceName( tomcat.getName());
		tomcatClient.switchMessagingClient( getMessagingIp(), getMessagingUsername(), getMessagingPassword(), getMessagingFactoryName());
		this.clients.add( tomcatClient );

		List<Message> apacheMessages = new ArrayList<Message> ();
		ReconfigurableClientAgent apacheClient = new ReconfigurableClientAgent();
		apacheClient.associateMessageProcessor( createAgentProcessor( apacheMessages ));
		apacheClient.setApplicationName( app1.getName());
		apacheClient.setRootInstanceName( apache.getName());
		apacheClient.switchMessagingClient( getMessagingIp(), getMessagingUsername(), getMessagingPassword(), getMessagingFactoryName());
		this.clients.add( apacheClient );

		List<Message> mySqlMessages = new ArrayList<Message> ();
		ReconfigurableClientAgent mySqlClient = new ReconfigurableClientAgent();
		mySqlClient.associateMessageProcessor( createAgentProcessor( mySqlMessages ));
		mySqlClient.setApplicationName( app1.getName());
		mySqlClient.setRootInstanceName( mysql.getName());
		mySqlClient.switchMessagingClient( getMessagingIp(), getMessagingUsername(), getMessagingPassword(), getMessagingFactoryName());
		this.clients.add( mySqlClient );

		List<Message> otherMessages = new ArrayList<Message> ();
		ReconfigurableClientAgent otherClient = new ReconfigurableClientAgent();
		otherClient.associateMessageProcessor( createAgentProcessor( otherMessages ));
		otherClient.setApplicationName( app2.getName());
		otherClient.setRootInstanceName( other.getName());
		otherClient.switchMessagingClient( getMessagingIp(), getMessagingUsername(), getMessagingPassword(), getMessagingFactoryName());
		this.clients.add( otherClient );

		// OK, let's start.
		// Tomcat requests MySQL exports but MySQL is not listening
		tomcatClient.requestExportsFromOtherAgents( tomcat );
		Thread.sleep( DELAY );
		Assert.assertEquals( 0, mySqlMessages.size());
		Assert.assertEquals( 0, apacheMessages.size());
		Assert.assertEquals( 0, tomcatMessages.size());
		Assert.assertEquals( 0, otherMessages.size());

		// Now, Other and MySQL are listening.
		// Only MySQL should receive it (Other is in another application).
		otherClient.listenToRequestsFromOtherAgents( ListenerCommand.START, other );
		mySqlClient.listenToRequestsFromOtherAgents( ListenerCommand.START, mysql );
		tomcatClient.requestExportsFromOtherAgents( tomcat );
		Thread.sleep( DELAY );

		Assert.assertEquals( 0, apacheMessages.size());
		Assert.assertEquals( 0, tomcatMessages.size());
		Assert.assertEquals( 0, otherMessages.size());
		Assert.assertEquals( 1, mySqlMessages.size());
		Assert.assertEquals( MsgCmdRequestImport.class, mySqlMessages.get( 0 ).getClass());

		MsgCmdRequestImport msg = (MsgCmdRequestImport) mySqlMessages.get( 0 );
		Assert.assertEquals( "MySQL", msg.getComponentOrFacetName());

		// Now, let's do it again but MySQL stops listening.
		mySqlClient.listenToRequestsFromOtherAgents( ListenerCommand.STOP, mysql );
		tomcatClient.requestExportsFromOtherAgents( tomcat );
		Thread.sleep( DELAY );

		Assert.assertEquals( 0, apacheMessages.size());
		Assert.assertEquals( 0, tomcatMessages.size());
		Assert.assertEquals( 0, otherMessages.size());
		Assert.assertEquals( 1, mySqlMessages.size());

		// Other requires exports from others.
		otherClient.requestExportsFromOtherAgents( other );
		Thread.sleep( DELAY );

		Assert.assertEquals( 0, apacheMessages.size());
		Assert.assertEquals( 0, tomcatMessages.size());
		Assert.assertEquals( 0, otherMessages.size());
		Assert.assertEquals( 1, mySqlMessages.size());
	}


	/**
	 * Tests exchanges between sibling agents and several variable groups.
	 * @throws Exception
	 */
	public void testExportsBetweenSiblingAgents() throws Exception {

		// The model
		Application app = new Application( "app" );

		Component component = new Component( "Component" );
		component.getExportedVariables().put( "Component.ip", "localhost" );
		component.getExportedVariables().put( "Component.port", "8080" );
		component.getExportedVariables().put( "facet.data", "hello" );

		component.getImportedVariables().put( "Component.port", Boolean.TRUE );
		component.getImportedVariables().put( "Component.ip", Boolean.TRUE );
		component.getImportedVariables().put( "facet.data", Boolean.TRUE );

		Instance instance1 = new Instance( "instance1" ).component( component );
		Instance instance2 = new Instance( "instance2" ).component( component );

		// Initialize the messaging
		List<Message> messages1 = new ArrayList<Message> ();
		ReconfigurableClientAgent client1 = new ReconfigurableClientAgent();
		client1.associateMessageProcessor( createAgentProcessor( messages1 ));
		client1.setApplicationName( app.getName());
		client1.setRootInstanceName( instance1.getName());
		client1.switchMessagingClient( getMessagingIp(), getMessagingUsername(), getMessagingPassword(), getMessagingFactoryName());
		this.clients.add( client1 );

		List<Message> messages2 = new ArrayList<Message> ();
		ReconfigurableClientAgent client2 = new ReconfigurableClientAgent();
		client2.associateMessageProcessor( createAgentProcessor( messages2 ));
		client2.setApplicationName( app.getName());
		client2.setRootInstanceName( instance2.getName());
		client2.switchMessagingClient( getMessagingIp(), getMessagingUsername(), getMessagingPassword(), getMessagingFactoryName());
		this.clients.add( client2 );

		// OK, let's start.
		// Instance1 is alone.
		client1.requestExportsFromOtherAgents( instance1 );
		Thread.sleep( DELAY );
		Assert.assertEquals( 0, messages1.size());
		Assert.assertEquals( 0, messages2.size());

		// Now, instance2 is listening.
		client2.listenToRequestsFromOtherAgents( ListenerCommand.START, instance2 );
		client1.requestExportsFromOtherAgents( instance1 );
		Thread.sleep( DELAY );
		Assert.assertEquals( 0, messages1.size());
		Assert.assertEquals( 2, messages2.size());
		Assert.assertEquals( MsgCmdRequestImport.class, messages2.get( 0 ).getClass());
		Assert.assertEquals( MsgCmdRequestImport.class, messages2.get( 1 ).getClass());

		String facet1 = ((MsgCmdRequestImport) messages2.get( 0 )).getComponentOrFacetName();
		String facet2 = ((MsgCmdRequestImport) messages2.get( 1 )).getComponentOrFacetName();
		Assert.assertNotSame( facet1, facet2 );
		Assert.assertTrue( facet1.equals( "Component" ) || facet1.equals( "facet" ));
		Assert.assertTrue( facet2.equals( "Component" ) || facet2.equals( "facet" ));

		// instance1 is now listening
		// instance2 stops listening
		// instance1 should receive the notification it has sent. It will be up to the agent to ignore it.
		client2.listenToRequestsFromOtherAgents( ListenerCommand.STOP, instance2 );
		client1.listenToRequestsFromOtherAgents( ListenerCommand.START, instance1 );
		client1.requestExportsFromOtherAgents( instance1 );
		Thread.sleep( DELAY );

		Assert.assertEquals( 2, messages2.size());
		Assert.assertEquals( 2, messages1.size());
		Assert.assertEquals( MsgCmdRequestImport.class, messages1.get( 0 ).getClass());
		Assert.assertEquals( MsgCmdRequestImport.class, messages1.get( 1 ).getClass());

		facet1 = ((MsgCmdRequestImport) messages1.get( 0 )).getComponentOrFacetName();
		facet2 = ((MsgCmdRequestImport) messages1.get( 1 )).getComponentOrFacetName();
		Assert.assertNotSame( facet1, facet2 );
		Assert.assertTrue( facet1.equals( "Component" ) || facet1.equals( "facet" ));
		Assert.assertTrue( facet2.equals( "Component" ) || facet2.equals( "facet" ));
	}


	protected abstract AbstractMessageProcessor<IDmClient> createDmProcessor( List<Message> dmMessages );
	protected abstract AbstractMessageProcessor<IAgentClient> createAgentProcessor( List<Message> agentMessages );
	protected abstract String getMessagingIp();
	protected abstract String getMessagingUsername();
	protected abstract String getMessagingPassword();
	protected abstract String getMessagingFactoryName();
}