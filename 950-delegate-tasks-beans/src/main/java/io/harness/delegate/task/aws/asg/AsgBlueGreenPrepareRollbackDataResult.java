/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws.asg;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AsgLoadBalancerConfig;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(HarnessTeam.CDP)
public class AsgBlueGreenPrepareRollbackDataResult {
  String prodAsgName;
  String asgName;
  @Deprecated AsgLoadBalancerConfig asgLoadBalancerConfig;
  Map<String, List<String>> prodAsgManifestsDataForRollback;
  Map<String, List<String>> stageAsgManifestsDataForRollback;
  List<AsgLoadBalancerConfig> loadBalancers;
}
