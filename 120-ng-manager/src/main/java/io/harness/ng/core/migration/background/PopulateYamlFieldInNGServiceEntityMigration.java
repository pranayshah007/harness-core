/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.background;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.account.utils.AccountUtils;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.NGMigration;
import io.harness.mongo.MongoPersistence;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.entity.ServiceEntity.ServiceEntityKeys;
import io.harness.ng.core.service.mappers.NGServiceEntityMapper;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.persistence.HIterator;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class PopulateYamlFieldInNGServiceEntityMigration implements NGMigration {
  @Inject private MongoPersistence mongoPersistence;
  @Inject private MongoTemplate mongoTemplate;
  @Inject private AccountUtils accountUtils;
  private static final String DEBUG_LOG = "[PopulateYamlFieldInNGServiceEntityMigration]: ";
  @Override
  public void migrate() {
    try {
      log.info(DEBUG_LOG + "Starting migration of populating yaml field in service entity");
      List<String> accountIdentifiers = accountUtils.getAllNGAccountIds();

      accountIdentifiers.forEach(accountId -> {
        try {
          log.info(
              DEBUG_LOG + "Starting migration of populating yaml field in service entity for account : " + accountId);
          Query<ServiceEntity> serviceQuery =
              mongoPersistence.createQuery(ServiceEntity.class).filter(ServiceEntityKeys.accountId, accountId);

          try (HIterator<ServiceEntity> iterator = new HIterator<>(serviceQuery.fetch())) {
            for (ServiceEntity svcEntity : iterator) {
              if (isBlank(svcEntity.getYaml())) {
                try {
                  NGServiceConfig ngServiceConfig = NGServiceEntityMapper.toNGServiceConfig(svcEntity);
                  String yaml = NGServiceEntityMapper.toYaml(ngServiceConfig);

                  Criteria criteria = Criteria.where(ServiceEntityKeys.id).is(svcEntity.getId());
                  org.springframework.data.mongodb.core.query.Query query =
                      new org.springframework.data.mongodb.core.query.Query(criteria);
                  Update update = new Update();
                  update.set(ServiceEntityKeys.yaml, yaml);

                  mongoTemplate.findAndModify(
                      query, update, new FindAndModifyOptions().returnNew(true), ServiceEntity.class);
                } catch (Exception e) {
                  log.info(String.format(DEBUG_LOG
                          + "Migration of populating yaml failed for serviceIdentifier: [%s], serviceName: [%s], projectId: [%s], orgId: [%s], accountId: [%s]",
                      svcEntity.getIdentifier(), svcEntity.getName(), svcEntity.getProjectIdentifier(),
                      svcEntity.getOrgIdentifier(), svcEntity.getAccountId()));
                }
              }
            }
          }
          log.info(
              DEBUG_LOG + "Migration of populating yaml field in service entity completed for account : " + accountId);
        } catch (Exception e) {
          log.error(
              DEBUG_LOG + "Migration of populating yaml field in service entity failed for account: " + accountId, e);
        }
      });
      log.info(DEBUG_LOG + "Migration of populating yaml field in service entity completed");
    } catch (Exception e) {
      log.error(DEBUG_LOG + "Migration of populating yaml field in service entity failed.", e);
    }
  }
}
