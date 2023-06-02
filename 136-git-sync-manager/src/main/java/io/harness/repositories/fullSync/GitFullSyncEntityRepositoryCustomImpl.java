/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.fullSync;

import static io.harness.annotations.dev.HarnessTeam.PL;

import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.core.beans.GitFullSyncEntityInfo;

import com.google.inject.Inject;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.repository.support.PageableExecutionUtils;

@OwnedBy(PL)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class GitFullSyncEntityRepositoryCustomImpl implements GitFullSyncEntityRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public UpdateResult update(Criteria criteria, Update update) {
    Query query = query(criteria);
    return mongoTemplate.updateFirst(query, update, GitFullSyncEntityInfo.class);
  }

  @Override
  public Page<GitFullSyncEntityInfo> findAll(Criteria criteria, Pageable pageable) {
    Query query = new Query(criteria).with(pageable);
    List<GitFullSyncEntityInfo> pagedlist = mongoTemplate.find(query, GitFullSyncEntityInfo.class);
    return PageableExecutionUtils.getPage(pagedlist, pageable,
        () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), GitFullSyncEntityInfo.class));
  }

  @Override
  public long count(Criteria criteria) {
    return mongoTemplate.count(new Query(criteria), GitFullSyncEntityInfo.class);
  }

  @Override
  public List<GitFullSyncEntityInfo> findAll(Criteria criteria) {
    return mongoTemplate.find(new Query(criteria), GitFullSyncEntityInfo.class);
  }

  @Override
  public DeleteResult deleteAll(Criteria criteria) {
    return mongoTemplate.remove(new Query(criteria), GitFullSyncEntityInfo.class);
  }
}
