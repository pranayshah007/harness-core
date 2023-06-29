/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.scmerrorhandling.handlers.bitbucketcloud;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(PL)
public class ScmErrorHints {
  public static final String INVALID_CREDENTIALS =
      "Please check if your Bitbucket credentials in connector<CONNECTOR> are valid.";
  public static final String REPO_NOT_FOUND = "Please check if the requested Bitbucket repository<REPO> exists.";
  public static final String FILE_NOT_FOUND =
      "Please check the requested file path<FILEPATH> / branch<BRANCH> / repo name<REPO> if they exist or not.";
  public static final String WRONG_REPO_OR_BRANCH =
      "Please check if the provided branch<BRANCH> or the Bitbucket repo name<REPO> are valid.";

  public static final String RATE_LIMIT = "Please try these out: \n"
      + "1. Please try and rotate your tokens used for GIT operations.\n"
      + "2. Please check your rate limits with your BitBucket GIT provider";
  public static final String OAUTH_ACCESS_FAILURE =
      "In-case you are using OAUTH, please check your OAUTH configurations and access permissions from Bitbucket, or try reconfiguring OAUTH setup.";
}
