package com.hypersocket.attributes.role;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.hypersocket.attributes.AbstractAttributeServiceImpl;
import com.hypersocket.attributes.user.events.UserAttributeCreatedEvent;
import com.hypersocket.attributes.user.events.UserAttributeDeletedEvent;
import com.hypersocket.attributes.user.events.UserAttributeEvent;
import com.hypersocket.attributes.user.events.UserAttributeUpdatedEvent;
import com.hypersocket.auth.FakePrincipal;
import com.hypersocket.permissions.PermissionCategory;
import com.hypersocket.permissions.Role;
import com.hypersocket.properties.AbstractPropertyTemplate;
import com.hypersocket.properties.PropertyCategory;
import com.hypersocket.properties.PropertyTemplate;
import com.hypersocket.realm.Principal;
import com.hypersocket.resource.AbstractResource;
import com.hypersocket.role.events.RoleEvent;

@Service
public class RoleAttributeServiceImpl extends AbstractAttributeServiceImpl<RoleAttribute, RoleAttributeCategory, Role>
		implements RoleAttributeService {

	static Logger log = LoggerFactory.getLogger(RoleAttributeServiceImpl.class);
	public static final String RESOURCE_BUNDLE = "RoleAttributes";

	@Autowired
	RoleAttributeRepository userAttributeRepository;

	@Autowired
	RoleAttributeCategoryRepository userAttributeCategoryRepository;

	@Autowired
	RoleAttributeCategoryService userAttributeCategoryService;

	Map<Role, Map<String, PropertyTemplate>> userPropertyTemplates = new HashMap<Role, Map<String, PropertyTemplate>>();
//	FakePrincipal allUsersPrincial = new FakePrincipal("allusers");

	public RoleAttributeServiceImpl() {
		super(RESOURCE_BUNDLE, RoleAttribute.class, RoleAttributePermission.class, RoleAttributePermission.CREATE,
				RoleAttributePermission.READ, RoleAttributePermission.UPDATE, RoleAttributePermission.DELETE, "RoleAttributes");
	}

	@PostConstruct
	protected void init() {
		attributeRepository = userAttributeRepository;
		categoryRepository = userAttributeCategoryRepository;
		categoryService = userAttributeCategoryService;

		PermissionCategory cat = permissionService.registerPermissionCategory(RESOURCE_BUNDLE,
				"category.RoleAttributes");

		for (RoleAttributePermission p : RoleAttributePermission.values()) {
			permissionService.registerPermission(p, cat);
		}

		i18nService.registerBundle(RESOURCE_BUNDLE);

		eventService.registerEvent(UserAttributeEvent.class, RESOURCE_BUNDLE);
		eventService.registerEvent(UserAttributeCreatedEvent.class, RESOURCE_BUNDLE);
		eventService.registerEvent(UserAttributeUpdatedEvent.class, RESOURCE_BUNDLE);
		eventService.registerEvent(UserAttributeDeletedEvent.class, RESOURCE_BUNDLE);

		super.init();
	}

	@Override
	protected RoleAttribute createNewAttributeInstance() {
		return new RoleAttribute();
	}

	@Override
	protected Role checkResource(AbstractResource resource) {
		if(resource==null) {
			return null;
		}
		if(!(resource instanceof Role)) {
			throw new IllegalArgumentException("Resource must be a Role in order to get attributes");
		}
		return (Role) resource;
	}

	protected Map<String, PropertyTemplate> getAttributeTemplates(Role role) {


		synchronized (userPropertyTemplates) {

			if (role!=null && userPropertyTemplates.containsKey(role)) {
				return userPropertyTemplates.get(role);
			}

			Collection<RoleAttribute> attributes = getRepository().getResources(getCurrentRealm());
			Map<String, PropertyTemplate> results = new HashMap<String, PropertyTemplate>();

			for (RoleAttribute attr : attributes) {
				results.put(attr.getVariableName(), propertyTemplates.get(attr.getVariableName()));
			}

			if(role!=null) {
				userPropertyTemplates.put(role, results);
			}
			return results;
		}

	}

	public void registerPropertyItem(PropertyCategory cat, RoleAttribute attr) {

		if (log.isInfoEnabled()) {
			log.info("Registering property " + attr.getVariableName());
		}
		
		String defaultValue =  attr.getDefaultValue();
		if (attr.getDefaultValue() != null && attr.getDefaultValue().startsWith("classpath:")) {
			String url = attr.getDefaultValue().substring(10);
			InputStream in = getClass().getResourceAsStream(url);
			try {
				if (in != null) {
					try {
						defaultValue = IOUtils.toString(in);
					} catch (IOException e) {
						log.error(
								"Failed to load default value classpath resource "
										+ defaultValue, e);
					}
				} else {
					log.error("Failed to load default value classpath resource "
							+ url);
				}
			} finally {
				IOUtils.closeQuietly(in);
			}
		}


		PropertyTemplate template = propertyTemplates.get(attr.getVariableName());
		if (template == null) {
			template = new PropertyTemplate();
			template.setResourceKey(attr.getVariableName());
		}

		template.getAttributes().put("inputType", attr.getType().toString().toLowerCase());
		template.getAttributes().put("filter", "custom");
		
		template.setDefaultValue(defaultValue);
		template.setWeight(attr.getWeight());
		template.setHidden(attr.getHidden());
		template.setDisplayMode(attr.getDisplayMode().equalsIgnoreCase("admin") ? "admin" : "");
		template.setReadOnly(attr.getReadOnly());
		template.setMapping("");
		template.setCategory(cat);
		template.setEncrypted(attr.getEncrypted());
		template.setDefaultsToProperty("");
		template.setPropertyStore(attributeRepository.getDatabasePropertyStore());

		cat.getTemplates().remove(template);
		cat.getTemplates().add(template);

		propertyTemplates.put(attr.getVariableName(), template);

		Collections.sort(cat.getTemplates(),
				new Comparator<AbstractPropertyTemplate>() {
					@Override
					public int compare(AbstractPropertyTemplate cat1,
							AbstractPropertyTemplate cat2) {
						return cat1.getWeight().compareTo(cat2.getWeight());
					}
				});


		synchronized (userPropertyTemplates) {
			userPropertyTemplates.clear();	
		}
	}

	@Override
	public void onApplicationEvent(RoleEvent event) {
		/**
		 * Really quick hack. We will do better.
		 */
		userPropertyTemplates.clear();
	}
}