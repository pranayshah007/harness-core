/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.data.Outcome;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@OwnedBy(CDP)
@TypeAlias("k8sCanaryOutcome")
@JsonTypeName("k8sCanaryOutcome")
@RecasterAlias("io.harness.cdng.k8s.K8sCanaryOutcome")
public class K8sCanaryOutcome implements Outcome, ExecutionSweepingOutput {
  String releaseName;
  Integer targetInstances;
  Integer releaseNumber;
  String canaryWorkload;
  K8sGitFetchInfo manifest;
  boolean canaryWorkloadDeployed;
}
