/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.remote;

import static io.harness.gitsync.common.beans.GitSyncSettings.IS_ENABLED_ONLY_FOR_FF;
import static io.harness.gitsync.common.beans.GitSyncSettings.IS_EXECUTE_ON_DELEGATE;
import static io.harness.gitsync.common.beans.GitSyncSettings.IS_GIT_SIMPLIFICATION_ENABLED;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.common.beans.GitSyncSettings;
import io.harness.gitsync.common.dtos.GitSyncSettingsDTO;

import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.DX)
@UtilityClass
public class GitSyncSettingsMapper {
  public GitSyncSettingsDTO getDTOFromGitSyncSettings(GitSyncSettings gitSyncSettings) {
    if (gitSyncSettings == null) {
      return null;
    }
    return GitSyncSettingsDTO.builder()
        .accountIdentifier(gitSyncSettings.getAccountIdentifier())
        .orgIdentifier(gitSyncSettings.getOrgIdentifier())
        .projectIdentifier(gitSyncSettings.getProjectIdentifier())
        .executeOnDelegate(gitSyncSettings.getSettings()
                               .getOrDefault(IS_EXECUTE_ON_DELEGATE, String.valueOf(false))
                               .equals(String.valueOf(true)))
        .isGitSimplificationEnabled(gitSyncSettings.getSettings()
                                        .getOrDefault(IS_GIT_SIMPLIFICATION_ENABLED, String.valueOf(false))
                                        .equals(String.valueOf(true)))
        .isEnabledOnlyForFF(gitSyncSettings.getSettings()
                                .getOrDefault(IS_ENABLED_ONLY_FOR_FF, String.valueOf(false))
                                .equals(String.valueOf(true)))
        .build();
  }

  public GitSyncSettings getGitSyncSettingsFromDTO(GitSyncSettingsDTO gitSyncSettingsDTO) {
    Map<String, String> settings = new HashMap<>();
    settings.put(IS_EXECUTE_ON_DELEGATE,
        (gitSyncSettingsDTO.isExecuteOnDelegate()) ? String.valueOf(true) : String.valueOf(false));
    return GitSyncSettings.builder()
        .accountIdentifier(gitSyncSettingsDTO.getAccountIdentifier())
        .orgIdentifier(gitSyncSettingsDTO.getOrgIdentifier())
        .projectIdentifier(gitSyncSettingsDTO.getProjectIdentifier())
        .settings(settings)
        .build();
  }
}
