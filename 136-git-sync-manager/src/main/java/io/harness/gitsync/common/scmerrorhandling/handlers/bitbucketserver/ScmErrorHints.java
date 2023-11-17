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
  public static final String INVALID_CREDENTIALS =
      "Please check if your Bitbucket credentials in connector<CONNECTOR> are valid.";
  public static final String REPO_NOT_FOUND = "Please check if the requested Bitbucket repository<REPO> exists.";
  public static final String REPO_OR_BRANCH_NOT_FOUND =
      "Please check if the requested Bitbucket repository<REPO> / branch<BRANCH> exists.";
  public static final String PR_ALREADY_EXISTS =
      "Please check if a PR already exists between given branches or the source branch<BRANCH> is up to date with the target branch<TARGET_BRANCH>.";
  public static final String BRANCH_ALREADY_EXISTS =
      "Please check if the branch<BRANCH> already exits in the repo<REPO>.";
  public static final String FILE_ALREADY_EXISTS =
      "Please check if the file<FILEPATH> already exits in the branch<BRANCH> and repo<REPO>.";

  public static final String WRONG_REPO_OR_BRANCH =
      "Please check if the provided branch<BRANCH> or the Bitbucket repo name<REPO> are valid.";

  public static final String RATE_LIMIT =
      "Please check the number of requests, you have reached your account's rate limit for too many requests.";
  public static final String HINT_REQUEST_TIMED_OUT = "Please try these out: \n"
      + "1. Check if Github server is working as expected from Bitbucket's status page (https://bitbucket.status.atlassian.com/)\n"
      + "2. Check if your selected delegates are able to connect to the Bitbucket server\n"
      + "3. If the problem persists, please contact Harness Support Team.";
}
