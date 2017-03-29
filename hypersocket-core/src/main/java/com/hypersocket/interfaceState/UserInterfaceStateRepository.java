package com.hypersocket.interfaceState;

import java.util.Collection;

import com.hypersocket.realm.Realm;
import com.hypersocket.resource.AbstractResourceRepository;

public interface UserInterfaceStateRepository extends
		AbstractResourceRepository<UserInterfaceState> {

	UserInterfaceState getStateByResourceId(Long resourceId);

	void updateState(UserInterfaceState newState);

	UserInterfaceState getState(String name, Long principalId,
			String resourceCategory, Realm realm);

	UserInterfaceState getState(String name, String resourceCategory,
			Realm realm);
	
	Collection<UserInterfaceState> getStateStartsWith(final String name, final String resourceCategory, final Realm realm);
}
