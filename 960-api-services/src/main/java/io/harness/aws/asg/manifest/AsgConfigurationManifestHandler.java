/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aws.asg.manifest;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.asg.AsgSdkManager;

import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupRequest;
import java.util.List;
import java.util.Map;

@OwnedBy(CDP)
public class AsgConfigurationManifestHandler extends AsgManifestHandler<CreateAutoScalingGroupRequest> {
  public interface OverrideProperties {
    String minSize = "minSize";
    String maxSize = "maxSize";
    String desiredCapacity = "desiredCapacity";
  }

  public AsgConfigurationManifestHandler(
      AsgSdkManager asgSdkManager, List<String> manifestContentList, Map<String, Object> overrideProperties) {
    super(asgSdkManager, manifestContentList, overrideProperties);
  }

  @Override
  public Class<CreateAutoScalingGroupRequest> getManifestContentUnmarshallClass() {
    return CreateAutoScalingGroupRequest.class;
  }

  @Override
  public void applyOverrideProperties(
      List<CreateAutoScalingGroupRequest> manifests, Map<String, Object> overrideProperties) {
    CreateAutoScalingGroupRequest createAutoScalingGroupRequest = manifests.get(0);
    overrideProperties.entrySet().stream().forEach(entry -> {
      switch (entry.getKey()) {
        case OverrideProperties.minSize:
          createAutoScalingGroupRequest.setMinSize((Integer) entry.getValue());
          break;
        case OverrideProperties.maxSize:
          createAutoScalingGroupRequest.setMaxSize((Integer) entry.getValue());
          break;
        case OverrideProperties.desiredCapacity:
          createAutoScalingGroupRequest.setDesiredCapacity((Integer) entry.getValue());
          break;
        default:
          // do nothing
      }
    });
  }

  @Override
  public AsgManifestHandlerChainState upsert(
      AsgManifestHandlerChainState chainState, List<CreateAutoScalingGroupRequest> manifests) {
    String asgName = chainState.getAsgName();
    CreateAutoScalingGroupRequest createAutoScalingGroupRequest = manifests.get(0);
    createAutoScalingGroupRequest.setAutoScalingGroupName(asgName);
    // TODO implement update

    String operationName = format("Create Asg %s", asgName);
    asgSdkManager.info("Operation `%s` has started", operationName);
    asgSdkManager.createASG(asgName, chainState.getLaunchTemplateVersion(), createAutoScalingGroupRequest);
    asgSdkManager.waitReadyState(asgName, asgSdkManager::checkAllInstancesInReadyState, operationName);
    asgSdkManager.infoBold("Operation `%s` ended successfully", operationName);

    AutoScalingGroup autoScalingGroup = asgSdkManager.getASG(asgName);
    chainState.setAutoScalingGroup(autoScalingGroup);

    return chainState;
  }

  @Override
  public AsgManifestHandlerChainState delete(
      AsgManifestHandlerChainState chainState, List<CreateAutoScalingGroupRequest> manifests) {
    return chainState;
  }
}
