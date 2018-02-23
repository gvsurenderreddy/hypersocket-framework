/*******************************************************************************
 * Copyright (c) 2013 LogonBox Limited.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package com.hypersocket.auth;

import java.util.Collection;
import java.util.List;

import com.hypersocket.realm.Realm;
import com.hypersocket.repository.AbstractEntityRepository;

public interface AuthenticationModuleRepository extends AbstractEntityRepository<AuthenticationModule,Long> {

	public List<AuthenticationModule> getModulesForScheme(
			AuthenticationScheme scheme);

	public List<AuthenticationModule> getAuthenticationModules();

	public List<AuthenticationModule> getAuthenticationModulesByScheme(
			AuthenticationScheme authenticationScheme);

	public AuthenticationModule getModuleById(Long id);

	public void updateSchemeModules(List<AuthenticationModule> moduleList);

	public AuthenticationModule createAuthenticationModule(
			AuthenticationModule authenticationModule);

	public AuthenticationModule updateAuthenticationModule(
			AuthenticationModule authenticationModule);

	public void deleteModule(AuthenticationModule authenticationModule);

	boolean isAuthenticatorInUse(Realm realm, String resourceKey);

	public Collection<AuthenticationScheme> getSchemesForModule(Realm currentRealm, String... resourceKeys);

	public void deleteRealm(Realm realm);

	Collection<AuthenticationModule> getModulesForRealm(Realm scheme);

	boolean isModuleInScheme(Realm realm, String resourceKey, String... resourceKeys);

}
