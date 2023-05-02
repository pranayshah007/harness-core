/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.favorites.custom;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.favorites.entities.FavoriteEntity;

import com.google.inject.Inject;
import com.mongodb.client.result.DeleteResult;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(PL)
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
public class FavoriteRepositoryCustomImpl implements FavoriteRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public List<FavoriteEntity> findAll(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.find(query, FavoriteEntity.class);
  }

  @Override
  public DeleteResult deleteAll(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.remove(query, FavoriteEntity.class);
  }
}
