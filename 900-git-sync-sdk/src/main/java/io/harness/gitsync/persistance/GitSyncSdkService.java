/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.persistance;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;

@OwnedBy(DX)
public interface GitSyncSdkService {
  /**
   * Gets details like repo and branch from context.
   */
  boolean isGitSyncEnabled(String accountIdentifier, String orgIdentifier, String projectIdentifier);

  boolean isGitSimplificationEnabled(String accountIdentifier, String orgIdentifier, String projectIdentifier);

  /**
   * Gets details like repo and branch from context.
   */
  boolean isDefaultBranch(String accountIdentifier, String orgIdentifier, String projectIdentifier);
}
