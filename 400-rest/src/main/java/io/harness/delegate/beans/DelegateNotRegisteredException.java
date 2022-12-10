/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans;

import static io.harness.eraro.ErrorCode.DELEGATE_NOT_REGISTERED;

public class DelegateNotRegisteredException extends NoDelegatesException {
  public DelegateNotRegisteredException(final String identifier) {
    super(String.format("Delegate with identifier %s is not registered", identifier), DELEGATE_NOT_REGISTERED);
  }
}
