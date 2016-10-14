package com.hypersocket.feedback;

import org.quartz.SchedulerException;

import com.hypersocket.auth.AuthenticatedService;
import com.hypersocket.scheduler.PermissionsAwareJobData;

public interface FeedbackService extends AuthenticatedService {
	
	FeedbackProgress startJob(Class<? extends FeedbackEnabledJob> jobClz, PermissionsAwareJobData data) throws SchedulerException;

	FeedbackProgress createFeedbackProgress();

	FeedbackProgress getFeedbackProgress(String uuid);

	void closeFeedbackProgress(FeedbackProgress progress);

	FeedbackProgress startJob(Class<? extends FeedbackEnabledJob> jobClz, String scheduleId,
			PermissionsAwareJobData data) throws SchedulerException;

}
