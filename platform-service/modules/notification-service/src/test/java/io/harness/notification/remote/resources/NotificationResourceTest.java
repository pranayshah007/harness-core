/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.notification.remote.resources;

import static io.harness.notification.utils.NotificationRequestTestUtils.getDummyNotification;
import static io.harness.rule.OwnerRule.ANKUSH;

import static junit.framework.TestCase.assertNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.notification.Team;
import io.harness.notification.remote.dto.NotificationDTO;
import io.harness.notification.service.api.NotificationService;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageImpl;

public class NotificationResourceTest extends CategoryTest {
  @Mock private NotificationService notificationService;
  private NotificationResource notificationResource;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    notificationResource = new NotificationResourceImpl(notificationService);
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void get_ValidNotificationId() {
    String notificationId = "12345";
    when(notificationService.getnotification(notificationId))
        .thenReturn(Optional.of(getDummyNotification(notificationId)));
    ResponseDTO<NotificationDTO> response = notificationResource.get(notificationId);
    assertThat(response.getData().getId()).isEqualTo(notificationId);
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void get_InvalidNotificationId() {
    String notificationId = "12345";
    when(notificationService.getnotification(notificationId)).thenReturn(Optional.empty());
    ResponseDTO<NotificationDTO> response = notificationResource.get(notificationId);
    assertNull(response.getData());
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void list_getAllNotifications() {
    Team team = Team.CD;
    PageRequest pageRequest = PageRequest.builder().pageSize(2).build();
    when(notificationService.list(team, pageRequest))
        .thenReturn(new PageImpl<>(Arrays.asList(getDummyNotification("1"), getDummyNotification("2"))));
    ResponseDTO<PageResponse<NotificationDTO>> response = notificationResource.list(team, pageRequest);
    List<NotificationDTO> responseNotificationDTOs = response.getData().getContent();
    assertThat(responseNotificationDTOs.get(0).getId()).isEqualTo("1");
    assertThat(responseNotificationDTOs.get(1).getId()).isEqualTo("2");
  }
}
