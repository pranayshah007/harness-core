/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.ldap;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.Level;
import io.harness.exception.WingsException;

/**
 * @author deepakPatankar on 11/27/19
 */
@OwnedBy(PL)
public class LdapDelegateException extends WingsException {
  @SuppressWarnings("squid:CallToDeprecatedMethod")
  public LdapDelegateException(String message, Throwable throwable) {
    super(message, throwable, GENERAL_ERROR, Level.ERROR, null, null);
  }
}
