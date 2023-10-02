/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.metrics.impl;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.metrics.beans.LoginApiMetricContext;
import io.harness.metrics.service.api.MetricService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
@Slf4j
@OwnedBy(HarnessTeam.PL)
public class LoginApiMetricsServiceImpl {
  public static final String LOGIN_SUCCESS_COUNT = "login_success_count";
  public static final String LOGIN_FAILURE_COUNT = "login_failure_count";

  @Inject private MetricService metricService;

  private void recordApiRequestMetric(String loginType, String loginSubType, String accountId, String metricName) {
    try (LoginApiMetricContext ignore = new LoginApiMetricContext(loginType, loginSubType, accountId)) {
      metricService.incCounter(metricName);
    }
  }

  public void recordLoginRequestSuccessSaml(String accountId) {
    recordApiRequestMetric("SAML", "NONE", accountId, LOGIN_SUCCESS_COUNT);
  }

  public void recordLoginRequestSuccessLdap(String accountId) {
    recordApiRequestMetric("LDAP", "NONE", accountId, LOGIN_SUCCESS_COUNT);
  }

  public void recordLoginRequestSuccessOAuth(String provider, String accountId) {
    recordApiRequestMetric("OAUTH", provider, accountId, LOGIN_SUCCESS_COUNT);
  }

  public void recordLoginRequestSuccessPassword(String accountId) {
    recordApiRequestMetric("PASSWORD", "NONE", accountId, LOGIN_SUCCESS_COUNT);
  }

  public void recordLoginRequestFailureSaml(String accountId) {
    recordApiRequestMetric("SAML", "NONE", accountId, LOGIN_FAILURE_COUNT);
  }

  public void recordLoginRequestFailureLdap(String accountId) {
    recordApiRequestMetric("LDAP", "NONE", accountId, LOGIN_FAILURE_COUNT);
  }

  public void recordLoginRequestFailureOAuth(String provider, String accountId) {
    recordApiRequestMetric("OAUTH", provider, accountId, LOGIN_FAILURE_COUNT);
  }

  public void recordLoginRequestFailurePassword(String accountId) {
    recordApiRequestMetric("PASSWORD", "NONE", accountId, LOGIN_FAILURE_COUNT);
  }
}
