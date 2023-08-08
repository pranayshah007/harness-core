/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.logging;

import static io.harness.network.SafeHttpCall.execute;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.concurrent.HTimeLimiter;
import io.harness.logging.AccessTokenBean;
import io.harness.logging.RemoteStackdriverLogAppender;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.rest.RestResponse;

import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(HarnessModule._420_DELEGATE_AGENT)
public class DelegateStackdriverLogAppender extends RemoteStackdriverLogAppender {
  private static final String APP_NAME = "delegate";

  private static TimeLimiter timeLimiter;
  private static DelegateAgentManagerClient delegateAgentManagerClient;
  private static String delegateId;

  @Getter @Setter private String accountId;
  @Getter @Setter private String managerHost;

  @Override
  protected String getAppName() {
    return APP_NAME;
  }

  @Override
  protected String getDelegateId() {
    return delegateId;
  }

  @Override
  protected AccessTokenBean getLoggingToken() {
    if (timeLimiter == null || delegateAgentManagerClient == null) {
      return null;
    }

    try {
      RestResponse<AccessTokenBean> response = HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofSeconds(60),
          () -> execute(delegateAgentManagerClient.getLoggingToken(getAccountId())));
      if (response != null) {
        return response.getResource();
      }
    } catch (UncheckedTimeoutException ex) {
      log.warn("Timed out getting logging token", ex);
    } catch (Exception e) {
      log.error("Error getting logging token", e);
    }

    return null;
  }

  public static void setTimeLimiter(TimeLimiter timeLimiter) {
    DelegateStackdriverLogAppender.timeLimiter = timeLimiter;
  }

  public static void setManagerClient(DelegateAgentManagerClient delegateAgentManagerClient) {
    DelegateStackdriverLogAppender.delegateAgentManagerClient = delegateAgentManagerClient;
  }

  public static void setDelegateId(String delegateId) {
    DelegateStackdriverLogAppender.delegateId = delegateId;
  }
}
