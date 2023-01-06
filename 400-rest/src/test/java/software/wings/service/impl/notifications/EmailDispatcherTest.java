/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.notifications;

import static io.harness.rule.OwnerRule.UJJAWAL;

import static software.wings.beans.EntityType.ORCHESTRATED_DEPLOYMENT;
import static software.wings.common.NotificationMessageResolver.NotificationMessageType.ENTITY_CREATE_NOTIFICATION;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ENV_NAME;
import static software.wings.utils.WingsTestConstants.WORKFLOW_EXECUTION_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_NAME;

import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.InformationNotification;
import software.wings.beans.Notification;
import software.wings.common.NotificationMessageResolver;
import software.wings.common.NotificationMessageResolver.ChannelTemplate.EmailTemplate;
import software.wings.persistence.mail.EmailData;
import software.wings.service.intfc.EmailNotificationService;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

public class EmailDispatcherTest extends WingsBaseTest {
  @Inject @InjectMocks EmailDispatcher emailDispatcher;
  @Mock private EmailNotificationService emailNotificationService;
  @Mock private NotificationMessageResolver notificationMessageResolver;

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testEmailIsSent() {
    List<String> toAddresses = Arrays.asList("a@b.com, c@d.com");

    EmailTemplate emailTemplate = new EmailTemplate();
    emailTemplate.setBody(ENTITY_CREATE_NOTIFICATION.name());
    emailTemplate.setSubject(ENTITY_CREATE_NOTIFICATION.name());
    when(notificationMessageResolver.getEmailTemplate(ENTITY_CREATE_NOTIFICATION.name())).thenReturn(emailTemplate);

    InformationNotification notification = InformationNotification.builder()
                                               .accountId(ACCOUNT_ID)
                                               .appId(APP_ID)
                                               .entityId(WORKFLOW_EXECUTION_ID)
                                               .entityType(ORCHESTRATED_DEPLOYMENT)
                                               .notificationTemplateId(ENTITY_CREATE_NOTIFICATION.name())
                                               .notificationTemplateVariables(ImmutableMap.of("WORKFLOW_NAME",
                                                   WORKFLOW_NAME, "ENV_NAME", ENV_NAME, "DATE", "DATE"))
                                               .build();

    List<Notification> notifications = Collections.singletonList(notification);

    emailDispatcher.dispatch(notifications, toAddresses);
    Mockito.verify(emailNotificationService).sendAsync(Mockito.any(EmailData.class));
  }
}
