/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.http.beans;

import io.harness.beans.HttpCertificate;
import io.harness.beans.KeyValuePair;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class HttpInternalConfig {
  String method;
  String url;
  String header;
  Map<String, String> requestHeaders;
  List<KeyValuePair> headers;
  String body;
  int socketTimeoutMillis;
  boolean useProxy;
  boolean isCertValidationRequired;
  boolean throwErrorIfNoProxySetWithDelegateProxy; // We need to throw this error in cg but not in ng
  HttpCertificate certificate;
  List<EncryptedDataDetail> encryptedDataDetails;
}
