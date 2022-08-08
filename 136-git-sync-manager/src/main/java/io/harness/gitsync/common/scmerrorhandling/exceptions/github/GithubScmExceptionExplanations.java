/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.scmerrorhandling.exceptions.github;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(PL)
public class GithubScmExceptionExplanations {
  public static final String LIST_REPO_WITH_INVALID_CRED =
      "Couldn't list repositories. Credentials provided in connector are invalid or have expired.";
  public static final String LIST_BRANCH_WITH_INVALID_CRED =
      "Couldn't list branches. Credentials provided in connector are invalid or have expired.";
  public static final String LIST_BRANCH_WHEN_REPO_NOT_EXIST =
      "Couldn't list branches. Provided repo does not exist or has been deleted.";
}
