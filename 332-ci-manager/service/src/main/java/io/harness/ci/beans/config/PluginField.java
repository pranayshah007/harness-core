/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.config;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum PluginField {
  ADDON("addonTag"),
  LITE_ENGINE("liteEngineTag"),
  GIT_CLONE("gitCloneTag"),
  BUILD_PUSH_DOCKER_REGISTRY("buildAndPushDockerRegistryTag"),
  BUILD_PUSH_ECR("buildAndPushECRTag"),
  BUILD_PUSH_GCR("buildAndPushGCRTag"),
  BUILD_PUSH_ACR("buildAndPushACRTag"),
  GCS_UPLOAD("gcsUploadTag"),
  S3_UPLOAD("s3UploadTag"),
  ARTIFACTORY_UPLOAD("artifactoryUploadTag"),
  CACHE_GCS("cacheGCSTag"),
  CACHE_S3("cacheS3Tag"),
  SECURITY("securityTag"),
  SSCA_ORCHESTRATION("sscaOrchestrationTag"),
  SSCA_ENFORCEMENT("sscaEnforcementTag"),
  UNKNOWN("unknown");

  public final String label;

  PluginField(String label) {
    this.label = label;
  }

  @JsonCreator
  public static PluginField getPluginField(String label) {
    for (PluginField pluginField : PluginField.values()) {
      if (pluginField.getLabel().equals(label)) {
        return pluginField;
      }
    }
    return UNKNOWN;
  }

  public String getLabel() {
    return this.label;
  }
}
