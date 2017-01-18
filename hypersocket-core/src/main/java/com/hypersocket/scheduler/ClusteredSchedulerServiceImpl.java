package com.hypersocket.scheduler;

import java.io.File;

import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.hypersocket.utils.HypersocketUtils;

@Service
public class ClusteredSchedulerServiceImpl extends AbstractSchedulerServiceImpl implements 
		ClusteredSchedulerService {

	@Autowired
	Scheduler clusteredScheduler;
	
	protected Scheduler configureScheduler() throws SchedulerException {
		
		
		File quartzProperties = new File(HypersocketUtils.getConfigDir(), "quartz.properties");
		if(quartzProperties.exists()) {
			System.setProperty("org.quartz.properties", quartzProperties.getAbsolutePath());
		}

		return clusteredScheduler;
	}
}