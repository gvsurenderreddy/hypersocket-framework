package com.hypersocket.triggers.actions.ip;

import javax.annotation.PostConstruct;

import org.springframework.stereotype.Repository;

import com.hypersocket.properties.ResourceTemplateRepositoryImpl;

@Repository
public class UnblockIPTaskRepositoryImpl extends
		ResourceTemplateRepositoryImpl implements UnblockIPTaskRepository {

	@PostConstruct
	private void postConstruct() {
		loadPropertyTemplates("tasks/unblockIP.xml");
	}
}

