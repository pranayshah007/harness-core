/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.watcher.app;

import io.harness.event.client.impl.EventPublisherConstants;

import java.util.Optional;
import lombok.Data;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;

/**
 * Created by brett on 10/30/17
 */
@Data
@ToString
public class WatcherConfiguration {
  private String accountId;
  @ToString.Exclude private String accountSecret;
  @ToString.Exclude private String delegateToken;
  private String managerUrl;
  private boolean doUpgrade;
  private String upgradeCheckLocation;
  private long upgradeCheckIntervalSeconds;
  private String delegateCheckLocation;
  private String queueFilePath;
  private String publishTarget;
  private String publishAuthority;
  private boolean fileHandlesMonitoringEnabled;
  private long fileHandlesMonitoringIntervalInMinutes;
  private long fileHandlesLogsRetentionInMinutes;

  // TODO: This method will get removed once we rolled out new delegate.
  public String getDelegateToken() {
    if (StringUtils.isEmpty(delegateToken)) {
      // Return account secret only if delegate token is not available.
      return accountSecret;
    }
    return delegateToken;
  }

  public String getQueueFilePath() {
    return Optional.ofNullable(queueFilePath).orElse(EventPublisherConstants.DEFAULT_QUEUE_FILE_PATH);
  }
}
