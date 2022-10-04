/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.encryptors;

import static io.harness.eraro.ErrorCode.SECRET_MANAGEMENT_ERROR;
import static io.harness.exception.WingsException.USER;

import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.SecretManagementException;

import lombok.experimental.UtilityClass;

@UtilityClass
public class DelegateTaskUtils {
  public void validateDelegateTaskResponse(DelegateResponseData delegateResponseData) {
    if (delegateResponseData instanceof ErrorNotifyResponseData) {
      throw new SecretManagementException(
          SECRET_MANAGEMENT_ERROR, ((ErrorNotifyResponseData) delegateResponseData).getErrorMessage(), USER);
    } else if (delegateResponseData instanceof RemoteMethodReturnValueData
        && (((RemoteMethodReturnValueData) delegateResponseData).getException() instanceof InvalidRequestException)) {
      throw(InvalidRequestException)((RemoteMethodReturnValueData) delegateResponseData).getException();
    }
  }
}
