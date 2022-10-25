/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.gcp.helpers;

import io.harness.network.Http;
import io.harness.serializer.JsonUtils;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.OAuth2Utils;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.container.ContainerScopes;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

@Singleton
@Slf4j
public class GcpCredentialsHelperService {
  @Inject private GcpHttpTransportHelperService gcpHttpTransportHelperService;

  public GoogleCredential getGoogleCredentialWithDefaultHttpTransport(char[] serviceAccountKeyFileContent)
      throws IOException {
    return appendScopesIfRequired(GoogleCredential.fromStream(
        IOUtils.toInputStream(String.valueOf(serviceAccountKeyFileContent), Charset.defaultCharset())));
  }

  public GoogleCredential getGoogleCredentialWithProxyConfiguredHttpTransport(char[] serviceAccountKeyFileContent)
      throws IOException {
    HttpTransport httpTransport = GcpHttpTransportHelperService.getProxyConfiguredHttpTransport();
    return appendScopesIfRequired(GoogleCredential.fromStream(
        IOUtils.toInputStream(String.valueOf(serviceAccountKeyFileContent), Charset.defaultCharset()), httpTransport,
        JacksonFactory.getDefaultInstance()));
  }

  public static GoogleCredential getGoogleCredentialFromFile(char[] serviceAccountKeyFileContent) throws IOException {
    String tokenUri =
        (String) (JsonUtils.asObject(new String(serviceAccountKeyFileContent), HashMap.class)).get("token_uri");
    if (Http.getProxyHostName() != null && !Http.shouldUseNonProxy(tokenUri)) {
      HttpTransport httpTransport = GcpHttpTransportHelperService.getProxyConfiguredHttpTransport();
      return appendScopesIfRequired(GoogleCredential.fromStream(
          IOUtils.toInputStream(String.valueOf(serviceAccountKeyFileContent), Charset.defaultCharset()), httpTransport,
          JacksonFactory.getDefaultInstance()));
    } else {
      return appendScopesIfRequired(GoogleCredential.fromStream(
          IOUtils.toInputStream(String.valueOf(serviceAccountKeyFileContent), Charset.defaultCharset())));
    }
  }

  private static GoogleCredential appendScopesIfRequired(GoogleCredential googleCredential) {
    if (googleCredential.createScopedRequired()) {
      return googleCredential.createScoped(Collections.singletonList(ContainerScopes.CLOUD_PLATFORM));
    }
    return googleCredential;
  }

  public static GoogleCredential getApplicationDefaultCredentials() throws IOException {
    return Http.getProxyHostName() != null && !Http.shouldUseNonProxy(OAuth2Utils.getMetadataServerUrl())
        ? GoogleCredential.getApplicationDefault(
            GcpHttpTransportHelperService.getProxyConfiguredHttpTransport(), JacksonFactory.getDefaultInstance())
        : GoogleCredential.getApplicationDefault();
  }
}
