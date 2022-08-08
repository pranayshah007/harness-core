/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.resourcegroup.migrations;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.NGMigration;
import io.harness.resourcegroup.framework.v1.repositories.spring.ResourceGroupRepository;
import io.harness.resourcegroup.framework.v2.remote.mapper.ResourceGroupMapper;
import io.harness.resourcegroup.framework.v2.repositories.spring.ResourceGroupV2Repository;
import io.harness.resourcegroup.framework.v2.service.ResourceGroupService;
import io.harness.resourcegroup.v1.model.ResourceGroup;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.util.CloseableIterator;

@Slf4j
@OwnedBy(HarnessTeam.PL)
public class MigrationOfV1ToV2 implements NGMigration {
  private final ResourceGroupRepository resourceGroupRepository;
  private final ResourceGroupV2Repository resourceGroupV2Repository;
  public static final int BATCH_SIZE = 1000;
  private MongoTemplate mongoTemplate;
  private final ResourceGroupService resourceGroupService;

  @Inject
  public MigrationOfV1ToV2(ResourceGroupRepository resourceGroupRepository,
      ResourceGroupV2Repository resourceGroupV2Repository, MongoTemplate mongoTemplate,
      ResourceGroupService resourceGroupService) {
    this.resourceGroupRepository = resourceGroupRepository;
    this.resourceGroupV2Repository = resourceGroupV2Repository;
    this.mongoTemplate = mongoTemplate;
    this.resourceGroupService = resourceGroupService;
  }

  private CloseableIterator<ResourceGroup> runQueryWithBatch(int batchSize) {
    Query query = new Query();
    query.cursorBatchSize(batchSize);
    return mongoTemplate.stream(query, ResourceGroup.class);
  }

  @Override
  public void migrate() {
    log.info("[MigrationOfV1ToV2] starting migration....");
    try (CloseableIterator<ResourceGroup> iterator = runQueryWithBatch(BATCH_SIZE)) {
      while (iterator.hasNext()) {
        ResourceGroup resourceGroup = iterator.next();
        resourceGroupService.upsert(ResourceGroupMapper.fromV1(resourceGroup), true);
      }
    }
    log.info("[MigrationOfV1ToV2] migration successful....");
  }
}
