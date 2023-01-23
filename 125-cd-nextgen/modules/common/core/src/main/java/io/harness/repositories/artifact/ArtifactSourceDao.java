/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.artifact;

import io.harness.cdng.artifact.bean.artifactsource.ArtifactSource;
import io.harness.cdng.artifact.bean.artifactsource.ArtifactSource.ArtifactSourceKeys;
import io.harness.validation.PersistenceValidator;

import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Entries are immutable, thus no update function should be there.
 */
@Singleton
public class ArtifactSourceDao {
  @Inject private ArtifactRepository artifactRepository;

  public ArtifactSource create(ArtifactSource artifactSource) {
    return PersistenceValidator.duplicateCheck(
        () -> artifactRepository.save(artifactSource), ArtifactSourceKeys.uniqueHash, artifactSource.getUniqueHash());
  }

  public ArtifactSource get(String accountId, String uuid) {
    return artifactRepository.findByAccountIdAndUuid(accountId, uuid);
  }

  public ArtifactSource getArtifactStreamByHash(String accountId, String uniqueHash) {
    return artifactRepository.findByAccountIdAndUniqueHash(accountId, uniqueHash);
  }
}
