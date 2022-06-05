package io.harness.utils;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import io.harness.account.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.remote.client.RestClientUtils;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;

import java.time.Duration;

@Slf4j
@OwnedBy(HarnessTeam.PL)
public class NGFeatureFlagHelperService {
    @Inject
    AccountClient accountClient;
    private static final RetryPolicy<Object> fetchRetryPolicy =
            RetryUtils.getRetryPolicy("Unexpected error, could not fetch the feature flag",
                    "Unexpected error, could not fetch the could not fetch the feature flag",
                    Lists.newArrayList(InvalidRequestException.class), Duration.ofSeconds(5), 3, log);

    public boolean isEnabled(String accountId, FeatureName featureName) {
        try {
            return Failsafe.with(fetchRetryPolicy)
                    .get(() -> RestClientUtils.getResponse(accountClient.isFeatureFlagEnabled(featureName.name(), accountId)));
        }
        catch (InvalidRequestException e) {
            throw new UnexpectedException("Unexpected error, could not fetch the feature flag");
        }
    }
}
