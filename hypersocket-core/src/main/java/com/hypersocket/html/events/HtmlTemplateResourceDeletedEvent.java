package com.hypersocket.html.events;

import org.apache.commons.lang3.ArrayUtils;

import com.hypersocket.html.HtmlTemplateResource;
import com.hypersocket.session.Session;

public class HtmlTemplateResourceDeletedEvent extends
		HtmlTemplateResourceEvent {

	private static final long serialVersionUID = 7941565015220738772L;

	public static final String EVENT_RESOURCE_KEY = "htmlTemplate.deleted";

	public HtmlTemplateResourceDeletedEvent(Object source,
			Session session, HtmlTemplateResource resource) {
		super(source, EVENT_RESOURCE_KEY, session, resource);
	}

	public HtmlTemplateResourceDeletedEvent(Object source,
			HtmlTemplateResource resource, Throwable e, Session session) {
		super(source, EVENT_RESOURCE_KEY, resource, e, session);
	}

	public String[] getResourceKeys() {
		return ArrayUtils.add(super.getResourceKeys(), EVENT_RESOURCE_KEY);
	}
}
