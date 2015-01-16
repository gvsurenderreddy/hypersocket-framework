package com.hypersocket.realm;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import com.hypersocket.resource.Resource;

@Entity
@Table(name = "suspensions")
public class PrincipalSuspension extends Resource {

	@ManyToOne
	@JoinColumn(name = "realm_id")
	protected Realm realm;

	@OneToOne
	@JoinColumn(name = "principal_id")
	protected Principal principal;

	@Column(name = "start_time")
	Date startTime;

	@Column(name = "duration")
	Long duration;

	public PrincipalSuspension() {

	}

	public PrincipalSuspension(Realm realm, Principal principal,
			Date startTime, Long duration) {
		this.realm = realm;
		this.principal = principal;
		this.startTime = startTime;
		this.duration = duration;
	}

	public Realm getRealm() {
		return realm;
	}

	public void setRealm(Realm realm) {
		this.realm = realm;
	}

	public Principal getPrincipal() {
		return principal;
	}

	public void setPrincipal(Principal principal) {
		this.principal = principal;
	}

	public Date getStartTime() {
		return startTime;
	}

	public void setStartTime(Date startTime) {
		this.startTime = startTime;
	}

	public Long getDuration() {
		return duration;
	}

	public void setDuration(Long duration) {
		this.duration = duration;
	}

}
