/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.fullSync;

import static io.harness.annotations.dev.HarnessTeam.DX;

import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.core.fullsync.entity.GitFullSyncJob;

import com.google.inject.Inject;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(DX)
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
public class FullSyncJobRepositoryCustomImpl implements FullSyncJobRepositoryCustom {
  MongoTemplate mongoTemplate;

  @Override
  public UpdateResult update(Criteria criteria, Update update) {
    Query query = query(criteria);
    return mongoTemplate.updateFirst(query, update, GitFullSyncJob.class);
  }

  @Override
  public GitFullSyncJob find(Criteria criteria) {
    Query query = query(criteria);
    return mongoTemplate.findOne(query, GitFullSyncJob.class);
  }

  @Override
  public DeleteResult deleteAll(Criteria criteria) {
    return mongoTemplate.remove(new Query(criteria), GitFullSyncJob.class);
  }
}
