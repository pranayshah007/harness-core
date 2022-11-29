/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.security;

import io.harness.exception.InvalidRequestException;

import com.google.protobuf.StringValue;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
public class PrincipalProtoMapper {
  public io.harness.security.dto.Principal toPrincipalDTO(String accountId, Principal principalFromProto) {
    if (principalFromProto.hasUserPrincipal()) {
      final io.harness.security.UserPrincipal userPrincipal = principalFromProto.getUserPrincipal();
      return new io.harness.security.dto.UserPrincipal(userPrincipal.getUserId().getValue(),
          userPrincipal.getEmail().getValue(), userPrincipal.getUserName().getValue(), accountId);
    } else if (principalFromProto.hasServicePrincipal()) {
      return new io.harness.security.dto.ServicePrincipal(principalFromProto.getServicePrincipal().getName());
    } else if (principalFromProto.hasServiceAccountPrincipal()) {
      final io.harness.security.ServiceAccountPrincipal serviceAccountPrincipal =
          principalFromProto.getServiceAccountPrincipal();
      final String name = getStringFromStringValue(serviceAccountPrincipal.getName());
      final String email = getStringFromStringValue(serviceAccountPrincipal.getEmail());
      final String username = getStringFromStringValue(serviceAccountPrincipal.getUserName());
      return new io.harness.security.dto.ServiceAccountPrincipal(name, email, username, accountId);
    }
    log.error("The principal in the request is {}", principalFromProto);
    throw new InvalidRequestException("The request doesn't has the correct user principal set");
  }

  private String getStringFromStringValue(StringValue stringValue) {
    if (stringValue != null) {
      return stringValue.getValue();
    }
    return null;
  }
}
