/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.steps;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.dev.OwnedBy;

@OwnedBy(CI)
public interface StepSpecTypeConstants {
  String RUN = "Run";
  String BACKGROUND = "Background";
  String RUN_TEST = "RunTests";
  String PLUGIN = "Plugin";
  String SECURITY = "Security";
  String RESTORE_CACHE_GCS = "RestoreCacheGCS";
  String RESTORE_CACHE_S3 = "RestoreCacheS3";
  String SAVE_CACHE_GCS = "SaveCacheGCS";
  String SAVE_CACHE_S3 = "SaveCacheS3";
  String ARTIFACTORY_UPLOAD = "ArtifactoryUpload";
  String GCS_UPLOAD = "GCSUpload";
  String S3_UPLOAD = "S3Upload";
  String GIT_CLONE = "GitClone";

  String BUILD_AND_PUSH_GCR = "BuildAndPushGCR";
  String BUILD_AND_PUSH_ECR = "BuildAndPushECR";
  String BUILD_AND_PUSH_ACR = "BuildAndPushACR";
  String BUILD_AND_PUSH_DOCKER_REGISTRY = "BuildAndPushDockerRegistry";
  String ACTION = "Action";
  String BITRISE = "Bitrise";

  String CI_STAGE = "CI";
  String SECURITY_STAGE = "SecurityTests";
  String CI_STAGE_V2 = "ci";
  String IACM_TERRAFORM_PLUGIN = "IACMTerraformPlugin";
}
