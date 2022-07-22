/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.steps;

import static io.harness.beans.steps.CIStepInfoType.CIStepExecEnvironment.CI_LITE_ENGINE;
import static io.harness.beans.steps.CIStepInfoType.CIStepExecEnvironment.CI_MANAGER;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

public enum CIStepInfoType {
  BUILD(CI_LITE_ENGINE, "Build"),
  TEST(CI_LITE_ENGINE, "Test"),
  SETUP_ENV(CI_MANAGER, "SetupEnv"),
  CLEANUP(CI_MANAGER, "Cleanup"),
  RUN(CI_LITE_ENGINE, "Run"),
  PLUGIN(CI_LITE_ENGINE, "Plugin"),
  SECURITY(CI_LITE_ENGINE, "Security"),
  GIT_CLONE(CI_LITE_ENGINE, "GitClone"),
  INITIALIZE_TASK(CI_LITE_ENGINE, "liteEngineTask"),
  RUN_TESTS(CI_LITE_ENGINE, "RunTests"),
  ECR(CI_LITE_ENGINE, "BuildAndPushECR"),
  GCR(CI_LITE_ENGINE, "BuildAndPushGCR"),
  DOCKER(CI_LITE_ENGINE, "BuildAndPushDockerRegistry"),
  UPLOAD_GCS(CI_LITE_ENGINE, "GCSUpload"),
  UPLOAD_S3(CI_LITE_ENGINE, "S3Upload"),
  SAVE_CACHE_GCS(CI_LITE_ENGINE, "SaveCacheGCS"),
  RESTORE_CACHE_GCS(CI_LITE_ENGINE, "RestoreCacheGCS"),
  SAVE_CACHE_S3(CI_LITE_ENGINE, "SaveCacheS3"),
  RESTORE_CACHE_S3(CI_LITE_ENGINE, "RestoreCacheS3"),
  UPLOAD_ARTIFACTORY(CI_LITE_ENGINE, "ArtifactoryUpload");

  @Getter private final CIStepExecEnvironment ciStepExecEnvironment;
  private final String displayName;

  @JsonValue
  public String getDisplayName() {
    return displayName;
  }

  CIStepInfoType(CIStepExecEnvironment ciStepExecEnvironment, String displayName) {
    this.ciStepExecEnvironment = ciStepExecEnvironment;
    this.displayName = displayName;
  }
  public enum CIStepExecEnvironment { CI_MANAGER, CI_LITE_ENGINE }
}
