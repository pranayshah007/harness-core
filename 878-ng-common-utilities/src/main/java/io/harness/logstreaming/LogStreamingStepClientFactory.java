/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.logstreaming;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.network.SafeHttpCall;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.steps.StepUtils;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.validation.constraints.NotNull;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
public class LogStreamingStepClientFactory {
  @Inject LogStreamingServiceConfiguration logStreamingServiceConfiguration;
  @Inject LogStreamingClient logStreamingClient;
  @Inject LogStreamingServiceRestClient logStreamingServiceRestClient;
  @Inject @Named("logStreamingDelayExecutor") ScheduledExecutorService logStreamingDelayExecutor;

  public LoadingCache<String, String> accountIdToTokenCache =
      CacheBuilder.newBuilder().maximumSize(1000).expireAfterWrite(5, TimeUnit.MINUTES).build(new CacheLoader<>() {
        @Override
        public String load(@NotNull String accountId) throws IOException {
          return retrieveLogStreamingAccountToken(accountId);
        }
      });

  public ILogStreamingStepClient getLogStreamingStepClient(Ambiance ambiance) {
    return getLogStreamingStepClientInternal(ambiance, new HashSet<>());
  }

  private ILogStreamingStepClient getLogStreamingStepClientInternal(Ambiance ambiance, Set<String> secrets) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    try {
      String logBaseKey = getLogBaseKey(ambiance);

      return LogStreamingStepClientImpl.builder()
          .logStreamingClient(logStreamingClient)
          .logStreamingSanitizer(LogStreamingSanitizer.builder().secrets(secrets).build())
          .baseLogKey(logBaseKey)
          .accountId(accountId)
          .token(accountIdToTokenCache.get(accountId))
          .logStreamingDelayExecutor(logStreamingDelayExecutor)
          .delayToClosePrefixLogStream(logStreamingServiceConfiguration.getDelayToClosePrefixLogStreamInSeconds())
          .build();

    } catch (Exception exception) {
      throw new InvalidRequestException("Could not generate token for given account Id " + accountId);
    }
  }

  public String retrieveLogStreamingAccountToken(String accountId) throws IOException {
    return SafeHttpCall.executeWithExceptions(logStreamingServiceRestClient.retrieveAccountToken(
        logStreamingServiceConfiguration.getServiceToken(), accountId));
  }

  public static String getLogBaseKey(Ambiance ambiance, String lastGroup) {
    if (AmbianceUtils.shouldSimplifyLogBaseKey(ambiance)) {
      return LogStreamingHelper.generateSimplifiedLogBaseKey(StepUtils.generateLogAbstractions(ambiance, lastGroup));
    } else {
      return LogStreamingHelper.generateLogBaseKey(StepUtils.generateLogAbstractions(ambiance, lastGroup));
    }
  }

  public static String getLogBaseKey(Ambiance ambiance) {
    return getLogBaseKey(ambiance, null);
  }
}
