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

import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketApiAccessType;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketOAuthDTO;
import io.harness.gitsync.common.beans.BitbucketSCM;
import io.harness.gitsync.common.beans.BitbucketSCM.BitbucketSCMKeys;
import io.harness.gitsync.common.beans.UserSourceCodeManager.UserSourceCodeManagerKeys;
import io.harness.gitsync.common.mappers.BitbucketSCMMapper;
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
public class BitbucketSCMOAuthTokenRefresher extends AbstractSCMOAuthTokenRefresher<BitbucketSCM> {
  @Inject
  public BitbucketSCMOAuthTokenRefresher(
      PersistenceIteratorFactory persistenceIteratorFactory, MongoTemplate mongoTemplate) {
    this.persistenceIteratorFactory = persistenceIteratorFactory;
    this.mongoTemplate = mongoTemplate;
  }

  public void registerIterators(int threadPoolSize) {
    log.info("Register Enabled:{}, Frequency:{}, clientID:{}, clientSecret:{}", configuration.isOauthRefreshEnabled(),
        configuration.getOauthRefreshFrequency(), configuration.getBitbucketConfig().getClientId(),
        configuration.getBitbucketConfig().getClientSecret());

    if (configuration.isOauthRefreshEnabled()) {
      SpringFilterExpander springFilterExpander = getFilterQuery();

      persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
          PersistenceIteratorFactory.PumpExecutorOptions.builder()
              .name(this.getClass().getName())
              .poolSize(threadPoolSize)
              .interval(ofSeconds(10))
              .build(),
          BitbucketSCM.class,
          MongoPersistenceIterator.<BitbucketSCM, SpringFilterExpander>builder()
              .clazz(BitbucketSCM.class)
              .fieldName(BitbucketSCMKeys.nextTokenRenewIteration)
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
      Criteria criteria = Criteria.where(UserSourceCodeManagerKeys.type)
                              .is(SCMType.BITBUCKET)
                              .and(BitbucketSCMKeys.apiAccessType)
                              .is(BitbucketApiAccessType.OAUTH);

      query.addCriteria(criteria);
    };
  }

  @Override
  public OAuthRef getOAuthDecrypted(BitbucketSCM entity) {
    BitbucketOAuthDTO bitbucketOAuthDTO =
        (BitbucketOAuthDTO) BitbucketSCMMapper.toApiAccessDTO(entity.getApiAccessType(), entity.getBitbucketApiAccess())
            .getSpec();
    bitbucketOAuthDTO = (BitbucketOAuthDTO) getOAuthDecryptedInternal(bitbucketOAuthDTO, entity.getAccountIdentifier());
    return OAuthRef.builder()
        .tokenRef(bitbucketOAuthDTO.getTokenRef())
        .refreshTokenRef(bitbucketOAuthDTO.getRefreshTokenRef())
        .build();
  }

  @Override
  public OAuthConfig getOAuthConfig() {
    return OAuthConfig.builder()
        .endpoint(OAuthConstants.BITBUCKET_ENDPOINT)
        .clientSecret(configuration.getBitbucketConfig().getClientSecret())
        .clientId(configuration.getBitbucketConfig().getClientId())
        .build();
  }
}
