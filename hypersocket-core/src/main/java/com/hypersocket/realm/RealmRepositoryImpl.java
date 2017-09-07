/*******************************************************************************
 * Copyright (c) 2013 Hypersocket Limited.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package com.hypersocket.realm;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.hibernate.Criteria;
import org.hibernate.Query;
import org.hibernate.criterion.CriteriaSpecification;
import org.hibernate.criterion.Restrictions;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.hypersocket.repository.CriteriaConfiguration;
import com.hypersocket.repository.DeletedCriteria;
import com.hypersocket.repository.DistinctRootEntity;
import com.hypersocket.resource.AbstractSimpleResourceRepositoryImpl;
import com.hypersocket.resource.HiddenFilter;
import com.hypersocket.resource.ResourceException;
import com.hypersocket.resource.TransactionOperation;
import com.hypersocket.tables.ColumnSort;

@Repository
public class RealmRepositoryImpl extends
		AbstractSimpleResourceRepositoryImpl<Realm> implements
		RealmRepository {


	@Override
	@Transactional
	public Realm createRealm(String name, String uuid, String module,
							 Map<String, String> properties, RealmProvider provider, Realm parent, Long owner, Boolean publicRealm, @SuppressWarnings("unchecked") TransactionOperation<Realm>... ops) throws ResourceException {
		Realm realm = new Realm();
		realm.setName(name);
		realm.setResourceCategory(module);
		realm.setOwner(owner);
		realm.setParent(parent);
		realm.setPublicRealm(publicRealm);
		realm.setUuid(uuid);

		for(TransactionOperation<Realm> op : ops) {
			op.beforeOperation(realm, properties);
		}

		save(realm);

		provider.setValues(realm, properties);

		for(TransactionOperation<Realm> op : ops) {
			op.afterOperation(realm, properties);
		}

		return realm;
	}

	@Override
	@Transactional
	public Realm saveRealm(Realm realm) {
		save(realm);
		flush();
		refresh(realm);
		return realm;
	}

	@Override
	@Transactional
	public Realm saveRealm(Realm realm, Map<String, String> properties,
						   RealmProvider provider, 
						   @SuppressWarnings("unchecked") TransactionOperation<Realm> ... ops) throws ResourceException {

		boolean isNew = realm.getId() == null;
		
		for(TransactionOperation<Realm> op : ops) {
			op.beforeOperation(realm, properties);
		}

		save(realm);

		provider.setValues(realm, properties);

		for(TransactionOperation<Realm> op : ops) {
			op.afterOperation(realm, properties);
		}


		if (!isNew) {
			refresh(realm);
		}

		return realm;
	}

	@Override
	@Transactional(readOnly = true)
	public List<Realm> allRealms() {
		return allEntities(Realm.class, new HiddenFilter(),
				new DeletedCriteria(false), new DistinctRootEntity(),
				new PublicRealmCriteria());
	}

	@Override
	@Transactional(readOnly = true)
	public List<Realm> searchRealms(String searchPattern, String searchColumn, int start,
									int length, ColumnSort[] sorting) {
		return search(Realm.class, searchColumn, searchPattern, start, length,
				sorting, new PublicRealmCriteria(), new CriteriaConfiguration() {

					@Override
					public void configure(Criteria criteria) {
						criteria.add(Restrictions.eq("hidden", false));
						criteria.add(Restrictions.eq("deleted", false));
					}
				});
	}

	@Override
	@Transactional(readOnly = true)
	public List<Realm> allRealms(String resourceKey) {
		return list("resourceCategory", resourceKey, Realm.class,
				new HiddenFilter(), new DeletedCriteria(false),
				new DistinctRootEntity(), new PublicRealmCriteria());
	}

	protected Realm getRealm(String column, Object value, CriteriaConfiguration...configurations ) {
		return get(column, value, Realm.class, true, configurations);
	}

	@Override
	@Transactional(readOnly = true)
	public Realm getRealmById(Long id) {
		return get("id", id, Realm.class);
	}

	@Override
	@Transactional(readOnly = true)
	public Realm getRealmByName(String name) {
		return getRealm("name", name, new DeletedCriteria(false), new PublicRealmCriteria());
	}
	
	@Override
	@Transactional(readOnly = true)
	public Realm getRealmByNameAndOwner(String name, final Realm owner) {
		return getRealm("name", name, new CriteriaConfiguration() {

			@Override
			public void configure(Criteria criteria) {
				criteria.add(Restrictions.eq("owner", owner.getId()));
			}
			
		});
	}

	@Override
	@Transactional(readOnly = true)
	public Realm getRealmByName(String name, boolean deleted) {
		return get("name", name, Realm.class, new DeletedCriteria(deleted), new PublicRealmCriteria());
	}

	@Override
	@Transactional(readOnly = true)
	public Realm getRealmByHost(String host) {
		return getRealm("host", host, new PublicRealmCriteria());
	}

	@Override
	@Transactional(readOnly = true)
	public Realm getRealmByOwner(Long owner) {
		return getRealm("owner", owner);
	}

	@Override
	@Transactional
	public void delete(Realm realm) {
		super.delete(realm);
	}

	@Override
	@Transactional(readOnly = true)
	public Long countRealms(String searchPattern, String searchColumn) {
		return getCount(Realm.class, searchColumn, searchPattern,
				new PublicRealmCriteria(), new CriteriaConfiguration() {

					@Override
					public void configure(Criteria criteria) {
						criteria.add(Restrictions.eq("hidden", false));
						criteria.add(Restrictions.eq("deleted", false));
					}
				});
	}

	@Override
	protected Class<Realm> getResourceClass() {
		return Realm.class;
	}

	@Override
	@Transactional(readOnly = true)
	public Realm getDefaultRealm() {
		return get("defaultRealm", true, Realm.class);
	}

	@Override
	@Transactional
	public Realm setDefaultRealm(Realm realm) {

		realm.setDefaultRealm(true);

		for (Realm r : allRealms()) {
			if (!r.equals(realm)) {
				r.setDefaultRealm(false);
			}
			save(r);
		}
		save(realm);
		flush();
		refresh(realm);

		return realm;
	}

	@Override
	@Transactional(readOnly=true)
	public Realm getSystemRealm() {
		return get("system", true, Realm.class);
	}

	@SuppressWarnings("unchecked")
	@Override
	@Transactional(readOnly=true)
	public List<Realm> getRealmsByIds(Long... ids) {
		Criteria criteria = createCriteria(Realm.class);
		criteria.setResultTransformer(CriteriaSpecification.DISTINCT_ROOT_ENTITY);
		criteria.add(Restrictions.in("id", ids));
		criteria.add(Restrictions.eq("deleted", false));
		return criteria.list();
	}

	@Override
	@Transactional(readOnly=true)
	public Collection<? extends Realm> getRealmsByParent(final Realm realm) {
		if(realm.isSystem()) {
			return allRealms();
		}
		return list(Realm.class, new DeletedCriteria(false), new CriteriaConfiguration() {

			@Override
			public void configure(Criteria criteria) {
				criteria.add(Restrictions.eq("parent", realm));
			}
			
		});
	}
	@Override
	public void clearRealm(Realm realm) {
	}

}
