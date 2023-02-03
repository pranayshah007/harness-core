/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aws.asg.manifest;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.asg.AsgSdkManager;
import io.harness.aws.asg.manifest.request.AsgSwapServiceManifestRequest;
import io.harness.aws.beans.AsgLoadBalancerConfig;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.manifest.request.ManifestRequest;

import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.PutScalingPolicyRequest;
import java.util.List;

@OwnedBy(CDP)
public class AsgSwapServiceHandler extends AsgManifestHandler<PutScalingPolicyRequest> {
  public AsgSwapServiceHandler(AsgSdkManager asgSdkManager, ManifestRequest manifestRequest) {
    super(asgSdkManager, manifestRequest);
  }

  @Override
  public Class<PutScalingPolicyRequest> getManifestContentUnmarshallClass() {
    return PutScalingPolicyRequest.class;
  }

  @Override
  public AsgManifestHandlerChainState upsert(AsgManifestHandlerChainState chainState, ManifestRequest manifestRequest) {
    AsgSwapServiceManifestRequest asgSwapServiceManifestRequest = (AsgSwapServiceManifestRequest) manifestRequest;
    asgSdkManager.info("Swapping target groups & updating tags");
    swapTargetGroups(((AsgSwapServiceManifestRequest) manifestRequest).getRegion(),
        ((AsgSwapServiceManifestRequest) manifestRequest).getAsgLoadBalancerConfig(),
        ((AsgSwapServiceManifestRequest) manifestRequest).getAwsInternalConfig());

    // logic to update tags of asg
    asgSdkManager.info("Updating tags of the autoscaling groups");
    asgSdkManager.updateBGTags(chainState.getNewAsgName(), asgSdkManager.BG_BLUE);
    AutoScalingGroup stageAsg = asgSdkManager.getASG(chainState.getAsgName());
    if (stageAsg != null) {
      asgSdkManager.updateBGTags(chainState.getAsgName(), asgSdkManager.BG_GREEN);
    }
    asgSdkManager.info("Successfully updated tags");
    asgSdkManager.infoBold("Swapped target groups & updated tags successfully");

    return chainState;
  }

  public void swapTargetGroups(
      String region, AsgLoadBalancerConfig asgLoadBalancerConfig, AwsInternalConfig awsInternalConfig) {
    // modify target group of prod listener with stage target group and target group of stage listener with prod
    // target group

    asgSdkManager.info(
        "Modifying ELB Prod Listener to Forward requests to Target groups associated with new autoscaling group");
    // modify prod listener rule with stage target group
    modifyListenerRule(region, asgLoadBalancerConfig.getProdListenerArn(),
        asgLoadBalancerConfig.getProdListenerRuleArn(), asgLoadBalancerConfig.getStageTargetGroupArnsList(),
        awsInternalConfig);
    asgSdkManager.info("Successfully updated Prod Listener %n%n");

    asgSdkManager.info(
        "Modifying ELB Stage Listener to Forward requests to Target groups associated with old autoscaling group");
    // modify stage listener rule with prod target group
    modifyListenerRule(region, asgLoadBalancerConfig.getStageListenerArn(),
        asgLoadBalancerConfig.getStageListenerRuleArn(), asgLoadBalancerConfig.getProdTargetGroupArnsList(),
        awsInternalConfig);
    asgSdkManager.info("Successfully updated Stage Listener %n%n");
  }

  public void modifyListenerRule(String region, String listenerArn, String listenerRuleArn,
      List<String> targetGroupArnsList, AwsInternalConfig awsInternalConfig) {
    // check if listener rule is default one in listener
    if (asgSdkManager.checkForDefaultRule(region, listenerArn, listenerRuleArn, awsInternalConfig)) {
      asgSdkManager.info(
          "Modifying the default Listener: %s %n with listener rule: %s %n to forward traffic to required TargetGroups",
          listenerArn, listenerRuleArn);
      // update listener with target group
      asgSdkManager.modifyDefaultListenerRule(region, listenerArn, targetGroupArnsList, awsInternalConfig);
    } else {
      asgSdkManager.info(
          "Modifying the Listener rule: %s %n to forward traffic to required TargetGroups", listenerRuleArn);
      // update listener rule with target group
      asgSdkManager.modifySpecificListenerRule(region, listenerRuleArn, targetGroupArnsList, awsInternalConfig);
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
