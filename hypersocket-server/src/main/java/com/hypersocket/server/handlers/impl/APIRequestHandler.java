/*******************************************************************************
 * Copyright (c) 2013 Hypersocket Limited.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package com.hypersocket.server.handlers.impl;

import javax.servlet.Servlet;
import javax.servlet.http.HttpServletRequest;

import com.hypersocket.server.HypersocketServerImpl;

public class APIRequestHandler extends ServletRequestHandler {

	public APIRequestHandler(Servlet servlet,
			int priority) {
		super("api", servlet, priority);
	}
	
	protected void registered() {
		server.addCompressablePath(server.resolvePath(server.getAttribute(
				HypersocketServerImpl.API_PATH, HypersocketServerImpl.API_PATH)));
	}

	@Override
	public boolean handlesRequest(HttpServletRequest request) {
		return request.getRequestURI().startsWith(
				server.resolvePath(server.getAttribute(
						HypersocketServerImpl.API_PATH,
						HypersocketServerImpl.API_PATH)));
	}

}
