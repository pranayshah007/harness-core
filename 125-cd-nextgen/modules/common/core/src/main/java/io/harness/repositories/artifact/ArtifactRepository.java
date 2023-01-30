/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.artifact;

import io.harness.annotation.HarnessRepo;
import io.harness.cdng.artifact.bean.artifactsource.ArtifactSource;

import org.springframework.data.repository.PagingAndSortingRepository;

@HarnessRepo
public interface ArtifactRepository extends PagingAndSortingRepository<ArtifactSource, String> {
  ArtifactSource findByAccountIdAndUniqueHash(String accountId, String uniqueHash);
  ArtifactSource findByAccountIdAndUuid(String accountId, String uuid);
}
