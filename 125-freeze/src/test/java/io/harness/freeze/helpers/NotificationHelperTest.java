/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.freeze.helpers;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.encryption.Scope;
import io.harness.freeze.beans.FreezeType;
import io.harness.freeze.entity.FreezeConfigEntity;
import io.harness.freeze.notifications.NotificationHelper;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.notification.notificationclient.NotificationResultWithStatus;
import io.harness.rule.Owner;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDC)
public class NotificationHelperTest extends CategoryTest {
  @InjectMocks NotificationHelper notificationHelper;
  @Mock NotificationClient notificationClient;

  private final String ACCOUNT_ID = "accountId";
  private final String ORG_IDENTIFIER = "oId";
  private final String PROJ_IDENTIFIER = "pId";
  private final String FREEZE_IDENTIFIER = "freezeId";

  private String yaml;

  FreezeConfigEntity freezeConfigEntity;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    ClassLoader classLoader = this.getClass().getClassLoader();
    String filename = "projectFreezeConfig.yaml";
    yaml = Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);

    freezeConfigEntity = FreezeConfigEntity.builder()
                             .accountId(ACCOUNT_ID)
                             .orgIdentifier(ORG_IDENTIFIER)
                             .projectIdentifier(PROJ_IDENTIFIER)
                             .identifier(FREEZE_IDENTIFIER)
                             .name(FREEZE_IDENTIFIER)
                             .yaml(yaml)
                             .type(FreezeType.MANUAL)
                             .freezeScope(Scope.PROJECT)
                             .build();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testsendNotification() throws IOException {
    when(notificationClient.sendNotificationAsync(any())).thenReturn(NotificationResultWithStatus.builder().build());
    notificationHelper.sendNotification(freezeConfigEntity);
    verify(notificationClient, times(7)).sendNotificationAsync(any());
  }
}
