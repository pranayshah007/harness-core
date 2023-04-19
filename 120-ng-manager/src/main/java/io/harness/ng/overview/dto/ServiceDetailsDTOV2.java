/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.overview.dto;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.models.dashboard.InstanceCountDetailsByEnvTypeBase;

import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@OwnedBy(HarnessTeam.DX)
@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ServiceDetailsDTOV2 {
  String serviceName;
  String serviceIdentifier;
  String description;
  Map<String, String> tags;
  Set<String> deploymentTypeList;
  Set<IconDTO> deploymentIconList;
  long totalDeployments;
  ChangeRate totalDeploymentChangeRate;
  double successRate;
  ChangeRate successRateChangeRate;
  double failureRate;
  ChangeRate failureRateChangeRate;
  double frequency;
  ChangeRate frequencyChangeRate;
  InstanceCountDetailsByEnvTypeBase instanceCountDetails;
  ServicePipelineInfo lastPipelineExecuted;
}
