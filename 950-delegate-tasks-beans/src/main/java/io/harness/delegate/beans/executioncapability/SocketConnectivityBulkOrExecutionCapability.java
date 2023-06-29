/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.executioncapability;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import java.time.Duration;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SocketConnectivityBulkOrExecutionCapability implements ExecutionCapability {
  private final CapabilityType capabilityType = CapabilityType.SOCKET_BULK_OR;

  protected List<String> hostNames;
  protected Integer port;

  @Override
  public EvaluationMode evaluationMode() {
    return EvaluationMode.AGENT;
  }

  @Override
  public String fetchCapabilityBasis() {
    return String.format("Hosts: [%s], port: %d", hostNames.toString(), port);
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
    return isNotEmpty(fetchCapabilityBasis()) ? String.format("Capability to reach, %s ", fetchCapabilityBasis())
                                              : null;
  }

  /**
   * Error message to show mostly in delegate selection log if none of the delegates passed the validation check
   */
  @Override
  public String getCapabilityValidationError() {
    // Following delegate(s) unable to connect to any of the [hostname1, hostname2]:  [h1,h2]
    return isNotEmpty(fetchCapabilityBasis())
        ? String.format("Following delegate(s) unable to connect to any of the %s", fetchCapabilityBasis())
        : ExecutionCapability.super.getCapabilityValidationError();
  }
}
