/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import software.wings.beans.SlackMessage;
import software.wings.beans.SlackMessageJSON;
import software.wings.beans.TaskType;
import software.wings.delegatetasks.DelegateTaskType;

@OwnedBy(CDC)
public interface SlackMessageSender {
  @DelegateTaskType(TaskType.SLACK)
  void send(SlackMessage slackMessage, boolean forceProxyCallForDelegate, boolean isCertValidationRequired);

  @DelegateTaskType(TaskType.SLACK) void sendJSON(SlackMessageJSON slackMessage);
}
