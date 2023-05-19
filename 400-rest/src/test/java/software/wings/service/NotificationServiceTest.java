/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.rule.OwnerRule.ANUBHAW;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.ApprovalNotification.Builder.anApprovalNotification;
import static software.wings.beans.EntityType.ARTIFACT;
import static software.wings.beans.NotificationAction.NotificationActionType.APPROVE;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.ARTIFACT_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_NAME;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.NOTIFICATION_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.harness.beans.PageRequest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.ApprovalNotification;
import software.wings.beans.InformationNotification;
import software.wings.beans.Notification;
import software.wings.common.NotificationMessageResolver.NotificationMessageType;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.NotificationServiceImpl;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.NotificationDispatcherService;
import software.wings.service.intfc.NotificationService;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Injector;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;

/**
 * Created by anubhaw on 7/28/16.
 */
public class NotificationServiceTest extends WingsBaseTest {
  @Mock private WingsPersistence wingsPersistence;
  @Mock private Injector injector;
  @Mock private AppService appService;
  @Mock private NotificationDispatcherService notificationDispatcherService;

  @Inject @InjectMocks private NotificationService notificationService;

  @Spy @InjectMocks private NotificationService spyNotificationService = new NotificationServiceImpl();

  /**
   * Sets up.
   *
   * @throws Exception the exception
   */
  @Before
  public void setUp() throws Exception {
    when(appService.get(APP_ID)).thenReturn(anApplication().uuid(APP_ID).appId(APP_ID).accountId("ACCOUNT_ID").build());
  }

  /**
   * Should list.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldList() {
    PageRequest pageRequest = aPageRequest().addFilter("appId", EQ, APP_ID).build();
    notificationService.list(pageRequest);
    verify(wingsPersistence).query(Notification.class, pageRequest);
  }

  /**
   * Should get.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldGet() {
    final InformationNotification informationNotification = InformationNotification.builder().appId(APP_ID).build();
    informationNotification.setUuid(NOTIFICATION_ID);
    when(wingsPersistence.getWithAppId(Notification.class, APP_ID, NOTIFICATION_ID))
        .thenReturn(informationNotification);
    Notification notification = notificationService.get(APP_ID, NOTIFICATION_ID);
    assertThat(notification)
        .isInstanceOf(Notification.class)
        .hasFieldOrPropertyWithValue("appId", APP_ID)
        .hasFieldOrPropertyWithValue("uuid", NOTIFICATION_ID);
  }

  /**
   * Should send notification async.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldSendNotificationAsync() {
    InformationNotification notification =
        InformationNotification.builder()
            .appId(APP_ID)
            .environmentId(ENV_ID)
            .notificationTemplateId(NotificationMessageType.ENTITY_CREATE_NOTIFICATION.name())
            .notificationTemplateVariables(ImmutableMap.of("ENTITY_TYPE", "Application", "ENTITY_NAME", APP_NAME))
            .build();
    notificationService.sendNotificationAsync(notification);
    verify(wingsPersistence).save(notification);
    verify(notificationDispatcherService).dispatchNotification(any(), any());
    verifyNoMoreInteractions(wingsPersistence, injector);
  }

  /**
   * Should mark notification completed.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldMarkNotificationCompleted() {
    notificationService.markNotificationCompleted(APP_ID, NOTIFICATION_ID);
    verify(wingsPersistence).updateFields(Notification.class, NOTIFICATION_ID, ImmutableMap.of("complete", true));
  }

  /**
   * Should act.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldAct() {
    ApprovalNotification approvalNotification = Mockito.spy(anApprovalNotification()
                                                                .withAppId(APP_ID)
                                                                .withUuid(NOTIFICATION_ID)
                                                                .withEntityId(ARTIFACT_ID)
                                                                .withEntityType(ARTIFACT)
                                                                .withEntityName(ARTIFACT_NAME)
                                                                .withArtifactStreamId(ARTIFACT_STREAM_ID)
                                                                .build());

    doReturn(approvalNotification).when(spyNotificationService).get(APP_ID, NOTIFICATION_ID);
    doNothing().when(spyNotificationService).markNotificationCompleted(APP_ID, NOTIFICATION_ID);
    doReturn(true).when(approvalNotification).performAction(APPROVE);
    spyNotificationService.act(APP_ID, NOTIFICATION_ID, APPROVE);

    verify(spyNotificationService, times(2)).get(APP_ID, NOTIFICATION_ID);
    verify(injector).injectMembers(approvalNotification);
    verify(approvalNotification).performAction(APPROVE);
    verify(spyNotificationService).markNotificationCompleted(APP_ID, NOTIFICATION_ID);
  }
}
