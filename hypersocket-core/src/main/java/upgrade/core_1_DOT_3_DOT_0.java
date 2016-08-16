/*******************************************************************************
 * Copyright (c) 2013 Hypersocket Limited.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package upgrade;


import org.quartz.JobDataMap;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.hypersocket.scheduler.SchedulerService;
import com.hypersocket.session.Session;
import com.hypersocket.session.SessionReaperJob;
import com.hypersocket.session.SessionService;


public class core_1_DOT_3_DOT_0 implements Runnable {

	static Logger log = LoggerFactory.getLogger(core_1_DOT_3_DOT_0.class);

	@Autowired
	SchedulerService schedulerService;
	
	@Autowired
	SessionService sessionService;
	
	@Override
	public void run() {

		if (log.isInfoEnabled()) {
			log.info("Scheduling session reaper job");
		}
		
		try {
			if(schedulerService.jobDoesNotExists("firstRunSessionReaperJob")){
				JobDataMap data = new JobDataMap();
				data.put("jobName", "firstRunSessionReaperJob");
				data.put("firstRun", true);
				
				Session session = sessionService.getSystemSession();
				sessionService.setCurrentSession(session, session.getCurrentRealm(), session.getCurrentPrincipal(), null);
				schedulerService.scheduleNow(SessionReaperJob.class, "firstRunSessionReaperJob", data);
			}

		} catch (SchedulerException e) {
			log.error("Failed to schedule session reaper job", e);
		} 
		
	}


}
