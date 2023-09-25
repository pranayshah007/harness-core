/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.pipeline.PipelineEntity;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@Slf4j
@OwnedBy(PIPELINE)
@Singleton
public class PipelineEntityReadHelper {
  @Inject @Named("secondary-mongo") public MongoTemplate secondaryMongoTemplate;

  public long findCount(Query query) {
    return secondaryMongoTemplate.count(Query.of(query).limit(-1).skip(-1), PipelineEntity.class);
  }

  public List<String> findAllIdentifiers(Criteria criteria, Pageable pageable) {
    List<PipelineEntity> pipelineEntities =
        secondaryMongoTemplate.find(new Query(criteria).with(pageable), PipelineEntity.class);
    return pipelineEntities.stream().map(PipelineEntity::getIdentifier).collect(Collectors.toList());
  }
}
