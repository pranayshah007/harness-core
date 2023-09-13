/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.environment.helper;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.environment.bean.IndividualEnvData;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.environment.yaml.EnvironmentsPlanCreatorConfig;
import io.harness.cdng.environment.yaml.EnvironmentsPlanCreatorConfig.EnvironmentsPlanCreatorConfigBuilder;
import io.harness.cdng.environment.yaml.EnvironmentsYaml;
import io.harness.cdng.gitops.service.ClusterService;
import io.harness.cdng.gitops.yaml.ClusterYaml;
import io.harness.data.structure.CollectionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.core.environment.mappers.EnvironmentFilterHelper;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.yaml.ParameterField;
import io.harness.serializer.KryoSerializer;
import io.harness.utils.NGFeatureFlagHelperService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@Slf4j
@OwnedBy(GITOPS)
@Singleton
public class EnvironmentsPlanCreatorHelper {
  @Inject private EnvironmentService environmentService;
  @Inject private EnvironmentFilterHelper environmentFilterHelper;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private EnvironmentInfraFilterHelper environmentInfraFilterHelper;
  @Inject private ClusterService clusterService;
  @Inject private NGFeatureFlagHelperService featureFlagHelperService;

  public EnvironmentsPlanCreatorConfig createEnvironmentsPlanCreatorConfig(
      PlanCreationContext ctx, EnvironmentsYaml environmentsYaml) {
    final String accountIdentifier = ctx.getAccountIdentifier();
    final String orgIdentifier = ctx.getOrgIdentifier();
    final String projectIdentifier = ctx.getProjectIdentifier();

    List<EnvironmentYamlV2> environmentYamlV2s = CollectionUtils.emptyIfNull(environmentsYaml.getValues().getValue());

    List<String> envRefs =
        environmentYamlV2s.stream().map(e -> e.getEnvironmentRef().getValue()).collect(Collectors.toList());

    List<Environment> environments = environmentService.fetchesNonDeletedEnvironmentFromListOfRefs(
        accountIdentifier, orgIdentifier, projectIdentifier, envRefs);

    // To fetch the env name. This is required for populating GitOps ClusterRefs
    Map<String, Environment> envMapping =
        emptyIfNull(environments).stream().collect(Collectors.toMap(Environment::fetchRef, Function.identity()));

    Set<IndividualEnvData> listEnvData = new HashSet<>();
    if (!EnvironmentInfraFilterUtils.areFiltersPresent(environmentsYaml)) {
      if (isNotEmpty(environmentYamlV2s)) {
        for (EnvironmentYamlV2 envV2Yaml : environmentYamlV2s) {
          if (isNotEmpty(envV2Yaml.getFilters().getValue())) {
            log.info("Environment contains filters.");
            continue;
          }

          if (!envV2Yaml.getDeployToAll().getValue() && isEmpty(envV2Yaml.getGitOpsClusters().getValue())) {
            throw new InvalidRequestException("List of GitOps clusters must be provided");
          }
          String envref = envV2Yaml.getEnvironmentRef().getValue();

          IndividualEnvData envData = IndividualEnvData.builder()
                                          .envRef(envref)
                                          .envName(envMapping.get(envref).getName())
                                          .type(envMapping.get(envref).getType().toString())
                                          .gitOpsClusterRefs(getClusterRefs(envV2Yaml))
                                          .deployToAll(envV2Yaml.getDeployToAll().getValue())
                                          .build();

          listEnvData.add(envData);
        }
      }
    }
    EnvironmentsPlanCreatorConfigBuilder environmentsPlanCreatorConfigBuilder =
        EnvironmentsPlanCreatorConfig.builder()
            .orgIdentifier(orgIdentifier)
            .projectIdentifier(projectIdentifier)
            .individualEnvDataList(new ArrayList<>(listEnvData));

    if (isEmpty(listEnvData)) {
      environmentsPlanCreatorConfigBuilder.environmentsYaml(environmentsYaml);
    }
    return environmentsPlanCreatorConfigBuilder.build();
  }

  private static IndividualEnvData getIndividualEnvData(
      String envRef, String envName, EnvironmentType type, Set<String> filteredClsRefs, boolean isDeployToAll) {
    return IndividualEnvData.builder()
        .envRef(envRef)
        .envName(envName)
        .type(type.toString())
        .deployToAll(isDeployToAll)
        .gitOpsClusterRefs(filteredClsRefs)
        .build();
  }

  private Set<String> getClusterRefs(EnvironmentYamlV2 environmentV2) {
    if (!environmentV2.getDeployToAll().getValue()) {
      return environmentV2.getGitOpsClusters()
          .getValue()
          .stream()
          .map(ClusterYaml::getIdentifier)
          .map(ParameterField::getValue)
          .collect(Collectors.toSet());
    }
    return new HashSet<>();
  }
}
