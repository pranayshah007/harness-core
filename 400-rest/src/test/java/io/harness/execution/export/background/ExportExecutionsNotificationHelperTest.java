/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution.export.background;

import static io.harness.rule.OwnerRule.GARVIT;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.EmbeddedUser;
import io.harness.category.element.UnitTests;
import io.harness.execution.export.request.ExportExecutionsRequest;
import io.harness.execution.export.request.ExportExecutionsRequestHelper;
import io.harness.execution.export.request.ExportExecutionsRequestSummary;
import io.harness.execution.export.request.RequestTestUtils;
import io.harness.rule.Owner;

import software.wings.beans.Notification;
import software.wings.beans.NotificationRule;
import software.wings.service.intfc.NotificationService;

import com.google.inject.Inject;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class ExportExecutionsNotificationHelperTest extends CategoryTest {
  @Mock private ExportExecutionsRequestHelper exportExecutionsRequestHelper;
  @Mock private NotificationService notificationService;
  @Inject @InjectMocks private ExportExecutionsNotificationHelper exportExecutionsNotificationHelper;

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testDispatchToTriggeringUser() {
    when(exportExecutionsRequestHelper.prepareSummary(any()))
        .thenReturn(ExportExecutionsRequestSummary.builder().build());

    ExportExecutionsRequest request =
        RequestTestUtils.prepareExportExecutionsRequest(ExportExecutionsRequest.Status.READY);
    request.setNotifyOnlyTriggeringUser(true);
    exportExecutionsNotificationHelper.dispatch(request);
    verify(notificationService, never()).sendNotificationToTriggeredByUserOnly(any(), any());

    EmbeddedUser embeddedUser = EmbeddedUser.builder().name("n").email("e").build();
    request.setCreatedBy(embeddedUser);
    exportExecutionsNotificationHelper.dispatch(request);
    ArgumentCaptor<Notification> argument = ArgumentCaptor.forClass(Notification.class);
    verify(notificationService, times(1)).sendNotificationToTriggeredByUserOnly(argument.capture(), eq(embeddedUser));
    assertThat(argument.getValue()).isNotNull();

    request = RequestTestUtils.prepareExportExecutionsRequest(ExportExecutionsRequest.Status.FAILED);
    request.setNotifyOnlyTriggeringUser(true);
    request.setCreatedBy(embeddedUser);
    exportExecutionsNotificationHelper.dispatch(request);
    argument = ArgumentCaptor.forClass(Notification.class);
    verify(notificationService, times(2)).sendNotificationToTriggeredByUserOnly(argument.capture(), eq(embeddedUser));
    assertThat(argument.getValue()).isNotNull();

    request = RequestTestUtils.prepareExportExecutionsRequest();
    request.setNotifyOnlyTriggeringUser(true);
    request.setCreatedBy(embeddedUser);
    exportExecutionsNotificationHelper.dispatch(request);
    argument = ArgumentCaptor.forClass(Notification.class);
    verify(notificationService, times(3)).sendNotificationToTriggeredByUserOnly(argument.capture(), eq(embeddedUser));
    assertThat(argument.getValue()).isNull();

    request = RequestTestUtils.prepareExportExecutionsRequest(ExportExecutionsRequest.Status.EXPIRED);
    request.setNotifyOnlyTriggeringUser(true);
    request.setCreatedBy(embeddedUser);
    exportExecutionsNotificationHelper.dispatch(request);
    argument = ArgumentCaptor.forClass(Notification.class);
    verify(notificationService, times(4)).sendNotificationToTriggeredByUserOnly(argument.capture(), eq(embeddedUser));
    assertThat(argument.getValue()).isNull();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testDispatchToUserGroup() {
    when(exportExecutionsRequestHelper.prepareSummary(any()))
        .thenReturn(ExportExecutionsRequestSummary.builder().build());

    ExportExecutionsRequest request =
        RequestTestUtils.prepareExportExecutionsRequest(ExportExecutionsRequest.Status.READY);
    exportExecutionsNotificationHelper.dispatch(request);
    verify(notificationService, never()).sendNotificationAsync(any(), any());

    request.setUserGroupIds(asList("uid1", "uid2"));
    exportExecutionsNotificationHelper.dispatch(request);
    ArgumentCaptor<List> argument = ArgumentCaptor.forClass(List.class);
    verify(notificationService, times(1)).sendNotificationAsync(any(), argument.capture());
    assertThat(argument.getValue()).isNotNull();
    assertThat(argument.getValue().size()).isEqualTo(1);
    assertThat(((NotificationRule) argument.getValue().get(0)).getUserGroupIds()).containsExactly("uid1", "uid2");
  }
}
