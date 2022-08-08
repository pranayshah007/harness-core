/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import software.wings.beans.notification.SlackNotificationConfiguration;

import java.util.List;

/**
 * Created by anubhaw on 12/14/16.
 */
@OwnedBy(CDC)
public interface SlackNotificationService {
  /**
   * Send message.
   *
   * @param slackConfig the slack config
   * @param slackChannel the slack channel
   * @param senderName  the sender name
   * @param message     the message
   */
  void sendMessage(SlackNotificationConfiguration slackConfig, String slackChannel, String senderName, String message,
      String accountId);

  /**
   * This handled json based messages and not YAML based templates.
   * @param message
   * @param slackWebhooks
   */
  void sendJSONMessage(String message, List<String> slackWebhooks, String accountId);
}
