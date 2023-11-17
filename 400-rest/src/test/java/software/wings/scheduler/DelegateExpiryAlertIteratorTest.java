/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scheduler;

import static io.harness.delegate.beans.DelegateType.KUBERNETES;
import static io.harness.rule.OwnerRule.JENNY;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateGroup;
import io.harness.iterator.DelegateExpiryAlertIterator;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.notification.NotificationTriggerRequest;
import io.harness.notification.entities.NotificationEntity;
import io.harness.notification.entities.NotificationEvent;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class DelegateExpiryAlertIteratorTest extends WingsBaseTest {
  private static final String VERSION = "1.0.0";
  private static final String DELEGATE_TYPE = "dockerType";

  @Mock PersistenceIteratorFactory persistenceIteratorFactory;
  @InjectMocks @Inject private DelegateExpiryAlertIterator delegateExpiryAlertIterator;
  @Inject private HPersistence persistence;
  @Mock private NotificationClient notificationClient;

  private static final long TIMESTAMP = 1698209786;

  @Before
  public void setup() {}

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testNotificationOnDelegateExpiry() {
    ArgumentCaptor<NotificationTriggerRequest> argumentCaptor =
        ArgumentCaptor.forClass(NotificationTriggerRequest.class);
    delegateExpiryAlertIterator.handle(delegateGroup());
    verify(notificationClient).sendNotificationTrigger(argumentCaptor.capture());
    NotificationTriggerRequest notificationTriggerRequest = argumentCaptor.getValue();
    assertThat(notificationTriggerRequest).isNotNull();
    assertThat(notificationTriggerRequest.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(notificationTriggerRequest.getEvent()).isEqualTo(NotificationEvent.DELEGATE_EXPIRED.name());
    assertThat(notificationTriggerRequest.getEventEntity()).isEqualTo(NotificationEntity.DELEGATE.name());
    assertThat(notificationTriggerRequest.getAccountId()).isEqualTo(ACCOUNT_ID);
    verify(notificationClient, times(1)).sendNotificationTrigger(any(NotificationTriggerRequest.class));
  }

  private DelegateGroup delegateGroup() {
    return DelegateGroup.builder()
        .name("grp1")
        .accountId(ACCOUNT_ID)
        .ng(true)
        .delegatesExpireOn(TIMESTAMP)
        .identifier("_localDelegate")
        .delegateType(KUBERNETES)
        .description("description")
        .delegateConfigurationId("delegateProfileId")
        .tags(ImmutableSet.of("custom-grp-tag"))
        .build();
  }
}
