/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.service.mappers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.System.currentTimeMillis;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.NGResourceFilterConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.common.beans.NGTag.NGTagKeys;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.entity.ServiceEntity.ServiceEntityKeys;
import io.harness.pms.yaml.YamlField;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.experimental.UtilityClass;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(PIPELINE)
@UtilityClass
public class ServiceFilterHelper {
  private final String ACCOUNT_ID = "accountId";
  private final String ORG_ID = "orgIdentifier";
  private final String PROJECT_ID = "projectIdentifier";
  private final String DELETED = "deleted";

  public Criteria createCriteriaForGetList(String accountId, String orgIdentifier, String projectIdentifier,
      boolean deleted, String searchTerm, ServiceDefinitionType type, Boolean gitOpsEnabled,
      boolean includeAllServicesAccessibleAtScope) {
    Criteria criteria = createCriteriaForGetList(
        accountId, orgIdentifier, projectIdentifier, deleted, includeAllServicesAccessibleAtScope);
    final List<Criteria> andCriterias = new ArrayList<>();
    if (isNotEmpty(searchTerm)) {
      Criteria searchCriteria = new Criteria().orOperator(
          where(ServiceEntityKeys.name).regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
          where(ServiceEntityKeys.identifier)
              .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
          where(ServiceEntityKeys.tags + "." + NGTagKeys.key)
              .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
          where(ServiceEntityKeys.tags + "." + NGTagKeys.value)
              .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS));
      andCriterias.add(searchCriteria);
    }

    if (type != null) {
      criteria.and(ServiceEntityKeys.type).is(type.name());
    }

    if (isNotEmpty(andCriterias)) {
      criteria = criteria.andOperator(andCriterias.toArray(Criteria[] ::new));
    }

    return applyBooleanFilter(criteria, gitOpsEnabled, ServiceEntityKeys.gitOpsEnabled);
  }

  public Update getUpdateOperations(ServiceEntity serviceEntity) {
    Update update = new Update();
    update.set(ServiceEntityKeys.accountId, serviceEntity.getAccountId());
    update.set(ServiceEntityKeys.orgIdentifier, serviceEntity.getOrgIdentifier());
    update.set(ServiceEntityKeys.projectIdentifier, serviceEntity.getProjectIdentifier());
    update.set(ServiceEntityKeys.identifier, serviceEntity.getIdentifier());
    update.set(ServiceEntityKeys.name, serviceEntity.getName());
    update.set(ServiceEntityKeys.description, serviceEntity.getDescription());
    update.set(ServiceEntityKeys.tags, serviceEntity.getTags());
    update.set(ServiceEntityKeys.deleted, false);
    update.setOnInsert(ServiceEntityKeys.createdAt, System.currentTimeMillis());
    update.set(ServiceEntityKeys.lastModifiedAt, System.currentTimeMillis());
    update.set(ServiceEntityKeys.yaml, serviceEntity.getYaml());
    update.set(ServiceEntityKeys.gitOpsEnabled, serviceEntity.getGitOpsEnabled());
    update.set(ServiceEntityKeys.type, serviceEntity.getType());
    return update;
  }

  public Update getUpdateOperationsForDelete() {
    Update update = new Update();
    update.set(ServiceEntityKeys.deleted, true);
    update.set(ServiceEntityKeys.deletedAt, currentTimeMillis());
    return update;
  }

  private Criteria applyBooleanFilter(Criteria criteria, Boolean aBoolean, String key) {
    if (aBoolean == Boolean.TRUE) {
      criteria.and(key).is(true);
    } else if (aBoolean == Boolean.FALSE) {
      criteria.and(key).in(Arrays.asList(false, null));
    }
    return criteria;
  }

  public YamlField getPrimaryArtifactNodeFromServiceYaml(YamlField serviceField) {
    if (serviceField == null) {
      return null;
    }

    YamlField serviceDefinitionField = serviceField.getNode().getField(YamlTypes.SERVICE_DEFINITION);
    if (serviceDefinitionField == null) {
      return null;
    }

    YamlField serviceSpecField = serviceDefinitionField.getNode().getField(YamlTypes.SERVICE_SPEC);
    if (serviceSpecField == null) {
      return null;
    }

    YamlField artifactsField = serviceSpecField.getNode().getField(YamlTypes.ARTIFACT_LIST_CONFIG);
    if (artifactsField == null) {
      return null;
    }

    return artifactsField.getNode().getField(YamlTypes.PRIMARY_ARTIFACT);
  }

  public Criteria createCriteriaForGetList(String accountId, String orgIdentifier, String projectIdentifier,
      boolean deleted, boolean includeAllServicesAccessibleAtScope) {
    Criteria criteria = new Criteria();
    if (isNotEmpty(accountId)) {
      // accountId
      criteria.and(ACCOUNT_ID).is(accountId);

      // select services at levels accessible at that level
      Criteria includeAllServicesCriteria = null;
      if (includeAllServicesAccessibleAtScope) {
        includeAllServicesCriteria = getCriteriaToReturnAllAccessibleServicesAtScope(orgIdentifier, projectIdentifier);
      } else {
        criteria.and(ORG_ID).is(orgIdentifier);
        criteria.and(PROJECT_ID).is(projectIdentifier);
      }
      List<Criteria> criteriaList = new ArrayList<>();
      if (includeAllServicesCriteria != null) {
        criteriaList.add(includeAllServicesCriteria);
      }
      if (criteriaList.size() != 0) {
        criteria.andOperator(criteriaList.toArray(new Criteria[0]));
      }
      criteria.and(DELETED).is(deleted);
      return criteria;
    } else {
      throw new InvalidRequestException("Account identifier cannot be null for services list");
    }
  }

  private Criteria getCriteriaToReturnAllAccessibleServicesAtScope(String orgIdentifier, String projectIdentifier) {
    Criteria criteria = new Criteria();
    Criteria accountCriteria =
        Criteria.where(ServiceEntityKeys.orgIdentifier).is(null).and(ServiceEntityKeys.projectIdentifier).is(null);
    Criteria orgCriteria = Criteria.where(ServiceEntityKeys.orgIdentifier)
                               .is(orgIdentifier)
                               .and(ServiceEntityKeys.projectIdentifier)
                               .is(null);
    Criteria projectCriteria = Criteria.where(ServiceEntityKeys.orgIdentifier)
                                   .is(orgIdentifier)
                                   .and(ServiceEntityKeys.projectIdentifier)
                                   .is(projectIdentifier);

    if (isNotBlank(projectIdentifier)) {
      return criteria.orOperator(projectCriteria, orgCriteria, accountCriteria);
    } else if (isNotBlank(orgIdentifier)) {
      return criteria.orOperator(orgCriteria, accountCriteria);
    } else {
      return criteria.orOperator(accountCriteria);
    }
  }
}
