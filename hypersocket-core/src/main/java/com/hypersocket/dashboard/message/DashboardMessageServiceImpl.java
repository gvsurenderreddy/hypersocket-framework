package com.hypersocket.dashboard.message;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;

import org.apache.log4j.Logger;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextStartedEvent;
import org.springframework.stereotype.Service;

import com.hypersocket.auth.AbstractAuthenticatedServiceImpl;
import com.hypersocket.dashboard.message.events.DashboardMessageCreatedEvent;
import com.hypersocket.dashboard.message.events.DashboardMessageEvent;
import com.hypersocket.events.EventService;
import com.hypersocket.i18n.I18NService;
import com.hypersocket.permissions.AccessDeniedException;
import com.hypersocket.permissions.PermissionCategory;
import com.hypersocket.scheduler.SchedulerService;

@Service
public class DashboardMessageServiceImpl extends
		AbstractAuthenticatedServiceImpl implements DashboardMessageService,
		ApplicationListener<ContextStartedEvent> {

	public static final String RESOURCE_BUNDLE = "DashboardMessageService";
	static Logger log = Logger.getLogger(DashboardMessageServiceImpl.class);

	@Autowired
	SchedulerService schedulerService;

	@Autowired
	DashboardMessageRepository repository;

	@Autowired
	I18NService i18nService;

	@Autowired
	EventService eventService;

	@PostConstruct
	private void postConstruct() {

		i18nService.registerBundle(RESOURCE_BUNDLE);

		PermissionCategory cat = permissionService.registerPermissionCategory(
				RESOURCE_BUNDLE, "category.dashboardMessage");

		for (DashboardMessagePermission p : DashboardMessagePermission.values()) {
			permissionService.registerPermission(p, cat);
		}

		eventService
				.registerEvent(DashboardMessageEvent.class, RESOURCE_BUNDLE);
		eventService.registerEvent(DashboardMessageCreatedEvent.class,
				RESOURCE_BUNDLE);

	}

	@Override
	public void saveNewMessages(DashboardMessage[] dashboardMessageList)
			throws AccessDeniedException {
		List<DashboardMessage> savedMessages = repository.getMessages();
		List<DashboardMessage> newMessages = new ArrayList<DashboardMessage>();
		for (DashboardMessage newMessage : dashboardMessageList) {
			boolean found = false;
			for (DashboardMessage message : savedMessages) {
				if (newMessage.getMessageId().equals(message.getMessageId())) {
					found = true;
					break;
				}
			}
			if (!found) {
				newMessages.add(newMessage);
			}
		}

		for (DashboardMessage message : newMessages) {
			repository.saveNewMessage(message);
		}
	}

	@Override
	public List<DashboardMessage> getMessages() throws AccessDeniedException {
		assertPermission(DashboardMessagePermission.READ);
		return repository.getMessages();
	}

	@Override
	public List<DashboardMessage> getUnexpiredMessages(int pageNum)
			throws AccessDeniedException {
		assertPermission(DashboardMessagePermission.READ);
		return repository.getUnexpiredMessages(pageNum);
	}

	@Override
	public DashboardMessage saveNewMessage(DashboardMessage dashboardMessage)
			throws AccessDeniedException {
		assertPermission(DashboardMessagePermission.CREATE);

		DashboardMessage message = repository.getMessage(dashboardMessage);
		try {
			if (message == null) {
				repository.saveNewMessage(dashboardMessage);
				message = repository.getMessage(dashboardMessage);

//				eventService.publishEvent(new DashboardMessageCreatedEvent(
//						this, getCurrentSession(), message));
			}

			return message;
		} catch (Exception e) {
//			eventService.publishEvent(new DashboardMessageCreatedEvent(this, e,
//					getCurrentSession(), message));
			throw e;
		}
	}

	@Override
	public Long getMessageCount() throws AccessDeniedException {
		assertPermission(DashboardMessagePermission.READ);
		return repository.getMessageCount();
	}

	@Override
	public void onApplicationEvent(ContextStartedEvent event) {
		try {
			schedulerService.scheduleNow(DashboardMessageJob.class, null,
					600000);

		} catch (SchedulerException e) {
			log.error("Failed to schedule DashboardMessageJob", e);
		}

	}

}
