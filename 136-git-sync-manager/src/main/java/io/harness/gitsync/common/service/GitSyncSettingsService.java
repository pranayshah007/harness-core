/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.service;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.common.dtos.GitSyncSettingsDTO;

import java.util.Optional;

@OwnedBy(DX)
public interface GitSyncSettingsService {
  Optional<GitSyncSettingsDTO> get(String accountIdentifier, String orgIdentifier, String projectIdentifier);

  GitSyncSettingsDTO save(GitSyncSettingsDTO request);

  GitSyncSettingsDTO update(GitSyncSettingsDTO request);

  void delete(String accountIdentifier, String orgIdentifier, String projectIdentifier);

  boolean enableGitSimplification(String accountIdentifier, String orgIdentifier, String projectIdentifier);

  boolean getGitSimplificationStatus(String accountIdentifier, String orgIdentifier, String projectIdentifier);
}
