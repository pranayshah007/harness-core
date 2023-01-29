/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.freeze.helpers;

import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.exception.InvalidRequestException;

import lombok.experimental.UtilityClass;

@UtilityClass
public class PrincipalTypeSecurityToPrincipalTypeMapper {
  public PrincipalType convertToAccessControlPrincipalType(PrincipalType principalType) {
    switch (principalType) {
      case USER:
        return PrincipalType.USER;
      case USER_GROUP:
        return PrincipalType.USER_GROUP;
      case SERVICE:
        return PrincipalType.SERVICE;
      case API_KEY:
        return PrincipalType.API_KEY;
      case SERVICE_ACCOUNT:
        return PrincipalType.SERVICE_ACCOUNT;
      default:
        throw new InvalidRequestException("Unknown principal type found");
    }
  }
}
