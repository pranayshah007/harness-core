/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.watcher.logging;

import static io.harness.network.SafeHttpCall.execute;
import static io.harness.watcher.app.WatcherApplication.getConfiguration;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.substringBefore;

import io.harness.concurrent.HTimeLimiter;
import io.harness.logging.RemoteStackdriverLogAppender;
import io.harness.logging.common.AccessTokenBean;
import io.harness.managerclient.ManagerClientV2;
import io.harness.rest.RestResponse;

import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WatcherStackdriverLogAppender extends RemoteStackdriverLogAppender {
  private static final String APP_NAME = "watcher";

  private static TimeLimiter timeLimiter;
  private static ManagerClientV2 managerClient;

  private String accountId = "";
  private String managerHost = "";

  @Override
  protected String getAppName() {
    return APP_NAME;
  }

  @Override
  protected String getAccountId() {
    if (isBlank(accountId) && getConfiguration() != null) {
      accountId = getConfiguration().getAccountId();
    }
    return accountId;
  }

  @Override
  protected String getManagerHost() {
    if (isBlank(managerHost) && getConfiguration() != null) {
      managerHost = substringBefore(getConfiguration().getManagerUrl(), "/api/");
    }
    return managerHost;
  }

  @Override
  protected String getDelegateId() {
    return null;
  }

  @Override
  protected AccessTokenBean getLoggingToken() {
    if (timeLimiter == null || managerClient == null) {
      return null;
    }

    try {
      RestResponse<AccessTokenBean> response = HTimeLimiter.callInterruptible21(
          timeLimiter, Duration.ofSeconds(15), () -> execute(managerClient.getLoggingToken(getAccountId())));
      if (response != null) {
        return response.getResource();
      }
    } catch (UncheckedTimeoutException ex) {
      log.warn("Timed out getting logging token");
    } catch (Exception e) {
      log.error("Error getting logging token", e);
    }

    return null;
  }

  public static void setTimeLimiter(TimeLimiter timeLimiter) {
    WatcherStackdriverLogAppender.timeLimiter = timeLimiter;
  }

  public static void setManagerClient(ManagerClientV2 managerClient) {
    WatcherStackdriverLogAppender.managerClient = managerClient;
  }
}
