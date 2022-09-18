package io.harness.watcher.app;

import static io.harness.concurrent.HTimeLimiter.callInterruptible21;

import static java.time.Duration.ofMinutes;

import io.harness.delegate.beans.DelegateConfiguration;
import io.harness.managerclient.ManagerClientV2;
import io.harness.managerclient.SafeHttpCall;
import io.harness.rest.RestResponse;

import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class DelegateConfigurationController {
  @Inject private ManagerClientV2 managerClient;
  @Inject private TimeLimiter timeLimiter;
  @Inject private WatcherConfiguration watcherConfiguration;
  public DelegateConfiguration delegateConfiguration;

  public DelegateConfigurationController() {
    delegateConfiguration = fetchDelegateConfiguration();
  }

  public DelegateConfiguration fetchDelegateConfiguration() {
    RestResponse<DelegateConfiguration> restResponse = null;
    try {
      log.info("Watcher getting delegate configuration");
      restResponse = callInterruptible21(timeLimiter, ofMinutes(15),
          () -> SafeHttpCall.execute(managerClient.getDelegateConfiguration(watcherConfiguration.getAccountId())));
    } catch (Exception e) {
      log.error("Call failed on getting delegate configuration. ", e);
    }

    if (restResponse == null) {
      log.error("Watcher unable to get delegate configuration from harness");
      return null;
    }
    delegateConfiguration = restResponse.getResource();
    return delegateConfiguration;
  }
}
