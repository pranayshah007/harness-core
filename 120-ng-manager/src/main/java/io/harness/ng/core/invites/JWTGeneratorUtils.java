/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.invites;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.security.JWTTokenServiceUtils;

import com.auth0.jwt.interfaces.Claim;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

//  I want to have JWTGeneratorUtils as util class but how will I inject dependencies then?
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
@OwnedBy(PL)
public class JWTGeneratorUtils {
  public static final String HARNESS_USER = "harnessUser";
  // A claim in the token to denote which environment the token is generated.
  public static final String ENV = "env";

  private static final String ISSUER = "Harness Inc";

  public String generateJWTToken(Map<String, String> claims, Long validityDurationInMillis, String jwtPasswordSecret) {
    return JWTTokenServiceUtils.generateJWTToken(claims, validityDurationInMillis, jwtPasswordSecret);
  }

  public Map<String, Claim> verifyJWTToken(String token, String jwtPasswordSecret) {
    return JWTTokenServiceUtils.verifyJWTToken(token, jwtPasswordSecret);
  }

  public Map<String, Claim> verifyJWTTokenV2(String token, String jwtPasswordSecret) {
    return JWTTokenServiceUtils.verifyJWTTokenV2(token, jwtPasswordSecret);
  }
}
