/**
 * Copyright 2017 Linagora, Université Joseph Fourier, Floralis
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

package net.roboconf.dm.rest.commons.security;

import java.io.IOException;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

/**
 * A class in charge of managing authentication and sessions.
 * <p>
 * Authentication is delegated to various implementations.
 * By default, it is handled by Karaf's JAAS implementation, but it is possible
 * to override it by using {@link #setAuthService(IAuthService)}. You will HAVE
 * TO use this method if you run the REST services outside Karaf.
 * </p>
 * <p>
 * When the authentication succeeds, a token is generated by this class
 * (a random UUID in fact). The token is stored by this class and associated
 * with the login time.
 * </p>
 * <p>
 * Since sessions can be limited in time (depending on admin preferences),
 * we can verify on every action that the session is still valid.
 * </p>
 * <p>
 * To prevent "man in the middle" "attacks, authentication should be
 * used along with HTTPS.
 * </p>
 *
 * @author Vincent Zurczak - Linagora
 */
public class AuthenticationManager {

	private final ConcurrentHashMap<String,Long> tokenToLoginTime = new ConcurrentHashMap<> ();
	private final Logger logger = Logger.getLogger( getClass().getName());
	private final String realm;

	private IAuthService authService;



	/**
	 * Constructor.
	 * @param realm
	 */
	public AuthenticationManager( String realm ) {
		this.realm = realm;
		this.authService = new KarafAuthService();
		this.authService.setRealm( this.realm );
	}


	/**
	 * @param authenticater the authService to set
	 */
	public void setAuthService( IAuthService authService ) {
		this.authService = authService;
		authService.setRealm( this.realm );
	}


	/**
	 * Authenticates a user and creates a new session.
	 * @param user a user name
	 * @param pwd a pass word
	 * @return a token if authentication worked, null if it failed
	 */
	public String login( String user, String pwd ) {

		String token = null;
		try {
			this.authService.authenticate( user, pwd );

			token = UUID.randomUUID().toString();
			Long now = new Date().getTime();
			this.tokenToLoginTime.put( token, now );

		} catch( LoginException e ) {
			this.logger.severe( "Invalid login attempt by user " + user );
		}

		return token;
	}


	/**
	 * Determines whether a session is valid.
	 * @param token a token
	 * @param validityPeriod the validity period for a session (in seconds)
	 * @return true if the session is valid, false otherwise
	 */
	public boolean isSessionValid( final String token, int validityPeriod ) {

		boolean valid = false;
		Long loginTime = null;
		if( token != null )
			loginTime = this.tokenToLoginTime.get( token );

		if( validityPeriod < 0 ) {
			valid = loginTime != null;

		} else if( loginTime != null ) {
			long now = new Date().getTime();
			valid = (now - loginTime) <= validityPeriod * 1000;

			// Invalid sessions should be deleted
			if( ! valid )
				logout( token );
		}

		return valid;
	}


	/**
	 * Invalidates a session.
	 * <p>
	 * No error is thrown if the session was already invalid.
	 * </p>
	 *
	 * @param token a token
	 */
	public void logout( String token ) {
		if( token != null )
			this.tokenToLoginTime.remove( token );
	}


	/**
	 * An abstraction to manage authentication.
	 * @author Vincent Zurczak - Linagora
	 */
	public interface IAuthService {

		/**
		 * Authenticates someone by user and password.
		 * @param user a user name
		 * @param pwd a password
		 * @throws LoginException if authentication failed
		 */
		void authenticate( String user, String pwd ) throws LoginException;

		/**
		 * Sets the REALM to use.
		 * @param realm a realm name
		 */
		void setRealm( String realm );
	}


	/**
	 * Authentication managed by Apache Karaf.
	 * <p>
	 * Karaf uses JAAS and by default supports several login modules
	 * (properties files, databases, LDAP, etc).
	 * </p>
	 * @author Vincent Zurczak - Linagora
	 */
	public static class KarafAuthService implements IAuthService {
		private String realm;


		@Override
		public void authenticate( String user, String pwd ) throws LoginException {
			LoginContext loginCtx = new LoginContext( this.realm, new RoboconfCallbackHandler( user, pwd ));
			loginCtx.login();
		}

		@Override
		public void setRealm( String realm ) {
			this.realm = realm;
		}
	}


	/**
	 * A callback handler for JAAS.
	 * @author Vincent Zurczak - Linagora
	 */
	static final class RoboconfCallbackHandler implements CallbackHandler {
		private final String username, password;


		/**
		 * Constructor.
		 * @param username
		 * @param password
		 */
		public RoboconfCallbackHandler( String username, String password ) {
			this.username = username;
			this.password = password;
		}


		@Override
		public void handle( Callback[] callbacks ) throws IOException, UnsupportedCallbackException {

			for( Callback callback : callbacks ) {
				if (callback instanceof NameCallback )
					((NameCallback) callback).setName( this.username );
				else if( callback instanceof PasswordCallback )
					((PasswordCallback) callback).setPassword( this.password.toCharArray());
				else
					throw new UnsupportedCallbackException( callback );
			}
		}
	}
}
