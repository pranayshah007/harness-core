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
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabOauthDTO;
import io.harness.gitsync.common.beans.GitlabSCM;
import io.harness.gitsync.common.beans.GitlabSCM.GitlabSCMKeys;
import io.harness.gitsync.common.beans.UserSourceCodeManager.UserSourceCodeManagerKeys;
import io.harness.gitsync.common.mappers.GitlabSCMMapper;
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
public class GitlabSCMOAuthTokenRefresher extends AbstractSCMOAuthTokenRefresher<GitlabSCM> {
  @Inject
  public GitlabSCMOAuthTokenRefresher(
      PersistenceIteratorFactory persistenceIteratorFactory, MongoTemplate mongoTemplate) {
    this.persistenceIteratorFactory = persistenceIteratorFactory;
    this.mongoTemplate = mongoTemplate;
  }

  public void registerIterators(int threadPoolSize) {
    String clientId = configuration.getGitlabConfig().getClientId();
    String clientSecret = configuration.getGitlabConfig().getClientSecret();

    String clientIdShort = clientId.substring(0, Math.min(clientId.length(), 3));
    String clientSecretShort = clientSecret.substring(0, Math.min(clientSecret.length(), 3));

    log.info("Register Enabled:{}, Frequency:{}, clientID short:{}, clientSecret short:{}",
        configuration.isOauthRefreshEnabled(), configuration.getOauthRefreshFrequency(), clientIdShort,
        clientSecretShort);

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

  @Override
  OAuthRef getOAuthDecrypted(GitlabSCM entity) {
    GitlabOauthDTO gitlabOauthDTO =
        (GitlabOauthDTO) GitlabSCMMapper.toApiAccessDTO(entity.getApiAccessType(), entity.getGitlabApiAccess())
            .getSpec();
    gitlabOauthDTO = (GitlabOauthDTO) getOAuthDecryptedInternal(gitlabOauthDTO, entity.getAccountIdentifier());
    return OAuthRef.builder()
        .tokenRef(gitlabOauthDTO.getTokenRef())
        .refreshTokenRef(gitlabOauthDTO.getRefreshTokenRef())
        .build();
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

  @Override
  public OAuthConfig getOAuthConfig() {
    return OAuthConfig.builder()
        .endpoint(OAuthConstants.GITLAB_ENDPOINT)
        .clientSecret(configuration.getGitlabConfig().getClientSecret())
        .clientId(configuration.getGitlabConfig().getClientId())
        .build();
  }
}
