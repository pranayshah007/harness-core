/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.gitxwebhook;

import io.harness.gitsync.gitxwebhooks.entity.GitXWebhookEvent;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @com.google.inject.Inject }))
public class GitXWebhookEventsRepositoryCustomImpl implements GitXWebhookEventsRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public GitXWebhookEvent create(GitXWebhookEvent gitXWebhookEvent) {
    return mongoTemplate.save(gitXWebhookEvent);
  }
}
