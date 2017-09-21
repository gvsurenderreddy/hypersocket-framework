/*******************************************************************************
 * Copyright (c) 2013 Hypersocket Limited.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package com.hypersocket.realm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.PostConstruct;
import javax.cache.Cache;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;

import com.hypersocket.attributes.AttributeType;
import com.hypersocket.attributes.user.UserAttribute;
import com.hypersocket.attributes.user.UserAttributeService;
import com.hypersocket.auth.PasswordEnabledAuthenticatedServiceImpl;
import com.hypersocket.cache.CacheService;
import com.hypersocket.config.ConfigurationService;
import com.hypersocket.config.SystemConfigurationService;
import com.hypersocket.events.EventPropertyCollector;
import com.hypersocket.events.EventService;
import com.hypersocket.local.LocalRealmProviderImpl;
import com.hypersocket.local.LocalUser;
import com.hypersocket.message.MessageResourceService;
import com.hypersocket.password.policy.PasswordPolicyResourceService;
import com.hypersocket.permissions.AccessDeniedException;
import com.hypersocket.permissions.PermissionCategory;
import com.hypersocket.permissions.PermissionRepository;
import com.hypersocket.permissions.PermissionScope;
import com.hypersocket.permissions.PermissionService;
import com.hypersocket.permissions.SystemPermission;
import com.hypersocket.properties.AbstractPropertyTemplate;
import com.hypersocket.properties.EntityResourcePropertyStore;
import com.hypersocket.properties.PropertyCategory;
import com.hypersocket.properties.PropertyTemplate;
import com.hypersocket.properties.ResourceUtils;
import com.hypersocket.realm.events.ChangePasswordEvent;
import com.hypersocket.realm.events.GroupCreatedEvent;
import com.hypersocket.realm.events.GroupDeletedEvent;
import com.hypersocket.realm.events.GroupEvent;
import com.hypersocket.realm.events.GroupUpdatedEvent;
import com.hypersocket.realm.events.PasswordUpdateEvent;
import com.hypersocket.realm.events.PrincipalEvent;
import com.hypersocket.realm.events.ProfileUpdatedEvent;
import com.hypersocket.realm.events.RealmCreatedEvent;
import com.hypersocket.realm.events.RealmDeletedEvent;
import com.hypersocket.realm.events.RealmEvent;
import com.hypersocket.realm.events.RealmUpdatedEvent;
import com.hypersocket.realm.events.SetPasswordEvent;
import com.hypersocket.realm.events.UserCreatedEvent;
import com.hypersocket.realm.events.UserDeletedEvent;
import com.hypersocket.realm.events.UserEvent;
import com.hypersocket.realm.events.UserUpdatedEvent;
import com.hypersocket.realm.ou.OrganizationalUnitRepository;
import com.hypersocket.resource.AbstractAssignableResourceRepository;
import com.hypersocket.resource.AbstractResourceRepository;
import com.hypersocket.resource.AbstractSimpleResourceRepository;
import com.hypersocket.resource.FindableResourceRepository;
import com.hypersocket.resource.ResourceChangeException;
import com.hypersocket.resource.ResourceConfirmationException;
import com.hypersocket.resource.ResourceCreationException;
import com.hypersocket.resource.ResourceException;
import com.hypersocket.resource.ResourceNotFoundException;
import com.hypersocket.resource.TransactionAdapter;
import com.hypersocket.scheduler.ClusteredSchedulerService;
import com.hypersocket.session.SessionService;
import com.hypersocket.session.SessionServiceImpl;
import com.hypersocket.tables.ColumnSort;
import com.hypersocket.tables.DefaultTableFilter;
import com.hypersocket.tables.TableFilter;
import com.hypersocket.transactions.TransactionCallbackWithError;
import com.hypersocket.transactions.TransactionService;
import com.hypersocket.upgrade.UpgradeService;
import com.hypersocket.upgrade.UpgradeServiceListener;

@Service
public class RealmServiceImpl extends PasswordEnabledAuthenticatedServiceImpl
		implements RealmService, UpgradeServiceListener {

	static Logger log = LoggerFactory.getLogger(RealmServiceImpl.class);

	Map<String, RealmProvider> providersByModule = new HashMap<String, RealmProvider>();

	List<RealmListener> realmListeners = new ArrayList<RealmListener>();
	List<PrincipalProcessor> principalProcessors = new ArrayList<PrincipalProcessor>();

	@Autowired
	RealmRepository realmRepository;

	@Autowired
	PermissionService permissionService;

	@Autowired
	PermissionRepository permissionRepository;
	
	@Autowired
	EventService eventService;

	Principal systemPrincipal;

	Realm systemRealm;

	@Autowired
	UpgradeService upgradeService;

	@Autowired
	SessionService sessionService;

	@Autowired
	ClusteredSchedulerService schedulerService;

	@Autowired
	PrincipalSuspensionService suspensionService;

	@Autowired
	UserVariableReplacementService userVariableReplacement;

	@Autowired
	ConfigurationService configurationService;
	
	@Autowired
	SystemConfigurationService systemConfigurationService;

	@Autowired
	UserAttributeService userAttributeService;

	@Autowired
	TransactionService transactionService;

	@Autowired
	PrincipalSuspensionRepository suspensionRepository; 

	Cache<String, Object> realmCache;
	
	@Autowired
	CacheService cacheService;

	@Autowired
	PrincipalRepository principalRepository; 

	@Autowired
	MessageResourceService messageService;
	
	@Autowired
	OrganizationalUnitRepository ouRepository;
	
	@Autowired
	PasswordPolicyResourceService passwordPolicyService;
	
	List<RealmOwnershipResolver> ownershipResolvers = new ArrayList<RealmOwnershipResolver>();

	public static final Integer MESSAGE_NEW_USER_NEW_PASSWORD = 6001;
	public static final Integer MESSAGE_NEW_USER_TMP_PASSWORD = 6002;
	public static final Integer MESSAGE_NEW_USER_SELF_CREATED = 6003;
	public static final Integer MESSAGE_PASSWORD_CHANGED = 6004;
	public static final Integer MESSAGE_PASSWORD_RESET = 6005;
	
	Map<String,TableFilter> principalFilters = new HashMap<String,TableFilter>();
	Map<String,TableFilter> builtInPrincipalFilters = new HashMap<String,TableFilter>();
	
	@PostConstruct
	private void postConstruct() {

		PermissionCategory cat = permissionService.registerPermissionCategory(RESOURCE_BUNDLE, "category.realms");

		for (RealmPermission p : RealmPermission.values()) {
			permissionService.registerPermission(p, cat);
		}

		cat = permissionService.registerPermissionCategory(RESOURCE_BUNDLE, "category.acl");

		for (UserPermission p : UserPermission.values()) {
			permissionService.registerPermission(p, cat);
		}

		for (ProfilePermission p : ProfilePermission.values()) {
			permissionService.registerPermission(p, cat);
		}

		for (GroupPermission p : GroupPermission.values()) {
			permissionService.registerPermission(p, cat);
		}

		for (RolePermission p : RolePermission.values()) {
			permissionService.registerPermission(p, cat);
		}

		cat = permissionService.registerPermissionCategory(RESOURCE_BUNDLE, "category.password");

		for (PasswordPermission p : PasswordPermission.values()) {
			permissionService.registerPermission(p, cat);
		}

		eventService.registerEvent(RealmEvent.class, RESOURCE_BUNDLE, new RealmPropertyCollector());
		eventService.registerEvent(RealmCreatedEvent.class, RESOURCE_BUNDLE, new RealmPropertyCollector());
		eventService.registerEvent(RealmUpdatedEvent.class, RESOURCE_BUNDLE, new RealmPropertyCollector());
		eventService.registerEvent(RealmDeletedEvent.class, RESOURCE_BUNDLE, new RealmPropertyCollector());

		eventService.registerEvent(PrincipalEvent.class, RESOURCE_BUNDLE);

		eventService.registerEvent(UserEvent.class, RESOURCE_BUNDLE, new UserPropertyCollector());
		eventService.registerEvent(UserCreatedEvent.class, RESOURCE_BUNDLE, new UserPropertyCollector());
		eventService.registerEvent(UserUpdatedEvent.class, RESOURCE_BUNDLE, new UserPropertyCollector());
		eventService.registerEvent(UserDeletedEvent.class, RESOURCE_BUNDLE, new UserPropertyCollector());
		eventService.registerEvent(PasswordUpdateEvent.class, RESOURCE_BUNDLE, new UserPropertyCollector());

		eventService.registerEvent(GroupEvent.class, RESOURCE_BUNDLE, new GroupPropertyCollector());
		eventService.registerEvent(GroupCreatedEvent.class, RESOURCE_BUNDLE, new GroupPropertyCollector());
		eventService.registerEvent(GroupUpdatedEvent.class, RESOURCE_BUNDLE, new GroupPropertyCollector());
		eventService.registerEvent(GroupDeletedEvent.class, RESOURCE_BUNDLE, new GroupPropertyCollector());

		eventService.registerEvent(ProfileUpdatedEvent.class, RESOURCE_BUNDLE);
		eventService.registerEvent(ChangePasswordEvent.class, RESOURCE_BUNDLE);
		eventService.registerEvent(SetPasswordEvent.class, RESOURCE_BUNDLE);

		upgradeService.registerListener(this);

		realmCache = cacheService.getCacheOrCreate("realmCache", String.class, Object.class);
		
		EntityResourcePropertyStore.registerResourceService(Principal.class, principalRepository);
		EntityResourcePropertyStore.registerResourceService(Realm.class, realmRepository);
		
		messageService.registerI18nMessage(MESSAGE_NEW_USER_NEW_PASSWORD, RESOURCE_BUNDLE,
				"realmService.newUserNewPassword", PrincipalWithPasswordResolver.getVariables());
		
		messageService.registerI18nMessage(MESSAGE_NEW_USER_TMP_PASSWORD, RESOURCE_BUNDLE,
				"realmService.newUserTmpPassword", PrincipalWithPasswordResolver.getVariables());

		messageService.registerI18nMessage(MESSAGE_NEW_USER_SELF_CREATED, RESOURCE_BUNDLE,
				"realmService.newUserSelfCreated", PrincipalWithPasswordResolver.getVariables());
		
		messageService.registerI18nMessage(MESSAGE_PASSWORD_CHANGED, RESOURCE_BUNDLE,
				"realmService.passwordChanged", PrincipalWithPasswordResolver.getVariables());
		
		messageService.registerI18nMessage(MESSAGE_PASSWORD_RESET, RESOURCE_BUNDLE,
				"realmService.passwordReset", PrincipalWithPasswordResolver.getVariables());
		
		registerBuiltInPrincipalFilter(new LocalAccountFilter());
		registerBuiltInPrincipalFilter(new RemoteAccountFilter());
	}

	@Override
	public void registerPrincipalProcessor(PrincipalProcessor processor) {
		principalProcessors.add(processor);
	}
	
	private void registerBuiltInPrincipalFilter(TableFilter filter) {
		builtInPrincipalFilters.put(filter.getResourceKey(), filter);
	}
	
	@Override
	public void registerPrincipalFilter(TableFilter filter) {
		principalFilters.put(filter.getResourceKey(), filter);
	}
	
	@Override
	public void registerOwnershipResolver(RealmOwnershipResolver resolver) {
		ownershipResolvers.add(resolver);
	}

	@Override
	public void onUpgradeComplete() {
	
		sessionService.executeInSystemContext(new Runnable() {
			public void run() {
				for (Realm realm : realmRepository.allRealms()) {
					for (RealmListener listener : realmListeners) {
						if (!listener.hasCreatedDefaultResources(realm)) {
							try {
								listener.onCreateRealm(realm);
							} catch (ResourceException | AccessDeniedException e) {
								log.error("Failed to create default resources in realm", e);
							}
						}
					}
				}
			}
		});

	}

	@Override
	public List<RealmProvider> getProviders() throws AccessDeniedException {

		assertAnyPermissionOrRealmAdministrator(PermissionScope.INCLUDE_CHILD_REALMS, RealmPermission.READ);
		
		return new ArrayList<RealmProvider>(providersByModule.values());
	}

	@Override
	public RealmProvider getProviderForRealm(Realm realm) {
		return getProviderForRealm(realm.getResourceCategory());
	}

	@Override
	public RealmProvider getProviderForRealm(String module) {
		if (!providersByModule.containsKey(module))
			throw new IllegalArgumentException("No provider available for realm module " + module);
		return providersByModule.get(module);
	}
	
	protected RealmProvider getProviderForPrincipal(Principal principal) {
		if(principal instanceof LocalUser) {
			return getLocalProvider();
		}
		return getProviderForRealm(principal.getRealm());
	}

	protected boolean hasProviderForRealm(Realm realm) {
		return providersByModule.containsKey(realm.getResourceCategory());
	}

	@Override
	public List<Principal> allUsers(Realm realm) throws AccessDeniedException {

		assertAnyPermission(UserPermission.READ, GroupPermission.READ, RealmPermission.READ);

		return allPrincipals(realm, PrincipalType.USER);
	}

	@Override
	public List<Principal> allGroups(Realm realm) throws AccessDeniedException {

		assertAnyPermission(UserPermission.READ, GroupPermission.READ, RealmPermission.READ);

		return allPrincipals(realm, PrincipalType.GROUP);
	}

	protected List<Principal> allPrincipals(Realm realm, PrincipalType... types) {
		if (types.length == 0) {
			types = PrincipalType.ALL_TYPES;
		}
		return getProviderForRealm(realm).allPrincipals(realm, types);
	}

	@Override
	public void registerRealmProvider(RealmProvider provider) {

		if (log.isInfoEnabled()) {
			log.info("Registering " + provider.getModule() + " realm provider");
		}
		providersByModule.put(provider.getModule(), provider);
	}

	@Override
	public void unregisterRealmProvider(RealmProvider provider) {

		if (log.isInfoEnabled()) {
			log.info("Unregistering " + provider.getModule() + " realm provider");
		}
		providersByModule.remove(provider.getModule());
	}

	@Override
	public Realm getRealmByName(String realm) {
		return realmRepository.getRealmByName(realm);
	}
	
	@Override
	public Realm getRealmByNameAndOwner(String realm, Realm owner) {
		return realmRepository.getRealmByNameAndOwner(realm, owner);
	}

	@Override
	public boolean isRegistered(RealmProvider provider) {
		return providersByModule.containsKey(provider.getModule());
	}

	@Override
	public String[] getRealmPropertyArray(Realm realm, String resourceKey) {
		String value = getRealmProperty(realm, resourceKey);
		if(StringUtils.isNotBlank(value)) {
			return value.split("\\]\\|\\[");
		} else {
			return new String[0];
		}
	}

	@Override
	public String getRealmProperty(Realm realm, String resourceKey) {

		RealmProvider provider = getProviderForRealm(realm);
		return provider.getValue(realm, resourceKey);
	}

	@Override
	public String getDecryptedValue(Realm realm, String resourceKey) {

		RealmProvider provider = getProviderForRealm(realm);
		return provider.getDecryptedValue(realm, resourceKey);
	}

	@Override
	public int getRealmPropertyInt(Realm realm, String resourceKey) {
		return Integer.parseInt(getRealmProperty(realm, resourceKey));
	}

	@Override
	public boolean getRealmPropertyBoolean(Realm realm, String resourceKey) {
		return Boolean.parseBoolean(getRealmProperty(realm, resourceKey));
	}

	@Override
	public Realm getRealmByHost(String host) {
		return getRealmByHost(host, getDefaultRealm());
	}

	@Override
	public Realm getRealmByHost(String host, Realm defaultRealm) {

		if (StringUtils.isBlank(host)) {
			return defaultRealm;
		}

		if (!realmCache.containsKey(host)) {
			Set<String> hosts = new HashSet<String>();
			hosts.add(host);
			int idx;
			if((idx = host.indexOf(":")) > -1) {
				hosts.add(host.substring(0,  idx));
			}
			for (Realm r : internalAllRealms()) {
				RealmProvider provider = getProviderForRealm(r);
				String[] realmHosts = provider.getValues(r, "realm.host");
				for (String realmHost : realmHosts) {
					if (realmHost != null && !"".equals(realmHost)) {
						if (hosts.contains(realmHost)) {
							realmCache.put(host, r);

							if(log.isDebugEnabled()) {
								log.debug(String.format("Returning resolved value for host %s realm %s", host, r.getName()));
							}
							return r;
						}
					}
				}
			}
			return defaultRealm;
		}

		Realm realm = (Realm) realmCache.get(host);
		
		if(log.isDebugEnabled()) {
			log.debug(String.format("Returning cached value for host %s realm %s", host, realm.getName()));
		}
		return realm;
	}

	@Override
	public String getRealmHostname(Realm realm) {
		RealmProvider provder = getProviderForRealm(realm);
		String[] names = provder.getValues(realm, "realm.host");
		if (names.length > 0) {
			return names[0];
		}
		return "";
	}

	@Override
	public Realm getRealmById(Long id) {

		Realm realm = realmRepository.getRealmById(id);
		
		return realm;
	}
	
	@Override
	public Realm getRealmByOwner(Long owner) throws AccessDeniedException {

		assertAnyPermission(RealmPermission.READ, SystemPermission.SWITCH_REALM);

		return realmRepository.getRealmByOwner(owner);
	}

	@Override
	public Map<String, String> filterSecretProperties(Principal principal, RealmProvider provider,
			Map<String, String> properties) {

		for (PropertyTemplate template : provider.getPropertyTemplates(principal)) {
			if (properties.containsKey(template.getResourceKey()) && template.isEncrypted()) {
				properties.put(template.getResourceKey(), "**********");
			}
		}

		if (principal != null) {
			for (UserAttribute attr : userAttributeService.getPersonalResources(principal)) {
				if (properties.containsKey(attr.getVariableName())) {
					if (attr.getEncrypted() || attr.getType() == AttributeType.PASSWORD) {
						properties.put(attr.getVariableName(), "**********");
					}
				}
			}
		}
		return properties;
	}

	@Override
	public Principal createLocalUser(Realm realm, String username, Map<String,String> properties,
			List<Principal> principals, String password, boolean forceChange, boolean selfCreated, boolean sendNotifications)
					throws ResourceException, AccessDeniedException {
		
		RealmProvider provider = getLocalProvider();
		
		return createUser(realm, username, properties, principals, password, forceChange, selfCreated, null, provider, sendNotifications);
	}
	
	private RealmProvider getLocalProvider() {
		return getProviderForRealm(LocalRealmProviderImpl.REALM_RESOURCE_CATEGORY);
	}
	
	@Override
	public Principal createUser(Realm realm, String username, Map<String, String> properties,
			List<Principal> principals, String password, boolean forceChange, boolean selfCreated, boolean sendNotifications)
					throws ResourceException, AccessDeniedException {
		return createUser(realm, 
				username, properties, 
				principals, password, 
				forceChange, selfCreated, 
				null, getProviderForRealm(realm),
				sendNotifications);
	}


	
	public Principal createUser(Realm realm, String username, Map<String, String> properties,
			List<Principal> principals, 
			String password, 
			boolean forceChange, 
			boolean selfCreated,
			Principal parent, 
			RealmProvider provider,
			boolean sendNotifications)
					throws ResourceException, AccessDeniedException {

		try {

			assertAnyPermission(UserPermission.CREATE, RealmPermission.CREATE);

			if (provider.isReadOnly(realm)) {
				throw new ResourceCreationException(RESOURCE_BUNDLE, "error.realmIsReadOnly");
			}
			
			Principal existing = getPrincipalByName(realm, username, PrincipalType.USER);
			if(existing!=null) {
				throw new ResourceCreationException(RESOURCE_BUNDLE, "error.principalAlreadyExists", username);
			}
			
			for (PrincipalProcessor processor : principalProcessors) {
				processor.beforeCreate(realm, provider.getModule(), username, password, properties);
			}
			
			Principal principal = provider.createUser(realm, username, properties, principals, password, forceChange);

			for (PrincipalProcessor processor : principalProcessors) {
				processor.afterCreate(principal, password, properties);
			}
			provider.reconcileUser(principal);
			eventService.publishEvent(new UserCreatedEvent(this, getCurrentSession(), realm, provider, principal,
					principals, filterSecretProperties(principal, provider, properties), password, forceChange,
					selfCreated));

			if(sendNotifications) {
				if(StringUtils.isNotBlank(principal.getEmail())) {
					if(selfCreated) {
						sendNewUserSelfCreatedNofification(principal, password);
					} else if(forceChange) {
						sendNewUserTemporaryPasswordNofification(principal, password);
					} else {
						sendNewUserFixedPasswordNotification(principal, password);
					}
				}
			}
			
			return principal;
		} catch (AccessDeniedException e) {
			eventService.publishEvent(new UserCreatedEvent(this, e, getCurrentSession(), realm, provider, username,
					filterSecretProperties(null, provider, properties), principals));
			throw e;
		} catch (ResourceCreationException e) {
			eventService.publishEvent(new UserCreatedEvent(this, e, getCurrentSession(), realm, provider, username,
					filterSecretProperties(null, provider, properties), principals));
			throw e;
		} catch (Exception e) {
			eventService.publishEvent(new UserCreatedEvent(this, e, getCurrentSession(), realm, provider, username,
					filterSecretProperties(null, provider, properties), principals));
			throw new ResourceCreationException(e, RESOURCE_BUNDLE, "error.unexpectedError", e.getMessage());
		}

	}

	private void sendNewUserTemporaryPasswordNofification(Principal principal, String password) throws ResourceException, AccessDeniedException {
		PrincipalWithPasswordResolver resolver = new PrincipalWithPasswordResolver((UserPrincipal)principal, password);
		messageService.sendMessage(MESSAGE_NEW_USER_TMP_PASSWORD, principal.getRealm(), resolver, principal);
	}

	private void sendNewUserSelfCreatedNofification(Principal principal, String password) throws ResourceException, AccessDeniedException {
		PrincipalWithPasswordResolver resolver = new PrincipalWithPasswordResolver((UserPrincipal)principal, password);
		messageService.sendMessage(MESSAGE_NEW_USER_SELF_CREATED, principal.getRealm(), resolver, principal);
	}
	
	private void sendNewUserFixedPasswordNotification(Principal principal, String password) throws ResourceException, AccessDeniedException {
		PrincipalWithPasswordResolver resolver = new PrincipalWithPasswordResolver((UserPrincipal)principal, password);
		messageService.sendMessage(MESSAGE_NEW_USER_NEW_PASSWORD, principal.getRealm(), resolver, principal);
	}

	@Override
	public Principal updateUserProperties(Principal user, 
			Map<String, String> properties) throws ResourceException, AccessDeniedException {

		final RealmProvider provider = getProviderForPrincipal(user);

		List<Principal> associated = getAssociatedPrincipals(user);
		try {

			assertAnyPermission(UserPermission.UPDATE, RealmPermission.UPDATE);

			for (PrincipalProcessor processor : principalProcessors) {
				processor.beforeUpdate(user, properties);
			}

			Principal principal = provider.updateUserProperties(user, properties);

			for (PrincipalProcessor processor : principalProcessors) {
				processor.afterUpdate(principal, properties);
			}

			eventService.publishEvent(new UserUpdatedEvent(this, getCurrentSession(), principal.getRealm(), provider, principal,
					associated, filterSecretProperties(principal, provider, properties)));

			return principal;
		} catch (AccessDeniedException e) {
			eventService.publishEvent(new UserUpdatedEvent(this, e, getCurrentSession(), user.getRealm(), provider, user.getPrincipalName(),
					filterSecretProperties(user, provider, properties), associated));
			throw e;
		} catch (ResourceChangeException e) {
			eventService.publishEvent(new UserUpdatedEvent(this, e, getCurrentSession(), user.getRealm(), provider, user.getPrincipalName(),
					filterSecretProperties(user, provider, properties), associated));
			throw e;
		} catch (Exception e) {
			eventService.publishEvent(new UserUpdatedEvent(this, e, getCurrentSession(), user.getRealm(), provider, user.getPrincipalName(),
					filterSecretProperties(user, provider, properties), associated));
			throw new ResourceChangeException(e, RESOURCE_BUNDLE, "updateUser.unexpectedError", e.getMessage());
		}
	}
	
	@Override
	public Principal updateUser(Realm realm, Principal user, String username, Map<String, String> properties,
			List<Principal> principals) throws ResourceException, AccessDeniedException {

		final RealmProvider provider = getProviderForPrincipal(user);

		try {

			assertAnyPermission(UserPermission.UPDATE, RealmPermission.UPDATE);

			for (PrincipalProcessor processor : principalProcessors) {
				processor.beforeUpdate(user, properties);
			}

			Principal principal = provider.updateUser(realm, user, username, properties, principals);

			for (PrincipalProcessor processor : principalProcessors) {
				processor.afterUpdate(principal, properties);
			}

			eventService.publishEvent(new UserUpdatedEvent(this, getCurrentSession(), realm, provider, principal,
					principals, filterSecretProperties(principal, provider, properties)));

			return principal;
		} catch (AccessDeniedException e) {
			eventService.publishEvent(new UserUpdatedEvent(this, e, getCurrentSession(), realm, provider, username,
					filterSecretProperties(user, provider, properties), principals));
			throw e;
		} catch (ResourceChangeException e) {
			eventService.publishEvent(new UserUpdatedEvent(this, e, getCurrentSession(), realm, provider, username,
					filterSecretProperties(user, provider, properties), principals));
			throw e;
		} catch (Exception e) {
			eventService.publishEvent(new UserUpdatedEvent(this, e, getCurrentSession(), realm, provider, username,
					filterSecretProperties(user, provider, properties), principals));
			throw new ResourceChangeException(e, RESOURCE_BUNDLE, "updateUser.unexpectedError", e.getMessage());
		}
	}

	@Override
	public boolean verifyPrincipal(final Principal principal) {
		
		Collection<PrincipalSuspension> suspensions = suspensionRepository.getSuspensions(principal);
		
		for(PrincipalSuspension s : suspensions) {
			if(s.isActive()) {
				return false;
			}
		}

		return true;
	}

	@Override
	public boolean verifyPassword(Principal principal, char[] password) throws LogonException, IOException {

		/**
		 * Adds support for session tokens. These can be created and used
		 * instead of passwords where we may not have, or want to distribute the
		 * password to an external service.
		 */
		try {
			String pwd = new String(password);
			if (pwd.startsWith(SessionServiceImpl.TOKEN_PREFIX)) {
				Principal tokenPrincipal = sessionService.getSessionTokenResource(pwd, Principal.class);
				return tokenPrincipal!=null && tokenPrincipal.equals(principal);
			} else {
				return getProviderForPrincipal(principal).verifyPassword(principal, password);
			}
		} catch(LogonException e) {
			if(configurationService.getBooleanValue(principal.getRealm(), "logon.verboseErrors")) {
				throw e;
			}
			return false;
		}
	}

	@Override
	public Principal getPrincipalByName(Realm realm, String principalName, PrincipalType... type) {
		if (type.length == 0) {
			type = PrincipalType.ALL_TYPES;
		}
		if (realm == null) {
			try {
				return getUniquePrincipal(principalName, type);
			} catch (ResourceNotFoundException e) {
				return null;
			}
		} else {
			try {
				return getUniquePrincipalForRealm(principalName, realm, type);			
			} catch (ResourceNotFoundException e) {
				return null;
			}
		}
	}

	@Override
	public void deleteRealm(String name)
			throws ResourceException, ResourceNotFoundException, AccessDeniedException {

		assertPermission(RealmPermission.DELETE);

		Realm realm = getRealmByName(name);

		if (realm == null) {
			throw new ResourceNotFoundException(RESOURCE_BUNDLE, "error.invalidRealm", name);
		}

		deleteRealm(realm);
	}

	@Override
	public List<Realm> allRealms() throws AccessDeniedException {
		assertAnyPermission(RealmPermission.READ, SystemPermission.SWITCH_REALM);

		return filterRealms(null, false);
	}

	@Override
	public List<Realm> allRealms(boolean ignoreMissingProvider) throws AccessDeniedException {
		assertAnyPermission(RealmPermission.READ, SystemPermission.SWITCH_REALM);
		return filterRealms(null, ignoreMissingProvider);
	}

	private List<Realm> internalAllRealms() {
		return filterRealms(null, false);
	}

	private List<Realm> filterRealms(Class<? extends RealmProvider> clz, boolean ignoreMissingProvider) {

		List<Realm> realms = realmRepository.allRealms();
		List<Realm> ret = new ArrayList<Realm>(realms);
		for (Realm r : realms) {
			if (!ignoreMissingProvider) {
				if (!hasProviderForRealm(r)) {
					ret.remove(r);
					continue;
				}
				if (clz != null && !clz.isAssignableFrom(getProviderForRealm(r).getClass())) {
					ret.remove(r);
				}
			}
		}
		return ret;
	}

	@Override
	public List<Realm> allRealms(Class<? extends RealmProvider> clz) {
		return filterRealms(clz, false);
	}

	@Override
	public void changePassword(final Principal principal, final String oldPassword, final String newPassword)
			throws ResourceException, AccessDeniedException {

		assertPermission(PasswordPermission.CHANGE);
		final RealmProvider provider = getProviderForPrincipal(principal);

		transactionService.doInTransaction(new TransactionCallbackWithError<Principal>() {

			@Override
			public Principal doInTransaction(TransactionStatus status) {
				
				try {
					for(PrincipalProcessor proc : principalProcessors) {
						proc.beforeChangePassword(principal, newPassword, oldPassword);
					}
					
					if (!verifyPassword(principal, oldPassword.toCharArray())) {
						throw new ResourceChangeException(RESOURCE_BUNDLE, "error.invalidPassword");
					}
	
					provider.changePassword(principal, oldPassword.toCharArray(), newPassword.toCharArray());
	
					setCurrentPassword(newPassword);
	
					for(PrincipalProcessor proc : principalProcessors) {
						proc.afterChangePassword(principal, newPassword, oldPassword);
					}
					
					messageService.sendMessage(MESSAGE_PASSWORD_CHANGED, 
							principal.getRealm(), 
							new PrincipalWithoutPasswordResolver((UserPrincipal)principal), 
							principal);
					
					eventService.publishEvent(new ChangePasswordEvent(this, getCurrentSession(), getCurrentRealm(), provider, newPassword));
					
					return principal;
				} catch(Throwable t) {
					throw new IllegalStateException(t.getMessage(), t);
				}
			}

			@Override
			public void doTransacationError(Throwable t) {
				eventService.publishEvent(new ChangePasswordEvent(this, t, getCurrentSession(), getCurrentRealm(), provider, newPassword));
			}
		});
	}

	@Override
	public void setPassword(Principal principal, String password, boolean forceChangeAtNextLogon, boolean administrative)
			throws ResourceException, AccessDeniedException {

		if (permissionService.hasSystemPermission(principal)) {
			try {
				assertPermission(SystemPermission.SYSTEM_ADMINISTRATION);
			} catch (AccessDeniedException e) {
				throw new ResourceCreationException(RESOURCE_BUNDLE, "error.sysadminOnly");
			}
		} else if (!getCurrentPrincipal().equals(principal)) {
			try {
				assertAnyPermission(UserPermission.CREATE, UserPermission.UPDATE);
			} catch (AccessDeniedException e) {
				throw new ResourceCreationException(RESOURCE_BUNDLE, "error.noUserChangePermission");
			}
		}

		RealmProvider provider = getProviderForPrincipal(principal);

		try {

			if (provider.isReadOnly(principal.getRealm())) {
				throw new ResourceCreationException(RESOURCE_BUNDLE, "error.realmIsReadOnly");
			}

			for(PrincipalProcessor proc : principalProcessors) {
				proc.beforeSetPassword(principal, password);
			}
			
			provider.setPassword(principal, password.toCharArray(), forceChangeAtNextLogon, administrative);

			for(PrincipalProcessor proc : principalProcessors) {
				proc.afterSetPassword(principal, password);
			}
			
			if(administrative) {
				messageService.sendMessage(MESSAGE_PASSWORD_RESET, 
						principal.getRealm(), 
						new PrincipalWithPasswordResolver((UserPrincipal)principal, password), 
						principal);
			} else {
				messageService.sendMessage(MESSAGE_PASSWORD_CHANGED, 
						principal.getRealm(), 
						new PrincipalWithoutPasswordResolver((UserPrincipal)principal), 
						principal);
			}
			
			eventService.publishEvent(
					new SetPasswordEvent(this, getCurrentSession(), getCurrentRealm(), 
							provider, principal, password, administrative));

		} catch (ResourceException ex) {
			eventService.publishEvent(new SetPasswordEvent(this, ex, getCurrentSession(), getCurrentRealm(), provider,
					principal.getPrincipalName(), password, administrative));
			throw ex;
		}

	}

	@Override
	public boolean isReadOnly(Realm realm) {

		RealmProvider provider = getProviderForRealm(realm);
		return provider.isReadOnly(realm);
	}

	@Override
	public Realm getSystemRealm() {
		if (systemRealm == null) {
			systemRealm = realmRepository.getSystemRealm();
		}
		return systemRealm;
	}

	@Override
	public Principal getSystemPrincipal() {
		if (systemPrincipal == null) {
			systemPrincipal = getPrincipalByName(realmRepository.getSystemRealm(), SYSTEM_PRINCIPAL, PrincipalType.SYSTEM);
		}
		return systemPrincipal;
	}

	@Override
	public Realm createPrimaryRealm(String name, String module, Map<String, String> properties)
			throws AccessDeniedException, ResourceException, ResourceConfirmationException {
		return createRealm(name, module, null, null, properties);
	}
	
	@Override
	public Realm createRealm(String name, String module, Realm parent, Long owner, Map<String, String> properties)
			throws AccessDeniedException, ResourceException, ResourceConfirmationException {

		try {
			assertPermission(RealmPermission.CREATE);

			if (realmRepository.getRealmByName(name) != null) {
				ResourceCreationException ex = new ResourceCreationException(RESOURCE_BUNDLE, "error.nameAlreadyExists",
						name);
				eventService.publishEvent(new RealmCreatedEvent(this, ex, getCurrentSession(), name, module));
				throw ex;
			}

			final RealmProvider realmProvider = getProviderForRealm(module);

			realmProvider.assertCreateRealm(properties);
			realmProvider.testConnection(properties);

			@SuppressWarnings("unchecked")
			Realm realm = realmRepository.createRealm(name, UUID.randomUUID().toString(), module, properties,
					realmProvider, parent, owner, owner==null, new TransactionAdapter<Realm>() {

				@Override
				public void afterOperation(Realm realm, Map<String,String> properties) {
					try {
						configurationService.setValue(realm, "realm.userEditableProperties","");
						configurationService.setValue(realm, "realm.userVisibleProperties",
								ResourceUtils.implodeValues(realmProvider.getDefaultUserPropertyNames()));
						
						realm.setReadOnly(realmProvider.isReadOnly(realm));
						realmRepository.saveRealm(realm);
						
						String externalHost = getRealmHostname(realm);
						if(StringUtils.isNotBlank(externalHost)) {
							configurationService.setValue("email.externalHostname", externalHost);
						}
						fireRealmCreate(realm);

					} catch (Throwable e) {
						throw new IllegalStateException(e.getMessage(), e);
					} 
				}
			});

			eventService.publishEvent(new RealmCreatedEvent(this, getCurrentSession(), realm));

			return realm;
		} catch (AccessDeniedException e) {
			eventService.publishEvent(new RealmCreatedEvent(this, e, getCurrentSession(), name, module));
			throw e;
		} catch (ResourceCreationException e) {
			eventService.publishEvent(new RealmCreatedEvent(this, e, getCurrentSession(), name, module));
			throw e;
		} catch(ResourceConfirmationException e) {
			throw e;
		} catch (Throwable t) {
			eventService.publishEvent(new RealmCreatedEvent(this, t, getCurrentSession(), name, module));
			throw new ResourceCreationException(t, RESOURCE_BUNDLE, "error.genericError", name, t.getMessage());
		}
	}

	private void clearCache(Realm realm) {
		RealmProvider realmProvider = getProviderForRealm(realm.getResourceCategory());

		String[] hosts = realmProvider.getValues(realm, "realm.host");
		for (String host : hosts) {
			realmCache.remove(host);
		}
	}

	@Override
	public void setRealmProperty(Realm realm, String resourceKey, String value) throws AccessDeniedException {

		assertAnyPermissionOrRealmAdministrator(PermissionScope.INCLUDE_CHILD_REALMS, RealmPermission.UPDATE);
		RealmProvider realmProvider = getProviderForRealm(realm.getResourceCategory());

		realmProvider.setValue(realm, resourceKey, value);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Realm resetRealm(Realm realm) throws ResourceException, AccessDeniedException {
		
		try {

			assertAnyPermissionOrRealmAdministrator(PermissionScope.INCLUDE_CHILD_REALMS, RealmPermission.UPDATE);

			String resourceCategory = realm.getResourceCategory();
			final RealmProvider oldProvider = getProviderForRealm(realm.getResourceCategory());
			
			final boolean changedType = !resourceCategory.equals(LocalRealmProviderImpl.REALM_RESOURCE_CATEGORY);
			
			if(!changedType) {
				throw new ResourceChangeException(RESOURCE_BUNDLE, "error.realmCannotBeReset");
			}
			
			final RealmProvider realmProvider = getProviderForRealm(realm.getResourceCategory());
			
			realm.setResourceCategory(LocalRealmProviderImpl.REALM_RESOURCE_CATEGORY);
			
			clearCache(realm);

			realm = realmRepository.saveRealm(realm, new HashMap<String,String>(), getProviderForRealm(realm), new TransactionAdapter<Realm>() {

				@Override
				public void afterOperation(Realm realm, Map<String,String> properties) {
					try {
						
						oldProvider.resetRealm(realm);
						realmProvider.resetRealm(realm);
						
						configurationService.setValue(realm, "realm.userEditableProperties",
								ResourceUtils.implodeValues(realmProvider.getDefaultUserPropertyNames()));
						
						realm.setReadOnly(false);
						realmRepository.saveRealm(realm);

						fireRealmUpdate(realm);

					} catch (Throwable e) {
						throw new IllegalStateException(e.getMessage(), e);
					} 
				}
			});

			eventService.publishEvent(new RealmUpdatedEvent(this, getCurrentSession(), realm.getName(),
					realmRepository.getRealmById(realm.getId())));

		} catch (AccessDeniedException e) {
			eventService.publishEvent(new RealmUpdatedEvent(this, e, getCurrentSession(), realm));
			throw e;
		} catch (ResourceChangeException e) {
			eventService.publishEvent(new RealmUpdatedEvent(this, e, getCurrentSession(), realm));
			throw e;
		} catch(ResourceConfirmationException e) {
			throw e;
		} catch (Throwable t) {
			log.error("Unexpected error", t);
			eventService.publishEvent(new RealmUpdatedEvent(this, t, getCurrentSession(), realm));
			throw new ResourceChangeException(t, RESOURCE_BUNDLE, "error.unexpectedError", t.getMessage());
		}
		return realm;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public Realm updateRealm(Realm realm, String name, String type, Map<String, String> properties)
			throws AccessDeniedException, ResourceException, ResourceConfirmationException {

		try {

			assertAnyPermissionOrRealmAdministrator(PermissionScope.INCLUDE_CHILD_REALMS, RealmPermission.UPDATE);

			if (!realm.getName().equalsIgnoreCase(name)) {
				if (realmRepository.getRealmByName(name) != null) {
					throw new ResourceChangeException(RESOURCE_BUNDLE, "error.nameAlreadyExists", name);
				}
			}

			String resourceCategory = realm.getResourceCategory();
			final boolean changedType = !resourceCategory.equals(type);
			
			realm.setResourceCategory(type);
			final RealmProvider realmProvider = getProviderForRealm(realm.getResourceCategory());
			
			/**
			 * Switch to system context in the updated realm so that updates from system realm 
			 * will be be able to correctly route through a secure node.
			 */
			setupSystemContext(realm);
			try {
				realmProvider.testConnection(properties, realm);
			} finally {
				clearPrincipalContext();
			}
			
			String oldName = realm.getName();

			clearCache(realm);

			realm.setName(name);

			realm = realmRepository.saveRealm(realm, properties, getProviderForRealm(realm), new TransactionAdapter<Realm>() {

				@Override
				public void afterOperation(Realm realm, Map<String,String> properties) {
					try {
						
						if(changedType) {
							configurationService.setValue(realm, "realm.userEditableProperties",
									ResourceUtils.implodeValues(realmProvider.getDefaultUserPropertyNames()));
							
							realm.setReadOnly(realmProvider.isReadOnly(realm));
							realmRepository.saveRealm(realm);
						}

						String externalHost = getRealmHostname(realm);
						if(StringUtils.isNotBlank(externalHost)) {
							configurationService.setValue("email.externalHostname", externalHost);
						}
						
						fireRealmUpdate(realm);

					} catch (Throwable e) {
						throw new IllegalStateException(e.getMessage(), e);
					} 
				}
			});

			eventService.publishEvent(new RealmUpdatedEvent(this, getCurrentSession(), oldName,
					realmRepository.getRealmById(realm.getId())));

		} catch (AccessDeniedException e) {
			eventService.publishEvent(new RealmUpdatedEvent(this, e, getCurrentSession(), realm));
			throw e;
		} catch (ResourceChangeException e) {
			eventService.publishEvent(new RealmUpdatedEvent(this, e, getCurrentSession(), realm));
			throw e;
		} catch(ResourceConfirmationException e) {
			throw e;
		} catch (Throwable t) {
			log.error("Unexpected error", t);
			eventService.publishEvent(new RealmUpdatedEvent(this, t, getCurrentSession(), realm));
			throw new ResourceChangeException(t, RESOURCE_BUNDLE, "error.unexpectedError", t.getMessage());
		}
		return realm;
	}

	private void fireRealmUpdate(Realm realm) throws ResourceException {

		for (RealmListener l : realmListeners) {
			try {
				l.onUpdateRealm(realm);
			} catch(ResourceChangeException e) { 
				throw e;
			} catch (Throwable t) {
				log.error("Caught error in RealmListener", t);
			}
		}
	}

	private void fireRealmCreate(Realm realm) throws ResourceException {

		Collections.<RealmListener>sort(realmListeners, new Comparator<RealmListener>() {

			@Override
			public int compare(RealmListener o1, RealmListener o2) {
				return o1.getWeight().compareTo(o2.getWeight());
			}
		});
		
		for (RealmListener l : realmListeners) {
			try {
				l.onCreateRealm(realm);
			} catch(ResourceCreationException e) { 
				throw e;
			} catch (Throwable t) {
				log.error("Caught error in RealmListener", t);
			}
		}
	}

	private void fireRealmDelete(Realm realm) throws ResourceException {

		for (RealmListener l : realmListeners) {
			try {
				l.onDeleteRealm(realm);
			} catch(ResourceChangeException e) { 
				throw e;
			} catch (Throwable t) {
				log.error("Caught error in RealmListener", t);
			}
		}
	}

	@Override
	public void deleteRealm(final Realm realm) throws AccessDeniedException, ResourceException {

		try {

			assertPermission(RealmPermission.DELETE);

			if (realm.isDefaultRealm()) {
				throw new ResourceChangeException(RESOURCE_BUNDLE, "error.cannotDeleteDefault", realm.getName());
			}
			
			if (realm.isSystem()) {
				throw new ResourceChangeException(RESOURCE_BUNDLE, "error.cannotDeleteSystem", realm.getName());
			}

			/**
			 * Get a copy of the realm to delete so we can fire events with the
			 * current realm detail as delete will rename it
			 */
			
			transactionService.doInTransaction(new TransactionCallback<Void>() {

				@Override
				public Void doInTransaction(TransactionStatus status) {
					try {
						Realm deletedRealm = getRealmById(realm.getId());

						clearCache(deletedRealm);

						sessionService.deleteRealm(realm);
						
						fireRealmDelete(deletedRealm);
						
						for(FindableResourceRepository<?> repository : EntityResourcePropertyStore.getRepositories()) {
							if(repository instanceof AbstractSimpleResourceRepository
									&& !(repository instanceof RealmRepository)
									&& !(repository instanceof PermissionRepository)) {
								AbstractResourceRepository<?> r = (AbstractResourceRepository<?>)repository;
								if(r.isDeletable()) {
									r.deleteRealm(realm);
								}
							}
							if(repository instanceof AbstractAssignableResourceRepository) {
								AbstractAssignableResourceRepository<?> r = (AbstractAssignableResourceRepository<?>)repository;
								if(r.isDeletable()) {
									r.deleteRealm(realm);
								}
							}
						}
						
						permissionRepository.deleteRealm(realm);
						
						ouRepository.deleteRealm(realm);
						getLocalProvider().deleteRealm(realm);
						
						RealmProvider provider = getProviderForRealm(realm);
						provider.deleteRealm(realm);
						
						passwordPolicyService.deleteRealm(realm);
						configurationService.deleteRealm(realm);
						
						realmRepository.delete(deletedRealm);
						return null;
					} catch (ResourceException e) {
						throw new IllegalStateException(e.getMessage(), e);
					}
				}
			});
			

			eventService.publishEvent(new RealmDeletedEvent(this, getCurrentSession(), realm));

		} catch (AccessDeniedException e) {
			eventService.publishEvent(new RealmDeletedEvent(this, e, getCurrentSession(), realm));
			throw e;
		} catch (ResourceChangeException e) {
			eventService.publishEvent(new RealmDeletedEvent(this, e, getCurrentSession(), realm));
			throw e;
		} catch (Throwable t) {
			eventService.publishEvent(new RealmDeletedEvent(this, t, getCurrentSession(), realm));
			throw new ResourceChangeException(t, RESOURCE_BUNDLE, "error.unexpectedError");
		}
	}

	@Override
	public Realm setDefaultRealm(Realm realm) throws AccessDeniedException {
		assertPermission(SystemPermission.SYSTEM_ADMINISTRATION);

		return realmRepository.setDefaultRealm(realm);
	}

	@Override
	public Collection<PropertyCategory> getRealmPropertyTemplates(Realm realm) throws AccessDeniedException {

		assertAnyPermissionOrRealmAdministrator(PermissionScope.INCLUDE_CHILD_REALMS, RealmPermission.READ);

		RealmProvider provider = getProviderForRealm(realm);

		return provider.getRealmProperties(realm);

	}

	@Override
	public Collection<PropertyCategory> getRealmPropertyTemplates(String module) throws AccessDeniedException {

		assertAnyPermissionOrRealmAdministrator(PermissionScope.INCLUDE_CHILD_REALMS, RealmPermission.READ);

		RealmProvider provider = getProviderForRealm(module);

		return provider.getRealmProperties(null);
	}

	@Override
	@Deprecated
	public Principal getPrincipalById(Realm realm, Long id, PrincipalType... type) throws AccessDeniedException {

		Principal principal = principalRepository.getResourceById(id);
		return principal;
	}
	
	@Override
	public Principal getPrincipalById(Long id) {

		Principal principal = principalRepository.getResourceById(id);
		return principal;
	}
	
	@Override
	public Principal getDeletedPrincipalById(Realm realm, Long id, PrincipalType... type) throws AccessDeniedException {

		assertAnyPermission(UserPermission.READ, GroupPermission.READ, RealmPermission.READ);

		if (type.length == 0) {
			type = PrincipalType.ALL_TYPES;
		}
		Principal principal = getProviderForRealm(realm).getDeletedPrincipalById(id, realm, type);
		if(principal==null) {
			return getLocalProvider().getDeletedPrincipalById(id, realm, type);
		}
		return principal;
	}
	
	@Override
	public Principal getPrincipalByEmail(Realm realm, String email) throws AccessDeniedException, ResourceNotFoundException {

		assertAnyPermission(UserPermission.READ, GroupPermission.READ, RealmPermission.READ);

		Principal principal = getProviderForRealm(realm).getPrincipalByEmail(realm, email);
		if(principal==null) {
			principal = getLocalProvider().getPrincipalByEmail(realm, email);
		}
		if(principal==null) {
			throw new ResourceNotFoundException(RESOURCE_BUNDLE,"error.noPrincipalForEmail", email);
		}
		return principal;
	}	

	@Override
	public boolean requiresPasswordChange(Principal principal, Realm realm) {
		return getProviderForPrincipal(principal).requiresPasswordChange(principal);
	}

	@Override
	public Principal createGroup(Realm realm, String name, Map<String, String> properties, 
			final List<Principal> principals,
			final List<Principal> groups) throws ResourceException, AccessDeniedException {

		RealmProvider provider = getProviderForRealm(realm);

		try {
			assertAnyPermission(GroupPermission.CREATE, RealmPermission.CREATE);

			if (provider.isReadOnly(realm)) {
				throw new ResourceCreationException(RESOURCE_BUNDLE, "error.realmIsReadOnly");
			}

			Principal group = getPrincipalByName(realm, name, PrincipalType.GROUP);

			if (group != null) {
				ResourceCreationException ex = new ResourceCreationException(RESOURCE_BUNDLE,
						"error.group.alreadyExists", name);
				throw ex;
			}

			Principal principal = provider.createGroup(realm, name, properties, principals, groups);

			eventService.publishEvent(new GroupCreatedEvent(this, 
					getCurrentSession(), 
					realm, 
					provider, 
					principal,
					principals));
			return principal;

		} catch (AccessDeniedException e) {
			eventService.publishEvent(
					new GroupCreatedEvent(this, e, getCurrentSession(), realm, provider, name, principals));
			throw e;
		} catch (ResourceCreationException e) {
			eventService.publishEvent(
					new GroupCreatedEvent(this, e, getCurrentSession(), realm, provider, name, principals));
			throw e;
		} catch (Exception e) {
			eventService.publishEvent(
					new GroupCreatedEvent(this, e, getCurrentSession(), realm, provider, name, principals));
			throw new ResourceCreationException(e, RESOURCE_BUNDLE, "createGroup.unexpectedError", e.getMessage());
		}
	}

	@Override
	public Principal updateGroup(final Realm realm, final Principal group, final String name,
			final Map<String, String> properties, final List<Principal> principals, final List<Principal> groups)
					throws ResourceException, AccessDeniedException {

		final RealmProvider provider = getProviderForRealm(realm);

		assertAnyPermission(GroupPermission.UPDATE, RealmPermission.UPDATE);

		if (provider.isReadOnly(realm)) {
			throw new ResourceChangeException(RESOURCE_BUNDLE, "error.realmIsReadOnly");
		}

		Principal tmpGroup = getPrincipalByName(realm, name, PrincipalType.GROUP);

		if (tmpGroup != null && !tmpGroup.getId().equals(group.getId())) {
			ResourceChangeException ex = new ResourceChangeException(RESOURCE_BUNDLE, "error.group.alreadyExists",
					name);
			throw ex;
		}
		
		Collection<Principal> existingPrincipals = new HashSet<Principal>();
		existingPrincipals.addAll(getGroupUsers(group));
		existingPrincipals.addAll(getGroupGroups(group));
		
		final Collection<Principal> assigned = new ArrayList<Principal>();
		assigned.addAll(principals);
		assigned.addAll(groups);
		
		assigned.removeAll(existingPrincipals);
		
		final Collection<Principal> unassigned = new ArrayList<Principal>();
		unassigned.addAll(existingPrincipals);
		unassigned.removeAll(principals);
		unassigned.removeAll(groups);
		
		final Collection<Principal> all = new HashSet<Principal>();
		all.addAll(principals);
		all.addAll(groups);
		
		return transactionService.doInTransaction(new TransactionCallbackWithError<Principal>() {

			@Override
			public Principal doInTransaction(TransactionStatus arg0) {

				try {
					for (PrincipalProcessor processor : principalProcessors) {
						processor.beforeUpdate(group, properties);
					}

					Principal principal = provider.updateGroup(realm, group, name, properties, principals, groups);

					for (PrincipalProcessor processor : principalProcessors) {
						processor.afterUpdate(principal, properties);
					}

					eventService.publishEvent(new GroupUpdatedEvent(this, 
							getCurrentSession(), 
							realm, provider,
							principal, all, assigned, unassigned));

					return principal;
				} catch (ResourceChangeException e) {
					throw new IllegalStateException(e.getMessage(), e);
				} catch (Throwable e) {
					ResourceChangeException ex = new ResourceChangeException(RESOURCE_BUNDLE, "groupUser.unexpectedError", e.getMessage(), e);
					throw new IllegalStateException(ex.getMessage(), ex);
				}
			}

			@Override
			public void doTransacationError(Throwable e) {
				eventService.publishEvent(
						new GroupUpdatedEvent(this, e, getCurrentSession(), realm, provider, name, principals));
			}

		});
	}

	@Override
	public void deleteGroup(Realm realm, Principal group) throws ResourceException, AccessDeniedException {

		RealmProvider provider = getProviderForRealm(realm);

		Collection<Principal> assosiatedPrincipals = provider.getAssociatedPrincipals(group);
		
		try {
			assertAnyPermission(GroupPermission.DELETE, RealmPermission.DELETE);

			if (provider.isReadOnly(realm)) {
				throw new ResourceCreationException(RESOURCE_BUNDLE, "error.realmIsReadOnly");
			}

			provider.deleteGroup(group);

			eventService.publishEvent(new GroupDeletedEvent(this, getCurrentSession(), realm, provider, group, assosiatedPrincipals));

		} catch (AccessDeniedException e) {
			eventService.publishEvent(
					new GroupDeletedEvent(this, e, getCurrentSession(), realm, provider, group.getPrincipalName(), assosiatedPrincipals));
			throw e;
		} catch (ResourceChangeException e) {
			eventService.publishEvent(
					new GroupDeletedEvent(this, e, getCurrentSession(), realm, provider, group.getPrincipalName(), assosiatedPrincipals));
			throw e;
		} catch (Throwable e) {
			eventService.publishEvent(
					new GroupDeletedEvent(this, e, getCurrentSession(), realm, provider, group.getPrincipalName(), assosiatedPrincipals));
			throw new ResourceChangeException(e, RESOURCE_BUNDLE, "deleteGroup.unexpectedError", e.getMessage());
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public void deleteUser(Realm realm, Principal user) throws ResourceException, AccessDeniedException {

		final RealmProvider provider = getProviderForPrincipal(user);

		try {
			assertAnyPermission(UserPermission.DELETE, RealmPermission.DELETE);

			if (provider.isReadOnly(realm)) {
				throw new ResourceCreationException(RESOURCE_BUNDLE, "error.realmIsReadOnly");
			}

			if (permissionService.hasSystemPermission(user)) {
				throw new ResourceChangeException(RESOURCE_BUNDLE, "error.cannotDeleteSystemAdmin",
						user.getPrincipalName());
			}

			permissionService.revokePermissions(user, new TransactionAdapter<Principal>() {
				@Override
				public void afterOperation(Principal resource, Map<String, String> properties) throws ResourceException {
					try {
						provider.deleteUser(resource);
					} catch (ResourceChangeException e) {
						throw new IllegalStateException(e.getMessage(), e);
					}
				}
			});

			eventService.publishEvent(new UserDeletedEvent(this, getCurrentSession(), realm, provider, user));

		} catch (AccessDeniedException e) {
			eventService.publishEvent(
					new UserDeletedEvent(this, e, getCurrentSession(), realm, provider, user.getPrincipalName()));
			throw e;
		} catch (ResourceChangeException e) {
			eventService.publishEvent(
					new UserDeletedEvent(this, e, getCurrentSession(), realm, provider, user.getPrincipalName()));
			throw e;
		} catch (Throwable e) {
			eventService.publishEvent(
					new UserDeletedEvent(this, e, getCurrentSession(), realm, provider, user.getPrincipalName()));
			throw new ResourceChangeException(e, RESOURCE_BUNDLE, "deleteUser.unexpectedError", e.getMessage());
		}

	}

	@Override
	public String getPrincipalAddress(Principal principal, MediaType type) throws MediaNotFoundException {

		RealmProvider provider = getProviderForPrincipal(principal);
		return provider.getAddress(principal, type);
	}

	@Override
	public Collection<PropertyCategory> getGroupPropertyTemplates(Principal principal) throws AccessDeniedException {

		assertAnyPermission(UserPermission.READ, ProfilePermission.READ, RealmPermission.READ);

		RealmProvider provider = getProviderForPrincipal(principal);

		return provider.getGroupProperties(principal);
	}

	@Override
	public Collection<PropertyCategory> getUserPropertyTemplates(Principal principal) throws AccessDeniedException {

		assertAnyPermission(UserPermission.READ, ProfilePermission.READ, RealmPermission.READ);

		RealmProvider provider = getProviderForPrincipal(principal);

		return provider.getUserProperties(principal);
	}

	@Override
	public Collection<PropertyCategory> getUserProfileTemplates(Principal principal) throws AccessDeniedException {

		assertAnyPermission(ProfilePermission.READ);

		RealmProvider provider = getProviderForPrincipal(principal);
		RealmProvider realmProvider = getProviderForRealm(principal.getRealm());
		
		Collection<PropertyCategory> ret = provider.getUserProperties(principal);

		Set<String> editable = new HashSet<String>(
				Arrays.asList(configurationService.getValues(principal.getRealm(), "realm.userEditableProperties")));
		Set<String> visible = new HashSet<String>(
				Arrays.asList(configurationService.getValues(principal.getRealm(), "realm.userVisibleProperties")));

		/**
		 * Filter the properties down to read only and editable as defined by
		 * the realm configuration.
		 */

		List<PropertyCategory> results = new ArrayList<PropertyCategory>();

		for (PropertyCategory c : ret) {

			List<AbstractPropertyTemplate> tmp = new ArrayList<AbstractPropertyTemplate>();

			for (AbstractPropertyTemplate t : c.getTemplates()) {

				if (c.isUserCreated()) {
					/**
					 * Custom user created properties
					 */
					if (t.getDisplayMode() != null && t.getDisplayMode().equals("admin")) {
						tmp.add(t);
					}

				} else {
					/**
					 * These are built-in realm properties
					 */
					if(!realmProvider.equals(provider)) {
						if (t.getDisplayMode() != null && t.getDisplayMode().equals("admin")) {
							tmp.add(t);
						}
						continue;
					} else {
						if (!editable.contains(t.getResourceKey())) {
							if (!visible.contains(t.getResourceKey())) {
								tmp.add(t);
								continue;
							}
							t.setReadOnly(true);
							continue;
						}
					}
				}
			}

			c.getTemplates().removeAll(tmp);

			if (c.getTemplates().size() > 0) {
				results.add(c);
			}
		}
		

		return results;
	}

	@Override
	public Collection<PropertyCategory> getUserPropertyTemplates(String module) throws AccessDeniedException {

		assertAnyPermission(UserPermission.READ, ProfilePermission.READ, RealmPermission.READ);

		RealmProvider provider = getProviderForRealm(module);

		return provider.getUserProperties(null);
	}

	@Override
	public Collection<PropertyCategory> getUserPropertyTemplates() throws AccessDeniedException {

		assertAnyPermission(UserPermission.READ, ProfilePermission.READ, RealmPermission.READ);

		RealmProvider provider = getProviderForRealm(getCurrentRealm());

		return provider.getUserProperties(null);
	}

	@Override
	public Collection<String> getUserPropertyNames(Realm realm, Principal principal) throws AccessDeniedException {

		assertAnyPermission(UserPermission.READ, ProfilePermission.READ, RealmPermission.READ);

		RealmProvider provider = principal != null ? getProviderForPrincipal(principal) : getProviderForRealm(realm);

		return provider.getUserPropertyNames(principal);

	}
	
	@Override
	public Collection<String> getEditablePropertyNames(Realm realm) throws AccessDeniedException {

		assertAnyPermission(UserPermission.READ, ProfilePermission.READ, RealmPermission.READ);

		RealmProvider provider = getProviderForRealm(realm);

		return provider.getEditablePropertyNames(realm);

	}
	
	@Override
	public Collection<String> getVisiblePropertyNames(Realm realm) throws AccessDeniedException {

		assertAnyPermission(UserPermission.READ, ProfilePermission.READ, RealmPermission.READ);

		RealmProvider provider = getProviderForRealm(realm);

		return provider.getVisiblePropertyNames(realm);

	}

	@Override
	public Collection<String> getUserPropertyNames(String module) throws AccessDeniedException {

		assertAnyPermission(UserPermission.READ, RealmPermission.READ);

		RealmProvider provider = getProviderForRealm(module);

		return provider.getUserPropertyNames(null);
	}

	@Override
	public Collection<PropertyCategory> getGroupPropertyTemplates(String module) throws AccessDeniedException {

		assertAnyPermission(GroupPermission.READ, RealmPermission.READ);

		RealmProvider provider = getProviderForRealm(module);

		return provider.getGroupProperties(null);
	}

	@Override
	public List<Principal> getAssociatedPrincipals(Principal principal) {

		Collection<Principal> result = getProviderForPrincipal(principal).getAssociatedPrincipals(principal);
		if (!result.contains(principal)) {
			result.add(principal);
		}
		return new ArrayList<Principal>(result);
	}
	
	@Override
	public List<Principal> getUserGroups(Principal principal) {
		return getProviderForPrincipal(principal).getUserGroups(principal);
	}
	
	@Override
	public List<Principal> getGroupUsers(Principal principal) {
		return getProviderForPrincipal(principal).getGroupUsers(principal);
	}
	
	@Override
	public List<Principal> getGroupGroups(Principal principal) {
		return getProviderForPrincipal(principal).getGroupGroups(principal);
	}

	@Override
	public List<Principal> getAssociatedPrincipals(Principal principal, PrincipalType type) {
		List<Principal> result =  getProviderForPrincipal(principal).getAssociatedPrincipals(principal, type);
		if (!result.contains(principal)) {
			result.add(principal);
		}
		return result;
	}

	@Override
	public List<?> searchPrincipals(Realm realm, PrincipalType type, String searchColumn, String searchPattern, int start, int length,
			ColumnSort[] sorting) throws AccessDeniedException {
		return searchPrincipals(realm, type, searchColumn, "name", searchPattern, start, length, sorting);
	}
	
	@Override
	public List<?> searchPrincipals(Realm realm, PrincipalType type, String module, String searchColumn, String searchPattern, int start, int length,
			ColumnSort[] sorting) throws AccessDeniedException {

		assertAnyPermission(UserPermission.READ, GroupPermission.READ, ProfilePermission.READ, RealmPermission.READ);

		switch(type) {
		case USER:
		{
			if(StringUtils.isNotBlank(module)) {
				TableFilter filter = principalFilters.get(module);
				if(filter==null) {
					filter = builtInPrincipalFilters.get(module);
				}
				return filter.searchResources(realm, searchColumn, searchPattern, start, length, sorting);
			} else {
				return principalRepository.search(realm, type, searchColumn, searchPattern, start, length, sorting);
			}
		}
		default:
		{
			if(StringUtils.isNotBlank(module)) {
				RealmProvider provider = getProviderForRealm(module);
				return provider.getPrincipals(realm, type, searchColumn, searchPattern, start, length, sorting);
			} else {
				return principalRepository.search(realm, type, searchColumn, searchPattern, start, length, sorting);
			}
		}
		}
	}

	@Override
	public Long getSearchPrincipalsCount(Realm realm, PrincipalType type, String searchColumn, String searchPattern) throws AccessDeniedException {
		return getSearchPrincipalsCount(realm, type, null, searchColumn, searchPattern);
	}
	
	@Override
	public Long getSearchPrincipalsCount(Realm realm, PrincipalType type, String module, String searchColumn, String searchPattern) throws AccessDeniedException {

		assertAnyPermission(UserPermission.READ, GroupPermission.READ, ProfilePermission.READ, RealmPermission.READ);

		
		
		switch(type) {
		case USER:
		{
			if(StringUtils.isNotBlank(module)) {
				TableFilter filter = principalFilters.get(module);
				if(filter==null) {
					filter = builtInPrincipalFilters.get(module);
				}
				return filter.searchResourcesCount(realm, searchColumn, searchPattern);
			} else {
				return principalRepository.getResourceCount(realm, type, searchColumn, searchPattern);
			}
		}
		default:
		{
			if(StringUtils.isNotBlank(module)) {
				RealmProvider provider = getProviderForRealm(module);
				return provider.getPrincipalCount(realm, type, searchColumn, searchPattern);
			} else {
				return principalRepository.getResourceCount(realm, type, searchColumn, searchPattern);
			}
		}
		}
	}

	@Override
	public boolean findUniquePrincipal(String user) {

		int found = 0;
		for (Realm r : internalAllRealms()) {
			Principal p = getPrincipalByName(r, user, PrincipalType.USER);
			if (p != null) {
				found++;
			}
		}
		return found == 1;
	}

	@Override
	public Principal getUniquePrincipal(String username, PrincipalType... type) throws ResourceNotFoundException {

		String realmName = null;
		// Can we extract realm from username?
		int idx;
		idx = username.indexOf('\\');
		if (idx > -1) {
			realmName = username.substring(0, idx);
			username = username.substring(idx + 1);
		} else {
			idx = username.indexOf('/');
			if (idx > -1) {
				realmName = username.substring(0, idx);
				username = username.substring(idx + 1);
			}
		}

		if (realmName != null) {
			Realm realm = getRealmByName(realmName);
			if(realm==null) {
				throw new ResourceNotFoundException(RESOURCE_BUNDLE, "error.invalidRealm", realmName);
			}
			return getPrincipalByName(realm, username, PrincipalType.USER);
		}
		
		Collection<Principal> found = principalRepository.getPrincpalsByName(username, type);
		return selectPrincipal(found, username);
	}
	
	@Override
	public Principal getUniquePrincipalForRealm(String username, Realm realm, PrincipalType... type) throws ResourceNotFoundException {	
		Collection<Principal> found = principalRepository.getPrincpalsByName(username, realm, type);
		return selectPrincipal(found, username);
	}

	protected Principal selectPrincipal(Collection<Principal> found, String username) throws ResourceNotFoundException {
		if (found.size() != 1) {
			if (found.size() > 1) {
				// Fire Event 
				if(log.isInfoEnabled()) {
					log.info("More than one principal found for username " + username);
				}
				for(Principal principal : found) {
					if(principal.getRealm().isDeleted()) {
						continue;
					}
					if(principal.isSystem() || permissionService.hasAdministrativePermission(principal)) {
						log.info(String.format("Resolving duplicate principals to %s/%s [System User]", principal.getRealm().getName(), principal.getPrincipalName()));
						return principal;
					} 
				}
				for(Principal principal : found) {
					if(principal.getRealm().isDeleted()) {
						continue;
					}
					if(principal.getRealm().isDefaultRealm()) {
						log.info(String.format("Resolving duplicate principals to %s/%s [Default Realm]", principal.getRealm().getName(), principal.getPrincipalName()));
						return principal;
					}
				}
				
			}
			throw new ResourceNotFoundException(RESOURCE_BUNDLE, "principal.notFound");
		}
		return found.iterator().next();
	}
	
	@Override
	public List<Realm> getRealms(String searchPattern, String searchColumn, int start, int length, ColumnSort[] sorting)
			throws AccessDeniedException {

		assertPermission(RealmPermission.READ);

		return realmRepository.searchRealms(searchPattern, searchColumn, start, length, sorting);
	}

	@Override
	public Long getRealmCount(String searchPattern, String searchColumn) throws AccessDeniedException {

		assertPermission(RealmPermission.READ);

		return realmRepository.countRealms(searchPattern, searchColumn);
	}

	@Override
	public void updateProfile(Realm realm, Principal principal, Map<String, String> properties)
			throws AccessDeniedException, ResourceException {

		RealmProvider provider = getProviderForPrincipal(principal);
		RealmProvider realmProvider = getProviderForRealm(principal.getRealm());
		Map<String, String> changedProperties = new HashMap<String, String>();
		
		if(realmProvider.equals(provider)) {
			/**
			 * This ensures we only ever update those properties that are allowed
			 */
			String[] editableProperties = configurationService.getValues(realm, "realm.userEditableProperties");

			Collection<PropertyTemplate> userAttributes = userAttributeService.getPropertyResolver().getPropertyTemplates(principal);
	
			for (String allowed : editableProperties) {
				if (properties.containsKey(allowed)) {
					changedProperties.put(allowed, properties.get(allowed));
				}
			}
	
			for (PropertyTemplate t : userAttributes) {
				if (properties.containsKey(t.getResourceKey())) {
					if (t.getDisplayMode() == null || !t.getDisplayMode().equals("admin")) {
						changedProperties.put(t.getResourceKey(), properties.get(t.getResourceKey()));
					}
				}
			}
		} else {
			changedProperties.putAll(properties);
		}

		try {
			assertAnyPermission(ProfilePermission.UPDATE, RealmPermission.UPDATE, UserPermission.UPDATE);

			principal = provider.updateUserProperties(principal, changedProperties);

			eventService.publishEvent(new ProfileUpdatedEvent(this, getCurrentSession(), realm, provider, principal,
					filterSecretProperties(principal, provider, changedProperties)));
		} catch (AccessDeniedException e) {
			eventService.publishEvent(new ProfileUpdatedEvent(this, e, getCurrentSession(), realm, provider,
					principal.getPrincipalName(), filterSecretProperties(principal, provider, changedProperties)));
			throw e;
		} catch (ResourceChangeException e) {
			eventService.publishEvent(new ProfileUpdatedEvent(this, e, getCurrentSession(), realm, provider,
					principal.getPrincipalName(), filterSecretProperties(principal, provider, changedProperties)));
			throw e;
		}

	}

	@Override
	public String getPrincipalDescription(Principal principal) {

		RealmProvider provider = getProviderForRealm(principal.getRealm());

		return provider.getPrincipalDescription(principal);
	}

	@Override
	public boolean supportsAccountUnlock(Realm realm) throws ResourceException {

		RealmProvider provider = getProviderForRealm(realm);

		return provider.supportsAccountUnlock(realm);
	}

	@Override
	public boolean supportsAccountDisable(Realm realm) throws ResourceException {

		RealmProvider provider = getProviderForRealm(realm);

		return provider.supportsAccountDisable(realm);
	}

	@Override
	public Principal disableAccount(Principal principal) throws AccessDeniedException, ResourceException {

		assertAnyPermission(UserPermission.UPDATE, RealmPermission.UPDATE);

		RealmProvider provider = getProviderForRealm(principal.getRealm());

		if (provider.isReadOnly(principal.getRealm())) {
			throw new ResourceChangeException(RESOURCE_BUNDLE, "error.realmIsReadOnly");
		}

		return provider.disableAccount(principal);

	}

	@Override
	public Principal enableAccount(Principal principal) throws AccessDeniedException, ResourceException {

		assertAnyPermission(UserPermission.UPDATE, RealmPermission.UPDATE);

		RealmProvider provider = getProviderForRealm(principal.getRealm());

		if (provider.isReadOnly(principal.getRealm())) {
			throw new ResourceChangeException(RESOURCE_BUNDLE, "error.realmIsReadOnly");
		}

		return provider.enableAccount(principal);
	}

	@Override
	public Principal unlockAccount(Principal principal) throws AccessDeniedException, ResourceException {

		assertAnyPermission(UserPermission.UPDATE, RealmPermission.UPDATE);

		RealmProvider provider = getProviderForRealm(principal.getRealm());

		if (provider.isReadOnly(principal.getRealm())) {
			throw new ResourceChangeException(RESOURCE_BUNDLE, "error.realmIsReadOnly");
		}

		return provider.unlockAccount(principal);
	}

	@Override
	public void registerRealmListener(RealmListener listener) {
		realmListeners.add(listener);
	}

	@Override
	public Realm getDefaultRealm() {
		return realmRepository.getDefaultRealm();
	}

	class RealmPropertyCollector implements EventPropertyCollector {

		@Override
		public Set<String> getPropertyNames(String resourceKey, Realm realm) {
			RealmProvider provider = getProviderForRealm(realm);
			return provider.getPropertyNames(null);
		}

	}

	class UserPropertyCollector implements EventPropertyCollector {

		@Override
		public Set<String> getPropertyNames(String resourceKey, Realm realm) {
			RealmProvider provider = getProviderForRealm(realm);
			return provider.getUserPropertyNames(null);
		}
	}

	class GroupPropertyCollector implements EventPropertyCollector {

		@Override
		public Set<String> getPropertyNames(String resourceKey, Realm realm) {
			RealmProvider provider = getProviderForRealm(realm);
			return provider.getGroupPropertyNames(null);
		}

	}

	@Override
	public boolean isRealmStrictedToHost(Realm realm) {

		if (realm == null) {
			return false;
		}
		RealmProvider realmProvider = getProviderForRealm(realm);
		return realmProvider.getBooleanValue(realm, "realm.hostRestriction");

	}

	@Override
	public Collection<String> getUserVariableNames(Realm realm, Principal principal) {

		RealmProvider provider = getProviderForRealm(realm);

		Set<String> tmp = new HashSet<String>(UserVariableReplacementServiceImpl.getDefaultReplacements());
		tmp.addAll(provider.getUserVariableNames(principal));
		return tmp;

	}

	@Override
	public String getPrincipalEmail(Principal principal) {
		return principal.getEmail();
	}

	@Override
	public String getPrincipalPhone(Principal principal) {
		try {
			return getPrincipalAddress(principal, MediaType.PHONE);
		} catch (MediaNotFoundException e) {
			return "";
		}
	}
	
	@Override
	public String getProfileProperty(Principal principal, String resourceKey) {
		
		RealmProvider provider = getProviderForPrincipal(principal);
		return provider.getUserPropertyValue(principal, resourceKey);
	}

	@Override
	public Map<String, String> getUserPropertyValues(Principal principal, String... variableNames) {

		Map<String, String> variables = new HashMap<String, String>();

		for (String variableName : variableNames) {
			variables.put(variableName, userVariableReplacement.getVariableValue(principal, variableName));
		}

		return variables;
	}
	
	@Override
	public Map<String, String> getUserPropertyValues(Principal principal) {

		RealmProvider provider = getProviderForPrincipal(principal);
		return provider.getUserPropertyValues(principal);
	}

	@Override
	public Long getUserPropertyLong(Principal principal, String resourceKey) {
		RealmProvider provider = getProviderForPrincipal(principal);
		return provider.getUserPropertyLong(principal, resourceKey);
	
	}
	
	@Override
	public Integer getUserPropertyInt(Principal principal, String resourceKey) {
		RealmProvider provider = getProviderForPrincipal(principal);
		return provider.getUserPropertyInt(principal, resourceKey);
	}
	
	@Override
	public boolean getUserPropertyBoolean(Principal principal, String resourceKey) {
		RealmProvider provider = getProviderForPrincipal(principal);
		return provider.getUserPropertyBoolean(principal, resourceKey);
	}
	
	@Override
	public String getUserProperty(Principal principal, String resourceKey) {
		RealmProvider provider = getProviderForPrincipal(principal);
		return provider.getUserProperty(principal, resourceKey);
	}
	
	
	
	@Override
	public long getPrincipalCount(Realm realm, PrincipalType type) {
		return principalRepository.getResourceCount(realm, type);
	}
	
	@Override
	public long getPrincipalCount(Collection<Realm> realms, PrincipalType type) {
		return principalRepository.getResourceCount(realms, type);
	}

	@Override
	public boolean canChangePassword(Principal principal) {
		
		RealmProvider provider = getProviderForPrincipal(principal);
		
		return provider.canChangePassword(principal);
	}

	@Override
	public Collection<PropertyCategory> getUserProperties(Principal principal) {
		
		RealmProvider provider = getProviderForPrincipal(principal);
		return provider.getUserProperties(principal);
	}

	@Override
	public void setUserPropertyLong(Principal principal, String resourceKey, Long val) {
		RealmProvider provider = getProviderForPrincipal(principal);
		provider.setUserProperty(principal, resourceKey, val);
	}

	@Override
	public void setUserPropertyInt(Principal principal, String resourceKey, Integer val) {
		RealmProvider provider = getProviderForPrincipal(principal);
		provider.setUserProperty(principal, resourceKey, val);
	}

	@Override
	public void setUserPropertyBoolean(Principal principal, String resourceKey, Boolean val) {
		RealmProvider provider = getProviderForPrincipal(principal);
		provider.setUserProperty(principal, resourceKey, val);
	}

	@Override
	public void setUserProperty(Principal principal, String resourceKey, String val) {
		RealmProvider provider = getProviderForPrincipal(principal);
		provider.setUserProperty(principal, resourceKey, val);
	}

	@Override
	public void deleteRealms(final List<Realm> resources)
			throws ResourceException, AccessDeniedException {
		transactionService.doInTransaction(new TransactionCallback<Void>() {

			@Override
			public Void doInTransaction(TransactionStatus status) {
				for (Realm realm : resources) {
					try {
						deleteRealm(realm);
					} catch (ResourceException | AccessDeniedException e) {
						throw new IllegalStateException(e.getMessage(), e);
					}
				}
				return null;
			}
		});
		
	}

	@Override
	public void assignUserToGroup(Principal user, Principal group) throws ResourceException, AccessDeniedException {
		
		List<Principal> groups = new ArrayList<Principal>(getUserGroups(user));
		groups.add(group);
		
		updateUser(user.getRealm(), user, user.getPrincipalName(), new HashMap<String,String>(), groups);
	}
	
	@Override
	public void unassignUserFromGroup(Principal user, Principal group) throws ResourceException, AccessDeniedException {
		
		List<Principal> groups = new ArrayList<Principal>(getUserGroups(user));
		groups.remove(group);
		
		updateUser(user.getRealm(), user, user.getPrincipalName(), new HashMap<String,String>(), groups);
	}
	
	@Override
	public List<Realm> getRealmsByIds(Long... ids) throws AccessDeniedException {
		assertPermission(RealmPermission.READ);
		return realmRepository.getRealmsByIds(ids);
	}

	@Override
	public void deleteUsers(final Realm realm, final List<Principal> users) throws ResourceException, AccessDeniedException {
		transactionService.doInTransaction(new TransactionCallback<Void>() {

			@Override
			public Void doInTransaction(TransactionStatus status) {
				for (Principal user : users) {
					try {
						deleteUser(realm, user);
					} catch (ResourceException | AccessDeniedException e) {
						throw new IllegalStateException(e.getMessage(), e);
					}
				}
				return null;
			}
		});
	}

	@Override
	public List<Principal> getUsersByIds(Long...ids) throws AccessDeniedException {
		return getPrincipalsByIds(ids);
	}

	@Override
	public void deleteGroups(final Realm realm, final List<Principal> groups) throws ResourceException, AccessDeniedException {
		transactionService.doInTransaction(new TransactionCallback<Void>() {

			@Override
			public Void doInTransaction(TransactionStatus status) {
				for (Principal group : groups) {
					try {
						deleteGroup(realm, group);
					} catch (ResourceException | AccessDeniedException e) {
						throw new IllegalStateException(e.getMessage(), e);
					}
				}
				return null;
			}
		});
		
	}

	@Override
	public List<Principal> getGroupsByIds(Long... ids) throws AccessDeniedException {
		return getPrincipalsByIds(ids);
	}

	private List<Principal> getPrincipalsByIds(Long... ids) throws AccessDeniedException {
		List<Principal> principals = new ArrayList<>();
		for (Long id : ids) {
			Principal principal = getPrincipalById(id);
			if(principal == null) {
				throw new IllegalStateException(String.format("Principal by id %d not found.", id));
			}
			principals.add(principal);
		}
		return principals;
	}

	@Override
	public boolean isLocked(Principal principal) throws ResourceException {
		
		RealmProvider provider = getProviderForPrincipal(principal);
		principal = provider.reconcileUser(principal);
		
		return principal.getPrincipalStatus() == PrincipalStatus.LOCKED;
	}

	@Override
	public boolean isUserSelectingRealm() {
		return systemConfigurationService.getBooleanValue("auth.chooseRealm");
	}
	
	@Override
	public String[] getRealmHostnames(Realm realm) {
		RealmProvider provder = getProviderForRealm(realm);
		return provder.getValues(realm, "realm.host");
	}

	@Override
	public Collection<TableFilter> getPrincipalFilters() {
		return principalFilters.values();
	}
	
	
	class LocalAccountFilter extends DefaultTableFilter {

		@Override
		public String getResourceKey() {
			return "filter.accounts.local";
		}

		@Override
		public List<?> searchResources(Realm realm, String searchColumn, String searchPattern, int start,
				int length, ColumnSort[] sorting) {
			RealmProvider local = getLocalProvider();
			return local.getPrincipals(realm, PrincipalType.USER, searchColumn, searchPattern, start, length, sorting);
		}

		@Override
		public Long searchResourcesCount(Realm realm, String searchColumn, String searchPattern) {
			RealmProvider local = getLocalProvider();
			return local.getPrincipalCount(realm, PrincipalType.USER, searchColumn, searchPattern);
		}
		
	}
	
	class RemoteAccountFilter extends DefaultTableFilter {

		@Override
		public String getResourceKey() {
			return "filter.accounts.remote";
		}

		@Override
		public List<?> searchResources(Realm realm, String searchColumn, String searchPattern, int start,
				int length, ColumnSort[] sorting) {
			RealmProvider remote = getProviderForRealm(realm);
			return remote.getPrincipals(realm, PrincipalType.USER, searchColumn, searchPattern, start, length, sorting);
		}

		@Override
		public Long searchResourcesCount(Realm realm, String searchColumn, String searchPattern) {
			RealmProvider remote = getProviderForRealm(realm);
			return remote.getPrincipalCount(realm, PrincipalType.USER, searchColumn, searchPattern);
		}
		
	}

	@Override
	public Collection<Realm> getRealmsByOwner() {
		
		if(getCurrentRealm().isSystem()) {
			try {
				assertAnyPermission(SystemPermission.SYSTEM_ADMINISTRATION, SystemPermission.SYSTEM);
				return allRealms();
			} catch (AccessDeniedException e) {
			}
		}
		
		Set<Realm> realms = new HashSet<Realm>();
		realms.add(getCurrentRealm());
		realms.addAll(realmRepository.getRealmsByParent(getCurrentRealm()));
		
		for(RealmOwnershipResolver resolver : ownershipResolvers) {
			realms.addAll(resolver.resolveRealms(getCurrentPrincipal()));
		}
		return realms;
	}

	@Override
	public Collection<Realm> getRealmsByParent(Realm currentRealm) {
		return realmRepository.getRealmsByParent(currentRealm);
	}
}

