/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.gcp.request;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import java.time.Duration;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@OwnedBy(CDP)
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class GcpListClustersRequest extends GcpRequest {
  @Override
  public Duration getExecutionTimeout() {
    return Duration.ofMinutes(5);
  }
}
