/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.helper;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.errorhandling.NGErrorHelper;
import io.harness.exception.DelegateNotAvailableException;
import io.harness.exception.DelegateServiceDriverException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.ScmException;
import io.harness.exception.WingsException;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(PL)
public class GitConnectivityExceptionHelper {
  public static final String CONNECTIVITY_ERROR = "Unable to connect to Git provider due to error: ";
  public static final String ERROR_MSG_MSVC_DOWN = "Something went wrong, Please contact Harness Support.";

  public String getErrorMessage(Exception ex) {
    if (!(ex instanceof WingsException)) {
      return NGErrorHelper.DEFAULT_ERROR_MESSAGE;
    }

    if (ExceptionUtils.cause(ScmException.class, ex) != null
        || ExceptionUtils.cause(DelegateNotAvailableException.class, ex) != null
        || ExceptionUtils.cause(DelegateServiceDriverException.class, ex) != null) {
      return CONNECTIVITY_ERROR + ExceptionUtils.getMessage(ex);
    }

    return "Error: " + ExceptionUtils.getMessage(ex);
  }
}
