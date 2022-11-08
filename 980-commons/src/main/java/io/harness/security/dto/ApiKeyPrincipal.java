/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.security.dto;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.security.dto.SecurityContextBuilder.PRINCIPAL_NAME;
import static io.harness.security.dto.SecurityContextBuilder.PRINCIPAL_TYPE;
import static io.harness.security.dto.PrincipalType.API_KEY;

import io.harness.annotations.dev.OwnedBy;

import com.auth0.jwt.interfaces.Claim;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.HashMap;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(PL)
@Getter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("API_KEY")
@TypeAlias("ApiKeyPrincipal")
public class ApiKeyPrincipal extends Principal {
  public ApiKeyPrincipal(String name) {
    this.type = API_KEY;
    this.name = name;
  }

  @Override
  public Map<String, String> getJWTClaims() {
    Map<String, String> claims = new HashMap<>();
    claims.put(PRINCIPAL_TYPE, getType().toString());
    claims.put(PRINCIPAL_NAME, getName());
    return claims;
  }

  public static ApiKeyPrincipal getPrincipal(Map<String, Claim> claims) {
    return new ApiKeyPrincipal(claims.get(PRINCIPAL_NAME) == null ? null : claims.get(PRINCIPAL_NAME).asString());
  }
}
