/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.artifacts.gcr.service;
import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.artifacts.gcr.beans.GcrInternalConfig;

import java.util.List;

/**
 * Created by brett on 8/2/17
 */

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@OwnedBy(CDC)
public interface GcrApiService {
  int MAX_NO_OF_TAGS_PER_IMAGE = 10000;
  /**
   * Gets builds.
   *
   * @param gcpConfig         the gcp cloud provider config
   * @param maxNumberOfBuilds the max number of builds
   * @return the builds
   */
  List<BuildDetailsInternal> getBuilds(GcrInternalConfig gcpConfig, String imageName, int maxNumberOfBuilds);

  /**
   * Validates the Image
   *
   * @param gcpConfig
   */
  boolean verifyImageName(GcrInternalConfig gcpConfig, String imageName);

  /**
   * Validate the credentials
   *
   * @param gcpConfig
   * @return
   */
  boolean validateCredentials(GcrInternalConfig gcpConfig, String imageName);

  /**
   * Gets the last successful build with input as tag regex.
   */
  BuildDetailsInternal getLastSuccessfulBuildFromRegex(
      GcrInternalConfig gcrInternalConfig, String imageName, String tagRegex);

  /**
   * Validates the Image Tag
   */
  BuildDetailsInternal verifyBuildNumber(GcrInternalConfig gcrInternalConfig, String imageName, String tag);
}
