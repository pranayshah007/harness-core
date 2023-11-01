/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.oidc.accesstoken;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.PL)
public class OidcAccessTokenConstants {
  public static final String END_POINT = "endpoint";
  public static final String AUDIENCE = "audience";
  public static final String GRANT_TYPE = "grant_type";
  public static final String REQUESTED_TOKEN_TYPE = "requested_token_type";
  public static final String SCOPE = "scope";
  public static final String SUBJECT_TOKEN_TYPE = "subject_token_type";
  public static final String SUBJECT_TOKEN = "subject_token";
  public static final String OPTIONS = "options";
  public static final String USER_PROJECT = "userProject";
  public static final String ACCESS_TOKEN_STS_ENDPOINT = "access_token_sts_endpoint";
  public static final String ACCESS_TOKEN_IAM_SA_CREDENTIALS_ENDPOINT = "access_token_iam_sa_credentials_endpoint";
  public static final String WORKLOAD_ACCESS_TOKEN_CONFIG = "workload_access_token_config";
  public static final String ACCESS_TOKEN = "access_token";
  public static final String ISSUED_TOKEN_TYPE = "issued_token_type";
  public static final String TOKEN_TYPE = "token_type";
  public static final String EXPIRES_IN = "expires_in";
}
