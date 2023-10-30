/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.gitxwebhook;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.gitsync.gitxwebhooks.entity.GitXWebhookEvent;

import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITX})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @com.google.inject.Inject }))
public class GitXWebhookEventsRepositoryCustomImpl implements GitXWebhookEventsRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public GitXWebhookEvent create(GitXWebhookEvent gitXWebhookEvent) {
    return mongoTemplate.save(gitXWebhookEvent);
  }

  @Override
  public List<GitXWebhookEvent> list(Query query) {
    return mongoTemplate.find(query, GitXWebhookEvent.class);
  }

  @Override
  public GitXWebhookEvent update(Query query, Update update) {
    return mongoTemplate.findAndModify(
        query, update, new FindAndModifyOptions().returnNew(true).upsert(true), GitXWebhookEvent.class);
  }
}
