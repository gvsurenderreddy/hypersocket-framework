package com.hypersocket.server.handlers.impl;

import javax.servlet.Servlet;
import javax.servlet.ServletException;

import com.hypersocket.server.HypersocketServer;

public class DefaultServletRequestHandler extends ServletRequestHandler {

	public DefaultServletRequestHandler(String path, Servlet servlet,
			int priority, HypersocketServer server) throws ServletException {
		super(path, servlet, priority);
	}
	
	protected void registered() {
		try {
			servlet.init(server.getServletConfig());
		} catch (ServletException e) {
			log.error("Failed to init servlet", e);
		}
	}

	@Override
	public boolean handlesRequest(String request) {
		return request.startsWith(server.resolvePath(server.getAttribute(getName(), getName())));
	}

}
