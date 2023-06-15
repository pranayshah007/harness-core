/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.oauth;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.Scope;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.common.beans.UserSourceCodeManager;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.logging.AutoLogContext;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.ng.NextGenConfiguration;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.user.UserInfo;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.product.ci.scm.proto.RefreshTokenResponse;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.UserPrincipal;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.ScmClient;

import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public abstract class AbstractSCMOAuthTokenRefresher<T extends UserSourceCodeManager>
    implements MongoPersistenceIterator.Handler<T> {
  @Inject private ScmClient scmClient;
  @Inject private OAuthTokenRefresherHelper oAuthTokenRefresherHelper;
  @Inject DecryptionHelper decryptionHelper;
  PersistenceIteratorFactory persistenceIteratorFactory;
  MongoTemplate mongoTemplate;
  @Inject NextGenConfiguration configuration;
  @Inject NgUserService ngUserService;

  @Override
  public void handle(T entity) {
    try (AutoLogContext autoLogContext = new TokenRefresherLogContext(entity.getAccountIdentifier(),
             entity.getUserIdentifier(), entity.getType(), AutoLogContext.OverrideBehavior.OVERRIDE_NESTS)) {
      try {
        log.info("Starting Token Refresh..");
        setPrincipal(entity.getUserIdentifier(), entity.getAccountIdentifier());
        OAuthRef oAuthRef = getOAuthDecrypted(entity);

        SecretDTOV2 tokenDTO = getSecretSecretValue(entity, oAuthRef.getTokenRef());
        SecretDTOV2 refreshTokenDTO = getSecretSecretValue(entity, oAuthRef.getRefreshTokenRef());

        if (tokenDTO == null) {
          log.error("Error getting access token");
          return;
        }
        if (refreshTokenDTO == null) {
          log.error("Error getting refresh token");
          return;
        }

        OAuthConfig oAuthConfig = getOAuthConfig();
        RefreshTokenResponse refreshTokenResponse = null;
        String clientId = oAuthConfig.getClientId();
        String clientSecret = oAuthConfig.getClientSecret();

        String clientIdShort = clientId.substring(0, Math.min(clientId.length(), 3));
        String clientSecretShort = clientSecret.substring(0, Math.min(clientSecret.length(), 3));

        try {
          refreshTokenResponse = scmClient.refreshToken(null, clientId, clientSecret, oAuthConfig.getEndpoint(),
              String.valueOf(oAuthRef.getRefreshTokenRef().getDecryptedValue()));
        } catch (Exception e) {
          log.error("Error from SCM for refreshing token clientID short:{}, client Secret short:{}, Error:{}",
              clientIdShort, clientSecretShort, e.getMessage());
          return;
        }

        log.info("Got new access & refresh token");

        updateSecretSecretValue(entity, tokenDTO, refreshTokenResponse.getAccessToken());
        updateSecretSecretValue(entity, refreshTokenDTO, refreshTokenResponse.getRefreshToken());

        log.info("Successfully updated access and refresh tokens!");
      } catch (Exception e) {
        log.error("Error in refreshing token ", e);
      }
    }
  }

  SecretDTOV2 getSecretSecretValue(T entity, SecretRefData token) {
    return oAuthTokenRefresherHelper.getSecretSecretValue(Scope.of(entity.getAccountIdentifier(), null, null), token);
  }

  void updateSecretSecretValue(T entity, SecretDTOV2 secretDTOV2, String newSecret) {
    oAuthTokenRefresherHelper.updateSecretSecretValue(
        Scope.of(entity.getAccountIdentifier(), secretDTOV2.getOrgIdentifier(), secretDTOV2.getProjectIdentifier()),
        secretDTOV2, newSecret);
  }

  DecryptableEntity getOAuthDecryptedInternal(DecryptableEntity decryptableEntity, String accountIdentifier) {
    List<EncryptedDataDetail> encryptionDetails =
        oAuthTokenRefresherHelper.getEncryptionDetails(decryptableEntity, accountIdentifier, null, null);
    return decryptionHelper.decrypt(decryptableEntity, encryptionDetails);
  }

  abstract OAuthRef getOAuthDecrypted(T entity);

  abstract OAuthConfig getOAuthConfig();

  private void setPrincipal(String userId, String accountIdentifier) {
    Principal principal = SecurityContextBuilder.getPrincipal();
    boolean isUserPrincipal = principal instanceof UserPrincipal;
    if (principal == null || !isUserPrincipal) {
      Optional<UserInfo> userInfo = ngUserService.getUserById(userId, false);
      if (userInfo.isEmpty()) {
        log.error("Failed to get user details for user id: {}", userId);
        throw new InvalidRequestException(String.format("Failed to get user details for user id: %s", userId));
      }
      principal = new UserPrincipal(userId, userInfo.get().getEmail(), userInfo.get().getName(), accountIdentifier);
    }
    SourcePrincipalContextBuilder.setSourcePrincipal(principal);
  }
}
