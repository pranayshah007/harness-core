/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.background;
import static io.harness.mongo.MongoConfig.NO_LIMIT;

import static java.lang.String.format;

import io.harness.account.utils.AccountUtils;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.entities.Instance;
import io.harness.entities.Instance.InstanceKeys;
import io.harness.migration.NGMigration;
import io.harness.repositories.instance.InstanceRepository;

import com.google.inject.Inject;
import java.util.HashSet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.util.CloseableIterator;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class CleanupInstanceNg implements NGMigration {
  @Inject private AccountUtils accountUtils;
  @Inject private MongoTemplate mongoTemplate;
  @Inject private InstanceRepository repository;

  private static final String DEBUG_LOG = "[CleanupInstanceNg]: ";
  private static final int BATCH_SIZE = 10000;

  @Override
  public void migrate() {
    log.info(DEBUG_LOG + "Starting deletion of InstanceNg entries for deleted accounts");
    int iterationCounter = 0;
    int deletionCounter = 0;

    HashSet<String> existingAccounts = new HashSet<>(accountUtils.getAllNGAccountIds());
    Query query = new Query(new Criteria()).limit(NO_LIMIT).cursorBatchSize(BATCH_SIZE);
    query.fields().include("_id", InstanceKeys.accountIdentifier, InstanceKeys.instanceType,
        InstanceKeys.lastDeployedAt, InstanceKeys.isDeleted, InstanceKeys.deletedAt);
    try (CloseableIterator<Instance> iterator = mongoTemplate.stream(query, Instance.class)) {
      while (iterator.hasNext()) {
        iterationCounter++;
        Instance instance = iterator.next();
        if (!existingAccounts.contains(instance.getAccountIdentifier())) {
          repository.deleteById(instance.getId());
          deletionCounter++;
        }
      }
    } catch (Exception e) {
      log.error(format("%s Migration has failed. Iterated through %s entries, and deleted %s.", DEBUG_LOG,
                    iterationCounter, deletionCounter),
          e);
    }
    log.info(format("%s Migration was successful. Iterated through %s entries, and deleted %s.", DEBUG_LOG,
        iterationCounter, deletionCounter));
  }
}