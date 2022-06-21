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
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.entities.Organization.OrganizationKeys;

import com.google.inject.Inject;
import com.mongodb.client.result.UpdateResult;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Collation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.repository.support.PageableExecutionUtils;

@OwnedBy(PL)
@AllArgsConstructor(access = AccessLevel.PROTECTED, onConstructor = @__({ @Inject }))
public class OrganizationRepositoryCustomImpl implements OrganizationRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public Page<Organization> findAll(Criteria criteria, Pageable pageable, boolean ignoreCase) {
    Query query = new Query(criteria).with(pageable);
    if (ignoreCase) {
      query.collation(Collation.of("en").strength(Collation.ComparisonLevel.primary()));
    }
    List<Organization> organizations = mongoTemplate.find(query, Organization.class);
    return PageableExecutionUtils.getPage(
        organizations, pageable, () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), Organization.class));
  }

  @Override
  public List<String> findDistinctAccounts() {
    return mongoTemplate.findDistinct(
        new Query(), OrganizationKeys.accountIdentifier, Organization.class, String.class);
  }

  @Override
  public List<Organization> findAll(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.find(query, Organization.class);
  }

  @Override
  public Organization restore(String accountIdentifier, String identifier) {
    Criteria criteria = Criteria.where(OrganizationKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(OrganizationKeys.identifier)
                            .is(identifier)
                            .and(OrganizationKeys.deleted)
                            .is(Boolean.TRUE);
    Query query = new Query(criteria);
    Update update = new Update().set(OrganizationKeys.deleted, Boolean.FALSE);
    return mongoTemplate.findAndModify(query, update, Organization.class);
  }

  @Override
  public List<Scope> findAllOrgs(Criteria criteria) {
    Query query = new Query(criteria);
    query.fields().include(OrganizationKeys.identifier);
    query.fields().include(OrganizationKeys.accountIdentifier);
    return mongoTemplate.find(query, Organization.class)
        .stream()
        .map(org
            -> Scope.builder().accountIdentifier(org.getAccountIdentifier()).orgIdentifier(org.getIdentifier()).build())
        .collect(Collectors.toList());
  }

  @Override
  public Organization update(Query query, Update update) {
    return mongoTemplate.findAndModify(query, update, new FindAndModifyOptions().returnNew(true), Organization.class);
  }

  @Override
  public Organization delete(String accountIdentifier, String identifier, Long version) {
    Criteria criteria = Criteria.where(OrganizationKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(OrganizationKeys.identifier)
                            .is(identifier)
                            .and(OrganizationKeys.deleted)
                            .ne(Boolean.TRUE);
    if (version != null) {
      criteria.and(OrganizationKeys.version).is(version);
    }
    Query query = new Query(criteria);
    Update update = new Update().set(OrganizationKeys.deleted, Boolean.TRUE);
    return mongoTemplate.findAndModify(query, update, Organization.class);
  }

  @Override
  public UpdateResult updateMultiple(Query query, Update update) {
    return mongoTemplate.updateMulti(query, update, Organization.class);
  }
}
