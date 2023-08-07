/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.ecs.beans;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.ecs.EcsS3FetchFileConfig;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
@RecasterAlias("io.harness.cdng.ecs.beans.EcsS3ManifestFileConfigs")
public class EcsS3ManifestFileConfigs {
  EcsS3FetchFileConfig ecsS3TaskDefinitionFileConfig;
  EcsS3FetchFileConfig ecsS3ServiceDefinitionFileConfig;
  List<EcsS3FetchFileConfig> ecsS3ScalableTargetFileConfigs;
  List<EcsS3FetchFileConfig> ecsS3ScalingPolicyFileConfigs;
}
