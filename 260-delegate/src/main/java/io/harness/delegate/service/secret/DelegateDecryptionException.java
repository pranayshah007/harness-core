/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.secret;

import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.exception.WingsException;

public class DelegateDecryptionException extends WingsException {
    public DelegateDecryptionException(String message, ErrorCode code) {
        super(message, null, code, Level.ERROR, null, null);
    }

    public DelegateDecryptionException(String message, Throwable e, ErrorCode code) {
        super(message, e, code, Level.ERROR, null, null);
    }
}
