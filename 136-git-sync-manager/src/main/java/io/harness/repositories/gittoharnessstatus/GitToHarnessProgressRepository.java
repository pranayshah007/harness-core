/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.gittoharnessstatus;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.common.beans.GitToHarnessProgress;
import io.harness.gitsync.common.beans.YamlChangeSetEventType;

import org.springframework.data.repository.CrudRepository;

@HarnessRepo
@OwnedBy(HarnessTeam.DX)
public interface GitToHarnessProgressRepository
    extends CrudRepository<GitToHarnessProgress, String>, GitToHarnessProgressRepositoryCustom {
  GitToHarnessProgress findByRepoUrlAndCommitIdAndEventType(
      String repoUrl, String commitId, YamlChangeSetEventType yamlChangeSetEventType);

  GitToHarnessProgress findByRepoUrlAndBranchAndEventType(
      String repoURL, String branch, YamlChangeSetEventType eventType);

  GitToHarnessProgress findByYamlChangeSetId(String yamlChangeSetId);

  long deleteAllByAccountIdentifier(String accountIdentifier);
}
