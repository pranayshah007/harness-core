/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.executioncapability;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER_SRE;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.delegate.task.ssh.WinRmInfraDelegateConfig;
import io.harness.delegate.task.utils.PhysicalDataCenterUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.secretmanagerclient.WinRmAuthScheme;

import java.time.Duration;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WinrmConnectivityExecutionCapability implements ExecutionCapability {
  private static final String UNKNOWN_HOST = "Unknown host";
  private final CapabilityType capabilityType = CapabilityType.NG_WINRM_HOST_CONNECTION;

  WinRmInfraDelegateConfig winRmInfraDelegateConfig;
  boolean useWinRMKerberosUniqueCacheFile;
  String host;

  @Override
  public EvaluationMode evaluationMode() {
    return EvaluationMode.AGENT;
  }

  @Override
  public String fetchCapabilityBasis() {
    StringBuilder builder = new StringBuilder(128);

    String port = String.valueOf(winRmInfraDelegateConfig.getWinRmCredentials().getPort());
    String extractedHost = PhysicalDataCenterUtils.extractHostnameFromHost(host).orElseThrow(
        ()
            -> new InvalidArgumentsException(
                format("Not found hostName, host: %s, extracted port: %s", host, port), USER_SRE));

    builder.append(extractedHost);
    if (isNotBlank(port)) {
      builder.append(':').append(port);
    }

    if (winRmInfraDelegateConfig.getWinRmCredentials().getAuth().getAuthScheme() == WinRmAuthScheme.Kerberos) {
      builder.append(":kerberos");
    }

    return builder.toString();
  }

  @Override
  public Duration getMaxValidityPeriod() {
    return Duration.ofHours(6);
  }

  @Override
  public Duration getPeriodUntilNextValidation() {
    return Duration.ofHours(4);
  }

  @Override
  public String getCapabilityToString() {
    String authScheme = winRmInfraDelegateConfig.getWinRmCredentials() != null
        ? winRmInfraDelegateConfig.getWinRmCredentials().getAuth().getAuthScheme().toString()
        : " no authentication details";
    String host = fetchCapabilityBasis();
    return isNotEmpty(host) ? String.format("Capability to connect %s with %s", host, authScheme) : null;
  }

  /**
   * Error message to show mostly in delegate selection log if none of the delegates passed the validation check
   */
  @Override
  public String getCapabilityValidationError() {
    return isNotEmpty(getCapabilityToString())
        ? String.format("Following delegate(s) doesn't have %s", getCapabilityToString())
        : ExecutionCapability.super.getCapabilityValidationError();
  }
}
