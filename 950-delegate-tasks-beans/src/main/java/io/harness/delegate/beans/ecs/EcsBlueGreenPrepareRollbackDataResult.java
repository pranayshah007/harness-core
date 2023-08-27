/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.ecs;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.ecs.EcsLoadBalancerConfig;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(HarnessTeam.CDP)
public class EcsBlueGreenPrepareRollbackDataResult {
  String serviceName;
  String createServiceRequestBuilderString;
  List<String> registerScalableTargetRequestBuilderStrings;
  List<String> registerScalingPolicyRequestBuilderStrings;
  boolean isFirstDeployment;
  EcsLoadBalancerConfig ecsLoadBalancerConfig;
  String greenServiceName;
  String greenServiceRequestBuilderString;
  List<String> greenServiceScalableTargetRequestBuilderStrings;
  List<String> greenServiceScalingPolicyRequestBuilderStrings;
  boolean greenServiceExist;
  boolean greenServiceRollbackDataExist;
}
