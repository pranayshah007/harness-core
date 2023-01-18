/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.adfs;

public interface AdfsConstants {
  String ADFS_GRANT_TYPE = "client_credentials";
  String ADFS_CLIENT_ASSERTION_TYPE = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";
  String CLIENT_ID = "client_id";
  String CLIENT_ASSERTION_TYPE = "client_assertion_type";
  String CLIENT_ASSERTION = "client_assertion";
  String GRANT_TYPE = "grant_type";
  String RESOURCE_ID = "resource";
  String ADFS_ACCESS_TOKEN_ENDPOINT = "adfs/oauth2/token";
  String INVALID_ADFS_CREDENTIALS = "Invalid ADFS credentials";
  String NOT_FOUND = "404 Not found";
}
