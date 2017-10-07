/*******************************************************************************
 * Copyright (c) 2013 LogonBox Limited.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package com.hypersocket.auth.json;

public class JsonRedirectException extends RuntimeException {

	private static final long serialVersionUID = -8592093219854938960L;

	public JsonRedirectException(String location) {
		super(location);
		if(location == null)
			throw new IllegalArgumentException("Location for a redirect cannot be null.");
	}
}
