/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.app.modules.common;

import io.harness.delegate.configuration.DelegateConfiguration;
import io.harness.security.TokenGenerator;

import com.google.inject.AbstractModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;

@RequiredArgsConstructor
public class DelegateTokensModule extends AbstractModule {
  private final DelegateConfiguration configuration;

  @Override
  protected void configure() {
    bind(TokenGenerator.class)
        .toInstance(
            new TokenGenerator(configuration.getAccountId(), getDecodedTokenString(configuration.getDelegateToken())));
  }

  /**
   * Token copied from Harness Saas UI is base64 encoded. However, since kubernetes secret is used to create the token
   * with token value put in 'data' field of secret yaml as plain text, the token passed to delegate agent is already
   * decoded. For Docker delegates, token passes to delegate agent is still not decoded. This function is to provide
   * compatibility to both use cases.
   *
   * @param token maybe base64 encoded or not encoded
   * @return non-encoded delegate token
   */
  private String getDecodedTokenString(final String token) {
    // Step 1. Check if the token is base64 encoded
    if (!Base64.isBase64(token)) {
      return token;
    }
    // Step 2. decode the token, check if the decoded is hex decimal string
    String decoded = new String(Base64.decodeBase64(token)).trim();
    if (isHexDecimalString(decoded)) {
      return decoded;
    }
    return token;
  }

  private boolean isHexDecimalString(final String decodedToken) {
    return decodedToken.matches("[0-9A-Fa-f]{32}");
  }
}
