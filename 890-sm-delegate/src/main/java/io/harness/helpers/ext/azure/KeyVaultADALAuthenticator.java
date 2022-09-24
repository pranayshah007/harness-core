/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.helpers.ext.azure;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.UnexpectedException;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.microsoft.aad.adal4j.AuthenticationContext;
import com.microsoft.aad.adal4j.AuthenticationResult;
import com.microsoft.aad.adal4j.ClientCredential;
import com.microsoft.azure.keyvault.KeyVaultClient;
import com.microsoft.azure.keyvault.authentication.KeyVaultCredentials;
import com.microsoft.rest.credentials.ServiceClientCredentials;
import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@UtilityClass
@Slf4j
public class KeyVaultADALAuthenticator {
  public static KeyVaultClient getClient(String clientId, String clientKey) {
    // Creates the KeyVaultClient using the created credentials.
    return new KeyVaultClient(createCredentials(clientId, clientKey));
  }

  private static ServiceClientCredentials createCredentials(String clientId, String clientKey) {
    return new KeyVaultCredentials() {
      // Callback that supplies the token type and access token on request.
      @Override
      public String doAuthenticate(String authorization, String resource, String scope) {
        AuthenticationResult authResult;
        try {
          authResult = getAccessToken(clientId, clientKey, authorization, resource);
          return authResult.getAccessToken();
        } catch (Exception e) {
          log.error("Failed to get access token for clientId {}", clientId, e);
        }
        return "";
      }
    };
  }

  private static AuthenticationResult getAccessToken(String clientId, String clientKey, String authorization,
      String resource) throws InterruptedException, ExecutionException, MalformedURLException {
    AuthenticationResult result = null;

    // Starts a service to fetch access token.
    ExecutorService service = null;
    try {
      service = Executors.newFixedThreadPool(
          1, new ThreadFactoryBuilder().setNameFormat("keyvault-get-access-token").build());
      AuthenticationContext context = new AuthenticationContext(authorization, false, service);

      Future<AuthenticationResult> future = null;

      // Acquires token based on client ID and client secret.
      if (clientKey != null && clientId != null) {
        ClientCredential credentials = new ClientCredential(clientId, clientKey);
        future = context.acquireToken(resource, credentials, null);
      }
      if (future != null) {
        result = future.get();
      }
    } finally {
      if (service != null) {
        service.shutdown();
      }
    }

    if (result == null) {
      throw new UnexpectedException("Authentication results were null.");
    }
    return result;
  }
}
