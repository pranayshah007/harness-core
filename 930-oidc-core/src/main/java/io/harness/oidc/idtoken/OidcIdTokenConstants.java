/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.oidc.idtoken;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.PL)
public class OidcIdTokenConstants {
  public static final String TYPE = "typ";
  public static final String ALGORITHM = "alg";
  public static final String KEY_IDENTIFIER = "kid";

  public static final String SUBJECT = "sub";
  public static final String AUDIENCE = "aud";
  public static final String ISSUER = "iss";
  public static final String EXPIRY = "exp";
  public static final String ISSUED_AT = "iat";
  public static final String ACCOUNT_ID = "account_id";

  public static final String HEADER = "header";
  public static final String PAYLOAD = "payload";

  public static final Integer KID_LENGTH = 32;
}
