/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.engine.expressions.usages;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.expressions.usages.beans.ExpressionCategory;
import io.harness.engine.expressions.usages.beans.ExpressionMetadata;
import io.harness.engine.expressions.usages.beans.ExpressionUsagesEntity;
import io.harness.engine.expressions.usages.beans.ExpressionUsagesEntity.ExpressionUsagesEntityKeys;
import io.harness.engine.expressions.usages.dto.ExpressionMetadataDTOMapper;
import io.harness.engine.expressions.usages.dto.ExpressionUsagesDTO;
import io.harness.repositories.ExpressionUsagesRepository;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(HarnessTeam.PIPELINE)
public class ExpressionUsageServiceImpl implements ExpressionUsageService {
  @Inject MongoTemplate mongoTemplate;
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

  @Override
  public ExpressionUsagesDTO fetchExpressionUsages(
      String accountId, String orgId, String projectId, ExpressionCategory category) {
    Criteria criteria = new Criteria();
    criteria.and(ExpressionUsagesEntityKeys.accountIdentifier).is(accountId);
    // Adding org and project criteria if nonEmpty
    if (!EmptyPredicate.isEmpty(orgId)) {
      criteria.and(ExpressionUsagesEntityKeys.orgIdentifier).is(orgId);
      if (!EmptyPredicate.isEmpty(projectId)) {
        criteria.and(ExpressionUsagesEntityKeys.projectIdentifier).is(projectId);
      }
    }
    Query query = new Query(criteria);

    // TODO(BRIJESH): Add projections on category.
    List<ExpressionUsagesEntity> expressionUsagesEntities = mongoTemplate.find(query, ExpressionUsagesEntity.class);
    List<Map<ExpressionCategory, Set<ExpressionMetadata>>> expressionsusagaeMapsList =
        expressionUsagesEntities.stream()
            .map(ExpressionUsagesEntity::getExpressionsMap)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

    Set<ExpressionMetadata> expressionMetadataSet = getFilteredMetadaByCategory(expressionsusagaeMapsList, category);
    return ExpressionUsagesDTO.builder()
        .accountIdentifier(accountId)
        .category(category)
        .expressions(ExpressionMetadataDTOMapper.toMetadataDTO(expressionMetadataSet))
        .build();
  }

  private Set<ExpressionMetadata> getFilteredMetadaByCategory(
      List<Map<ExpressionCategory, Set<ExpressionMetadata>>> expressionsusagaeMapsList, ExpressionCategory category) {
    Set<ExpressionMetadata> expressionMetadataSet = new HashSet<>();
    // When category is ANY or not provided. Return all the expressions.
    if (category == null || category == ExpressionCategory.ANY) {
      expressionsusagaeMapsList.forEach(
          o -> o.values().stream().filter(Objects::nonNull).forEach(expressionMetadataSet::addAll));
    } else {
      expressionsusagaeMapsList.forEach(o -> {
        // Add all the expressions falling under provided category.
        Set<ExpressionMetadata> metadataSet = o.get(category);
        if (metadataSet != null) {
          expressionMetadataSet.addAll(metadataSet);
        }
      });
    }
    return expressionMetadataSet;
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
