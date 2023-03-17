/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.scmerrorhandling.handlers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(PIPELINE)
public class ScmErrorMessageHelper {
  public final String DEFAULT_ERROR_MESSAGE = "Failed to perform GIT operation.";

  public String validateErrorMessage(String errorMessage) {
    return EmptyPredicate.isEmpty(errorMessage) ? DEFAULT_ERROR_MESSAGE : errorMessage;
  }
}
