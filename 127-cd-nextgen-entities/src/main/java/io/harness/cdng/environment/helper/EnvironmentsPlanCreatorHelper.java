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

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.environment.bean.IndividualEnvData;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.environment.yaml.EnvironmentsPlanCreatorConfig;
import io.harness.cdng.environment.yaml.EnvironmentsYaml;
import io.harness.cdng.gitops.yaml.ClusterYaml;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.yaml.ParameterField;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@OwnedBy(GITOPS)
@Singleton
public class EnvironmentsPlanCreatorHelper {
  @Inject private EnvironmentService environmentService;
  @Inject private KryoSerializer kryoSerializer;

  public EnvironmentsPlanCreatorConfig createEnvironmentsPlanCreatorConfig(
      PlanCreationContext ctx, EnvironmentsYaml environmentsYaml) {
    final String accountIdentifier = ctx.getAccountIdentifier();
    final String orgIdentifier = ctx.getOrgIdentifier();
    final String projectIdentifier = ctx.getProjectIdentifier();

    List<EnvironmentYamlV2> environmentYamlV2s = environmentsYaml.getValues().getValue();

    List<String> envRefs =
        environmentYamlV2s.stream().map(e -> e.getEnvironmentRef().getValue()).collect(Collectors.toList());

    List<Environment> environments = environmentService.fetchesNonDeletedEnvironmentFromListOfIdentifiers(
        accountIdentifier, orgIdentifier, projectIdentifier, envRefs);

    // To fetch the env name. This is required for populating GitOps ClusterRefs
    Map<String, Environment> envMapping =
        emptyIfNull(environments).stream().collect(Collectors.toMap(Environment::getIdentifier, Function.identity()));

    List<IndividualEnvData> listEnvData = new ArrayList<>();

    for (EnvironmentYamlV2 envV2Yaml : environmentYamlV2s) {
      if (!envV2Yaml.getDeployToAll().getValue() && isEmpty(envV2Yaml.getGitOpsClusters().getValue())) {
        throw new InvalidRequestException("List of Gitops clusters must be provided");
      }
      String envref = envV2Yaml.getEnvironmentRef().getValue();

      IndividualEnvData envData = IndividualEnvData.builder()
                                      .envRef(envref)
                                      .envName(envMapping.get(envref).getName())
                                      .gitOpsClusterRefs(getClusterRefs(envV2Yaml))
                                      .deployToAll(envV2Yaml.getDeployToAll().getValue())
                                      .build();
      listEnvData.add(envData);
    }

    return EnvironmentsPlanCreatorConfig.builder()
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .individualEnvDataList(listEnvData)
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
