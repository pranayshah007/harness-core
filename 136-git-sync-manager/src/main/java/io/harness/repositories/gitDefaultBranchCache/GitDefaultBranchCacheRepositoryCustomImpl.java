/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.gitDefaultBranchCache;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.caching.entity.GitDefaultBranchCache;

import com.google.inject.Inject;
import com.mongodb.client.result.DeleteResult;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.PIPELINE)
public class GitDefaultBranchCacheRepositoryCustomImpl implements GitDefaultBranchCacheRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  public GitDefaultBranchCache upsert(Criteria criteria, Update update) {
    Query query = new Query(criteria);
    return mongoTemplate.findAndModify(
        query, update, new FindAndModifyOptions().returnNew(true).upsert(true), GitDefaultBranchCache.class);
  }

  public DeleteResult delete(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.remove(query, GitDefaultBranchCache.class);
  }

  @Override
  public List<GitDefaultBranchCache> list(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.find(query, GitDefaultBranchCache.class);
  }
}
