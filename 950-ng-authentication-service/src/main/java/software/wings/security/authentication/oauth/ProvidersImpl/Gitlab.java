/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.security.authentication.oauth.ProvidersImpl;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import software.wings.security.authentication.oauth.OAuthConstants;

import com.github.scribejava.apis.openid.OpenIdJsonTokenExtractor;
import com.github.scribejava.core.builder.api.DefaultApi20;
import com.github.scribejava.core.extractors.TokenExtractor;
import com.github.scribejava.core.model.OAuth2AccessToken;

@OwnedBy(PL)
public class Gitlab extends DefaultApi20 {
  protected Gitlab() {}

  public static Gitlab instance() {
    return Gitlab.InstanceHolder.INSTANCE;
  }

  @Override
  public String getAccessTokenEndpoint() {
    return OAuthConstants.GITLAB_ENDPOINT;
  }

  @Override
  public String getAuthorizationBaseUrl() {
    return "https://gitlab.com/oauth/authorize";
  }

  @Override
  public TokenExtractor<OAuth2AccessToken> getAccessTokenExtractor() {
    return OpenIdJsonTokenExtractor.instance();
  }

  @Override
  public String getRevokeTokenEndpoint() {
    return "https://accounts.google.com/o/oauth2/revoke";
  }

  private static class InstanceHolder {
    private static final Gitlab INSTANCE = new Gitlab();

    private InstanceHolder() {}
  }
}
