/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.oauth;

import static io.harness.connector.entities.embedded.gitlabconnector.GitlabConnector.GitlabConnectorKeys;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;

import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.entities.Connector.ConnectorKeys;
import io.harness.connector.entities.embedded.gitlabconnector.GitlabApiAccess;
import io.harness.connector.entities.embedded.gitlabconnector.GitlabConnector;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.connector.mappers.ConnectorMapper;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabOauthDTO;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.SpringFilterExpander;
import io.harness.mongo.iterator.provider.SpringPersistenceProvider;
import io.harness.ng.NextGenConfiguration;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.product.ci.scm.proto.RefreshTokenResponse;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.ScmClient;

import software.wings.security.authentication.oauth.OAuthConstants;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;

@Singleton
@Slf4j
public class GitlabConnectorOAuthTokenRefresher implements Handler<GitlabConnector> {
  PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private ConnectorMapper connectorMapper;
  @Inject private ScmClient scmClient;
  private final MongoTemplate mongoTemplate;
  @Inject DecryptionHelper decryptionHelper;
  @Inject NextGenConfiguration configuration;
  @Inject private OAuthTokenRefresherHelper oAuthTokenRefresherHelper;

  @Inject
  public GitlabConnectorOAuthTokenRefresher(
      PersistenceIteratorFactory persistenceIteratorFactory, MongoTemplate mongoTemplate) {
    this.persistenceIteratorFactory = persistenceIteratorFactory;
    this.mongoTemplate = mongoTemplate;
  }

  private GitlabOauthDTO getGitlabOauthDecrypted(GitlabConnector entity) {
    GitlabApiAccess apiAccess = entity.getGitlabApiAccess();

    ConnectorResponseDTO connectorDTO = connectorMapper.writeDTO(entity);

    GitlabConnectorDTO gitlabConnectorDTO = (GitlabConnectorDTO) connectorDTO.getConnector().getConnectorConfig();

    GitlabHttpCredentialsDTO gitlabHttpCredentialsDTO =
        (GitlabHttpCredentialsDTO) gitlabConnectorDTO.getAuthentication().getCredentials();
    GitlabOauthDTO gitlabOauthDTO = (GitlabOauthDTO) gitlabHttpCredentialsDTO.getHttpCredentialsSpec();
    List<EncryptedDataDetail> encryptionDetails = oAuthTokenRefresherHelper.getEncryptionDetails(
        gitlabOauthDTO, entity.getAccountIdentifier(), entity.getOrgIdentifier(), entity.getProjectIdentifier());

    return (GitlabOauthDTO) decryptionHelper.decrypt(gitlabOauthDTO, encryptionDetails);
  }

  private SecretDTOV2 getSecretSecretValue(GitlabConnector entity, SecretRefData token) {
    String orgIdentifier = null;
    String projectIdentifier = null;

    if (token.getScope() != Scope.ACCOUNT) {
      orgIdentifier = entity.getOrgIdentifier();
      projectIdentifier = entity.getProjectIdentifier();
    }
    return oAuthTokenRefresherHelper.getSecretSecretValue(
        io.harness.beans.Scope.of(entity.getAccountIdentifier(), orgIdentifier, projectIdentifier), token);
  }

  private void updateSecretSecretValue(GitlabConnector entity, SecretDTOV2 secretDTOV2, String newSecret) {
    oAuthTokenRefresherHelper.updateSecretSecretValue(
        io.harness.beans.Scope.of(
            entity.getAccountIdentifier(), secretDTOV2.getOrgIdentifier(), secretDTOV2.getProjectIdentifier()),
        secretDTOV2, newSecret);
  }

  @Override
  public void handle(GitlabConnector entity) {
    log.info("[OAuth refresh] Working on: {}", entity.getAccountIdentifier() + " , " + entity.getIdentifier());

    try {
      oAuthTokenRefresherHelper.updateContext();
      GitlabOauthDTO gitlabOauthDTO = getGitlabOauthDecrypted(entity);

      SecretDTOV2 tokenDTO = getSecretSecretValue(entity, gitlabOauthDTO.getTokenRef());
      SecretDTOV2 refreshTokenDTO = getSecretSecretValue(entity, gitlabOauthDTO.getRefreshTokenRef());

      if (tokenDTO == null || refreshTokenDTO == null) {
        log.error("[OAuth refresh] Error getting refresh/access token for connector: ", entity.getName());
        return;
      }

      RefreshTokenResponse refreshTokenResponse = null;
      String clientId = configuration.getGitlabConfig().getClientId();
      String clientSecret = configuration.getGitlabConfig().getClientSecret();

      String clientIdShort = clientId.substring(0, Math.min(clientId.length(), 3));
      String clientSecretShort = clientSecret.substring(0, Math.min(clientSecret.length(), 3));

      try {
        refreshTokenResponse = scmClient.refreshToken(null, clientId, clientSecret, OAuthConstants.GITLAB_ENDPOINT,
            String.valueOf(gitlabOauthDTO.getRefreshTokenRef().getDecryptedValue()));
      } catch (Exception e) {
        log.error(
            "[OAuth refresh] Error from SCM for refreshing token for connector:{}, clientID short:{}, client Secret short:{}, Account:{}, Error:{}",
            entity.getIdentifier(), clientIdShort, clientSecretShort, entity.getAccountIdentifier(), e.getMessage());
        return;
      }

      log.info("[OAuth refresh]:" + entity.getName() + "-Got new access & refresh token");

      updateSecretSecretValue(entity, tokenDTO, refreshTokenResponse.getAccessToken());
      updateSecretSecretValue(entity, refreshTokenDTO, refreshTokenResponse.getRefreshToken());

    } catch (Exception e) {
      log.error("[OAuth refresh] Error in refreshing token for connector:" + entity.getIdentifier(), e);
    }
  }

  public void registerIterators(int threadPoolSize) {
    log.info("[OAuth refresh] Register Enabled:{}, Frequency:{}, clientID:{}, clientSecret{}",
        configuration.isOauthRefreshEnabled(), configuration.getOauthRefreshFrequency(),
        configuration.getGitlabConfig().getClientId(), configuration.getGitlabConfig().getClientSecret());

    if (configuration.isOauthRefreshEnabled()) {
      SpringFilterExpander springFilterExpander = getFilterQuery();

      persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
          PersistenceIteratorFactory.PumpExecutorOptions.builder()
              .name(this.getClass().getName())
              .poolSize(threadPoolSize)
              .interval(ofSeconds(10))
              .build(),
          GitlabConnector.class,
          MongoPersistenceIterator.<GitlabConnector, SpringFilterExpander>builder()
              .clazz(GitlabConnector.class)
              .fieldName(GitlabConnectorKeys.nextTokenRenewIteration)
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

  private SpringFilterExpander getFilterQuery() {
    return query -> {
      Criteria criteria =
          Criteria.where(ConnectorKeys.type).is(ConnectorType.GITLAB).and("authenticationDetails.type").is("OAUTH");

      query.addCriteria(criteria);
    };
  }
}
