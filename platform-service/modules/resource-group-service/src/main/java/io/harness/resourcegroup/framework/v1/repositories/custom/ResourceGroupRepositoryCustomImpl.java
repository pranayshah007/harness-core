/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.resourcegroup.framework.v1.repositories.custom;

import io.harness.resourcegroup.v1.model.ResourceGroup;

import com.google.inject.Inject;
import com.mongodb.client.result.DeleteResult;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.repository.support.PageableExecutionUtils;

@AllArgsConstructor(access = AccessLevel.PROTECTED, onConstructor = @__({ @Inject }))
public class ResourceGroupRepositoryCustomImpl implements ResourceGroupRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public Page<ResourceGroup> findAll(Criteria criteria, Pageable pageable) {
    Query query = new Query(criteria).with(pageable);
    List<ResourceGroup> resourceGroups = mongoTemplate.find(query, ResourceGroup.class);
    return PageableExecutionUtils.getPage(
        resourceGroups, pageable, () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), ResourceGroup.class));
  }

  @Override
  public Optional<ResourceGroup> find(Criteria criteria) {
    Query query = new Query(criteria);
    return Optional.ofNullable(mongoTemplate.findOne(query, ResourceGroup.class));
  }

  @Override
  public boolean delete(Criteria criteria) {
    Query query = new Query(criteria);
    DeleteResult removeResult = mongoTemplate.remove(query, ResourceGroup.class);
    return removeResult.wasAcknowledged();
  }

  @Override
  public boolean updateMultiple(Query query, Update update) {
    return mongoTemplate.updateMulti(query, update, ResourceGroup.class).wasAcknowledged();
  }
}
