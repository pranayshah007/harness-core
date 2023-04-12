/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.deploydetective.mapper.release;

import io.harness.deploydetective.beans.VersioningScheme;

import com.google.inject.Singleton;

@Singleton
public class ReleaseVersionMapperFactory {
  private HarnessReleaseVersionMapper harnessReleaseVersionMapper;
  private SemanticReleaseVersionMapper semanticReleaseVersionMapper;

  public ReleaseVersionMapperFactory() {
    this.harnessReleaseVersionMapper = new HarnessReleaseVersionMapper();
    this.semanticReleaseVersionMapper = new SemanticReleaseVersionMapper();
  }

  public IReleaseVersionMapper getReleaseVersionMapper(VersioningScheme versioningScheme) {
    if (versioningScheme == VersioningScheme.HARNESS_INTERNAL)
      return harnessReleaseVersionMapper;
    return semanticReleaseVersionMapper;
  }
}
