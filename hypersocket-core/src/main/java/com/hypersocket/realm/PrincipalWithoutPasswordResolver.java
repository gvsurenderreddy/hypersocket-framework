package com.hypersocket.realm;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.hypersocket.utils.StaticResolver;

public class PrincipalWithoutPasswordResolver extends StaticResolver {

	static Set<String> salutations = new HashSet<String>(Arrays.asList("MR", "MS", "MRS", "DR", "PROF"));
	
	public PrincipalWithoutPasswordResolver(Principal principal) {
		super();
		addToken("principalId", principal.getPrincipalName());
		addToken("principalName", principal.getPrincipalName());
		addToken("principalDesc", principal.getPrincipalDescription());
		addToken("principalRealm", principal.getRealm().getName());
		addToken("firstName", getFirstName(principal.getPrincipalDescription()));
		addToken("email", principal.getEmail());
		addToken("fullName", principal.getPrincipalDescription());
	}
	
	public static Set<String> getVariables() {
		return new HashSet<String>(Arrays.asList("principalName", "principalId",
				"principalDesc", "principalRealm", "firstName", "fullName", "email"));
	}
	
	public String getFirstName(String name) {
		if(StringUtils.isNotBlank(name)) {
			int idx = name.indexOf(' ');
			if(idx > 0) {
				String firstName = name.substring(0,  idx);
				int idx2 = name.indexOf(' ', idx+1);
				if(salutations.contains(firstName.toUpperCase()) && idx2 > 0) {
					firstName = name.substring(idx+1, idx2);
				}
				return firstName;
			}
			return name;
		}
		return "";
	}
}