/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.envGroup.services;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.NGResourceFilterConstants;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.envGroup.beans.EnvironmentGroupEntity;
import io.harness.cdng.envGroup.beans.EnvironmentGroupFilterPropertiesDTO;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.filter.FilterType;
import io.harness.filter.dto.FilterDTO;
import io.harness.filter.service.FilterService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.regex.PatternSyntaxException;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;

@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(PIPELINE)
public class EnvironmentGroupServiceHelper {
  @Inject private final FilterService filterService;

  public void populateEnvGroupFilter(Criteria filterCriteria, EnvironmentGroupFilterPropertiesDTO envGroupFilter) {
    // Environment Group Name/Identifier
    if (EmptyPredicate.isNotEmpty(envGroupFilter.getEnvGroupName())) {
      try {
        filterCriteria.orOperator(
            where(EnvironmentGroupEntity.EnvironmentGroupKeys.name)
                .regex(envGroupFilter.getEnvGroupName(), NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
            where(EnvironmentGroupEntity.EnvironmentGroupKeys.identifier)
                .regex(envGroupFilter.getEnvGroupName(), NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS));
      } catch (PatternSyntaxException pex) {
        throw new InvalidRequestException(pex.getMessage() + " Use \\\\ for special character", pex);
      }
    }

    // Environment Group Description and Tags
    if (EmptyPredicate.isNotEmpty(envGroupFilter.getDescription())) {
      filterCriteria.and(EnvironmentGroupEntity.EnvironmentGroupKeys.description).is(envGroupFilter.getDescription());
    }

    if (EmptyPredicate.isNotEmpty(envGroupFilter.getEnvGroupTags())) {
      filterCriteria.and(EnvironmentGroupEntity.EnvironmentGroupKeys.tags).in(envGroupFilter.getEnvGroupTags());
    }

    // Environment Identifier. Filter those environment groups whose environment list contains subset of env identifier
    // list passed in filter

    if (EmptyPredicate.isNotEmpty(envGroupFilter.getEnvIdentifiers())) {
      filterCriteria.and(EnvironmentGroupEntity.EnvironmentGroupKeys.envIdentifiers)
          .all(envGroupFilter.getEnvIdentifiers());
    }
  }

  public void populateEnvGroupFilterUsingIdentifier(Criteria filterCriteria, String accountId, String orgIdentifier,
      String projectIdentifier, @NotNull String filterIdentifier) {
    FilterDTO pipelineFilterDTO =
        filterService.get(accountId, orgIdentifier, projectIdentifier, filterIdentifier, FilterType.ENVIRONMENTGROUP);

    if (pipelineFilterDTO == null) {
      throw new InvalidRequestException("Could not find a pipeline filter with the identifier ");
    }
    populateEnvGroupFilter(
        filterCriteria, (EnvironmentGroupFilterPropertiesDTO) pipelineFilterDTO.getFilterProperties());
  }
}
