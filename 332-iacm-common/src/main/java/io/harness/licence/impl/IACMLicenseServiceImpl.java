/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.licence.impl;

import static io.harness.annotations.dev.HarnessTeam.IACM;

import static java.lang.String.format;

import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.execution.license.CILicenseService;
import io.harness.exception.ConnectorNotFoundException;
import io.harness.licensing.beans.summary.LicensesWithSummaryDTO;
import io.harness.licensing.remote.NgLicenseHttpClient;
import io.harness.ng.core.dto.ResponseDTO;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import retrofit2.Response;

@OwnedBy(IACM)
@Slf4j
@Singleton
public class IACMLicenseServiceImpl implements CILicenseService {
  private final Duration RETRY_SLEEP_DURATION = Duration.ofSeconds(2);
  private final int MAX_ATTEMPTS = 3;
  @Inject NgLicenseHttpClient ngLicenseHttpClient;

  private static final int CACHE_EVICTION_TIME_MINUTES = 10;

  private final LoadingCache<String, LicensesWithSummaryDTO> licenseCache =
      CacheBuilder.newBuilder()
          .expireAfterWrite(CACHE_EVICTION_TIME_MINUTES, TimeUnit.MINUTES)
          .build(new CacheLoader<>() {
            @Override
            public LicensesWithSummaryDTO load(@org.jetbrains.annotations.NotNull final String accountId) {
              return fetchLicenseSummary(accountId);
            }
          });

  public LicensesWithSummaryDTO getLicenseSummary(@NotNull String accountId) {
    try {
      return licenseCache.get(accountId);
    } catch (Exception e) {
      log.error("Error getting license summary for account {} with error {}", accountId, e);
      return null;
    }
  }

  private LicensesWithSummaryDTO fetchLicenseSummary(String accountId) {
    try {
      RetryPolicy<Object> retryPolicy = getRetryPolicy(format("[Retrying failed call to fetch license summary: {}"),
          format("Failed to fetch license summary after retrying {} times"));
      Response<ResponseDTO<LicensesWithSummaryDTO>> response =
          Failsafe.with(retryPolicy)
              .get(() -> ngLicenseHttpClient.getLicenseSummary(accountId, ModuleType.IACM.toString()).execute());
      if (response.isSuccessful()) {
        ResponseDTO<LicensesWithSummaryDTO> responseDTO = response.body();
        if (responseDTO != null && responseDTO.getData() != null) {
          return responseDTO.getData();
        }
      } else {
        log.warn("Error getting license summary for account {} with error {}", accountId, response.errorBody());
      }
    } catch (Exception e) {
      log.warn("Error getting license summary for account {} with error {}", accountId, e);
      return null;
    }

    return null;
  }

  private RetryPolicy<Object> getRetryPolicy(String failedAttemptMessage, String failureMessage) {
    return new RetryPolicy<>()
        .handle(Exception.class)
        .abortOn(ConnectorNotFoundException.class)
        .withDelay(RETRY_SLEEP_DURATION)
        .withMaxAttempts(MAX_ATTEMPTS)
        .onFailedAttempt(event -> log.info(failedAttemptMessage, event.getAttemptCount(), event.getLastFailure()))
        .onFailure(event -> log.error(failureMessage, event.getAttemptCount(), event.getFailure()));
  }
}
