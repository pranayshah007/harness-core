/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.ecs.beans;

import io.harness.delegate.task.ecs.EcsS3FetchFileConfig;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EcsRunTaskS3FileConfigs {
  EcsS3FetchFileConfig runTaskDefinitionS3FetchFileConfig;
  EcsS3FetchFileConfig runTaskRequestDefinitionS3FetchFileConfig;
}
