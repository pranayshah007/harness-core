/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.beans.notification;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.CollectionUtils;

import software.wings.beans.NotificationChannelType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.validation.constraints.NotNull;
import lombok.Value;

@Value
@OwnedBy(PL)
public class NotificationSettings {
  private boolean useIndividualEmails;
  private boolean sendMailToNewMembers;
  @NotNull private List<String> emailAddresses;
  @NotNull private SlackNotificationSetting slackConfig;
  private String pagerDutyIntegrationKey;
  private String microsoftTeamsWebhookUrl;

  @Nonnull
  @JsonIgnore
  public Map<NotificationChannelType, List<String>> getAddressesByChannelType() {
    return Collections.emptyMap();
  }

  @NotNull
  public SlackNotificationSetting getSlackConfig() {
    return slackConfig;
  }

  public List<String> getEmailAddresses() {
    return CollectionUtils.emptyIfNull(emailAddresses);
  }
}
