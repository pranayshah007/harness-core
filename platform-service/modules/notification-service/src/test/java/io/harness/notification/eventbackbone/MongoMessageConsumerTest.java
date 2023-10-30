/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.notification.eventbackbone;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.notification.NotificationRequest.ChannelCase.SLACK;
import static io.harness.rule.OwnerRule.ANKUSH;
import static io.harness.rule.OwnerRule.JENNY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.manage.GlobalContextManager;
import io.harness.notification.NotificationRequest;
import io.harness.notification.NotificationTriggerRequest;
import io.harness.notification.entities.MongoNotificationRequest;
import io.harness.notification.service.api.NotificationService;
import io.harness.notification.utils.NotificationRequestTestUtils;
import io.harness.queue.QueueConsumer;
import io.harness.rule.Owner;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.LoggerFactory;

public class MongoMessageConsumerTest extends CategoryTest {
  @Mock private NotificationService notificationService;
  @Mock private QueueConsumer<MongoNotificationRequest> queueConsumer;
  private MongoMessageConsumer mongoMessageConsumer;

  private static final String ACCOUNT_IDENTIFIER = "AccountId";
  private static final String ORG_IDENTIFIER = "OrgId";
  private static final String PROJECT_IDENTIFIER = "ProjectId";
  private static final String EMAIL_ID = "test@harness.com";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    mongoMessageConsumer = new MongoMessageConsumer(queueConsumer, notificationService);
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void onMessage_ValidNotificationRequest_ShouldCallNotificationService() {
    NotificationRequest notificationRequest = NotificationRequestTestUtils.getDummyNotificationRequest(SLACK);
    MongoNotificationRequest mongoNotificationRequest =
        MongoNotificationRequest.builder().bytes(notificationRequest.toByteArray()).build();
    mongoMessageConsumer.onMessage(mongoNotificationRequest);
    verify(notificationService, times(1)).processNewMessage(notificationRequest);
    assertThat(GlobalContextManager.isAvailable()).isEqualTo(false);
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void onMessage_InvalidNotificationRequest_ShouldThrowException() {
    Logger fooLogger = (Logger) LoggerFactory.getLogger(MongoMessageConsumer.class);
    ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
    listAppender.start();
    fooLogger.addAppender(listAppender);
    MongoNotificationRequest mongoNotificationRequest =
        MongoNotificationRequest.builder().bytes("00".getBytes()).build();
    mongoMessageConsumer.onMessage(mongoNotificationRequest);
    List<ILoggingEvent> logList = listAppender.list;
    assertThat(logList.get(logList.size() - 1).getMessage())
        .isEqualTo("Corrupted message received off the mongo queue");
    assertThat(GlobalContextManager.isAvailable()).isEqualTo(false);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void onMessage_ValidNotificationTriggerRequest() {
    String notificationTriggerRequestId = generateUuid();
    Map<String, String> templateData = new HashMap<>();
    templateData.put("DELEGATE_HOST", "hostname1");
    templateData.put("DELEGATE_NAME", "delegateName");
    templateData.put("TEMPLATE_IDENTIFIER", "email_test");
    NotificationTriggerRequest notificationTriggerRequest = NotificationTriggerRequest.newBuilder()
                                                                .setId(notificationTriggerRequestId)
                                                                .setAccountId(ACCOUNT_IDENTIFIER)
                                                                .setOrgId(ORG_IDENTIFIER)
                                                                .setProjectId(PROJECT_IDENTIFIER)
                                                                .setEventEntity("DELEGATE")
                                                                .setEvent("DELEGATE_DOWN")
                                                                .putAllTemplateData(templateData)
                                                                .build();
    MongoNotificationRequest mongoNotificationRequest =
        MongoNotificationRequest.builder()
            .bytes(notificationTriggerRequest.toByteArray())
            .requestType(notificationTriggerRequest.getClass().getSimpleName())
            .build();
    mongoMessageConsumer.onMessage(mongoNotificationRequest);
    verify(notificationService, times(1)).processNewMessage(notificationTriggerRequest);
    assertThat(GlobalContextManager.isAvailable()).isEqualTo(false);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void onMessage_InValidNotificationTriggerRequest() {
    String notificationTriggerRequestId = generateUuid();
    NotificationTriggerRequest notificationTriggerRequest = NotificationTriggerRequest.newBuilder()
                                                                .setId(notificationTriggerRequestId)
                                                                .setAccountId(ACCOUNT_IDENTIFIER)
                                                                .setOrgId(ORG_IDENTIFIER)
                                                                .setProjectId(PROJECT_IDENTIFIER)
                                                                .setEventEntity("DELEGATE")
                                                                .setEvent("DELEGATE_DOWN")
                                                                .build();
    MongoNotificationRequest mongoNotificationRequest =
        MongoNotificationRequest.builder().bytes(notificationTriggerRequest.toByteArray()).build();
    verify(notificationService, times(0)).processNewMessage(notificationTriggerRequest);
    assertThat(GlobalContextManager.isAvailable()).isEqualTo(false);
  }
}
