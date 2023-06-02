/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static software.wings.common.NotificationMessageResolver.NotificationMessageType.ARTIFACT_APPROVAL_NOTIFICATION;
import static software.wings.common.NotificationMessageResolver.NotificationMessageType.ARTIFACT_APPROVAL_NOTIFICATION_STATUS;

import static java.lang.String.format;
import static java.util.Arrays.asList;

import io.harness.beans.EmbeddedUser;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.sanitizer.HtmlInputSanitizer;

import software.wings.app.MainConfiguration;
import software.wings.beans.ActionableNotification;
import software.wings.beans.ApprovalNotification;
import software.wings.beans.ApprovalNotification.ApprovalStage;
import software.wings.beans.InformationNotification;
import software.wings.beans.Notification;
import software.wings.beans.NotificationAction.NotificationActionType;
import software.wings.beans.NotificationRule;
import software.wings.common.NotificationMessageResolver;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.NotificationDispatcherService;
import software.wings.service.intfc.NotificationService;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import javax.validation.constraints.NotNull;
import javax.validation.executable.ValidateOnExecution;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * Created by anubhaw on 7/22/16.
 */
@Singleton
@ValidateOnExecution
public class NotificationServiceImpl implements NotificationService {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private Injector injector;
  @Inject private ExecutorService executorService;
  @Inject private AppService appService;
  @Inject private NotificationDispatcherService notificationDispatcherService;
  @Inject private NotificationMessageResolver notificationMessageResolver;
  @Inject private MainConfiguration configuration;
  @Inject private HtmlInputSanitizer userNameSanitizer;

  @Override
  public PageResponse<Notification> list(PageRequest<Notification> pageRequest) {
    return wingsPersistence.query(Notification.class, pageRequest);
  }

  @Override
  public Notification get(@NotEmpty String appId, @NotEmpty String notificationId) {
    return wingsPersistence.getWithAppId(Notification.class, appId, notificationId);
  }

  @Override
  public Notification act(
      @NotEmpty String appId, @NotEmpty String notificationId, @NotNull NotificationActionType actionType) {
    Notification notification = get(appId, notificationId);
    if (notification == null) {
      throw new InvalidRequestException("Notification doesn't exist");
    }
    if (!(notification instanceof ActionableNotification)) {
      throw new InvalidRequestException("Notification not actionable");
    }
    ActionableNotification actionableNotification = (ActionableNotification) notification;
    if (actionableNotification.getNotificationActions().stream().noneMatch(
            notificationAction -> notificationAction.getType() == actionType)) {
      throw new InvalidRequestException("Action not supported for NotificationType");
    }
    injector.injectMembers(actionableNotification);
    boolean actionCompleted = actionableNotification.performAction(actionType);
    if (actionCompleted) {
      markNotificationCompleted(appId, notificationId);
    }
    Notification savedNotification = get(appId, notificationId);
    notificationDispatcherService.dispatchNotification(savedNotification, new ArrayList<>());
    return savedNotification;
  }

  @Override
  public void markNotificationCompleted(@NotEmpty String appId, @NotEmpty String notificationId) {
    wingsPersistence.updateFields(Notification.class, notificationId, ImmutableMap.of("complete", true));
  }

  @Override
  public void pruneByApplication(String appId) {
    wingsPersistence.delete(wingsPersistence.createQuery(Notification.class).filter("appId", appId));
  }

  @Override
  public void sendNotificationAsync(Notification notification, List<NotificationRule> notificationRules) {
    executorService.execute(() -> sendNotification(notification, notificationRules));
  }

  private void sendNotification(Notification notification, List<NotificationRule> notificationRules) {
    Notification savedNotification = renderAndSaveNotification(notification);
    notificationDispatcherService.dispatchNotification(savedNotification, notificationRules);
  }

  private Notification renderAndSaveNotification(Notification notification) {
    if (isEmpty(notification.getAccountId()) && appService.exist(notification.getAppId())) {
      notification.setAccountId(appService.get(notification.getAppId()).getAccountId());
    }

    if (notification instanceof InformationNotification) {
      ((InformationNotification) notification)
          .setDisplayText(NotificationMessageResolver.getDecoratedNotificationMessage(
              notificationMessageResolver.getWebTemplate(notification.getNotificationTemplateId()),
              notification.getNotificationTemplateVariables()));
    } else if (notification instanceof ApprovalNotification) {
      ApprovalNotification approvalNotification = (ApprovalNotification) notification;
      String actionUrl = configuration.getPortal().getUrl()
          + format(configuration.getPortal().getApplicationOverviewUrlPattern(), approvalNotification.getAppId());
      Map<String, String> placeHolderData = new HashMap<>();
      placeHolderData.put("ENTITY_TYPE", approvalNotification.getEntityType().name());
      placeHolderData.put("ENTITY_NAME", approvalNotification.getEntityName());
      placeHolderData.put("ACTION_URL", actionUrl);
      if (approvalNotification.getStage() == ApprovalStage.APPROVED
          || approvalNotification.getStage() == ApprovalStage.REJECTED) {
        notification.setNotificationTemplateId(ARTIFACT_APPROVAL_NOTIFICATION_STATUS.name());
        placeHolderData.put("NOTIFICATION_STATUS", approvalNotification.getStage().name().toLowerCase());
        placeHolderData.put("USER_NAME",
            approvalNotification.getLastUpdatedBy() == null
                ? "Harness System"
                : userNameSanitizer.sanitizeInput(approvalNotification.getLastUpdatedBy().getName()));
      } else if (notification.getNotificationTemplateId() == null) {
        notification.setNotificationTemplateId(ARTIFACT_APPROVAL_NOTIFICATION.name());
      }
      notification.getNotificationTemplateVariables().putAll(placeHolderData);
    }

    wingsPersistence.save(notification);
    return notification;
  }

  @Override
  public void sendNotificationAsync(Notification notification) {
    sendNotificationAsync(notification, new ArrayList<>());
  }

  @Override
  public void sendNotificationToTriggeredByUserOnly(Notification notification, EmbeddedUser embeddedUser) {
    Notification savedNotification = renderAndSaveNotification(notification);

    executorService.execute(()
                                -> notificationDispatcherService.dispatchNotificationToTriggeredByUserOnly(
                                    asList(savedNotification), embeddedUser));
  }
}
