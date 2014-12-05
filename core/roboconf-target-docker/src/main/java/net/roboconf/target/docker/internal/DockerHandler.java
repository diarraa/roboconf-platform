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

package net.roboconf.target.docker.internal;

import java.util.Map;
import java.util.logging.Logger;

import net.roboconf.core.utils.Utils;
import net.roboconf.target.api.TargetException;
import net.roboconf.target.api.TargetHandler;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig.DockerClientConfigBuilder;
import com.github.dockerjava.jaxrs.DockerClientBuilder;

/**
 * @author Pierre-Yves Gibello - Linagora
 */
public class DockerHandler implements TargetHandler {

	public static final String TARGET_ID = "docker";

	static String IMAGE_ID = "docker.image";
	static String ENDPOINT = "docker.endpoint";
	static String USER = "docker.user";
	static String PASSWORD = "docker.password";
	static String EMAIL = "docker.email";

	private final Logger logger = Logger.getLogger( getClass().getName());


	/*
	 * (non-Javadoc)
	 * @see net.roboconf.target.api.TargetHandler#getTargetId()
	 */
	@Override
	public String getTargetId() {
		return TARGET_ID;
	}


	/*
	 * (non-Javadoc)
	 * @see net.roboconf.target.api.TargetHandler
	 * #createOrConfigureMachine(java.util.Map, java.lang.String,
	 * java.lang.String, java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public String createOrConfigureMachine(
			Map<String,String> targetProperties,
			String messagingIp,
			String messagingUsername,
			String messagingPassword,
			String rootInstanceName,
			String applicationName )
	throws TargetException {

		this.logger.fine( "Creating a new machine." );
		DockerClient dockerClient = createDockerClient( targetProperties );
		CreateContainerResponse container = dockerClient
			.createContainerCmd( targetProperties.get( IMAGE_ID ))
			.withCmd("/usr/local/roboconf-agent/start.sh",
						"application-name=" + applicationName,
						"root-instance-name=" + rootInstanceName,
						"message-server-ip=" + messagingIp,
						"message-server-username=" + messagingUsername,
						"message-server-password=" + messagingPassword)
			.exec();

		dockerClient.startContainerCmd(container.getId()).exec();
		return container.getId();
	}


	/*
	 * (non-Javadoc)
	 * @see net.roboconf.target.api.TargetHandler
	 * #terminateMachine(java.util.Map, java.lang.String)
	 */
	@Override
	public void terminateMachine( Map<String, String> targetProperties, String instanceId ) throws TargetException {

		this.logger.fine( "Terminating machine " + instanceId );
		try {
			DockerClient dockerClient = createDockerClient( targetProperties );
			dockerClient.killContainerCmd(instanceId).exec();
			dockerClient.removeContainerCmd(instanceId).exec();

		} catch( Exception e ) {
			throw new TargetException(e);
		}
	}


	DockerClient createDockerClient( Map<String, String> targetProperties ) throws TargetException {

		this.logger.fine( "Setting the target properties." );
		if( Utils.isEmptyOrWhitespaces( targetProperties.get( IMAGE_ID )))
			throw new TargetException( IMAGE_ID + " is missing." );

		String endpoint = targetProperties.get( ENDPOINT );
		DockerClientConfigBuilder config = DockerClientConfig.createDefaultConfigBuilder();
		if(endpoint != null)
			config.withUri(endpoint);

		String username = targetProperties.get( USER );
		if(username != null) {
			String password = targetProperties.get( PASSWORD );
			String email = targetProperties.get( EMAIL );
			if(password == null)
				password = "";
			if(email == null)
				email = "";

			config.withUsername(username);
			config.withPassword(password);
			config.withEmail(email);
		}

		return DockerClientBuilder.getInstance( config.build()).build();
	}
}