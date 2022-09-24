/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.core.custom;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.ng.core.entities.Project;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(PL)
public interface ProjectRepositoryCustom {
  Page<Project> findAll(Criteria criteria, Pageable pageable);

  List<Scope> findAllProjects(Criteria criteria);

  Project update(Query query, Update update);

  boolean hardDelete(String accountIdentifier, String orgIdentifier, String identifier, Long version);

  Project delete(String accountIdentifier, String orgIdentifier, String identifier, Long version);

  List<Project> findAll(Criteria criteria);

  Project restore(String accountIdentifier, String orgIdentifier, String identifier);

  <T> AggregationResults<T> aggregate(Aggregation aggregation, Class<T> classToFillResultIn);

  long count(Criteria criteria);
}
