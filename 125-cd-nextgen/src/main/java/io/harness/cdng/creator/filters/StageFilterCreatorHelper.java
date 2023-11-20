/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.filters;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.infra.yaml.InfraStructureDefinitionYaml;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.pms.cdng.sample.cd.creator.filters.CdFilter.CdFilterBuilder;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.yaml.ParameterField;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(HarnessTeam.CDC)
@Singleton
public class StageFilterCreatorHelper {
  @Inject private EnvironmentService environmentService;
  @Inject private InfrastructureEntityService infraService;

  public void addEnvAndInfraToFilterBuilder(
      FilterCreationContext filterCreationContext, CdFilterBuilder filterBuilder, EnvironmentYamlV2 env) {
    if (env == null) {
      return;
    }
    final ParameterField<String> environmentRef = env.getEnvironmentRef();
    String environmentBranch = env.getGitBranch();
    if (environmentRef == null) {
      return;
    }
    Optional<Environment> environmentEntityOptional = environmentService.getMetadata(
        filterCreationContext.getSetupMetadata().getAccountId(), filterCreationContext.getSetupMetadata().getOrgId(),
        filterCreationContext.getSetupMetadata().getProjectId(), environmentRef.getValue(), false);
    environmentEntityOptional.ifPresent(environment -> {
      filterBuilder.environmentName(environmentRef.getValue());
      final List<InfraStructureDefinitionYaml> infraList = getInfraStructureDefinitionYamlsList(env);
      addFiltersForInfraYamlList(filterCreationContext, filterBuilder, environment, infraList, environmentBranch);
    });
  }

  private List<InfraStructureDefinitionYaml> getInfraStructureDefinitionYamlsList(EnvironmentYamlV2 env) {
    List<InfraStructureDefinitionYaml> infraList = new ArrayList<>();
    if (ParameterField.isNotNull(env.getInfrastructureDefinitions())) {
      if (!env.getInfrastructureDefinitions().isExpression()) {
        infraList.addAll(env.getInfrastructureDefinitions().getValue());
      }
    } else if (ParameterField.isNotNull(env.getInfrastructureDefinition())) {
      if (!env.getInfrastructureDefinition().isExpression()) {
        infraList.add(env.getInfrastructureDefinition().getValue());
      }
    }
    return infraList;
  }

  private void addFiltersForInfraYamlList(FilterCreationContext filterCreationContext, CdFilterBuilder filterBuilder,
      Environment entity, List<InfraStructureDefinitionYaml> infraList, String environmentBranch) {
    if (isEmpty(infraList)) {
      return;
    }
    List<InfrastructureEntity> infrastructureEntities = infraService.getAllInfrastructuresWithYamlFromIdentifierList(
        filterCreationContext.getSetupMetadata().getAccountId(), filterCreationContext.getSetupMetadata().getOrgId(),
        filterCreationContext.getSetupMetadata().getProjectId(), entity.getIdentifier(), environmentBranch,
        infraList.stream()
            .map(InfraStructureDefinitionYaml::getIdentifier)
            .filter(field -> !field.isExpression())
            .map(ParameterField::getValue)
            .collect(Collectors.toList()));
    for (InfrastructureEntity infrastructureEntity : infrastructureEntities) {
      if (infrastructureEntity.getType() == null) {
        throw new InvalidRequestException(format(
            "Infrastructure Definition [%s] in environment [%s] does not have an associated type. Please select a type for the infrastructure and try again",
            infrastructureEntity.getIdentifier(), infrastructureEntity.getEnvIdentifier()));
      }
      filterBuilder.infrastructureType(infrastructureEntity.getType().getDisplayName());
    }
  }
}
