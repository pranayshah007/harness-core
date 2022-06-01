/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.scmerrorhandling.handlers.bitbucketserver;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(PL)
public class ScmErrorHints {
  public static final String INVALID_CREDENTIALS = "Please check if your Bitbucket credentials are valid.";
  public static final String REPO_NOT_FOUND = "Please check if the requested Bitbucket repository exists.";
  public static final String REPO_OR_BRANCH_NOT_FOUND =
      "Please check if the requested Bitbucket repository/branch exists.";
  public static final String PR_ALREADY_EXISTS =
      "Please check if a PR already exists between given branches or the source branch is up to date with the target branch.";
  public static final String BRANCH_ALREADY_EXISTS = "Please check if the branch already exits in the repo.";
  public static final String FILE_ALREADY_EXISTS = "Please check if the file already exits in the repo.";
}
