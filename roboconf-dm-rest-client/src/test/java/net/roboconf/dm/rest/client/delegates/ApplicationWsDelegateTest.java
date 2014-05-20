/**
 * Copyright 2014 Linagora, Université Joseph Fourier
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

package net.roboconf.dm.rest.client.delegates;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.Response.Status;

import junit.framework.Assert;
import net.roboconf.core.actions.ApplicationAction;
import net.roboconf.core.model.helpers.ComponentHelpers;
import net.roboconf.core.model.helpers.InstanceHelpers;
import net.roboconf.core.model.runtime.Component;
import net.roboconf.core.model.runtime.Instance;
import net.roboconf.core.utils.Utils;
import net.roboconf.dm.internal.TestApplication;
import net.roboconf.dm.internal.TestIaasResolver;
import net.roboconf.dm.internal.TestMessageServerClient;
import net.roboconf.dm.internal.TestMessageServerClient.DmMessageServerClientFactory;
import net.roboconf.dm.management.ManagedApplication;
import net.roboconf.dm.management.Manager;
import net.roboconf.dm.rest.client.WsClient;
import net.roboconf.dm.rest.client.exceptions.ApplicationException;
import net.roboconf.dm.rest.client.test.RestTestUtils;
import net.roboconf.dm.utils.ResourceUtils;
import net.roboconf.messaging.client.MessageServerClientFactory;

import org.junit.Before;
import org.junit.Test;

import com.sun.jersey.test.framework.AppDescriptor;
import com.sun.jersey.test.framework.JerseyTest;
import com.sun.jersey.test.framework.spi.container.TestContainerFactory;
import com.sun.jersey.test.framework.spi.container.grizzly2.web.GrizzlyWebTestContainerFactory;

/**
 * @author Vincent Zurczak - Linagora
 */
public class ApplicationWsDelegateTest extends JerseyTest {

	private TestApplication app;
	private WsClient client;


	@Override
	protected AppDescriptor configure() {
		return RestTestUtils.buildTestDescriptor();
	}


	@Override
    public TestContainerFactory getTestContainerFactory() {
        return new GrizzlyWebTestContainerFactory();
    }


	@Before
	public void resetManager() {
		Manager.INSTANCE.cleanUpAll();
		Manager.INSTANCE.getAppNameToManagedApplication().clear();

		this.app = new TestApplication();
		Manager.INSTANCE.getAppNameToManagedApplication().put( this.app.getName(), new ManagedApplication( this.app, null ));
		Manager.INSTANCE.setIaasResolver( new TestIaasResolver());
		Manager.INSTANCE.setMessagingClientFactory( new DmMessageServerClientFactory());

		this.client = RestTestUtils.buildWsClient();
	}


	@Test( expected = IllegalArgumentException.class )
	public void testPerform_illegalArgument() throws Exception {
		this.client.getApplicationDelegate().perform( this.app.getName(), ApplicationAction.deploy, null, false );
	}


	@Test( expected = ApplicationException.class )
	public void testPerform_inexistingApplication() throws Exception {
		this.client.getApplicationDelegate().perform( "inexisting", ApplicationAction.deploy, null, true );
	}


	@Test( expected = ApplicationException.class )
	public void testPerform_inexistingInstance() throws Exception {
		this.client.getApplicationDelegate().perform( this.app.getName(), ApplicationAction.deploy, "/bip/bip", false );
	}


	@Test( expected = ApplicationException.class )
	public void testPerform_invalidAction() throws Exception {
		this.client.getApplicationDelegate().perform( this.app.getName(), null, null, true );
	}


	@Test
	public void testPerform_notConnected() throws Exception {

		Manager.INSTANCE.setMessagingClientFactory( new MessageServerClientFactory());
		Assert.assertFalse( Manager.INSTANCE.isConnectedToTheMessagingServer());

		try {
			this.client.getApplicationDelegate().perform(
					this.app.getName(),
					ApplicationAction.deploy,
					InstanceHelpers.computeInstancePath( this.app.getMySqlVm()),
					false );

			Assert.fail( "An exception was expected." );

		} catch( ApplicationException e ) {
			Assert.assertEquals( Status.FORBIDDEN.getStatusCode(), e.getResponseStatus());
		}
	}


	@Test
	public void testPerform_deploy_success() throws Exception {

		// The interest of this method is to check that URLs
		// and instance paths are correctly handled by the DM.
		this.client.getApplicationDelegate().perform(
				this.app.getName(),
				ApplicationAction.deploy,
				InstanceHelpers.computeInstancePath( this.app.getMySqlVm()),
				false );
	}


	@Test
	public void testPerform_deployRoots_success() throws Exception {

		// Create temporary directories
		final File rootDir = new File( System.getProperty( "java.io.tmpdir" ), "roboconf_app" );
		if( ! rootDir.exists()
				&& ! rootDir.mkdir())
			throw new IOException( "Failed to create a root directory for tests." );

		for( Instance inst : InstanceHelpers.getAllInstances( this.app )) {
			File f = ResourceUtils.findInstanceResourcesDirectory( rootDir, inst );
			if( ! f.exists()
					&& ! f.mkdirs())
				throw new IOException( "Failed to create a directory for tests. " + f.getAbsolutePath());
		}

		// The interest of this method is to check that URLs
		// and instance paths are correctly handled by the DM.
		Manager.INSTANCE.getAppNameToManagedApplication().put( this.app.getName(), new ManagedApplication( this.app, rootDir ));
		TestMessageServerClient msgClient = (TestMessageServerClient) Manager.INSTANCE.getMessagingClient();
		try {
			Assert.assertEquals( 0, msgClient.sentMessages.size());
			this.client.getApplicationDelegate().perform( this.app.getName(), ApplicationAction.deploy, null, true );

			int expected = InstanceHelpers.getAllInstances( this.app ).size() - this.app.getRootInstances().size();
			Assert.assertEquals( expected, msgClient.sentMessages.size());

		} finally {
			Utils.deleteFilesRecursively( rootDir );
		}
	}


	@Test
	public void testListChildrenInstances() throws Exception {

		List<Instance> instances = this.client.getApplicationDelegate().listChildrenInstances( this.app.getName(), "/bip/bip", false );
		Assert.assertEquals( 0, instances.size());

		instances = this.client.getApplicationDelegate().listChildrenInstances( this.app.getName(), null, false );
		Assert.assertEquals( this.app.getRootInstances().size(), instances.size());

		instances = this.client.getApplicationDelegate().listChildrenInstances( this.app.getName(), null, true );
		Assert.assertEquals( InstanceHelpers.getAllInstances( this.app ).size(), instances.size());

		instances = this.client.getApplicationDelegate().listChildrenInstances( this.app.getName(), InstanceHelpers.computeInstancePath( this.app.getTomcatVm()), true );
		Assert.assertEquals( 2, instances.size());
	}


	@Test
	public void testListAllComponents() throws Exception {

		List<Component> components = this.client.getApplicationDelegate().listAllComponents( "inexisting" );
		Assert.assertEquals( 0, components.size());

		components = this.client.getApplicationDelegate().listAllComponents( this.app.getName());
		Assert.assertEquals( ComponentHelpers.findAllComponents( this.app ).size(), components.size());
	}


	@Test
	public void testFindPossibleComponentChildren() throws Exception {

		List<Component> components = this.client.getApplicationDelegate().findPossibleComponentChildren( "inexisting", "" );
		Assert.assertEquals( 0, components.size());

		components = this.client.getApplicationDelegate().findPossibleComponentChildren( this.app.getName(), "inexisting-component" );
		Assert.assertEquals( 0, components.size());

		components = this.client.getApplicationDelegate().findPossibleComponentChildren( this.app.getName(), null );
		Assert.assertEquals( 1, components.size());
		Assert.assertTrue( components.contains( this.app.getMySqlVm().getComponent()));

		components = this.client.getApplicationDelegate().findPossibleComponentChildren( this.app.getName(), InstanceHelpers.computeInstancePath( this.app.getMySqlVm()));
		Assert.assertEquals( 2, components.size());
		Assert.assertTrue( components.contains( this.app.getMySql().getComponent()));
		Assert.assertTrue( components.contains( this.app.getTomcat().getComponent()));
	}


	@Test
	public void testFindPossibleParentInstances() throws Exception {

		List<String> instancePaths = this.client.getApplicationDelegate().findPossibleParentInstances( "inexisting", "my-comp" );
		Assert.assertEquals( 0, instancePaths.size());

		instancePaths = this.client.getApplicationDelegate().findPossibleParentInstances( this.app.getName(), "my-comp" );
		Assert.assertEquals( 0, instancePaths.size());

		instancePaths = this.client.getApplicationDelegate().findPossibleParentInstances( this.app.getName(), this.app.getTomcat().getComponent().getName());
		Assert.assertEquals( 2, instancePaths.size());
		Assert.assertTrue( instancePaths.contains( InstanceHelpers.computeInstancePath( this.app.getMySqlVm())));
		Assert.assertTrue( instancePaths.contains( InstanceHelpers.computeInstancePath( this.app.getTomcatVm())));
	}


	@Test
	public void testCreateInstanceFromComponent() throws Exception {

		Instance newInstance = this.client.getApplicationDelegate().createInstanceFromComponent( "inexisting", "my-comp" );
		Assert.assertNull( newInstance );

		String componentName = this.app.getMySqlVm().getComponent().getName();
		newInstance = this.client.getApplicationDelegate().createInstanceFromComponent( this.app.getName(), componentName );
		Assert.assertNotNull( newInstance );
		Assert.assertEquals( componentName, newInstance.getComponent().getName());
	}


	@Test
	public void testAddInstance_root_success() throws Exception {

		Assert.assertEquals( 2, this.app.getRootInstances().size());
		Instance newInstance = new Instance( "vm-mail" ).component( this.app.getMySqlVm().getComponent());
		this.client.getApplicationDelegate().addInstance( this.app.getName(), null, newInstance );
		Assert.assertEquals( 3, this.app.getRootInstances().size());
	}


	@Test( expected = ApplicationException.class )
	public void testAddInstance_root_failure() throws Exception {

		Assert.assertEquals( 2, this.app.getRootInstances().size());
		Instance existingInstance = new Instance( this.app.getMySqlVm().getName());
		this.client.getApplicationDelegate().addInstance( this.app.getName(), null, existingInstance );
	}


	@Test
	public void testAddInstance_child_success() throws Exception {

		Instance newMysql = new Instance( "mysql-2" ).component( this.app.getMySql().getComponent());

		Assert.assertEquals( 1, this.app.getTomcatVm().getChildren().size());
		Assert.assertFalse( this.app.getTomcatVm().getChildren().contains( newMysql ));

		this.client.getApplicationDelegate().addInstance( this.app.getName(), InstanceHelpers.computeInstancePath( this.app.getTomcatVm()), newMysql );
		Assert.assertEquals( 2, this.app.getTomcatVm().getChildren().size());

		List<String> paths = new ArrayList<String> ();
		for( Instance inst : this.app.getTomcatVm().getChildren())
			paths.add( InstanceHelpers.computeInstancePath( inst ));

		String rootPath = InstanceHelpers.computeInstancePath( this.app.getTomcatVm());
		Assert.assertTrue( paths.contains( rootPath + "/" + newMysql.getName()));
		Assert.assertTrue( paths.contains( rootPath + "/" + this.app.getTomcat().getName()));
	}


	@Test
	public void testAddInstance_child_incompleteComponent() throws Exception {

		// Pass an incomplete component object to the REST API
		String mySqlComponentName = this.app.getMySql().getComponent().getName();
		Instance newMysql = new Instance( "mysql-2" ).component( new Component( mySqlComponentName ));

		Assert.assertEquals( 1, this.app.getTomcatVm().getChildren().size());
		Assert.assertFalse( this.app.getTomcatVm().getChildren().contains( newMysql ));

		this.client.getApplicationDelegate().addInstance( this.app.getName(), InstanceHelpers.computeInstancePath( this.app.getTomcatVm()), newMysql );
		Assert.assertEquals( 2, this.app.getTomcatVm().getChildren().size());

		List<String> paths = new ArrayList<String> ();
		for( Instance inst : this.app.getTomcatVm().getChildren())
			paths.add( InstanceHelpers.computeInstancePath( inst ));

		String rootPath = InstanceHelpers.computeInstancePath( this.app.getTomcatVm());
		Assert.assertTrue( paths.contains( rootPath + "/" + newMysql.getName()));
		Assert.assertTrue( paths.contains( rootPath + "/" + this.app.getTomcat().getName()));
	}


	@Test( expected = ApplicationException.class )
	public void testAddInstance_child_failure() throws Exception {

		// We cannot deploy a WAR directly on a VM!
		// At least, this what the graph says.
		Instance newWar = new Instance( "war-2" ).component( this.app.getWar().getComponent());

		Assert.assertEquals( 1, this.app.getTomcatVm().getChildren().size());
		Assert.assertFalse( this.app.getTomcatVm().getChildren().contains( newWar ));
		this.client.getApplicationDelegate().addInstance( this.app.getName(), InstanceHelpers.computeInstancePath( this.app.getTomcatVm()), newWar );
	}


	@Test( expected = ApplicationException.class )
	public void testAddInstance_inexstingApplication() throws Exception {

		Instance newMysql = new Instance( "mysql-2" ).component( this.app.getMySql().getComponent());
		this.client.getApplicationDelegate().addInstance( "inexisting", InstanceHelpers.computeInstancePath( this.app.getTomcatVm()), newMysql );
	}


	@Test( expected = ApplicationException.class )
	public void testAddInstance_inexstingParentInstance() throws Exception {

		Instance newMysql = new Instance( "mysql-2" ).component( this.app.getMySql().getComponent());
		this.client.getApplicationDelegate().addInstance( "inexisting", "/bip/bip", newMysql );
	}
}
