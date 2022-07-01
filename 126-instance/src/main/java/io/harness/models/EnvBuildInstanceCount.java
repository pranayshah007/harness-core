/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.models;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@OwnedBy(HarnessTeam.DX)
public class EnvBuildInstanceCount {
  private String infraIdentifier;
  private String infraName;
  private String lastPipelineExecutionId;
  private String lastPipelineExecutionName;
  private String envIdentifier;
  private String envName;
  private String tag;
  private int count;
}
