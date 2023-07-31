/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories;

import io.harness.ssca.entities.NormalizedSBOMComponentEntity;

import com.google.inject.Inject;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class SBOMComponentRepoCustomImpl implements SBOMComponentRepoCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public List<NormalizedSBOMComponentEntity> findAllByQuery(Query query) {
    return mongoTemplate.find(query, NormalizedSBOMComponentEntity.class);
  }
}
