/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.steps.approval.step.entities.ApprovalInstance;

import com.mongodb.client.result.UpdateResult;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
@Slf4j
@OwnedBy(HarnessTeam.CDC)
@HarnessRepo
public class ApprovalInstanceCustomRepositoryImpl implements ApprovalInstanceCustomRepository {
  private final MongoTemplate mongoTemplate;

  @Autowired
  public ApprovalInstanceCustomRepositoryImpl(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  @Override
  public ApprovalInstance updateFirst(Query query, Update update) {
    return mongoTemplate.findAndModify(
        query, update, new FindAndModifyOptions().returnNew(true), ApprovalInstance.class);
  }

  @Override
  public UpdateResult updateMulti(Query query, Update update) {
    return mongoTemplate.updateMulti(query, update, ApprovalInstance.class);
  }

  @Override
  public List<ApprovalInstance> findAll(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.find(query, ApprovalInstance.class);
  }

  @Override
  public List<ApprovalInstance> find(Query query) {
    return mongoTemplate.find(query, ApprovalInstance.class);
  }
}
