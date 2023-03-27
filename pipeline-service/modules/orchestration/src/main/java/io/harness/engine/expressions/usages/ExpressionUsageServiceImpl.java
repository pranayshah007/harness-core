/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.engine.expressions.usages;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.repositories.ExpressionUsagesRepository;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@OwnedBy(HarnessTeam.PIPELINE)
public class ExpressionUsageServiceImpl implements ExpressionUsageService {
  @Inject ExpressionUsagesRepository expressionUsagesRepository;
  @Override
  public ExpressionUsagesEntity save(ExpressionUsagesEntity entity) {
    return expressionUsagesRepository.save(entity);
  }

  @Override
  public boolean doesExpressionUsagesEntityExists(
      String pipelineIdentifier, String accountId, String orgId, String projectId) {
    return expressionUsagesRepository
        .existsByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndPipelineIdentifier(
            accountId, orgId, projectId, pipelineIdentifier);
  }
  @Override
  public ExpressionUsagesEntity upsertExpressions(String pipelineIdentifier, String accountId, String orgId,
      String projectId, Map<ExpressionCategory, Set<ExpressionMetadata>> expressionUsages) {
    Optional<ExpressionUsagesEntity> optional =
        expressionUsagesRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndPipelineIdentifier(
            accountId, orgId, projectId, pipelineIdentifier);
    ExpressionUsagesEntity entity;
    entity = optional.orElseGet(()
                                    -> ExpressionUsagesEntity.builder()
                                           .accountIdentifier(accountId)
                                           .orgIdentifier(orgId)
                                           .projectIdentifier(projectId)
                                           .pipelineIdentifier(pipelineIdentifier)
                                           .expressionsMap(new HashMap<>())
                                           .build());
    upsertExpressions(entity, expressionUsages);
    return save(entity);
  }

  private ExpressionUsagesEntity upsertExpressions(
      ExpressionUsagesEntity entity, Map<ExpressionCategory, Set<ExpressionMetadata>> newMap) {
    Map<ExpressionCategory, Set<ExpressionMetadata>> orignalMap = entity.getExpressionsMap();
    newMap.forEach((key, value) -> {
      Set<ExpressionMetadata> expressionMetadataSet = orignalMap.getOrDefault(key, new HashSet<>());
      expressionMetadataSet.addAll(value);
      orignalMap.put(key, expressionMetadataSet);
    });
    entity.setExpressionsMap(orignalMap);
    return entity;
  }
}
