/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.artifacts.docker.service;
import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.artifacts.docker.beans.DockerInternalConfig;
import io.harness.beans.ArtifactMetaInfo;

import java.util.List;
import java.util.Map;

/**
 * Created by anubhaw on 1/6/17.
 */

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@OwnedBy(CDC)
public interface DockerRegistryService {
  int MAX_NO_OF_TAGS_PER_IMAGE = 10000;

  /**
   * Gets builds.
   *
   * @param dockerConfig      the docker config
   * @param imageName         the image name
   * @param maxNumberOfBuilds the max number of builds
   * @return the builds
   */
  List<BuildDetailsInternal> getBuilds(
      DockerInternalConfig dockerConfig, String imageName, int maxNumberOfBuilds, String tagRegex);

  /**
   * Gets labels.
   *
   * @param dockerConfig the docker config
   * @param imageName    the image name
   * @param tags         the image tags to find labels of
   * @return the builds
   */
  List<Map<String, String>> getLabels(DockerInternalConfig dockerConfig, String imageName, List<String> tags);

  /**
   * Gets last successful build.
   *
   * @param dockerConfig the docker config
   * @param imageName    the image name
   * @return the last successful build
   */
  BuildDetailsInternal getLastSuccessfulBuild(DockerInternalConfig dockerConfig, String imageName);

  /**
   * Gets the last successful build with input as tag regex.
   * @param dockerConfig the docker config
   * @param imageName the image name
   * @param tagRegex tag regex
   * @return the last successful build
   */
  BuildDetailsInternal getLastSuccessfulBuildFromRegex(
      DockerInternalConfig dockerConfig, String imageName, String tagRegex);

  /**
   * Validates the Image
   * @param dockerConfig the docker config
   * @param imageName the image name
   */
  boolean verifyImageName(DockerInternalConfig dockerConfig, String imageName);

  /**
   * Validates the Image Tag
   * @param dockerConfig the docker config
   * @param imageName the image name
   */
  BuildDetailsInternal verifyBuildNumber(DockerInternalConfig dockerConfig, String imageName, String tag);

  /**
   * Validate the credentials
   *
   * @param dockerConfig the docker config
   * @return boolean validate
   */
  boolean validateCredentials(DockerInternalConfig dockerConfig);

  ArtifactMetaInfo getArtifactMetaInfo(
      DockerInternalConfig dockerConfig, String imageName, String tag, boolean shouldFetchDockerV2DigestSHA256);
}
