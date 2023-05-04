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

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoApiAccessType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoOAuthDTO;
import io.harness.gitsync.common.beans.AzureRepoSCM;
import io.harness.gitsync.common.beans.AzureRepoSCM.AzureRepoSCMKeys;
import io.harness.gitsync.common.beans.UserSourceCodeManager.UserSourceCodeManagerKeys;
import io.harness.gitsync.common.mappers.AzureRepoSCMMapper;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.SpringFilterExpander;
import io.harness.mongo.iterator.provider.SpringPersistenceProvider;
import io.harness.ng.userprofile.commons.SCMType;

import software.wings.security.authentication.oauth.OAuthConstants;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class AzureRepoSCMOAuthTokenRefresher extends AbstractSCMOAuthTokenRefresher<AzureRepoSCM> {
  @Inject
  public AzureRepoSCMOAuthTokenRefresher(
      PersistenceIteratorFactory persistenceIteratorFactory, MongoTemplate mongoTemplate) {
    this.persistenceIteratorFactory = persistenceIteratorFactory;
    this.mongoTemplate = mongoTemplate;
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

  @Override
  OAuthRef getOAuthDecrypted(AzureRepoSCM entity) {
    AzureRepoOAuthDTO azureRepoOauthDTO =
        (AzureRepoOAuthDTO) AzureRepoSCMMapper.toApiAccessDTO(entity.getApiAccessType(), entity.getAzureRepoApiAccess())
            .getSpec();
    azureRepoOauthDTO = (AzureRepoOAuthDTO) getOAuthDecryptedInternal(azureRepoOauthDTO, entity.getAccountIdentifier());
    return OAuthRef.builder()
        .tokenRef(azureRepoOauthDTO.getTokenRef())
        .refreshTokenRef(azureRepoOauthDTO.getRefreshTokenRef())
        .build();
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

  @Override
  public OAuthConfig getOAuthConfig() {
    return OAuthConfig.builder()
        .endpoint(OAuthConstants.AZURE_REPO_ENDPOINT)
        .clientSecret(configuration.getAzureRepoConfig().getClientSecret())
        .clientId(configuration.getAzureRepoConfig().getClientId())
        .build();
  }
}
