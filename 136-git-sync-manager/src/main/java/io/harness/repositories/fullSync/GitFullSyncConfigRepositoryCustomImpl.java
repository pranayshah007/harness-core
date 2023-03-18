/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.fullSync;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.core.beans.GitFullSyncConfig;

import com.google.inject.Inject;
import com.mongodb.client.result.DeleteResult;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(PL)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class GitFullSyncConfigRepositoryCustomImpl implements GitFullSyncConfigRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public GitFullSyncConfig update(Criteria criteria, Update update) {
    return mongoTemplate.findAndModify(
        new Query(criteria), update, FindAndModifyOptions.options().returnNew(true), GitFullSyncConfig.class);
  }

  @Override
  public DeleteResult deleteAll(Criteria criteria) {
    return mongoTemplate.remove(new Query(criteria), GitFullSyncConfig.class);
  }
}
