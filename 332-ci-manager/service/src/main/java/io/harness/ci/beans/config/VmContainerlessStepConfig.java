/*
 * Copyright 2023 Harness Inc. All rights reserved.
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
@TypeAlias("vmContainerlessStepConfig")
@RecasterAlias("io.harness.ci.config.VmContainerlessStepConfig")
public class VmContainerlessStepConfig {
  ContainerlessPluginConfig gitCloneConfig;
  ContainerlessPluginConfig gcsUploadConfig;
  ContainerlessPluginConfig s3UploadConfig;
  ContainerlessPluginConfig artifactoryUploadConfig;
  ContainerlessPluginConfig cacheGCSConfig;
  ContainerlessPluginConfig cacheS3Config;
  ContainerlessPluginConfig dockerBuildxConfig;
  ContainerlessPluginConfig dockerBuildxEcrConfig;
  ContainerlessPluginConfig dockerBuildxGcrConfig;
  ContainerlessPluginConfig dockerBuildxAcrConfig;
}
