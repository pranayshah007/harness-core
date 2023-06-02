/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.ng.core.custom;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.springdata.SpringDataMongoUtils.getPaginatedResult;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.user.entities.UserGroup;

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

@OwnedBy(PL)
@AllArgsConstructor(access = AccessLevel.PROTECTED, onConstructor = @__({ @Inject }))
public class UserGroupRepositoryCustomImpl implements UserGroupRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public Optional<UserGroup> find(Criteria criteria) {
    Query query = new Query(criteria);
    return Optional.ofNullable(mongoTemplate.findOne(query, UserGroup.class));
  }

  @Override
  public Page<UserGroup> findAll(Criteria criteria, Pageable pageable) {
    return getPaginatedResult(criteria, pageable, UserGroup.class, mongoTemplate);
  }

  @Override
  public List<UserGroup> findAll(Criteria criteria, Integer skip, Integer limit) {
    Query query = new Query(criteria);
    if (null != skip && null != limit) {
      query.skip(skip);
      query.limit(limit);
    }
    return mongoTemplate.find(query, UserGroup.class);
  }

  @Override
  public List<UserGroup> findAll(Criteria criteria) {
    Query query = new Query(criteria);
    query.limit(500);
    query.fields().exclude("users").exclude("tags");
    return mongoTemplate.find(query, UserGroup.class);
  }

  @Override
  public UserGroup delete(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.findAndRemove(query, UserGroup.class);
  }

  @Override
  public DeleteResult deleteAll(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.remove(query, UserGroup.class);
  }

  @Override
  public List<UserGroup> findAllAndDelete(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.findAllAndRemove(query, UserGroup.class);
  }
}