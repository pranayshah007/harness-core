/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.repositories;

import io.harness.mongo.helper.SecondaryMongoTemplateHolder;
import io.harness.plan.NodeEntity;

import com.google.inject.Inject;
import com.mongodb.client.result.DeleteResult;
import java.util.List;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

public class NodeEntityRepositoryCustomImpl implements NodeEntityRepositoryCustom {
  private final MongoTemplate mongoTemplate;
  private final MongoTemplate secondaryMongoTemplate;

  @Inject
  public NodeEntityRepositoryCustomImpl(
      MongoTemplate mongoTemplate, SecondaryMongoTemplateHolder secondaryMongoTemplate) {
    this.mongoTemplate = mongoTemplate;
    this.secondaryMongoTemplate = secondaryMongoTemplate.getSecondaryMongoTemplate();
  }

  // These methods will be using the index planNodeId_nodeId .
  @Override
  public List<NodeEntity> findByPlanIdAndNodeIds(Query query) {
    return secondaryMongoTemplate.find(query, NodeEntity.class);
  }

  @Override
  public DeleteResult deleteAllByPlanIdNodeIds(Query query) {
    return mongoTemplate.remove(query, NodeEntity.class);
  }
}
