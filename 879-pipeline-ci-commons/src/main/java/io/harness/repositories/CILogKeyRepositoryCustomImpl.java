/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories;

import io.harness.beans.steps.CILogKeyMetadata;

import com.google.inject.Inject;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
public class CILogKeyRepositoryCustomImpl implements CILogKeyRepositoryCustom {
  MongoTemplate mongoTemplate;

  @Override
  public void appendLogKeys(String stageExecutionId, List<String> newLogKeys) {
    Query query = new Query(Criteria.where("stageExecutionId").is(stageExecutionId));
    Update update = new Update().addToSet("logKeys").each(newLogKeys);
    mongoTemplate.updateFirst(query, update, CILogKeyMetadata.class);
  }
}
