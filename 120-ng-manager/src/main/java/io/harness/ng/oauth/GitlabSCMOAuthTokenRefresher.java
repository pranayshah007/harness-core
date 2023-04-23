/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.oauth;

import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;

import io.harness.connector.helper.DecryptionHelper;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabOauthDTO;
import io.harness.encryption.SecretRefData;
import io.harness.gitsync.common.beans.GitlabSCM;
import io.harness.gitsync.common.beans.GitlabSCM.GitlabSCMKeys;
import io.harness.gitsync.common.beans.UserSourceCodeManager.UserSourceCodeManagerKeys;
import io.harness.gitsync.common.mappers.GitlabSCMMapper;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.logging.AutoLogContext;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.SpringFilterExpander;
import io.harness.mongo.iterator.provider.SpringPersistenceProvider;
import io.harness.ng.NextGenConfiguration;
import io.harness.ng.core.api.SecretCrudService;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.ng.core.dto.secrets.SecretTextSpecDTO;
import io.harness.ng.core.models.Secret;
import io.harness.ng.userprofile.commons.SCMType;
import io.harness.product.ci.scm.proto.RefreshTokenResponse;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.ScmClient;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;

@Singleton
@Slf4j
public class GitlabSCMOAuthTokenRefresher implements Handler<GitlabSCM> {
  PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private ScmClient scmClient;
  private final MongoTemplate mongoTemplate;
  @Inject DecryptionHelper decryptionHelper;
  @Inject private SecretCrudService ngSecretCrudService;
  @Inject NextGenConfiguration configuration;
  @Inject private OAuthTokenRefresherHelper oAuthTokenRefresherHelper;

  @Inject
  public GitlabSCMOAuthTokenRefresher(
      PersistenceIteratorFactory persistenceIteratorFactory, MongoTemplate mongoTemplate) {
    this.persistenceIteratorFactory = persistenceIteratorFactory;
    this.mongoTemplate = mongoTemplate;
  }

  @Override
  public void handle(GitlabSCM entity) {
    try (AutoLogContext autoLogContext = new TokenRefresherLogContext(entity.getAccountIdentifier(),
             entity.getUserIdentifier(), entity.getType(), AutoLogContext.OverrideBehavior.OVERRIDE_NESTS)) {
      log.info("Starting Token Refresh..");
      oAuthTokenRefresherHelper.updateContext();
      GitlabOauthDTO gitlabOauthDTO = getGitlabOauthDecrypted(entity);

      SecretDTOV2 tokenDTO = getSecretSecretValue(entity, gitlabOauthDTO.getTokenRef());
      SecretDTOV2 refreshTokenDTO = getSecretSecretValue(entity, gitlabOauthDTO.getRefreshTokenRef());

      if (tokenDTO == null) {
        log.error("Error getting access token");
        return;
      }
      if (refreshTokenDTO == null) {
        log.error("Error getting refresh token");
        return;
      }

      RefreshTokenResponse refreshTokenResponse = null;
      String clientId = configuration.getGitlabConfig().getClientId();
      String clientSecret = configuration.getGitlabConfig().getClientSecret();

      String clientIdShort = clientId.substring(0, Math.min(clientId.length(), 3));
      String clientSecretShort = clientSecret.substring(0, Math.min(clientSecret.length(), 3));

      try {
        refreshTokenResponse = scmClient.refreshToken(null, clientId, clientSecret, "https://gitlab.com/oauth/token",
            String.valueOf(gitlabOauthDTO.getRefreshTokenRef().getDecryptedValue()));
      } catch (Exception e) {
        log.error("Error from SCM for refreshing token clientID short:{}, client Secret short:{}, Error:{}",
            clientIdShort, clientSecretShort, e.getMessage());
        return;
      }

      log.info("Got new access & refresh token");

      updateSecretSecretValue(entity, tokenDTO, refreshTokenResponse.getAccessToken());
      updateSecretSecretValue(entity, refreshTokenDTO, refreshTokenResponse.getRefreshToken());

    } catch (Exception e) {
      log.error("Error in refreshing token ", e);
    }
  }

  public void registerIterators(int threadPoolSize) {
    log.info("Register Enabled:{}, Frequency:{}, clientID:{}, clientSecret{}", configuration.isOauthRefreshEnabled(),
        configuration.getOauthRefreshFrequency(), configuration.getGitlabConfig().getClientId(),
        configuration.getGitlabConfig().getClientSecret());

    if (configuration.isOauthRefreshEnabled()) {
      SpringFilterExpander springFilterExpander = getFilterQuery();

      persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
          PersistenceIteratorFactory.PumpExecutorOptions.builder()
              .name(this.getClass().getName())
              .poolSize(threadPoolSize)
              .interval(ofSeconds(10))
              .build(),
          GitlabSCM.class,
          MongoPersistenceIterator.<GitlabSCM, SpringFilterExpander>builder()
              .clazz(GitlabSCM.class)
              .fieldName(GitlabSCMKeys.nextTokenRenewIteration)
              .targetInterval(ofMinutes(configuration.getOauthRefreshFrequency()))
              .acceptableExecutionTime(ofMinutes(1))
              .acceptableNoAlertDelay(ofMinutes(1))
              .filterExpander(springFilterExpander)
              .handler(this)
              .schedulingType(REGULAR)
              .persistenceProvider(new SpringPersistenceProvider<>(mongoTemplate))
              .redistribute(true));
    }
  }

  private GitlabOauthDTO getGitlabOauthDecrypted(GitlabSCM entity) {
    GitlabOauthDTO gitlabOauthDTO =
        (GitlabOauthDTO) GitlabSCMMapper.toApiAccessDTO(entity.getApiAccessType(), entity.getGitlabApiAccess())
            .getSpec();
    List<EncryptedDataDetail> encryptionDetails =
        oAuthTokenRefresherHelper.getEncryptionDetails(gitlabOauthDTO, entity.getAccountIdentifier(), null, null);
    return (GitlabOauthDTO) decryptionHelper.decrypt(gitlabOauthDTO, encryptionDetails);
  }

  private SecretDTOV2 getSecretSecretValue(GitlabSCM entity, SecretRefData token) {
    String orgIdentifier = null;
    String projectIdentifier = null;

    SecretResponseWrapper tokenWrapper =
        ngSecretCrudService.get(entity.getAccountIdentifier(), orgIdentifier, projectIdentifier, token.getIdentifier())
            .orElse(null);

    if (tokenWrapper == null) {
      log.info("Error in secret with identifier: {}", token.getIdentifier());
      return null;
    }

    return tokenWrapper.getSecret();
  }

  private void updateSecretSecretValue(GitlabSCM entity, SecretDTOV2 secretDTOV2, String newSecret) {
    SecretTextSpecDTO secretSpecDTO = (SecretTextSpecDTO) secretDTOV2.getSpec();
    secretSpecDTO.setValue(newSecret);
    secretDTOV2.setSpec(secretSpecDTO);

    Secret secret = Secret.fromDTO(secretDTOV2);
    try {
      ngSecretCrudService.update(entity.getAccountIdentifier(), secret.getOrgIdentifier(),
          secret.getProjectIdentifier(), secretDTOV2.getIdentifier(), secretDTOV2);
    } catch (Exception ex) {
      log.error("Failed to update token in DB, secretDTO: {}", secretDTOV2, ex);
    }
  }

  private SpringFilterExpander getFilterQuery() {
    return query -> {
      Criteria criteria = Criteria.where(UserSourceCodeManagerKeys.type)
                              .is(SCMType.GITLAB)
                              .and(GitlabSCMKeys.apiAccessType)
                              .is(GitlabApiAccessType.OAUTH);

      query.addCriteria(criteria);
    };
  }
}
