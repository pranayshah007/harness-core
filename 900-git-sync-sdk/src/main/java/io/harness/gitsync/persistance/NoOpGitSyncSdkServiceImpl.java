/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.persistance;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(DX)
public class NoOpGitSyncSdkServiceImpl implements GitSyncSdkService {
  @Override
  public boolean isGitSyncEnabled(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return false;
  }

  @Override
  public boolean isGitSimplificationEnabled(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return false;
  }

  @Override
  public boolean isDefaultBranch(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return false;
  }
}
