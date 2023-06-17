/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.variable.custom;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.variable.entity.Variable;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.repository.support.PageableExecutionUtils;

@OwnedBy(PL)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Singleton
public class VariableRepositoryCustomImpl implements VariableRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public Page<Variable> findAll(Criteria criteria, Pageable pageable) {
    Query query = new Query(criteria).with(pageable);
    List<Variable> variables = mongoTemplate.find(query, Variable.class);

    return PageableExecutionUtils.getPage(
        variables, pageable, () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), Variable.class));
  }

  @Override
  public List<Variable> findAll(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.find(query, Variable.class);
  }
}
