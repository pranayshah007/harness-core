/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.gitDefaultBranchCache;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.caching.entity.GitDefaultBranchCache;

import java.util.List;
import org.springframework.data.repository.CrudRepository;

@HarnessRepo
@OwnedBy(HarnessTeam.PIPELINE)

public interface GitDefaultBranchCacheRepository
    extends CrudRepository<GitDefaultBranchCache, String>, GitDefaultBranchCacheRepositoryCustom {
  List<GitDefaultBranchCache> findByAccountIdentifierAndRepoUrlAndRepo(
      String accountIdentifier, String repoUrl, String repo);
}
