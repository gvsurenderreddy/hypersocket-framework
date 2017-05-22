package com.hypersocket.properties;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.w3c.dom.Element;

import com.hypersocket.resource.AbstractResource;

public class TransientPropertyStore implements ResourcePropertyStore {

	Map<String,PropertyTemplate> templates = new HashMap<String,PropertyTemplate>();
	
	public TransientPropertyStore() {
	}

	@Override
	public void init(Element element) throws IOException {
	}

	@Override
	public String getPropertyValue(PropertyTemplate template) {
		return "";
	}

	@Override
	public void setProperty(PropertyTemplate property, String value) {

	}

	@Override
	public void registerTemplate(PropertyTemplate template, String module) {
		templates.put(template.getResourceKey(), template);
	}

	@Override
	public PropertyTemplate getPropertyTemplate(String resourceKey) {
		return templates.get(resourceKey);
	}

	@Override
	public boolean isDefaultStore() {
		return false;
	}

	@Override
	public String getPropertyValue(AbstractPropertyTemplate template, AbstractResource resource) {
		return "";
	}

	@Override
	public void setPropertyValue(AbstractPropertyTemplate template, AbstractResource resource, String value) {

	}

	@Override
	public boolean hasPropertyValueSet(AbstractPropertyTemplate template, AbstractResource resource) {
		return false;
	}

	@Override
	public Collection<String> getPropertyNames() {
		return new ArrayList<String>(templates.keySet());
	}

	@Override
	public String getDecryptedValue(AbstractPropertyTemplate template, AbstractResource resource) {
		return "";
	}

}