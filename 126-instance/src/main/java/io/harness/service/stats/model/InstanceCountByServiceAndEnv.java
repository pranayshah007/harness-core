/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.service.stats.model;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;

@Value
@Builder
@AllArgsConstructor
@FieldNameConstants(innerTypeName = "InstanceCountByServiceAndEnvKeys")
@OwnedBy(HarnessTeam.CDP)
public class InstanceCountByServiceAndEnv {
  private String serviceIdentifier;
  private String envIdentifier;
  private String accountId;
  private String projectIdentifier;
  private String orgIdentifier;
  private String instanceType;
  private String connectorRef;
  private int count;
}