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
public class AsgLaunchTemplateManifestRequest extends ManifestRequest {
  Map<String, Object> overrideProperties;

  @Builder
  public AsgLaunchTemplateManifestRequest(List<String> manifests, Map<String, Object> overrideProperties) {
    super(manifests);
    this.overrideProperties = overrideProperties;
  }
}
