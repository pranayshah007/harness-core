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
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.entities.DeploymentSummary;
import io.harness.migration.NGMigration;
import io.harness.repositories.deploymentsummary.DeploymentSummaryRepository;

import com.google.inject.Inject;
import java.util.HashSet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.util.CloseableIterator;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class CleanupDeploymentSummaryNg implements NGMigration {
  @Inject private AccountUtils accountUtils;
  @Inject private MongoTemplate mongoTemplate;
  @Inject private DeploymentSummaryRepository deploymentSummaryRepository;

  private static final String DEBUG_LOG = "[CleanupDeploymentSummaryNg]: ";
  private static final int BATCH_SIZE = 10000;

  @Override
  public void migrate() {
    log.info(DEBUG_LOG + "Starting deletion of DeploymentSummaryNG entries for deleted accounts");
    int iterationCounter = 0;
    int deletionCounter = 0;

    HashSet<String> existingAccounts = new HashSet<>(accountUtils.getAllNGAccountIds());
    Query query = new Query(new Criteria()).limit(NO_LIMIT).cursorBatchSize(BATCH_SIZE);
    try (CloseableIterator<DeploymentSummary> iterator = mongoTemplate.stream(query, DeploymentSummary.class)) {
      while (iterator.hasNext()) {
        iterationCounter++;
        DeploymentSummary deploymentSummary = iterator.next();
        if (!existingAccounts.contains(deploymentSummary.getAccountIdentifier())) {
          deploymentSummaryRepository.deleteById(deploymentSummary.getId());
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
