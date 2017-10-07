/*******************************************************************************
 * Copyright (c) 2013 LogonBox Limited.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package com.hypersocket.resource;

import com.hypersocket.properties.ResourceTemplateRepository;
import com.hypersocket.realm.Realm;

public interface AssignableResourceRepository extends ResourceTemplateRepository {

	long getResourceCount(Realm realm);

	void deleteRealm(Realm realm);
	
}
