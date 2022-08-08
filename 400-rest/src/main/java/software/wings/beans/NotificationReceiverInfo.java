/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import software.wings.beans.notification.SlackNotificationSetting;

import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

/**
 * This is kind of a union interface for NotificationGroup and UserGroup to help with migration between the two.
 * The interface can be deleted once {@link software.wings.beans.NotificationGroup} class is removed.
 */
@OwnedBy(PL)
public interface NotificationReceiverInfo {
  @NotNull Map<NotificationChannelType, List<String>> getAddressesByChannelType();

  @Nullable SlackNotificationSetting getSlackConfig();

  @NotNull List<String> getEmailAddresses();

  @Nullable String getPagerDutyIntegrationKey();

  @Nullable String getMicrosoftTeamsWebhookUrl();
}
