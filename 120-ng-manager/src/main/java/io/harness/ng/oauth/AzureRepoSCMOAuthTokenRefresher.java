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
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoApiAccessType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoOAuthDTO;
import io.harness.encryption.SecretRefData;
import io.harness.gitsync.common.beans.AzureRepoSCM;
import io.harness.gitsync.common.beans.AzureRepoSCM.AzureRepoSCMKeys;
import io.harness.gitsync.common.beans.UserSourceCodeManager.UserSourceCodeManagerKeys;
import io.harness.gitsync.common.mappers.AzureRepoSCMMapper;
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
public class AzureRepoSCMOAuthTokenRefresher implements Handler<AzureRepoSCM> {
  PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private ScmClient scmClient;
  private final MongoTemplate mongoTemplate;
  @Inject DecryptionHelper decryptionHelper;
  @Inject private SecretCrudService ngSecretCrudService;
  @Inject NextGenConfiguration configuration;
  @Inject private OAuthTokenRefresherHelper oAuthTokenRefresherHelper;

  @Inject
  public AzureRepoSCMOAuthTokenRefresher(
      PersistenceIteratorFactory persistenceIteratorFactory, MongoTemplate mongoTemplate) {
    this.persistenceIteratorFactory = persistenceIteratorFactory;
    this.mongoTemplate = mongoTemplate;
  }

  @Override
  public void handle(AzureRepoSCM entity) {
    try (AutoLogContext autoLogContext = new TokenRefresherLogContext(entity.getAccountIdentifier(),
             entity.getUserIdentifier(), entity.getType(), AutoLogContext.OverrideBehavior.OVERRIDE_NESTS)) {
      log.info("Starting Token Refresh..");
      oAuthTokenRefresherHelper.updateContext();
      AzureRepoOAuthDTO azureRepoOauthDTO = getAzureRepoOauthDecrypted(entity);

      SecretDTOV2 tokenDTO = getSecretSecretValue(entity, azureRepoOauthDTO.getTokenRef());
      SecretDTOV2 refreshTokenDTO = getSecretSecretValue(entity, azureRepoOauthDTO.getRefreshTokenRef());

      if (tokenDTO == null) {
        log.error("Error getting access token");
        return;
      }
      if (refreshTokenDTO == null) {
        log.error("Error getting refresh token");
        return;
      }

      RefreshTokenResponse refreshTokenResponse = null;
      String clientId = configuration.getAzureRepoConfig().getClientId();
      String clientSecret = configuration.getAzureRepoConfig().getClientSecret();

      String clientIdShort = clientId.substring(0, Math.min(clientId.length(), 3));
      String clientSecretShort = clientSecret.substring(0, Math.min(clientSecret.length(), 3));

      try {
        refreshTokenResponse = scmClient.refreshToken(null, clientId, clientSecret,
            "https://login.microsoftonline.com/common/oauth2/v2.0/token",
            String.valueOf(azureRepoOauthDTO.getRefreshTokenRef().getDecryptedValue()));
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
        configuration.getOauthRefreshFrequency(), configuration.getAzureRepoConfig().getClientId(),
        configuration.getAzureRepoConfig().getClientSecret());

    if (configuration.isOauthRefreshEnabled()) {
      SpringFilterExpander springFilterExpander = getFilterQuery();

      persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
          PersistenceIteratorFactory.PumpExecutorOptions.builder()
              .name(this.getClass().getName())
              .poolSize(threadPoolSize)
              .interval(ofSeconds(10))
              .build(),
          AzureRepoSCM.class,
          MongoPersistenceIterator.<AzureRepoSCM, SpringFilterExpander>builder()
              .clazz(AzureRepoSCM.class)
              .fieldName(AzureRepoSCMKeys.nextTokenRenewIteration)
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

  private AzureRepoOAuthDTO getAzureRepoOauthDecrypted(AzureRepoSCM entity) {
    AzureRepoOAuthDTO azureRepoOauthDTO =
        (AzureRepoOAuthDTO) AzureRepoSCMMapper.toApiAccessDTO(entity.getApiAccessType(), entity.getAzureRepoApiAccess())
            .getSpec();
    List<EncryptedDataDetail> encryptionDetails =
        oAuthTokenRefresherHelper.getEncryptionDetails(azureRepoOauthDTO, entity.getAccountIdentifier(), null, null);
    return (AzureRepoOAuthDTO) decryptionHelper.decrypt(azureRepoOauthDTO, encryptionDetails);
  }

  private SecretDTOV2 getSecretSecretValue(AzureRepoSCM entity, SecretRefData token) {
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

  private void updateSecretSecretValue(AzureRepoSCM entity, SecretDTOV2 secretDTOV2, String newSecret) {
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
                              .is(SCMType.AZURE_REPO)
                              .and(AzureRepoSCMKeys.apiAccessType)
                              .is(AzureRepoApiAccessType.OAUTH);

      query.addCriteria(criteria);
    };
  }
}
