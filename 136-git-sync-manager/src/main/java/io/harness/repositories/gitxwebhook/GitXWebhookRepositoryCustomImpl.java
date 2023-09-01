/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.gitxwebhook;

import io.harness.gitsync.gitxwebhooks.entity.GitXWebhook;

import com.mongodb.client.result.DeleteResult;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @com.google.inject.Inject }))
public class GitXWebhookRepositoryCustomImpl implements GitXWebhookRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public GitXWebhook create(GitXWebhook gitXWebhook) {
    return mongoTemplate.save(gitXWebhook);
  }

  @Override
  public DeleteResult delete(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.remove(query, GitXWebhook.class);
  }

  @Override
  public List<GitXWebhook> list(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.find(query, GitXWebhook.class);
  }

  @Override
  public GitXWebhook update(Query query, Update update) {
    return mongoTemplate.findAndModify(
        query, update, new FindAndModifyOptions().returnNew(true).upsert(true), GitXWebhook.class);
  }
}
