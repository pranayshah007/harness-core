/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.models;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@AllArgsConstructor
@EqualsAndHashCode
public class ActiveServiceInstanceInfoV2 {
  private String serviceIdentifier;
  private String serviceName;
  private String envIdentifier;
  private String envName;
  private String infraIdentifier;
  private String infraName;
  private String clusterIdentifier;
  private String agentIdentifier;
  private String lastPipelineExecutionId;
  private String lastPipelineExecutionName;
  private Long lastDeployedAt;
  private String tag;
  private String displayName;
  private int count;
}
