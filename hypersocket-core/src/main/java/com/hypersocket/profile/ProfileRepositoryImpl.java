package com.hypersocket.profile;

import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.hibernate.Criteria;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.criterion.Subqueries;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.hypersocket.local.PrincipalTypeRestriction;
import com.hypersocket.realm.Principal;
import com.hypersocket.realm.PrincipalType;
import com.hypersocket.realm.Realm;
import com.hypersocket.repository.AbstractEntityRepositoryImpl;
import com.hypersocket.repository.CriteriaConfiguration;
import com.hypersocket.resource.RealmCriteria;
import com.hypersocket.tables.ColumnSort;

@Repository
public class ProfileRepositoryImpl extends AbstractEntityRepositoryImpl<Profile, Long> implements ProfileRepository {

	@Override
	protected Class<Profile> getEntityClass() {
		return Profile.class;
	}

	
	@Override
	@Transactional(readOnly=true)
	public long getCompleteProfileCount(Realm realm) {
		return getCount(Profile.class, new RealmCriteria(realm.isSystem() ? null : realm), new CriteriaConfiguration(){

			@Override
			public void configure(Criteria criteria) {
				criteria.add(Restrictions.eq("state", ProfileCredentialsState.COMPLETE));
			} 
		});
	}
	
	@Override
	@Transactional(readOnly=true)
	public long getCompleteProfileOnDateCount(Realm realm, final Date date) {
		return getCount(Profile.class, new RealmCriteria(realm.isSystem() ? null : realm), new CriteriaConfiguration(){

			@Override
			public void configure(Criteria criteria) {
				criteria.add(Restrictions.eq("state", ProfileCredentialsState.COMPLETE));
				criteria.add(Restrictions.eq("completed", date));
			} 
		});
	}
	
	@Override
	@Transactional(readOnly=true)
	public long getIncompleteProfileCount(Realm realm) {
		return getCount(Profile.class, new RealmCriteria(realm.isSystem() ? null : realm), new CriteriaConfiguration(){

			@Override
			public void configure(Criteria criteria) {
				criteria.add(Restrictions.eq("state", ProfileCredentialsState.INCOMPLETE));
			} 
		});
	}
	
	@Override
	@Transactional(readOnly=true)
	public long getPartiallyCompleteProfileCount(Realm realm) {
		return getCount(Profile.class, new RealmCriteria(realm.isSystem() ? null : realm), new CriteriaConfiguration(){

			@Override
			public void configure(Criteria criteria) {
				criteria.add(Restrictions.eq("state", ProfileCredentialsState.PARTIALLY_COMPLETE));
			} 
		});
	}


	@Override
	public Collection<Profile> getProfilesWithStatus(Realm realm, final ProfileCredentialsState...credentialsStates) {
		return list(Profile.class, new RealmCriteria(realm), new CriteriaConfiguration() {
			
			@Override
			public void configure(Criteria criteria) {
				if(credentialsStates.length > 0) {
					criteria.add(Restrictions.in("state", credentialsStates));
				}
			}
		});
	}


	@Override
	@Transactional(readOnly=true)
	public List<?> searchIncompleteProfiles(final Realm realm, String searchColumn, String searchPattern, ColumnSort[] sorting, int start, int length) {
		return search(Principal.class, searchColumn, searchPattern, start, length, sorting, new RealmCriteria(realm), new PrincipalTypeRestriction(PrincipalType.USER), new CriteriaConfiguration() {

			@Override
			public void configure(Criteria criteria) {
				DetachedCriteria profileSubquery = DetachedCriteria.forClass(Profile.class, "p")
						.add(Restrictions.eq("p.realm.id", realm.getId()))
						.add(Restrictions.in("p.state", 
							new ProfileCredentialsState[] { ProfileCredentialsState.COMPLETE, ProfileCredentialsState.NOT_REQUIRED }))  
					    		.setProjection( Projections.property("p.id"));
				
				criteria.add(Subqueries.propertyNotIn("id", profileSubquery));
			}
			
		});
	}


	@Override
	@Transactional(readOnly=true)
	public Long searchIncompleteProfilesCount(final Realm realm, String searchColumn, String searchPattern) {
		
		return getCount(Principal.class, searchColumn, searchPattern, new RealmCriteria(realm), new PrincipalTypeRestriction(PrincipalType.USER), new CriteriaConfiguration() {

			@Override
			public void configure(Criteria criteria) {
				DetachedCriteria profileSubquery = DetachedCriteria.forClass(Profile.class, "p")
						.add(Restrictions.eq("p.realm.id", realm.getId()))
						.add(Restrictions.in("p.state", 
							new ProfileCredentialsState[] { ProfileCredentialsState.COMPLETE, ProfileCredentialsState.NOT_REQUIRED }))  
					    		.setProjection( Projections.property("p.id"));
				
				criteria.add(Subqueries.propertyNotIn("id", profileSubquery));
			}
			
		});
	}
	
	@Override
	@Transactional(readOnly=true)
	public List<?> searchCompleteProfiles(final Realm realm, String searchColumn, String searchPattern, ColumnSort[] sorting, int start, int length) {
		return search(Principal.class, searchColumn, searchPattern, start, length, sorting, new RealmCriteria(realm), new PrincipalTypeRestriction(PrincipalType.USER), new CriteriaConfiguration() {

			@Override
			public void configure(Criteria criteria) {
				DetachedCriteria profileSubquery = DetachedCriteria.forClass(Profile.class, "p")
						.add(Restrictions.eq("p.realm.id", realm.getId()))
						.add(Restrictions.in("p.state", 
							new ProfileCredentialsState[] { ProfileCredentialsState.COMPLETE }))  
					    		.setProjection( Projections.property("p.id"));
				
				criteria.add(Subqueries.propertyIn("id", profileSubquery));
			}
			
		});
	}


	@Override
	@Transactional(readOnly=true)
	public Long searchCompleteProfilesCount(final Realm realm, String searchColumn, String searchPattern) {
		
		return getCount(Principal.class, searchColumn, searchPattern, new RealmCriteria(realm), new PrincipalTypeRestriction(PrincipalType.USER), new CriteriaConfiguration() {

			@Override
			public void configure(Criteria criteria) {
				DetachedCriteria profileSubquery = DetachedCriteria.forClass(Profile.class, "p")
						.add(Restrictions.eq("p.realm.id", realm.getId()))
						.add(Restrictions.in("p.state", 
							new ProfileCredentialsState[] { ProfileCredentialsState.COMPLETE }))  
					    		.setProjection( Projections.property("p.id"));
				
				criteria.add(Subqueries.propertyIn("id", profileSubquery));
			}
			
		});
	}
	
	@Override
	@Transactional(readOnly=true)
	public List<?> searchNeverVisitedProfiles(final Realm realm, String searchColumn, String searchPattern, ColumnSort[] sorting, int start, int length) {
		return search(Principal.class, searchColumn, searchPattern, start, length, sorting, new RealmCriteria(realm), new PrincipalTypeRestriction(PrincipalType.USER), new CriteriaConfiguration() {

			@Override
			public void configure(Criteria criteria) {
				DetachedCriteria profileSubquery = DetachedCriteria.forClass(Profile.class, "p")
						.add(Restrictions.eq("p.realm.id", realm.getId()))
						.setProjection( Projections.property("p.id"));
				
				criteria.add(Subqueries.propertyNotIn("id", profileSubquery));
			}
			
		});
	}


	@Override
	@Transactional(readOnly=true)
	public Long searchNeverVisitedProfilesCount(final Realm realm, String searchColumn, String searchPattern) {
		
		return getCount(Principal.class, searchColumn, searchPattern, new RealmCriteria(realm), new PrincipalTypeRestriction(PrincipalType.USER), new CriteriaConfiguration() {

			@Override
			public void configure(Criteria criteria) {
				DetachedCriteria profileSubquery = DetachedCriteria.forClass(Profile.class, "p") 
								.add(Restrictions.eq("p.realm.id", realm.getId()))
					    		.setProjection( Projections.property("p.id"));
				
				criteria.add(Subqueries.propertyNotIn("id", profileSubquery));
			}
			
		});
	}
	
}
