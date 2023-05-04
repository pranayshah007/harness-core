/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories;

import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.mongo.helper.SecondaryMongoTemplateHolder;

import com.google.inject.Inject;
import net.jodah.failsafe.Failsafe;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.PIPELINE)
public class PlanExecutionMetadataRepositoryCustomImpl implements PlanExecutionMetadataRepositoryCustom {
  private final MongoTemplate secondaryMongoTemplate;

  @Inject
  public PlanExecutionMetadataRepositoryCustomImpl(SecondaryMongoTemplateHolder secondaryMongoTemplateHolder) {
    this.secondaryMongoTemplate = secondaryMongoTemplateHolder.getSecondaryMongoTemplate();
  }

  @Override
  public PlanExecutionMetadata getExecutionNotes(Criteria criteria) {
    Query query = new Query(criteria);
    query.fields().include("notes");
    return secondaryMongoTemplate.findOne(query, PlanExecutionMetadata.class);
  }

  @Override
  public PlanExecutionMetadata updateExecutionNotes(Criteria criteria, Update update) {
    Query query = new Query(criteria);
    return Failsafe.with(DEFAULT_RETRY_POLICY)
        .get(()
                 -> secondaryMongoTemplate.findAndModify(
                     query, update, new FindAndModifyOptions().returnNew(true), PlanExecutionMetadata.class));
  }
}
