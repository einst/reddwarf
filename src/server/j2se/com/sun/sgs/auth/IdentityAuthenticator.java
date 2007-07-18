/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 3 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sun.sgs.auth;

import com.sun.sgs.kernel.KernelAppContext;

import javax.security.auth.login.LoginException;


/**
 * This interface is used to define modules that know how to authenticate
 * an identity based on provided credentials. The credentials must be of
 * a form recognizable to the implementation. Note that each application
 * context has its own instances of <code>IdentityAuthenticator</code>s.
 * Typically implementations of <code>IdentityAuthenticator</code> are
 * invoked by a containing <code>IdentityManager</code>.
 * <p>
 * All implementations of <code>IdentityAuthenticator</code> must have a
 * constructor that accepts an instance of <code>java.util.Properties</code>.
 * The provided properties are part of the application's configuration.
 * <p>
 * FIXME: When the IO interfaces are ready, these should also be provided
 * to the constructor.
 */
public interface IdentityAuthenticator
{

    /**
     * Returns the identifiers for this <code>IdentityAuthenticator</code>'s
     * supported credential types. This may contain any number of
     * identifiers, which are matched against the identifier of
     * <code>IdentityCredential</code>s to determine if this
     * <code>IdentityAuthenticator</code> can consume those credentials.
     *
     * @return the identifiers for the supported credential types
     */
    public String [] getSupportedCredentialTypes();

    /**
     * Tells this <code>IdentityAuthenticator</code> the context in which
     * it is running. This should only be called once for the lifetime of
     * this authenticator.
     *
     * @param context the context in which identities are authenticated
     *
     * @throws IllegalStateException if the context has already been assigned
     */
    public void assignContext(KernelAppContext context);

    /**
     * Authenticates the given credentials. The returned <code>Identity</code>
     * is valid, but has not yet been notified as logged in.
     *
     * @param credentials the <code>IdentityCredentials</code> to authenticate
     *
     * @return an authenticated <code>Identity</code>
     *
     * @throws LoginException if authentication fails
     */
    public Identity authenticateIdentity(IdentityCredentials credentials)
        throws LoginException;

}
