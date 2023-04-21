/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.account.AccountClient;
import io.harness.migration.NGMigration;
import io.harness.mongo.MongoPersistence;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.Environment.EnvironmentKeys;
import io.harness.ng.core.environment.mappers.EnvironmentMapper;
import io.harness.ng.core.environment.yaml.NGEnvironmentConfig;
import io.harness.persistence.HIterator;
import io.harness.remote.client.CGRestUtils;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@Slf4j
public class PopulateYamlFieldInNGEnvironmentMigration implements NGMigration {
  @Inject private MongoPersistence mongoPersistence;
  @Inject private MongoTemplate mongoTemplate;
  @Inject private AccountClient accountClient;
  private static final String DEBUG_LOG = "[PopulateYamlFieldInNGEnvironmentMigration]: ";
  @Override
  public void migrate() {
    try {
      log.info(DEBUG_LOG + "Starting migration of populating yaml field in environment");
      List<AccountDTO> allAccounts = CGRestUtils.getResponse(accountClient.getAllAccounts());
      List<String> accountIdentifiers = allAccounts.stream()
                                            .filter(AccountDTO::isNextGenEnabled)
                                            .map(AccountDTO::getIdentifier)
                                            .collect(Collectors.toList());

      accountIdentifiers.forEach(accountId -> {
        try {
          log.info(DEBUG_LOG + "Starting migration of populating yaml field in environment for account : " + accountId);
          Query<Environment> environmentQuery =
              mongoPersistence.createQuery(Environment.class).filter(EnvironmentKeys.accountId, accountId);

          try (HIterator<Environment> iterator = new HIterator<>(environmentQuery.fetch())) {
            for (Environment envEntity : iterator) {
              if (isBlank(envEntity.getYaml())) {
                try {
                  NGEnvironmentConfig ngEnvironmentConfig = EnvironmentMapper.toNGEnvironmentConfig(envEntity);
                  String yaml = EnvironmentMapper.toYaml(ngEnvironmentConfig);

                  Criteria criteria = Criteria.where(EnvironmentKeys.id).is(envEntity.getId());
                  org.springframework.data.mongodb.core.query.Query query =
                      new org.springframework.data.mongodb.core.query.Query(criteria);
                  Update update = new Update();
                  update.set(EnvironmentKeys.yaml, yaml);

                  mongoTemplate.findAndModify(
                      query, update, new FindAndModifyOptions().returnNew(true), Environment.class);
                } catch (Exception e) {
                  log.info(
                      String.format(DEBUG_LOG
                              + "Migration of populating yaml failed for envIdentifier: [%s], envName: [%s], projectId: [%s], orgId: [%s], accountId: [%s]",
                          envEntity.getIdentifier(), envEntity.getName(), envEntity.getProjectIdentifier(),
                          envEntity.getOrgIdentifier(), envEntity.getAccountId()),
                      e);
                }
              }
            }
          }
          log.info(
              DEBUG_LOG + "Migration of populating yaml field in environment completed for account : " + accountId);
        } catch (Exception e) {
          log.error(
              DEBUG_LOG + "Migration of populating yaml field in environment failed for account: " + accountId, e);
        }
      });
      log.info(DEBUG_LOG + "Migration of populating yaml field in environment completed");
    } catch (Exception e) {
      log.error(DEBUG_LOG + "Migration of populating yaml field in environment failed.", e);
    }
  }
}
