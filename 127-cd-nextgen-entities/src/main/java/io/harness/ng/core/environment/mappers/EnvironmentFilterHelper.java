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

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.NGResourceFilterConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.encryption.ScopeHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.filter.dto.FilterDTO;
import io.harness.filter.service.FilterService;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.Environment.EnvironmentKeys;
import io.harness.ng.core.environment.beans.EnvironmentFilterPropertiesDTO;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.ng.core.utils.CoreCriteriaUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.util.StringUtils;

@OwnedBy(PIPELINE)
@UtilityClass
public class EnvironmentFilterHelper {
  FilterService filterService;

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

  public Criteria createCriteriaForGetList(String accountId, String orgIdentifier, String projectIdentifier,
      boolean deleted, String searchTerm, String filterIdentifier, EnvironmentFilterPropertiesDTO filterProperties) {
    if (isNotBlank(filterIdentifier) && filterProperties != null) {
      throw new InvalidRequestException("Can not apply both filter properties and saved filter together");
    }

    Criteria criteria =
        CoreCriteriaUtils.createCriteriaForGetList(accountId, orgIdentifier, projectIdentifier, deleted);

    if (isNotBlank(filterIdentifier)) {
      populateSavedEnvironmentFilter(
          criteria, filterIdentifier, accountId, orgIdentifier, projectIdentifier, searchTerm);
    } else {
      populateEnvironmentFiltersInTheCriteria(criteria, filterProperties, searchTerm);
    }

    return criteria;
  }

  private void populateSavedEnvironmentFilter(Criteria criteria, String filterIdentifier, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String searchTerm) {
    FilterDTO envFilterDTO =
        filterService.get(accountIdentifier, orgIdentifier, projectIdentifier, filterIdentifier, ENVIRONMENT);
    if (envFilterDTO == null) {
      throw new InvalidRequestException(
          String.format("Could not find a environment filter with the identifier %s, in %s", filterIdentifier,
              ScopeHelper.getScopeMessageForLogs(accountIdentifier, orgIdentifier, projectIdentifier)));
    }
    populateEnvironmentFiltersInTheCriteria(
        criteria, (EnvironmentFilterPropertiesDTO) envFilterDTO.getFilterProperties(), searchTerm);
  }

  private void populateEnvironmentFiltersInTheCriteria(
      Criteria criteria, EnvironmentFilterPropertiesDTO environmentFilterPropertiesDTO, String searchTerm) {
    if (environmentFilterPropertiesDTO == null) {
      return;
    }

    populateInFilter(criteria, EnvironmentKeys.type, environmentFilterPropertiesDTO.getEnvironmentTypes());
    populateNameDesciptionAndSearchTermFilter(criteria, environmentFilterPropertiesDTO.getEnvironmentNames(),
        environmentFilterPropertiesDTO.getDescription(), searchTerm);
    populateInFilter(criteria, EnvironmentKeys.identifier, environmentFilterPropertiesDTO.getEnvironmentIdentifiers());
    populateTagsFilter(criteria, environmentFilterPropertiesDTO.getTags());
  }

  private void populateTagsFilter(Criteria criteria, Map<String, String> tags) {
    if (isEmpty(tags)) {
      return;
    }
    criteria.and(EnvironmentKeys.tags).in(TagMapper.convertToList(tags));
  }

  private void populateNameDesciptionAndSearchTermFilter(
      Criteria criteria, List<String> environmentNames, String description, String searchTerm) {
    List<Criteria> criteriaList = new ArrayList<>();
    Criteria nameCriteria = getNameFilter(criteria, environmentNames);
    if (nameCriteria != null) {
      criteriaList.add(nameCriteria);
    }
    Criteria descriptionCriteria = getDescriptionFilter(description);
    if (descriptionCriteria != null) {
      criteriaList.add(descriptionCriteria);
    }

    Criteria searchCriteria = getSearchTermFilter(searchTerm);
    if (searchCriteria != null) {
      criteriaList.add(searchCriteria);
    }
    if (criteriaList.size() != 0) {
      criteria.andOperator(criteriaList.toArray(new Criteria[0]));
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

  public Update getUpdateOperations(Environment environment) {
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

  public Update getUpdateOperationsForDelete() {
    Update update = new Update();
    update.set(EnvironmentKeys.deleted, true);
    return update;
  }
}
