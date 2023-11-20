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
import io.harness.ng.core.entities.Project.ProjectKeys;

import com.google.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Collation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.repository.support.PageableExecutionUtils;

@OwnedBy(PL)
@AllArgsConstructor(access = AccessLevel.PROTECTED, onConstructor = @__({ @Inject }))
public class ProjectRepositoryCustomImpl implements ProjectRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public Page<Project> findAll(Criteria criteria, Pageable pageable) {
    Query query = new Query(criteria).with(pageable);
    query.collation(Collation.of("en").strength(Collation.ComparisonLevel.secondary()));
    List<Project> projects = mongoTemplate.find(query, Project.class);
    return PageableExecutionUtils.getPage(
        projects, pageable, () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), Project.class));
  }

  @Override
  public List<Scope> findAllProjects(Criteria criteria) {
    Query query = new Query(criteria);
    query.fields().include(ProjectKeys.identifier);
    query.fields().include(ProjectKeys.orgIdentifier);
    query.fields().include(ProjectKeys.accountIdentifier);
    return mongoTemplate.find(query, Project.class)
        .stream()
        .map(project
            -> Scope.builder()
                   .accountIdentifier(project.getAccountIdentifier())
                   .orgIdentifier(project.getOrgIdentifier())
                   .projectIdentifier(project.getIdentifier())
                   .build())
        .collect(Collectors.toList());
  }

  @Override
  public List<Project> findAll(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.find(query, Project.class);
  }

  @Override
  public Project restore(String accountIdentifier, String orgIdentifier, String identifier) {
    Criteria criteria = Criteria.where(ProjectKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(ProjectKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(ProjectKeys.identifier)
                            .is(identifier)
                            .and(ProjectKeys.deleted)
                            .is(Boolean.TRUE);
    Query query = new Query(criteria);
    Update update = new Update().set(ProjectKeys.deleted, Boolean.FALSE);
    return mongoTemplate.findAndModify(query, update, Project.class);
  }

  @Override
  public Project update(Query query, Update update) {
    return mongoTemplate.findAndModify(query, update, new FindAndModifyOptions().returnNew(true), Project.class);
  }

  public Project hardDelete(String accountIdentifier, String orgIdentifier, String identifier, Long version) {
    Criteria criteria = Criteria.where(ProjectKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(ProjectKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(ProjectKeys.identifier)
                            .is(identifier);
    if (version != null) {
      criteria.and(ProjectKeys.version).is(version);
    }
    Query query = new Query(criteria);
    return mongoTemplate.findAndRemove(query, Project.class);
  }

  @Override
  public Project delete(String accountIdentifier, String orgIdentifier, String identifier, Long version) {
    Criteria criteria = Criteria.where(ProjectKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(ProjectKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(ProjectKeys.identifier)
                            .is(identifier)
                            .and(ProjectKeys.deleted)
                            .ne(Boolean.TRUE);
    if (version != null) {
      criteria.and(ProjectKeys.version).is(version);
    }
    Query query = new Query(criteria);
    Update update = new Update().set(ProjectKeys.deleted, Boolean.TRUE);
    return mongoTemplate.findAndModify(query, update, Project.class);
  }

  @Override
  public <T> AggregationResults<T> aggregate(Aggregation aggregation, Class<T> classToFillResultIn) {
    return mongoTemplate.aggregate(aggregation, Project.class, classToFillResultIn);
  }

  @Override
  public long count(Criteria criteria) {
    return mongoTemplate.count(new Query(criteria), Project.class);
  }
}
