/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.asg;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.data.Outcome;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDP)
@Value
@Builder
@TypeAlias("asgBlueGreenPrepareRollbackDataOutcome")
@JsonTypeName("asgBlueGreenPrepareRollbackDataOutcome")
@RecasterAlias("io.harness.cdng.aws.asg.AsgBlueGreenPrepareRollbackDataOutcome")
public class AsgBlueGreenPrepareRollbackDataOutcome implements Outcome, ExecutionSweepingOutput {
  Map<String, List<String>> prodAsgManifestDataForRollback;
  Map<String, List<String>> stageAsgManifestDataForRollback;
  String prodAsgName;
  String asgName;
  String loadBalancer;
  String prodListenerArn;
  String prodListenerRuleArn;
  List<String> prodTargetGroupArnsList;
  String stageListenerArn;
  String stageListenerRuleArn;
  List<String> stageTargetGroupArnsList;
  List<AwsAsgLoadBalancerConfigYaml> loadBalancerConfigs;
}
