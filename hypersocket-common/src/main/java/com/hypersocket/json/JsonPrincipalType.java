/*******************************************************************************
 * Copyright (c) 2013 LogonBox Limited.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package com.hypersocket.json;

public enum JsonPrincipalType {

	USER,
	GROUP,
	SERVICE,
	SYSTEM;
	
	public static final JsonPrincipalType[] ALL_TYPES = { USER, GROUP, SERVICE, SYSTEM };
	
}
