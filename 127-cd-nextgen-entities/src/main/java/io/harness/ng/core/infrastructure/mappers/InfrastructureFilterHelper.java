/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.infrastructure.mappers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.utils.IdentifierRefHelper.MAX_RESULT_THRESHOLD_FOR_SPLIT;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.NGResourceFilterConstants;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity.InfrastructureEntityKeys;
import io.harness.ng.core.utils.CoreCriteriaUtils;
import io.harness.utils.IdentifierRefHelper;

import java.util.List;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(PIPELINE)
@UtilityClass
public class InfrastructureFilterHelper {
  public Criteria createListCriteria(String accountId, String orgIdentifier, String projectIdentifier,
      String envIdentifier, String searchTerm, List<String> infraIdentifiers, ServiceDefinitionType deploymentType,
      String repoName, boolean includeAllAccessibleAtScope) {
    Criteria criteria;
    if (includeAllAccessibleAtScope) {
      criteria = getCriteriaToReturnAllAccessibleEnvironmentsAtScope(accountId, orgIdentifier, projectIdentifier);
    } else {
      criteria = CoreCriteriaUtils.createCriteriaForGetList(accountId, orgIdentifier, projectIdentifier);
    }
    if (envIdentifier != null) {
      String[] envRefSplit = StringUtils.split(envIdentifier, ".", MAX_RESULT_THRESHOLD_FOR_SPLIT);
      if (envRefSplit == null || envRefSplit.length == 1) {
        criteria.and(InfrastructureEntityKeys.envIdentifier).is(envIdentifier);
      } else {
        IdentifierRef envIdentifierRef =
            IdentifierRefHelper.getIdentifierRef(envIdentifier, accountId, orgIdentifier, projectIdentifier);
        criteria = CoreCriteriaUtils.createCriteriaForGetList(envIdentifierRef.getAccountIdentifier(),
            envIdentifierRef.getOrgIdentifier(), envIdentifierRef.getProjectIdentifier());
        criteria.and(InfrastructureEntityKeys.envIdentifier).is(envIdentifierRef.getIdentifier());
      }
    }

    if (EmptyPredicate.isNotEmpty(searchTerm)) {
      Criteria searchCriteria =
          new Criteria().orOperator(where(InfrastructureEntityKeys.name)
                                        .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
              where(InfrastructureEntityKeys.identifier)
                  .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS));
      criteria.andOperator(searchCriteria);
    }
    if (EmptyPredicate.isNotEmpty(infraIdentifiers)) {
      criteria.and(InfrastructureEntityKeys.identifier).in(infraIdentifiers);
    }
    if (deploymentType != null) {
      criteria.and(InfrastructureEntityKeys.deploymentType).is(deploymentType);
    }
    if (isNotEmpty(repoName)) {
      criteria.and(InfrastructureEntityKeys.repo).is(repoName);
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
    update.set(InfrastructureEntityKeys.deploymentType, infrastructureEntity.getDeploymentType());
    update.set(InfrastructureEntityKeys.obsolete, infrastructureEntity.getObsolete());
    return update;
  }

  private Criteria getCriteriaToReturnAllAccessibleEnvironmentsAtScope(
      String accountId, String orgIdentifier, String projectIdentifier) {
    Criteria criteria = new Criteria();
    Criteria accountCriteria = Criteria.where(InfrastructureEntityKeys.orgIdentifier)
                                   .is(null)
                                   .and(InfrastructureEntityKeys.projectIdentifier)
                                   .is(null)
                                   .and(InfrastructureEntityKeys.accountId)
                                   .is(accountId);
    Criteria orgCriteria = Criteria.where(InfrastructureEntityKeys.orgIdentifier)
                               .is(orgIdentifier)
                               .and(InfrastructureEntityKeys.projectIdentifier)
                               .is(null)
                               .and(InfrastructureEntityKeys.accountId)
                               .is(accountId);
    Criteria projectCriteria = Criteria.where(InfrastructureEntityKeys.orgIdentifier)
                                   .is(orgIdentifier)
                                   .and(InfrastructureEntityKeys.projectIdentifier)
                                   .is(projectIdentifier)
                                   .and(InfrastructureEntityKeys.accountId)
                                   .is(accountId);

    if (isNotBlank(projectIdentifier)) {
      criteria.orOperator(projectCriteria, orgCriteria, accountCriteria);
    } else if (isNotBlank(orgIdentifier)) {
      criteria.orOperator(orgCriteria, accountCriteria);
    } else {
      criteria.orOperator(accountCriteria);
    }
    return criteria;
  }
}
