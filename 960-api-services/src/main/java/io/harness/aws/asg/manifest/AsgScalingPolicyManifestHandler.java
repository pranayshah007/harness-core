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
import io.harness.manifest.request.ManifestRequest;

import com.amazonaws.services.autoscaling.model.PutScalingPolicyRequest;
import java.util.List;
import java.util.stream.Collectors;

@OwnedBy(CDP)
public class AsgScalingPolicyManifestHandler extends AsgManifestHandler<PutScalingPolicyRequest> {
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
    asgSdkManager.clearAllScalingPoliciesForAsg(asgName);
    asgSdkManager.attachScalingPoliciesToAsg(asgName, manifests);
    asgSdkManager.infoBold("All required scaling policies are attached to the Asg: [%s]", asgName);
    return chainState;
  }

  @Override
  public AsgManifestHandlerChainState delete(AsgManifestHandlerChainState chainState, ManifestRequest manifestRequest) {
    return chainState;
  }
}
