/*******************************************************************************
 * Copyright (c) 2013 Hypersocket Limited.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package com.hypersocket.properties;

import com.hypersocket.resource.SimpleResource;

public class ResourcePropertyTemplate extends AbstractPropertyTemplate {

	SimpleResource resource;
	
	public ResourcePropertyTemplate(AbstractPropertyTemplate t, SimpleResource resource) {
		this.resourceKey = t.getResourceKey();
		this.defaultValue = t.getDefaultValue();
		this.mapping = t.getMapping();
		this.weight = t.getWeight();
		this.category = t.getCategory();
		this.hidden = t.isHidden();
		this.displayMode = t.getDisplayMode();
		this.readOnly = t.isReadOnly();
		this.resource = resource;
		this.propertyStore = t.getPropertyStore();
		this.defaultsToProperty = t.getDefaultsToProperty();
		this.encrypted = t.isEncrypted();
		this.name = t.getName();
		this.description = t.getDescription();
		this.metaData = t.metaData;
		this.attributes.putAll(t.attributes);
//		this.options.addAll(t.getOptions());
	}
	
	public void setPropertyStore(ResourcePropertyStore propertyStore) {
		this.propertyStore = propertyStore;
	}
	
	public String getValue() {
		return ((ResourcePropertyStore)propertyStore).getPropertyValue(this, resource);
	}
	
	public SimpleResource getResource() {
		return resource;
	}
	
	public void setResource(SimpleResource resource) {
		this.resource = resource;
	}
	
}
