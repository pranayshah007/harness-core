/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.config;

import io.harness.annotation.RecasterAlias;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@TypeAlias("vmImageConfig")
@RecasterAlias("io.harness.ci.config.VmImageConfig")
public class VmImageConfig {
  String gitClone;
  String buildAndPushDockerRegistry;
  String buildAndPushECR;
  String buildAndPushACR;
  String buildAndPushGCR;
  String gcsUpload;
  String s3Upload;
  String security;
  String artifactoryUpload;
  String cacheGCS;
  String cacheS3;
  String iacmTerraform;
  String sscaOrchestration;
  String sscaEnforcement;
}
