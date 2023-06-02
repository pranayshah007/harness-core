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
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.data.Outcome;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDP)
@Value
@Builder
@TypeAlias("k8sRollingOutcome")
@JsonTypeName("k8sRollingOutcome")
@RecasterAlias("io.harness.cdng.k8s.K8sRollingOutcome")
public class K8sRollingOutcome implements Outcome, ExecutionSweepingOutput {
  String releaseName;
  int releaseNumber;
  K8sGitFetchInfo manifest;
  List<KubernetesResourceId> prunedResourceIds;
}
