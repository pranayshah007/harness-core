/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.audit.repositories;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.mongo.MongoConfig.NO_LIMIT;

import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.entities.AuditSettings;
import io.harness.audit.entities.AuditSettings.AuditSettingsKeys;

import java.util.List;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(PL)
public class AuditRetentionRepositoryCustomImpl implements AuditRetentionRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  public AuditRetentionRepositoryCustomImpl(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  @Override
  public List<String> fetchDistinctAccountIdentifiers() {
    Query query = new Query().limit(NO_LIMIT);
    return mongoTemplate.findDistinct(query, AuditSettingsKeys.accountIdentifier, AuditSettings.class, String.class);
  }
}
