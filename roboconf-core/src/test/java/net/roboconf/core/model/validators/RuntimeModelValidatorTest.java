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

package net.roboconf.core.model.validators;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import junit.framework.Assert;
import net.roboconf.core.ErrorCode;
import net.roboconf.core.RoboconfError;
import net.roboconf.core.internal.tests.TestUtils;
import net.roboconf.core.model.ApplicationDescriptor;
import net.roboconf.core.model.ModelError;
import net.roboconf.core.model.converters.FromGraphDefinition;
import net.roboconf.core.model.helpers.ComponentHelpers;
import net.roboconf.core.model.io.ParsingModelIo;
import net.roboconf.core.model.parsing.FileDefinition;
import net.roboconf.core.model.runtime.Application;
import net.roboconf.core.model.runtime.Component;
import net.roboconf.core.model.runtime.Graphs;
import net.roboconf.core.model.runtime.Instance;

import org.junit.Test;

/**
 * @author Vincent Zurczak - Linagora
 */
public class RuntimeModelValidatorTest {

	@Test
	public void testComponent() {

		Component comp = new Component();
		Iterator<RoboconfError> iterator = RuntimeModelValidator.validate( comp ).iterator();
		Assert.assertEquals( ErrorCode.RM_EMPTY_COMPONENT_NAME, iterator.next().getErrorCode());
		Assert.assertEquals( ErrorCode.RM_EMPTY_COMPONENT_ALIAS, iterator.next().getErrorCode());
		Assert.assertEquals( ErrorCode.RM_EMPTY_COMPONENT_INSTALLER, iterator.next().getErrorCode());
		Assert.assertFalse( iterator.hasNext());

		comp.setName( "my # component" );
		iterator = RuntimeModelValidator.validate( comp ).iterator();
		Assert.assertEquals( ErrorCode.RM_INVALID_COMPONENT_NAME, iterator.next().getErrorCode());
		Assert.assertEquals( ErrorCode.RM_EMPTY_COMPONENT_ALIAS, iterator.next().getErrorCode());
		Assert.assertEquals( ErrorCode.RM_EMPTY_COMPONENT_INSTALLER, iterator.next().getErrorCode());
		Assert.assertFalse( iterator.hasNext());

		comp.setName( "my-component" );
		iterator = RuntimeModelValidator.validate( comp ).iterator();
		Assert.assertEquals( ErrorCode.RM_EMPTY_COMPONENT_ALIAS, iterator.next().getErrorCode());
		Assert.assertEquals( ErrorCode.RM_EMPTY_COMPONENT_INSTALLER, iterator.next().getErrorCode());
		Assert.assertFalse( iterator.hasNext());

		comp.setAlias( "an alias" );
		iterator = RuntimeModelValidator.validate( comp ).iterator();
		Assert.assertEquals( ErrorCode.RM_EMPTY_COMPONENT_INSTALLER, iterator.next().getErrorCode());
		Assert.assertFalse( iterator.hasNext());

		comp.setInstallerName( "my installer !!" );
		iterator = RuntimeModelValidator.validate( comp ).iterator();
		Assert.assertEquals( ErrorCode.RM_INVALID_COMPONENT_INSTALLER, iterator.next().getErrorCode());
		Assert.assertFalse( iterator.hasNext());

		comp.setInstallerName( "my installer" );
		Assert.assertEquals( 0, RuntimeModelValidator.validate( comp ).size());

		comp.getExportedVariables().put( "comp.ip", null );
		comp.getExportedVariables().put( "comp.port", "9000" );
		comp.getImportedVariables().put( "comp.ip", Boolean.FALSE );
		iterator = RuntimeModelValidator.validate( comp ).iterator();
		Assert.assertEquals( ErrorCode.RM_COMPONENT_IMPORTS_EXPORTS, iterator.next().getErrorCode());
		Assert.assertFalse( iterator.hasNext());

		comp.getImportedVariables().put( "comp.ip", Boolean.TRUE );
		Assert.assertEquals( 0, RuntimeModelValidator.validate( comp ).size());
	}


	@Test
	public void testGraphs() {

		Graphs graphs = new Graphs();
		Iterator<RoboconfError> iterator = RuntimeModelValidator.validate( graphs ).iterator();
		Assert.assertEquals( ErrorCode.RM_NO_ROOT_COMPONENT, iterator.next().getErrorCode());
		Assert.assertFalse( iterator.hasNext());

		Component comp1 = new Component( "comp1" );
		comp1.setAlias( "component 1" );
		comp1.setInstallerName( "installer-1" );
		graphs.getRootComponents().add( comp1 );

		Component duplicateComp1 = new Component( "comp1" );
		duplicateComp1.setAlias( "component 1" );
		duplicateComp1.setInstallerName( "installer-1" );
		graphs.getRootComponents().add( duplicateComp1 );

		// The validator checks something that cannot happen for the moment.
		// But we must keep it to prevent regressions.
		iterator = RuntimeModelValidator.validate( graphs ).iterator();
		if( iterator.hasNext()) {
			Assert.assertEquals( ErrorCode.RM_DUPLICATE_COMPONENT, iterator.next().getErrorCode());
			Assert.assertFalse( iterator.hasNext());
		}

		// Unresolvable variable
		graphs.getRootComponents().clear();
		graphs.getRootComponents().add( comp1 );
		comp1.getImportedVariables().put( "tomcat.port", Boolean.FALSE );
		iterator = RuntimeModelValidator.validate( graphs ).iterator();
		Assert.assertEquals( ErrorCode.RM_UNRESOLVABLE_VARIABLE, iterator.next().getErrorCode());
		Assert.assertFalse( iterator.hasNext());

		comp1.getImportedVariables().put( "tomcat.port", Boolean.TRUE );
		iterator = RuntimeModelValidator.validate( graphs ).iterator();
		Assert.assertEquals( ErrorCode.RM_UNRESOLVABLE_VARIABLE, iterator.next().getErrorCode());
		Assert.assertFalse( iterator.hasNext());
		comp1.getImportedVariables().clear();

		// Test for loops
		Component comp2 = new Component( "comp2" );
		comp2.setAlias( "component 2" );
		comp2.setInstallerName( "installer-2" );
		ComponentHelpers.insertChild( comp1, comp2 );
		ComponentHelpers.insertChild( comp2, comp1 );

		iterator = RuntimeModelValidator.validate( graphs ).iterator();
		Assert.assertEquals( ErrorCode.RM_CYCLE_IN_COMPONENTS, iterator.next().getErrorCode());
		Assert.assertFalse( iterator.hasNext());
	}


	@Test
	public void testInstance() {

		Instance inst = new Instance();
		Iterator<RoboconfError> iterator = RuntimeModelValidator.validate( inst ).iterator();
		Assert.assertEquals( ErrorCode.RM_EMPTY_INSTANCE_NAME, iterator.next().getErrorCode());
		Assert.assertEquals( ErrorCode.RM_EMPTY_INSTANCE_COMPONENT, iterator.next().getErrorCode());
		Assert.assertFalse( iterator.hasNext());

		inst.setName( "?my instance?" );
		iterator = RuntimeModelValidator.validate( inst ).iterator();
		Assert.assertEquals( ErrorCode.RM_INVALID_INSTANCE_NAME, iterator.next().getErrorCode());
		Assert.assertEquals( ErrorCode.RM_EMPTY_INSTANCE_COMPONENT, iterator.next().getErrorCode());
		Assert.assertFalse( iterator.hasNext());

		inst.setName( "my-instance" );
		iterator = RuntimeModelValidator.validate( inst ).iterator();
		Assert.assertEquals( ErrorCode.RM_EMPTY_INSTANCE_COMPONENT, iterator.next().getErrorCode());
		Assert.assertFalse( iterator.hasNext());

		inst.setComponent( new Component( "comp" ));
		Assert.assertEquals( 0, RuntimeModelValidator.validate( inst ).size());

		inst.setName( "my instance" );
		Assert.assertEquals( 0, RuntimeModelValidator.validate( inst ).size());

		inst.getOverriddenExports().put( "inst.value", "whatever" );
		iterator = RuntimeModelValidator.validate( inst ).iterator();
		Assert.assertEquals( ErrorCode.RM_MAGIC_INSTANCE_VARIABLE, iterator.next().getErrorCode());
		Assert.assertFalse( iterator.hasNext());
	}


	@Test
	public void testInstances() {

		List<Instance> instances = new ArrayList<Instance> ();
		for( int i=0; i<10; i++ ) {
			Instance inst = new Instance( "inst-" + i );
			inst.setComponent( new Component( "comp" ));
			instances.add( inst );
		}

		Assert.assertEquals( 0, RuntimeModelValidator.validate( instances ).size());
	}


	@Test
	public void testApplication() {

		Application app = new Application();
		Iterator<RoboconfError> iterator = RuntimeModelValidator.validate( app ).iterator();
		Assert.assertEquals( ErrorCode.RM_MISSING_APPLICATION_NAME, iterator.next().getErrorCode());
		Assert.assertEquals( ErrorCode.RM_MISSING_APPLICATION_QUALIFIER, iterator.next().getErrorCode());
		Assert.assertEquals( ErrorCode.RM_MISSING_APPLICATION_GRAPHS, iterator.next().getErrorCode());
		Assert.assertFalse( iterator.hasNext());

		app.setName( "My Application #!" );
		iterator = RuntimeModelValidator.validate( app ).iterator();
		Assert.assertEquals( ErrorCode.RM_MISSING_APPLICATION_QUALIFIER, iterator.next().getErrorCode());
		Assert.assertEquals( ErrorCode.RM_MISSING_APPLICATION_GRAPHS, iterator.next().getErrorCode());
		Assert.assertFalse( iterator.hasNext());

		app.setQualifier( "Snapshot Build #2401" );
		iterator = RuntimeModelValidator.validate( app ).iterator();
		Assert.assertEquals( ErrorCode.RM_MISSING_APPLICATION_GRAPHS, iterator.next().getErrorCode());
		Assert.assertFalse( iterator.hasNext());

		app.setGraphs( new Graphs());
		iterator = RuntimeModelValidator.validate( app ).iterator();
		Assert.assertEquals( ErrorCode.RM_NO_ROOT_COMPONENT, iterator.next().getErrorCode());
		Assert.assertFalse( iterator.hasNext());

		Component comp = new Component( "root" );
		comp.setAlias( "a root component" );
		comp.setInstallerName( "_my_installer" );
		app.getGraphs().getRootComponents().add( comp );
		Assert.assertEquals( 0, RuntimeModelValidator.validate( app ).size());
	}


	@Test
	public void testApplicationDescriptor() {

		ApplicationDescriptor desc = new ApplicationDescriptor();
		Iterator<RoboconfError> iterator = RuntimeModelValidator.validate( desc ).iterator();
		Assert.assertEquals( ErrorCode.RM_MISSING_APPLICATION_NAME, iterator.next().getErrorCode());
		Assert.assertEquals( ErrorCode.RM_MISSING_APPLICATION_QUALIFIER, iterator.next().getErrorCode());
		Assert.assertEquals( ErrorCode.RM_MISSING_APPLICATION_GEP, iterator.next().getErrorCode());
		Assert.assertFalse( iterator.hasNext());

		desc.setName( "My Application #!" );
		iterator = RuntimeModelValidator.validate( desc ).iterator();
		Assert.assertEquals( ErrorCode.RM_MISSING_APPLICATION_QUALIFIER, iterator.next().getErrorCode());
		Assert.assertEquals( ErrorCode.RM_MISSING_APPLICATION_GEP, iterator.next().getErrorCode());
		Assert.assertFalse( iterator.hasNext());

		desc.setQualifier( "Snapshot Build #2401" );
		iterator = RuntimeModelValidator.validate( desc ).iterator();
		Assert.assertEquals( ErrorCode.RM_MISSING_APPLICATION_GEP, iterator.next().getErrorCode());
		Assert.assertFalse( iterator.hasNext());

		desc.setGraphEntryPoint( "graph.graph" );
		Assert.assertEquals( 0, RuntimeModelValidator.validate( desc ).size());
	}


	@Test
	public void testSelfImports() throws Exception {

		File f = TestUtils.findTestFile( "/configurations/invalid/component-self-imports.graph" );
		FileDefinition def = ParsingModelIo.readConfigurationFile( f, true );
		Assert.assertEquals( 0, def.getParsingErrors().size());

		Collection<ModelError> validationErrors = ParsingModelValidator.validate( def );
		Assert.assertEquals( 0, validationErrors.size());

		FromGraphDefinition fromDef = new FromGraphDefinition( def );
		Graphs graphs = fromDef.buildGraphs();
		Assert.assertEquals( 0, fromDef.getErrors().size());

		Iterator<RoboconfError> iterator = RuntimeModelValidator.validate( graphs ).iterator();
		Assert.assertEquals( ErrorCode.RM_COMPONENT_IMPORTS_EXPORTS, iterator.next().getErrorCode());
		Assert.assertEquals( ErrorCode.RM_COMPONENT_IMPORTS_EXPORTS, iterator.next().getErrorCode());
		Assert.assertFalse( iterator.hasNext());
	}
}