/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.artifacts.githubpackages.service;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.beans.BuildDetailsInternal;

import java.util.List;

@OwnedBy(CDC)
public interface GithubPackagesRegistryService {
  int MAX_NO_OF_TAGS_PER_IMAGE = 10000;

  /**
   * Gets builds.
   * @return the builds
   */
  List<BuildDetailsInternal> getBuilds();
}
