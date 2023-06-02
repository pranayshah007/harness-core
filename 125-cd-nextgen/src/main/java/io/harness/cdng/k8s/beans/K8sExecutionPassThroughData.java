/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.k8s.beans;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.k8s.K8sGitFetchInfo;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.task.localstore.ManifestFiles;
import io.harness.pms.sdk.core.steps.io.PassThroughData;

import java.util.List;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@OwnedBy(CDP)
@TypeAlias("k8sExecutionPassThroughData")
@RecasterAlias("io.harness.cdng.k8s.beans.K8sExecutionPassThroughData")
public class K8sExecutionPassThroughData implements PassThroughData {
  InfrastructureOutcome infrastructure;
  UnitProgressData lastActiveUnitProgressData;
  String zippedManifestId;
  List<ManifestFiles> manifestFiles;
  K8sGitFetchInfo k8sGitFetchInfo;
}
