/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.background;

import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.account.utils.AccountUtils;
import io.harness.migration.NGMigration;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity.NGServiceOverridesEntityKeys;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.mongodb.client.result.UpdateResult;
import dev.morphia.query.Query;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@Slf4j
public class AddServiceOverrideV2RelatedFieldsMigration implements NGMigration {
  @Inject private HPersistence persistence;
  @Inject private MongoTemplate mongoTemplate;
  @Inject private AccountUtils accountUtils;
  private static final String DEBUG_LOG = "[AddServiceOverrideV2RelatedFieldsMigration]: ";

  @Override
  public void migrate() {
    try {
      log.info(DEBUG_LOG + "Staring migration to add Service Override V2 related fields");
      List<String> allNGAccountIds = accountUtils.getAllNGAccountIds();
      for (String accountId : allNGAccountIds) {
        Query<NGServiceOverridesEntity> serviceOverridesEntityQuery =
            persistence.createQuery(NGServiceOverridesEntity.class, excludeAuthority)
                .filter(NGServiceOverridesEntityKeys.accountId, accountId);

        try (HIterator<NGServiceOverridesEntity> iterator = new HIterator<>(serviceOverridesEntityQuery.fetch())) {
          for (NGServiceOverridesEntity overridesEntity : iterator) {
            try {
              Criteria criteria = new Criteria().and(NGServiceOverridesEntityKeys.id).is(overridesEntity.getId());
              org.springframework.data.mongodb.core.query.Query query =
                  new org.springframework.data.mongodb.core.query.Query(criteria);
              Update update = new Update();
              update.set(NGServiceOverridesEntityKeys.identifier, generateServiceOverrideIdentifier(overridesEntity));
              update.set(NGServiceOverridesEntityKeys.type, ServiceOverridesType.ENV_SERVICE_OVERRIDE);
              UpdateResult updateResult = mongoTemplate.updateFirst(query, update, NGServiceOverridesEntity.class);
              if (updateResult.getModifiedCount() == 0L) {
                log.error(String.format(DEBUG_LOG
                        + "Couldn't update for override with environmentRef: [%s], serviceRef: [%s], projectId: [%s], orgId: [%s]",
                    overridesEntity.getEnvironmentRef(), overridesEntity.getServiceRef(),
                    overridesEntity.getProjectIdentifier(), overridesEntity.getOrgIdentifier()));
              }
            } catch (Exception e) {
              log.error(
                  String.format(DEBUG_LOG
                          + "Migration failed for override with environmentRef: [%s], serviceRef: [%s], projectId: [%s], orgId: [%s]",
                      overridesEntity.getEnvironmentRef(), overridesEntity.getServiceRef(),
                      overridesEntity.getProjectIdentifier(), overridesEntity.getOrgIdentifier()),
                  e);
            }
          }
        } catch (Exception e) {
          log.error(DEBUG_LOG + "Migration failed for accountId: " + accountId, e);
        }
      }

    } catch (Exception e) {
      log.error(DEBUG_LOG + "Migration failed", e);
    }
  }
  public String generateServiceOverrideIdentifier(NGServiceOverridesEntity serviceOverridesEntity) {
    return String.join("_", serviceOverridesEntity.getEnvironmentRef(), serviceOverridesEntity.getServiceRef())
        .replace(".", "_");
  }
}
