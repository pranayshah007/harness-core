/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.infrastructure.mappers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.NGResourceFilterConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity.InfrastructureEntityKeys;
import io.harness.ng.core.utils.CoreCriteriaUtils;

import lombok.experimental.UtilityClass;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(PIPELINE)
@UtilityClass
public class InfrastructureFilterHelper {
  public Criteria createCriteriaForGetList(
      String accountId, String orgIdentifier, String projectIdentifier, String envIdentifier, String searchTerm) {
    Criteria criteria = CoreCriteriaUtils.createCriteriaForGetList(accountId, orgIdentifier, projectIdentifier);
    criteria.and(InfrastructureEntityKeys.envIdentifier).is(envIdentifier);
    if (isNotEmpty(searchTerm)) {
      Criteria searchCriteria =
          new Criteria().orOperator(where(InfrastructureEntityKeys.name)
                                        .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
              where(InfrastructureEntityKeys.identifier)
                  .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS));
      criteria.andOperator(searchCriteria);
    }
    return criteria;
  }

  public Update getUpdateOperations(InfrastructureEntity infrastructureEntity) {
    Update update = new Update();
    update.set(InfrastructureEntityKeys.accountId, infrastructureEntity.getAccountId());
    update.set(InfrastructureEntityKeys.orgIdentifier, infrastructureEntity.getOrgIdentifier());
    update.set(InfrastructureEntityKeys.projectIdentifier, infrastructureEntity.getProjectIdentifier());
    update.set(InfrastructureEntityKeys.identifier, infrastructureEntity.getIdentifier());
    update.set(InfrastructureEntityKeys.name, infrastructureEntity.getName());
    update.set(InfrastructureEntityKeys.description, infrastructureEntity.getDescription());
    update.set(InfrastructureEntityKeys.tags, infrastructureEntity.getTags());
    update.setOnInsert(InfrastructureEntityKeys.createdAt, System.currentTimeMillis());
    update.set(InfrastructureEntityKeys.lastModifiedAt, System.currentTimeMillis());
    update.set(InfrastructureEntityKeys.yaml, infrastructureEntity.getYaml());
    update.set(InfrastructureEntityKeys.envIdentifier, infrastructureEntity.getEnvIdentifier());
    return update;
  }
}
