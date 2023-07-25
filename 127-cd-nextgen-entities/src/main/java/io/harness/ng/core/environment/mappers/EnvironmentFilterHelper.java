/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.environment.mappers;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.filter.FilterType.ENVIRONMENT;
import static io.harness.springdata.SpringDataMongoUtils.populateInFilter;
import static io.harness.utils.IdentifierRefHelper.MAX_RESULT_THRESHOLD_FOR_SPLIT;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.NGResourceFilterConstants;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.IdentifierRef;
import io.harness.exception.InvalidRequestException;
import io.harness.filter.dto.FilterDTO;
import io.harness.filter.service.FilterService;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.Environment.EnvironmentKeys;
import io.harness.ng.core.environment.beans.EnvironmentFilterPropertiesDTO;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.ng.core.service.mappers.ServiceFilterHelper;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity.NGServiceOverridesEntityKeys;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType;
import io.harness.ng.core.utils.CoreCriteriaUtils;
import io.harness.scope.ScopeHelper;
import io.harness.utils.IdentifierRefHelper;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.util.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(PIPELINE)
@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
public class EnvironmentFilterHelper {
  @Inject private final FilterService filterService;

  public Criteria createCriteriaForGetList(
      String accountId, String orgIdentifier, String projectIdentifier, boolean deleted, String searchTerm) {
    Criteria criteria =
        CoreCriteriaUtils.createCriteriaForGetList(accountId, orgIdentifier, projectIdentifier, deleted);
    if (isNotEmpty(searchTerm)) {
      Criteria searchCriteria = new Criteria().orOperator(
          where(EnvironmentKeys.name).regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
          where(EnvironmentKeys.identifier)
              .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS));
      criteria.andOperator(searchCriteria);
    }
    return criteria;
  }

  public Criteria createCriteriaForGetList(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> scopedEnvRefs, boolean deleted) {
    Criteria criteria = new Criteria();
    if (isNotEmpty(accountId)) {
      criteria.and(EnvironmentKeys.accountId).is(accountId);

      Criteria scopedEnvsCriteria = null;

      if (isNotEmpty(scopedEnvRefs) && !Iterables.all(scopedEnvRefs, Predicates.isNull())) {
        scopedEnvsCriteria =
            getCriteriaToReturnEnvsFromEnvList(accountId, orgIdentifier, projectIdentifier, scopedEnvRefs);
      } else {
        criteria.and(EnvironmentKeys.orgIdentifier).is(orgIdentifier);
        criteria.and(EnvironmentKeys.projectIdentifier).is(projectIdentifier);
      }

      List<Criteria> criteriaList = new ArrayList<>();
      if (scopedEnvsCriteria != null) {
        criteriaList.add(scopedEnvsCriteria);
      }
      if (isNotEmpty(criteriaList)) {
        criteria.andOperator(criteriaList.toArray(new Criteria[0]));
      }

      criteria.and(EnvironmentKeys.deleted).is(deleted);
      return criteria;
    } else {
      throw new InvalidRequestException("account identifier cannot be null for environment list");
    }
  }

  private Criteria getCriteriaToReturnEnvsFromEnvList(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, List<String> scopedEnvRefs) {
    Criteria criteria = new Criteria();
    List<Criteria> orCriteriaList = new ArrayList<>();
    List<String> projectLevelIdentifiers = new ArrayList<>();
    List<String> orgLevelIdentifiers = new ArrayList<>();
    List<String> accountLevelIdentifiers = new ArrayList<>();

    if (isNotEmpty(scopedEnvRefs)) {
      ServiceFilterHelper.populateIdentifiersOfEachLevel(accountIdentifier, orgIdentifier, projectIdentifier,
          scopedEnvRefs, projectLevelIdentifiers, orgLevelIdentifiers, accountLevelIdentifiers);
    }

    Criteria accountCriteria;
    Criteria orgCriteria;
    Criteria projectCriteria;

    if (isNotEmpty(accountLevelIdentifiers)) {
      accountCriteria = Criteria.where(EnvironmentKeys.orgIdentifier)
                            .is(null)
                            .and(EnvironmentKeys.projectIdentifier)
                            .is(null)
                            .and(EnvironmentKeys.identifier)
                            .in(accountLevelIdentifiers);
      orCriteriaList.add(accountCriteria);
    }

    if (isNotEmpty(orgLevelIdentifiers)) {
      orgCriteria = Criteria.where(EnvironmentKeys.orgIdentifier)
                        .is(orgIdentifier)
                        .and(EnvironmentKeys.projectIdentifier)
                        .is(null)
                        .and(EnvironmentKeys.identifier)
                        .in(orgLevelIdentifiers);
      orCriteriaList.add(orgCriteria);
    }

    if (isNotEmpty(projectLevelIdentifiers)) {
      projectCriteria = Criteria.where(EnvironmentKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(EnvironmentKeys.projectIdentifier)
                            .is(projectIdentifier)
                            .and(EnvironmentKeys.identifier)
                            .in(projectLevelIdentifiers);
      orCriteriaList.add(projectCriteria);
    }

    if (isNotEmpty(orCriteriaList)) {
      criteria.orOperator(orCriteriaList);
      return criteria;
    }

    return null;
  }

  public Criteria createCriteriaForGetList(String accountId, String orgIdentifier, String projectIdentifier,
      boolean deleted, String searchTerm, String filterIdentifier, EnvironmentFilterPropertiesDTO filterProperties,
      boolean includeAllAccessibleAtScope) {
    if (isNotBlank(filterIdentifier) && filterProperties != null) {
      throw new InvalidRequestException("Can not apply both filter properties and saved filter together");
    }
    Criteria criteria = Criteria.where(EnvironmentKeys.accountId).is(accountId);
    List<Criteria> andCriteriaList = new ArrayList<>();
    if (includeAllAccessibleAtScope) {
      getCriteriaToReturnAllAccessibleEnvironmentsAtScope(orgIdentifier, projectIdentifier, andCriteriaList);
    } else {
      criteria.and(EnvironmentKeys.orgIdentifier).is(orgIdentifier);
      criteria.and(EnvironmentKeys.projectIdentifier).is(projectIdentifier);
    }
    criteria.and(EnvironmentKeys.deleted).is(deleted);
    if (isNotBlank(filterIdentifier)) {
      populateSavedEnvironmentFilter(
          criteria, filterIdentifier, accountId, orgIdentifier, projectIdentifier, searchTerm, andCriteriaList);
    } else {
      populateEnvironmentFiltersInTheCriteria(criteria, filterProperties, searchTerm, andCriteriaList);
    }
    if (andCriteriaList.size() != 0) {
      criteria.andOperator(andCriteriaList.toArray(new Criteria[0]));
    }
    return criteria;
  }

  private void getCriteriaToReturnAllAccessibleEnvironmentsAtScope(
      String orgIdentifier, String projectIdentifier, List<Criteria> andCriteriaList) {
    Criteria criteria = new Criteria();
    Criteria accountCriteria =
        Criteria.where(EnvironmentKeys.orgIdentifier).is(null).and(EnvironmentKeys.projectIdentifier).is(null);
    Criteria orgCriteria =
        Criteria.where(EnvironmentKeys.orgIdentifier).is(orgIdentifier).and(EnvironmentKeys.projectIdentifier).is(null);
    Criteria projectCriteria = Criteria.where(EnvironmentKeys.orgIdentifier)
                                   .is(orgIdentifier)
                                   .and(EnvironmentKeys.projectIdentifier)
                                   .is(projectIdentifier);

    if (isNotBlank(projectIdentifier)) {
      criteria.orOperator(projectCriteria, orgCriteria, accountCriteria);
    } else if (isNotBlank(orgIdentifier)) {
      criteria.orOperator(orgCriteria, accountCriteria);
    } else {
      criteria.orOperator(accountCriteria);
    }
    andCriteriaList.add(criteria);
  }

  private void populateSavedEnvironmentFilter(Criteria criteria, String filterIdentifier, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String searchTerm, List<Criteria> andCriteriaList) {
    FilterDTO envFilterDTO =
        filterService.get(accountIdentifier, orgIdentifier, projectIdentifier, filterIdentifier, ENVIRONMENT);
    if (envFilterDTO == null) {
      throw new InvalidRequestException(
          String.format("Could not find a environment filter with the identifier %s, in %s", filterIdentifier,
              ScopeHelper.getScopeMessageForLogs(accountIdentifier, orgIdentifier, projectIdentifier)));
    }
    populateEnvironmentFiltersInTheCriteria(
        criteria, (EnvironmentFilterPropertiesDTO) envFilterDTO.getFilterProperties(), searchTerm, andCriteriaList);
  }

  private void populateEnvironmentFiltersInTheCriteria(Criteria criteria,
      EnvironmentFilterPropertiesDTO environmentFilterPropertiesDTO, String searchTerm,
      List<Criteria> andCriteriaList) {
    if (environmentFilterPropertiesDTO == null) {
      return;
    }

    populateInFilter(criteria, EnvironmentKeys.type, environmentFilterPropertiesDTO.getEnvironmentTypes());
    populateNameDescriptionAndSearchTermFilter(criteria, environmentFilterPropertiesDTO.getEnvironmentNames(),
        environmentFilterPropertiesDTO.getDescription(), searchTerm, andCriteriaList);
    populateInFilter(criteria, EnvironmentKeys.identifier, environmentFilterPropertiesDTO.getEnvironmentIdentifiers());
    populateTagsFilter(criteria, environmentFilterPropertiesDTO.getTags());
  }

  private void populateTagsFilter(Criteria criteria, Map<String, String> tags) {
    if (isEmpty(tags)) {
      return;
    }
    criteria.and(EnvironmentKeys.tags).in(TagMapper.convertToList(tags));
  }

  private void populateNameDescriptionAndSearchTermFilter(Criteria criteria, List<String> environmentNames,
      String description, String searchTerm, List<Criteria> andCriteriaList) {
    Criteria nameCriteria = getNameFilter(criteria, environmentNames);
    if (nameCriteria != null) {
      andCriteriaList.add(nameCriteria);
    }
    Criteria descriptionCriteria = getDescriptionFilter(description);
    if (descriptionCriteria != null) {
      andCriteriaList.add(descriptionCriteria);
    }

    Criteria searchCriteria = getSearchTermFilter(searchTerm);
    if (searchCriteria != null) {
      andCriteriaList.add(searchCriteria);
    }
  }

  private Criteria getSearchTermFilter(String searchTerm) {
    if (isNotBlank(searchTerm)) {
      Criteria tagCriteria = createCriteriaForSearchingTag(searchTerm);
      return new Criteria().orOperator(
          where(EnvironmentKeys.name).regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
          where(EnvironmentKeys.identifier).regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
          where(EnvironmentKeys.description)
              .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
          tagCriteria);
    }
    return null;
  }

  private Criteria createCriteriaForSearchingTag(String searchTerm) {
    String keyToBeSearched = searchTerm;
    String valueToBeSearched = "";
    if (searchTerm.contains(":")) {
      String[] split = searchTerm.split(":");
      keyToBeSearched = split[0];
      valueToBeSearched = split.length >= 2 ? split[1] : "";
    }
    return where(EnvironmentKeys.tags).is(NGTag.builder().key(keyToBeSearched).value(valueToBeSearched).build());
  }

  private Criteria getDescriptionFilter(String description) {
    if (isBlank(description)) {
      return null;
    }
    String[] descriptionsWords = description.split(" ");
    if (isNotEmpty(descriptionsWords)) {
      String pattern = StringUtils.collectionToDelimitedString(Arrays.asList(descriptionsWords), "|");
      return where(EnvironmentKeys.description)
          .regex(pattern, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS);
    }
    return null;
  }

  private Criteria getNameFilter(Criteria criteria, List<String> environmentNames) {
    if (isEmpty(environmentNames)) {
      return null;
    }
    List<Criteria> criteriaForNames =
        environmentNames.stream()
            .map(name
                -> where(EnvironmentKeys.name).regex(name, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS))
            .collect(Collectors.toList());
    return new Criteria().orOperator(criteriaForNames.toArray(new Criteria[0]));
  }

  // Todo: This code handles all four cases where request and entity in DB can contain any of environment Ref or
  // identifier. Clean up in future after successful migration
  public Criteria createCriteriaForGetServiceOverrides(
      String accountId, String orgIdentifier, String projectIdentifier, String environmentRef, String serviceRef) {
    Criteria baseCriteria;
    final String qualifiedEnvironmentRef;
    final String environmentIdentifier;
    String[] envRefSplit =
        org.apache.commons.lang3.StringUtils.split(environmentRef, ".", MAX_RESULT_THRESHOLD_FOR_SPLIT);
    if (envRefSplit == null || envRefSplit.length == 1) {
      baseCriteria = CoreCriteriaUtils.createCriteriaForGetList(accountId, orgIdentifier, projectIdentifier);
      qualifiedEnvironmentRef =
          IdentifierRefHelper.getRefFromIdentifierOrRef(accountId, orgIdentifier, projectIdentifier, environmentRef);
      environmentIdentifier = environmentRef;
    } else {
      IdentifierRef envIdentifierRef =
          IdentifierRefHelper.getIdentifierRef(environmentRef, accountId, orgIdentifier, projectIdentifier);
      baseCriteria = CoreCriteriaUtils.createCriteriaForGetList(envIdentifierRef.getAccountIdentifier(),
          envIdentifierRef.getOrgIdentifier(), envIdentifierRef.getProjectIdentifier());
      qualifiedEnvironmentRef = environmentRef;
      environmentIdentifier = envIdentifierRef.getIdentifier();
    }
    // to exclude other type of overrides present in V2
    baseCriteria.and(NGServiceOverridesEntityKeys.type).is(ServiceOverridesType.ENV_SERVICE_OVERRIDE);
    baseCriteria.andOperator(
        new Criteria().orOperator(Criteria.where(NGServiceOverridesEntityKeys.environmentRef).is(environmentIdentifier),
            Criteria.where(NGServiceOverridesEntityKeys.environmentRef).is(qualifiedEnvironmentRef)));

    if (isNotBlank(serviceRef)) {
      baseCriteria.and(NGServiceOverridesEntityKeys.serviceRef).is(serviceRef);
    }

    return baseCriteria;
  }

  public static Update getUpdateOperations(Environment environment) {
    Update update = new Update();
    update.set(EnvironmentKeys.accountId, environment.getAccountId());
    update.set(EnvironmentKeys.orgIdentifier, environment.getOrgIdentifier());
    update.set(EnvironmentKeys.projectIdentifier, environment.getProjectIdentifier());
    update.set(EnvironmentKeys.identifier, environment.getIdentifier());
    update.set(EnvironmentKeys.name, environment.getName());
    update.set(EnvironmentKeys.description, environment.getDescription());
    update.set(EnvironmentKeys.type, environment.getType());
    update.set(EnvironmentKeys.deleted, false);
    update.set(EnvironmentKeys.tags, environment.getTags());
    update.set(EnvironmentKeys.color, environment.getColor());
    update.set(EnvironmentKeys.yaml, environment.getYaml());
    update.setOnInsert(EnvironmentKeys.createdAt, System.currentTimeMillis());
    update.set(EnvironmentKeys.lastModifiedAt, System.currentTimeMillis());
    return update;
  }

  public static Update getUpdateOperationsForDelete() {
    Update update = new Update();
    update.set(EnvironmentKeys.deleted, true);
    return update;
  }

  // Should not be used for Overrides V2
  @Deprecated
  public static Update getUpdateOperationsForServiceOverride(NGServiceOverridesEntity serviceOverridesEntity) {
    String qualifiedEnvironmentRef = IdentifierRefHelper.getRefFromIdentifierOrRef(
        serviceOverridesEntity.getAccountId(), serviceOverridesEntity.getOrgIdentifier(),
        serviceOverridesEntity.getProjectIdentifier(), serviceOverridesEntity.getEnvironmentRef());
    String identifier = generateServiceOverrideIdentifier(serviceOverridesEntity);
    ServiceOverridesType type = ServiceOverridesType.ENV_SERVICE_OVERRIDE;
    Update update = new Update();
    update.set(NGServiceOverridesEntityKeys.accountId, serviceOverridesEntity.getAccountId());
    update.set(NGServiceOverridesEntityKeys.orgIdentifier, serviceOverridesEntity.getOrgIdentifier());
    update.set(NGServiceOverridesEntityKeys.projectIdentifier, serviceOverridesEntity.getProjectIdentifier());
    update.set(NGServiceOverridesEntityKeys.environmentRef, qualifiedEnvironmentRef);
    update.set(NGServiceOverridesEntityKeys.serviceRef, serviceOverridesEntity.getServiceRef());
    update.set(NGServiceOverridesEntityKeys.yaml, serviceOverridesEntity.getYaml());
    update.setOnInsert(NGServiceOverridesEntityKeys.createdAt, System.currentTimeMillis());
    update.set(NGServiceOverridesEntityKeys.lastModifiedAt, System.currentTimeMillis());
    // for service override v2
    update.set(NGServiceOverridesEntityKeys.identifier, identifier);
    update.set(NGServiceOverridesEntityKeys.type, type);
    return update;
  }

  private static String generateServiceOverrideIdentifier(NGServiceOverridesEntity serviceOverridesEntity) {
    return String.join("_", serviceOverridesEntity.getEnvironmentRef(), serviceOverridesEntity.getServiceRef())
        .replace(".", "_");
  }
}
