/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aws.asg.manifest;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.aws.asg.manifest.AsgManifestType.AsgConfiguration;
import static io.harness.aws.asg.manifest.AsgManifestType.AsgInstanceRefresh;
import static io.harness.aws.asg.manifest.AsgManifestType.AsgLaunchTemplate;
import static io.harness.aws.asg.manifest.AsgManifestType.AsgScalingPolicy;
import static io.harness.aws.asg.manifest.AsgManifestType.AsgSwapService;

import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.asg.AsgSdkManager;
import io.harness.exception.InvalidArgumentsException;
import io.harness.manifest.handler.ManifestHandler;
import io.harness.manifest.handler.ManifestHandlerChainFactory;
import io.harness.manifest.request.ManifestRequest;

import lombok.Builder;

@OwnedBy(CDP)
public class AsgManifestHandlerChainFactory extends ManifestHandlerChainFactory<AsgManifestHandlerChainState> {
  private final AsgSdkManager asgSdkManager;

  @Builder
  public AsgManifestHandlerChainFactory(AsgManifestHandlerChainState initialChainState, AsgSdkManager asgSdkManager) {
    super(initialChainState);
    this.asgSdkManager = asgSdkManager;
  }

  @Override
  public ManifestHandler createHandler(String manifestType, ManifestRequest manifestRequest) {
    switch (manifestType) {
      case AsgLaunchTemplate:
        return new AsgLaunchTemplateManifestHandler(this.asgSdkManager, manifestRequest);
      case AsgConfiguration:
        return new AsgConfigurationManifestHandler(this.asgSdkManager, manifestRequest);
      case AsgScalingPolicy:
        return new AsgScalingPolicyManifestHandler(this.asgSdkManager, manifestRequest);
      case AsgInstanceRefresh:
        return new AsgInstanceRefreshHandler(this.asgSdkManager, manifestRequest);
      case AsgSwapService:
        return new AsgSwapServiceHandler(this.asgSdkManager, manifestRequest);
      default:
        throw new InvalidArgumentsException("Invalid asgManifestType provided");
    }
  }
}
