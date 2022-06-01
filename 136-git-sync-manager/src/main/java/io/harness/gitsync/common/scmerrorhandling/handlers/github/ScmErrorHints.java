/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.scmerrorhandling.handlers.github;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(PL)
public class ScmErrorHints {
  public static final String INVALID_CREDENTIALS = "Please check if your Github credentials are valid.";
  public static final String REPO_NOT_FOUND = "Please check if the requested Github repository exists.";
  public static final String FILE_NOT_FOUND =
      "Please check the requested file path / branch / Github repo name if they exist or not.";
}
