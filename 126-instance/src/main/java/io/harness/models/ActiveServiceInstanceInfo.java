/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.models;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ActiveServiceInstanceInfo {
  private String infraIdentifier;
  private String infraName;
  private String lastPipelineExecutionId;
  private String lastPipelineExecutionName;
  private String lastDeployedAt;
  private String envIdentifier;
  private String envName;
  private String tag;
  private String displayName;
  private int count;
}
