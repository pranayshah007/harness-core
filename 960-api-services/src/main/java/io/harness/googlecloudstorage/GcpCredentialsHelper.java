/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.googlecloudstorage;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;

import io.harness.exception.InvalidRequestException;
import io.harness.gcp.helpers.GcpHttpTransportHelperService;
import io.harness.network.Http;
import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.core.JsonParseException;
import com.google.api.client.googleapis.auth.oauth2.OAuth2Utils;
import com.google.api.services.container.ContainerScopes;
import com.google.auth.http.HttpTransportFactory;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.http.HttpTransportOptions;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.HashMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

@Singleton
@Slf4j
public class GcpCredentialsHelper {
  public static HttpTransportOptions getHttpTransportOptionsForProxy() throws IOException, GeneralSecurityException {
    HttpTransportFactory httpTransportFactory = GcpHttpTransportHelperService.getHttpTransportFactory();
    return HttpTransportOptions.newBuilder().setHttpTransportFactory(httpTransportFactory).build();
  }

  public GoogleCredentials getGoogleCredentials(char[] serviceAccountKeyFileContent, boolean isUseDelegate)
      throws IOException {
    if (isUseDelegate) {
      return getApplicationDefaultCredentials();
    }
    validateServiceAccountKey(serviceAccountKeyFileContent);
    return checkIfUseProxyAndGetGoogleCredentials(serviceAccountKeyFileContent);
  }

  private static GoogleCredentials getApplicationDefaultCredentials() throws IOException {
    return Http.getProxyHostName() != null && !Http.shouldUseNonProxy(OAuth2Utils.getMetadataServerUrl())
        ? GoogleCredentials.getApplicationDefault(GcpHttpTransportHelperService.getHttpTransportFactory())
        : GoogleCredentials.getApplicationDefault();
  }

  private void validateServiceAccountKey(char[] serviceAccountKeyFileContent) {
    if (isEmpty(serviceAccountKeyFileContent)) {
      throw new InvalidRequestException("Empty service key found. Unable to validate", USER);
    }
    try {
      GoogleCredentials.fromStream(
          IOUtils.toInputStream(String.valueOf(serviceAccountKeyFileContent), Charset.defaultCharset()));
    } catch (Exception e) {
      if (e instanceof JsonParseException) {
        throw new InvalidRequestException("Provided Service account key is not in JSON format ", USER);
      }
      throw new InvalidRequestException("Invalid Google Cloud Platform credentials: " + e.getMessage(), e, USER);
    }
  }

  private GoogleCredentials checkIfUseProxyAndGetGoogleCredentials(char[] serviceAccountKeyFileContent)
      throws IOException {
    String tokenUri =
        (String) (JsonUtils.asObject(new String(serviceAccountKeyFileContent), HashMap.class)).get("token_uri");
    return Http.getProxyHostName() != null && !Http.shouldUseNonProxy(tokenUri)
        ? getGoogleCredentialWithProxyConfiguredHttpTransport(serviceAccountKeyFileContent)
        : getGoogleCredentialWithDefaultHttpTransport(serviceAccountKeyFileContent);
  }

  public GoogleCredentials getGoogleCredentialWithDefaultHttpTransport(char[] serviceAccountKeyFileContent)
      throws IOException {
    return appendScopesIfRequired(GoogleCredentials.fromStream(
        IOUtils.toInputStream(String.valueOf(serviceAccountKeyFileContent), Charset.defaultCharset())));
  }

  public GoogleCredentials getGoogleCredentialWithProxyConfiguredHttpTransport(char[] serviceAccountKeyFileContent)
      throws IOException {
    return appendScopesIfRequired(GoogleCredentials.fromStream(
        IOUtils.toInputStream(String.valueOf(serviceAccountKeyFileContent), Charset.defaultCharset()),
        GcpHttpTransportHelperService.getHttpTransportFactory()));
  }

  private static GoogleCredentials appendScopesIfRequired(GoogleCredentials googleCredential) {
    if (googleCredential.createScopedRequired()) {
      return googleCredential.createScoped(Collections.singletonList(ContainerScopes.CLOUD_PLATFORM));
    }
    return googleCredential;
  }
}
