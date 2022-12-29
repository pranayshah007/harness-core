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
public class AsgInstanceRefreshRequest extends ManifestRequest {
  public boolean skipMatching;
  public Integer instanceWarmup;
  public Integer minimumHealthyPercentage;
  Map<String, Object> overrideProperties = null;
  @Builder
  public AsgInstanceRefreshRequest(
      List<String> manifests, boolean skipMatching, Integer instanceWarmup, Integer minimumHealthyPercentage) {
    super(manifests);
    this.skipMatching = skipMatching;
    this.instanceWarmup = instanceWarmup;
    this.minimumHealthyPercentage = minimumHealthyPercentage;
  }
}
