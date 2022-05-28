/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.scmerrorhandling;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.PL)
@UtilityClass
public class ScmErrorCodeToHttpStatusCodeMapping {
  public static final int HTTP_200 = 200;
  public static final int HTTP_500 = 500;

  public int getHttpStatusCode(ErrorCode errorCode) {
    switch (errorCode) {
      case SCM_BAD_REQUEST:
        return 400;
      case SCM_UNAUTHORIZED_ERROR_V2:
        return 401;
      case SCM_CONFLICT_ERROR_V2:
        return 409;
      default:
        return HTTP_500;
    }
  }
}
