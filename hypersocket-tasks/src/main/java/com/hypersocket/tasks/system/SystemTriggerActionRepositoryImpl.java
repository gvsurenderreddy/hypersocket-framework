package com.hypersocket.tasks.system;

import javax.annotation.PostConstruct;

import org.springframework.stereotype.Repository;

import com.hypersocket.properties.ResourceTemplateRepositoryImpl;

@Repository
public class SystemTriggerActionRepositoryImpl extends
		ResourceTemplateRepositoryImpl implements SystemTriggerActionRepository {
	
	@PostConstruct
	private void postConstruct() {
		loadPropertyTemplates("tasks/systemRestart.xml");
	}
}
