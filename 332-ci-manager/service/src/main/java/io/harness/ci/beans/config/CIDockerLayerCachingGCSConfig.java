/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.config;

import io.harness.annotation.RecasterAlias;

import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@TypeAlias("ciDockerLayerCachingGCSConfig")
@RecasterAlias("io.harness.ci.config.CIDockerLayerCachingGCSConfig")
public class CIDockerLayerCachingGCSConfig {
  String endpoint;
  String bucket;
  String accessKey;
  String secretKey;
  String region;
}
