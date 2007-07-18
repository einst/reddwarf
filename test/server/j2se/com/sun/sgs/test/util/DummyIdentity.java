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

package com.sun.sgs.test.util;

import com.sun.sgs.auth.Identity;
import java.io.Serializable;

/** Provides a simple implementation of Identity used for testing. */
public class DummyIdentity implements Identity, Serializable {

    private static final long serialVersionUID = 1;

    /** Creates an instance of this class. */
    public DummyIdentity() { }

    /** -- Implement Identity -- */

    public String getName() { return "Me!"; }

    public void notifyLoggedIn() { }

    public void notifyLoggedOut() { }

}
