package io.harness.template.utils;

import io.harness.account.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.remote.client.RestClientUtils;
import io.harness.utils.RetryUtils;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;

@Slf4j
@OwnedBy(HarnessTeam.PL)
public class NGTemplateFeatureFlagHelperService {
  private static final String ERROR_MESSAGE = "Unexpected error, could not fetch the feature flag";

  @Inject AccountClient accountClient;
  private static final RetryPolicy<Object> fetchRetryPolicy = RetryUtils.getRetryPolicy(
      ERROR_MESSAGE, ERROR_MESSAGE, Lists.newArrayList(InvalidRequestException.class), Duration.ofSeconds(5), 3, log);

  public boolean isEnabled(String accountId, FeatureName featureName) {
    try {
      return Failsafe.with(fetchRetryPolicy)
          .get(() -> RestClientUtils.getResponse(accountClient.isFeatureFlagEnabled(featureName.name(), accountId)));
    } catch (InvalidRequestException e) {
      throw new UnexpectedException(ERROR_MESSAGE);
    }
  }
}
