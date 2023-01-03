/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aws.asg.manifest;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.aws.asg.manifest.AsgManifestType.AsgScalingPolicy;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.asg.AsgMapper;
import io.harness.aws.asg.AsgSdkManager;
import io.harness.manifest.request.ManifestRequest;

import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.PutScalingPolicyRequest;
import com.amazonaws.services.autoscaling.model.ScalingPolicy;
import com.google.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;

@OwnedBy(CDP)
public class AsgScalingPolicyManifestHandler extends AsgManifestHandler<PutScalingPolicyRequest> {
  @Inject private AsgMapper asgMapper;
  public AsgScalingPolicyManifestHandler(AsgSdkManager asgSdkManager, ManifestRequest manifestRequest) {
    super(asgSdkManager, manifestRequest);
  }

  @Override
  public Class<PutScalingPolicyRequest> getManifestContentUnmarshallClass() {
    return PutScalingPolicyRequest.class;
  }

  @Override
  public AsgManifestHandlerChainState upsert(AsgManifestHandlerChainState chainState, ManifestRequest manifestRequest) {
    List<PutScalingPolicyRequest> manifests =
        manifestRequest.getManifests().stream().map(this::parseContentToManifest).collect(Collectors.toList());
    String asgName = chainState.getAsgName();
    String operationName = format("Attach required scaling policies to Asg %s", asgName);
    asgSdkManager.info("Operation `%s` has started", operationName);
    asgSdkManager.clearAllScalingPoliciesForAsg(asgName);
    asgSdkManager.attachScalingPoliciesToAsg(asgName, manifests);
    asgSdkManager.infoBold("Operation `%s` ended successfully", operationName);
    return chainState;
  }

  @Override
  public AsgManifestHandlerChainState delete(AsgManifestHandlerChainState chainState, ManifestRequest manifestRequest) {
    return chainState;
  }

  @Override
  public AsgManifestHandlerChainState getManifestTypeContent(
      AsgManifestHandlerChainState chainState, ManifestRequest manifestRequest) {
    if (chainState.getAutoScalingGroup() == null) {
      AutoScalingGroup autoScalingGroup = asgSdkManager.getASG(chainState.getAsgName());
      chainState.setAutoScalingGroup(autoScalingGroup);
    }

    AutoScalingGroup autoScalingGroup = chainState.getAutoScalingGroup();
    if (autoScalingGroup != null) {
      List<ScalingPolicy> scalingPoliciesList = asgSdkManager.listAllScalingPoliciesOfAsg(chainState.getAsgName());
      List<String> scalingPolicies =
          asgMapper.createScalingPolicyRequestsListFromScalingPoliciesList(scalingPoliciesList);

      chainState.getPrepareRollbackDataAsgStoreManifestsContent().put(AsgScalingPolicy, scalingPolicies);
    }
    return chainState;
  }
}
