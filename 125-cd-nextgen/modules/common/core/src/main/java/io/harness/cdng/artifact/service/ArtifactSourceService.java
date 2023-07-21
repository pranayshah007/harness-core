/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact.service;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.artifact.bean.artifactsource.ArtifactSource;
import io.harness.validation.Create;

import javax.validation.Valid;
import ru.vyarus.guice.validator.group.annotation.ValidationGroups;

/**
 * Entries are immutable, thus no update function should be there.
 */

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
public interface ArtifactSourceService {
  /**
   * Create/Save ArtifactSource in collection.
   * @param artifactSource
   * @return ArtifactSource
   */
  @ValidationGroups(Create.class) ArtifactSource create(@Valid ArtifactSource artifactSource);

  /**
   * Create/Save ArtifactSource in collection with validations.
   * @param artifactSource
   * @param validate
   * @return ArtifactSource
   */
  @ValidationGroups(Create.class) ArtifactSource create(@Valid ArtifactSource artifactSource, boolean validate);

  /**
   * Save if not created and return artifactSource.
   * @param artifactSource
   * @return ArtifactSource.
   */
  @ValidationGroups(Create.class) ArtifactSource saveOrGetArtifactStream(@Valid ArtifactSource artifactSource);

  /**
   * Get ArtifactSource using accountId, uuid.
   * @param accountId
   * @param uuid
   * @return ArtifactSource
   */
  ArtifactSource get(String accountId, String uuid);

  /**
   * Get ArtifactSource based on accountId, uniqueHash(set by different stream types).
   * @param accountId
   * @param uniqueHash
   * @return artifactStream
   */
  ArtifactSource getArtifactStreamByHash(String accountId, String uniqueHash);
}
