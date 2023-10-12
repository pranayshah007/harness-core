/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.mongo.helper.SecondaryMongoTemplateHolder;
import io.harness.pms.pipeline.PipelineEntity;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
@OwnedBy(PIPELINE)
@Singleton
public class PipelineEntityReadHelper {
  public static final int MAX_BATCH_SIZE = 10000;
  private final MongoTemplate secondaryMongoTemplate;

  @Inject
  public PipelineEntityReadHelper(SecondaryMongoTemplateHolder secondaryMongoTemplateHolder) {
    this.secondaryMongoTemplate = secondaryMongoTemplateHolder.getSecondaryMongoTemplate();
  }

  public long findCount(Query query) {
    return secondaryMongoTemplate.count(Query.of(query).limit(-1).skip(-1), PipelineEntity.class);
  }

  public List<String> findAllIdentifiers(Criteria criteria) {
    List<PipelineEntity> pipelineEntities =
        secondaryMongoTemplate.find(new Query(criteria).limit(MAX_BATCH_SIZE), PipelineEntity.class);
    return pipelineEntities.stream().map(PipelineEntity::getIdentifier).collect(Collectors.toList());
  }
}
