/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aws.asg.manifest;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.asg.AsgSdkManager;
import io.harness.aws.asg.manifest.request.AsgShiftTrafficManifestRequest;
import io.harness.aws.beans.AsgLoadBalancerConfig;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.manifest.request.ManifestRequest;

import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.PutScalingPolicyRequest;
import java.util.Arrays;
import java.util.List;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetGroupTuple;

@OwnedBy(CDP)
public class AsgShiftTrafficHandler extends AsgManifestHandler<PutScalingPolicyRequest> {
  public static final int MIN_TRAFFIC_SHIFT_WEIGHT = 0;
  public static final int MAX_TRAFFIC_SHIFT_WEIGHT = 100;

  public AsgShiftTrafficHandler(AsgSdkManager asgSdkManager, ManifestRequest manifestRequest) {
    super(asgSdkManager, manifestRequest);
  }

  @Override
  public Class<PutScalingPolicyRequest> getManifestContentUnmarshallClass() {
    return PutScalingPolicyRequest.class;
  }

  @Override
  public AsgManifestHandlerChainState upsert(AsgManifestHandlerChainState chainState, ManifestRequest manifestRequest) {
    AsgShiftTrafficManifestRequest asgShiftTrafficManifestRequest = (AsgShiftTrafficManifestRequest) manifestRequest;
    int weight = asgShiftTrafficManifestRequest.getWeight();
    asgSdkManager.info("Shifting traffic to %d weight", weight);

    int stageTrafficWeight = changeTargetGroupsWeight(asgShiftTrafficManifestRequest.getRegion(),
        asgShiftTrafficManifestRequest.getLoadBalancers(), asgShiftTrafficManifestRequest.getAwsInternalConfig(),
        weight);

    // update BLUE|GREEN tags only if last step (weight=100%)
    if (stageTrafficWeight == MAX_TRAFFIC_SHIFT_WEIGHT) {
      asgSdkManager.info("Updating tags of the autoscaling groups");
      asgSdkManager.updateBGTags(chainState.getNewAsgName(), AsgSdkManager.BG_BLUE);
      if (isNotEmpty(chainState.getAsgName())) {
        AutoScalingGroup oldAsg = asgSdkManager.getASG(chainState.getAsgName());
        if (oldAsg != null) {
          asgSdkManager.updateBGTags(chainState.getAsgName(), AsgSdkManager.BG_GREEN);
        }
      }
      asgSdkManager.info("Successfully updated tags");
    }

    return chainState;
  }

  public int changeTargetGroupsWeight(
      String region, List<AsgLoadBalancerConfig> lbConfigs, AwsInternalConfig awsInternalConfig, int weight) {
    int stageTrafficWeight = weight;
    if (stageTrafficWeight < MIN_TRAFFIC_SHIFT_WEIGHT) {
      stageTrafficWeight = MIN_TRAFFIC_SHIFT_WEIGHT;
    } else if (stageTrafficWeight > MAX_TRAFFIC_SHIFT_WEIGHT) {
      stageTrafficWeight = MAX_TRAFFIC_SHIFT_WEIGHT;
    }
    int prodTrafficWeight = MAX_TRAFFIC_SHIFT_WEIGHT - stageTrafficWeight;

    for (AsgLoadBalancerConfig lbCfg : lbConfigs) {
      String stageTargetGroupArn = lbCfg.getStageTargetGroupArnsList().get(0);
      TargetGroupTuple stageTargetGroupTuple =
          TargetGroupTuple.builder().targetGroupArn(stageTargetGroupArn).weight(stageTrafficWeight).build();

      String prodTargetGroupArn = lbCfg.getProdTargetGroupArnsList().get(0);
      TargetGroupTuple prodTargetGroupTuple =
          TargetGroupTuple.builder().targetGroupArn(prodTargetGroupArn).weight(prodTrafficWeight).build();

      List<TargetGroupTuple> targetGroupTuples = Arrays.asList(stageTargetGroupTuple, prodTargetGroupTuple);

      asgSdkManager.info(
          "Modifying ALB Prod Listener to Forward requests to Target groups associated with new autoscaling group for loadBalancer: %s",
          lbCfg.getLoadBalancer());
      // modify prod listener rule with stage target group
      modifyListenerRule(
          region, lbCfg.getProdListenerArn(), lbCfg.getProdListenerRuleArn(), targetGroupTuples, awsInternalConfig);
      asgSdkManager.info(
          "Successfully shifted traffic to %d weight for loadBalancer: %s %n%n", weight, lbCfg.getLoadBalancer());
    }

    return stageTrafficWeight;
  }

  public void modifyListenerRule(String region, String listenerArn, String listenerRuleArn,
      List<TargetGroupTuple> targetGroupTuples, AwsInternalConfig awsInternalConfig) {
    // check if listener rule is default one in listener
    if (asgSdkManager.checkForDefaultRule(region, listenerArn, listenerRuleArn, awsInternalConfig)) {
      asgSdkManager.info(
          "Modifying the default Listener: %s %n with listener rule: %s %n to shift traffic weight to required TargetGroups",
          listenerArn, listenerRuleArn);
      // update listener with target group
      asgSdkManager.modifyDefaultListenerRule(region, listenerArn, awsInternalConfig, targetGroupTuples);
    } else {
      asgSdkManager.info(
          "Modifying the Listener rule: %s %n to shift traffic weight to required TargetGroups", listenerRuleArn);
      // update listener rule with target group
      asgSdkManager.modifySpecificListenerRule(region, listenerRuleArn, awsInternalConfig, targetGroupTuples);
    }
  }

  @Override
  public AsgManifestHandlerChainState delete(AsgManifestHandlerChainState chainState, ManifestRequest manifestRequest) {
    return chainState;
  }

  @Override
  public AsgManifestHandlerChainState getManifestTypeContent(
      AsgManifestHandlerChainState chainState, ManifestRequest manifestRequest) {
    return chainState;
  }
}
