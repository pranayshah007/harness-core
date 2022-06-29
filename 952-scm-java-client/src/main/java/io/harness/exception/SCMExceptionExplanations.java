/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.exception;

import lombok.experimental.UtilityClass;

@UtilityClass
public class SCMExceptionExplanations {
  public static final String UNABLE_TO_PUSH_TO_REPO_WITH_USER_CREDENTIALS =
      "We couldn't perform required git operation. Possible reasons can be:\n"
      + "1. Missing permissions to fetch selected branch of the repo\n"
      + "2. Repo does not exist or has been deleted\n"
      + "3. Credentials are invalid or have expired";
}
