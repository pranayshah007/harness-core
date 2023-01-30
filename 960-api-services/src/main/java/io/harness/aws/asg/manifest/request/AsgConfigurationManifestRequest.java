/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aws.asg.manifest.request;

import io.harness.manifest.request.ManifestRequest;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

@Getter
public class AsgConfigurationManifestRequest extends ManifestRequest {
  private boolean useAlreadyRunningInstances;
  Map<String, Object> overrideProperties;
  @Builder
  public AsgConfigurationManifestRequest(
      List<String> manifests, boolean useAlreadyRunningInstances, Map<String, Object> overrideProperties) {
    super(manifests);
    this.useAlreadyRunningInstances = useAlreadyRunningInstances;
    this.overrideProperties = overrideProperties;
  }

  public void setOverrideProperties(Map<String, Object> overrideProperties) {
    this.overrideProperties = overrideProperties;
  }
}
