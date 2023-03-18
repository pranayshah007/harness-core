/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.k8s;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.data.Outcome;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
@TypeAlias("K8sDryRunManifestOutcome")
@JsonTypeName("K8sDryRunManifestOutcome")
@RecasterAlias("io.harness.cdng.k8s.K8sDryRunManifestOutcome")
public class K8sDryRunManifestOutcome implements Outcome, ExecutionSweepingOutput {
  public static final String OUTPUT_NAME = "k8s";
  String manifestDryRun;
  K8sGitFetchInfo manifest;
}
