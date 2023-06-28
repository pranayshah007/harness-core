/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.servicenow.auth.refreshtoken;

public interface RefreshTokenConstants {
  String CLIENT_ID = "client_id";
  String CLIENT_SECRET = "client_secret";
  String GRANT_TYPE = "grant_type";
  String REFRESH_TOKEN_GRANT_TYPE = "refresh_token";
  String REFRESH_TOKEN = "refresh_token";
  String SCOPE = "scope";
  String NOT_FOUND = "404 Not found";
  String INVALID_CREDENTIALS = "Invalid Refresh Token grant credentials";
  String SERVICENOW_TOKEN_URL_SUFFIX = "oauth_token.do";
}
