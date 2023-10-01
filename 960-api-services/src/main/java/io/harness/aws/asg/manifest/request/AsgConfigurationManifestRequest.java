/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aws.asg.manifest.request;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.manifest.request.ManifestRequest;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_AMI_ASG})
@Getter
public class AsgConfigurationManifestRequest extends ManifestRequest {
  private boolean useAlreadyRunningInstances;
  private Map<String, Object> overrideProperties;
  private AwsInternalConfig awsInternalConfig;
  private String region;
  private AsgInstanceCapacity alreadyRunningInstanceCapacity;

  @Builder
  public AsgConfigurationManifestRequest(List<String> manifests, boolean useAlreadyRunningInstances,
      Map<String, Object> overrideProperties, AwsInternalConfig awsInternalConfig, String region,
      AsgInstanceCapacity alreadyRunningInstanceCapacity) {
    super(manifests);
    this.useAlreadyRunningInstances = useAlreadyRunningInstances;
    this.overrideProperties = overrideProperties;
    this.awsInternalConfig = awsInternalConfig;
    this.region = region;
    this.alreadyRunningInstanceCapacity = alreadyRunningInstanceCapacity;
  }

  public void setOverrideProperties(Map<String, Object> overrideProperties) {
    this.overrideProperties = overrideProperties;
  }
}
