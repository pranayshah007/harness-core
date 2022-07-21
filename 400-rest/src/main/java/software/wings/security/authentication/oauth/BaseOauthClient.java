/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.security.authentication.oauth;

import static io.harness.annotations.dev.HarnessModule._950_NG_AUTHENTICATION_SERVICE;
import static io.harness.annotations.dev.HarnessTeam.PL;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.WingsException;

import software.wings.security.JWT_CATEGORY;
import software.wings.security.SecretManager;

import java.net.URI;
import java.net.URISyntaxException;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.utils.URIBuilder;

@Slf4j
@OwnedBy(PL)
@TargetModule(_950_NG_AUTHENTICATION_SERVICE)
public class BaseOauthClient {
  private static final String STATE_KEY = "state";

  private SecretManager secretManager;

  public BaseOauthClient(SecretManager secretManager) {
    this.secretManager = secretManager;
  }

  public URI appendStateToURL(URIBuilder uriBuilder) throws URISyntaxException {
    String jwtSecret = secretManager.generateJWTToken(null, JWT_CATEGORY.OAUTH_REDIRECT);
    log.info("Status appending to oauth url is [{}]", jwtSecret);
    uriBuilder.addParameter(STATE_KEY, jwtSecret);
    return uriBuilder.build();
  }

  public void verifyState(String state) {
    try {
      log.info("The status received is: [{}]", state);
      secretManager.verifyJWTToken(state, JWT_CATEGORY.OAUTH_REDIRECT);
    } catch (Exception ex) {
      log.warn("State verification failed in oauth.", ex);
      throw new WingsException("Oauth failed because of state mismatch");
    }
  }

  protected void populateEmptyFields(OauthUserInfo oauthUserInfo) {
    String email = oauthUserInfo.getEmail();
    String handle = email.substring(0, email.indexOf('@'));
    log.info("Populating the name, from email. Email is {} and the new name is {} ", email, handle);
    oauthUserInfo.setLogin(isBlank(oauthUserInfo.getLogin()) ? handle : oauthUserInfo.getEmail());
    oauthUserInfo.setName(isBlank(oauthUserInfo.getName()) ? handle : oauthUserInfo.getName());
  }
}
