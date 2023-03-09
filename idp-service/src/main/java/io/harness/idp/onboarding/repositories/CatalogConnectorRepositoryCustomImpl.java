/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.onboarding.repositories;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.onboarding.entities.CatalogConnector;

import com.google.inject.Inject;
import com.mongodb.client.result.DeleteResult;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.IDP)
public class CatalogConnectorRepositoryCustomImpl implements CatalogConnectorRepositoryCustom {
  private MongoTemplate mongoTemplate;

  @Override
  public CatalogConnector update(Criteria criteria, Update update) {
    Query query = new Query(criteria);
    return mongoTemplate.findAndModify(query, update, CatalogConnector.class);
  }

  @Override
  public boolean delete(Criteria criteria) {
    Query query = new Query(criteria);
    DeleteResult deleteResult = mongoTemplate.remove(query, CatalogConnector.class);
    return deleteResult.wasAcknowledged() && deleteResult.getDeletedCount() == 1;
  }
}
