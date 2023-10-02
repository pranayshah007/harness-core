/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.ecs;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.storeconfig.S3StoreDelegateConfig;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
@RecasterAlias("io.harness.delegate.task.ecs.EcsS3FetchFileConfig")
public class EcsS3FetchFileConfig {
  String identifier;
  String manifestType;
  S3StoreDelegateConfig s3StoreDelegateConfig;
  boolean succeedIfFileNotFound;
}
