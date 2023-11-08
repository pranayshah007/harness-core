/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.checks.repositories;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.checks.entity.CheckStatusEntity;

import com.google.inject.Inject;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.ProjectionOperation;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
public class CheckStatusRepositoryCustomImpl implements CheckStatusRepositoryCustom {
  private MongoTemplate mongoTemplate;
  public static final String ID_KEY = "_id";
  public static final String IDENTIFIER_KEY = "identifier";
  public static final String CUSTOM_KEY = "isCustom";
  public static final String CHECK_STATUS_ENTITY_KEY = "checkStatusEntity";
  public static final String CHECK_STATUS_COLLECTION_NAME = "checkStatus";
  @Override
  public List<CheckStatusEntityByIdentifier> findByAccountIdentifierAndIdentifierIn(
      String accountIdentifier, List<String> identifiers) {
    Criteria criteria = Criteria.where(CheckStatusEntity.CheckStatusKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(CheckStatusEntity.CheckStatusKeys.identifier)
                            .in(identifiers);

    ProjectionOperation projectionOperation = Aggregation.project()
                                                  .andExpression(ID_KEY + "." + IDENTIFIER_KEY)
                                                  .as(IDENTIFIER_KEY)
                                                  .andExpression(ID_KEY + "." + CUSTOM_KEY)
                                                  .as(CUSTOM_KEY)
                                                  .andExpression(CHECK_STATUS_ENTITY_KEY)
                                                  .as(CHECK_STATUS_ENTITY_KEY);

    Aggregation aggregation = Aggregation.newAggregation(Aggregation.match(criteria),
        Aggregation.sort(Sort.Direction.DESC, CheckStatusEntity.CheckStatusKeys.timestamp),
        Aggregation.group(CheckStatusEntity.CheckStatusKeys.identifier, CheckStatusEntity.CheckStatusKeys.isCustom)
            .push(IDENTIFIER_KEY)
            .as(CheckStatusEntity.CheckStatusKeys.identifier)
            .first(Aggregation.ROOT)
            .as(CHECK_STATUS_ENTITY_KEY),
        projectionOperation);

    AggregationResults<CheckStatusEntityByIdentifier> result =
        mongoTemplate.aggregate(aggregation, CHECK_STATUS_COLLECTION_NAME, CheckStatusEntityByIdentifier.class);
    return result.getMappedResults();
  }
}
