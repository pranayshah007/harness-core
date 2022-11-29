/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.envGroup;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.creator.plan.environment.EnvironmentPlanCreatorHelper;
import io.harness.cdng.envGroup.beans.EnvironmentGroupEntity;
import io.harness.cdng.envGroup.services.EnvironmentGroupService;
import io.harness.cdng.envGroup.yaml.EnvGroupPlanCreatorConfig;
import io.harness.cdng.envgroup.yaml.EnvironmentGroupYaml;
import io.harness.cdng.environment.helper.EnvironmentPlanCreatorConfigMapper;
import io.harness.cdng.environment.yaml.EnvironmentPlanCreatorConfig;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.mappers.EnvironmentMapper;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.utils.YamlPipelineUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@OwnedBy(CDP)
@Singleton
public class EnvGroupPlanCreatorHelper {
  @Inject private EnvironmentGroupService environmentGroupService;
  @Inject private EnvironmentService environmentService;

  public EnvGroupPlanCreatorConfig createEnvGroupPlanCreatorConfig(
      PlanCreationContext ctx, EnvironmentGroupYaml envGroupYaml) {
    final String accountIdentifier = ctx.getAccountIdentifier();
    final String orgIdentifier = ctx.getOrgIdentifier();
    final String projectIdentifier = ctx.getProjectIdentifier();
    final String envGroupIdentifier = envGroupYaml.getEnvGroupRef().getValue();

    final Optional<EnvironmentGroupEntity> entity =
        environmentGroupService.get(accountIdentifier, orgIdentifier, projectIdentifier, envGroupIdentifier, false);

    if (entity.isEmpty()) {
      throw new InvalidRequestException(format("No environment group found with %s identifier in %s project in %s org",
          envGroupIdentifier, projectIdentifier, orgIdentifier));
    }

    List<Environment> environments = environmentService.fetchesNonDeletedEnvironmentFromListOfIdentifiers(
        accountIdentifier, orgIdentifier, projectIdentifier, entity.get().getEnvIdentifiers());

    Map<String, Environment> envMapping =
        emptyIfNull(environments).stream().collect(Collectors.toMap(Environment::getIdentifier, Function.identity()));

    List<EnvironmentYamlV2> envV2Yamls = envGroupYaml.getEnvironments().getValue();

    List<EnvironmentPlanCreatorConfig> envConfigs = new ArrayList<>();
    if (envGroupYaml.getDeployToAll().getValue()) {
      for (Environment env : environments) {
        if (env == null) {
          throw new InvalidRequestException(format("Environment %s not found in environment group %s",
              envGroupYaml.getEnvGroupRef().getValue(), entity.get().getIdentifier()));
        }
        EnvironmentYamlV2 envV2Yaml = envV2Yamls.stream()
                                          .filter(e -> e.getEnvironmentRef().getValue().equals(env.getIdentifier()))
                                          .findAny()
                                          .orElse(null);

        createEnvConfigs(envConfigs, envV2Yaml, env);
      }
    } else {
      for (EnvironmentYamlV2 envV2Yaml : envV2Yamls) {
        Environment environment = envMapping.get(envV2Yaml.getEnvironmentRef().getValue());
        if (environment == null) {
          throw new InvalidRequestException(format("Environment %s not found in environment group %s",
              envGroupYaml.getEnvGroupRef().getValue(), entity.get().getIdentifier()));
        }
        createEnvConfigs(envConfigs, envV2Yaml, environment);
      }
    }

    return EnvGroupPlanCreatorConfig.builder()
        .name(entity.get().getName())
        .identifier(entity.get().getIdentifier())
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .environmentGroupRef(envGroupYaml.getEnvGroupRef())
        .deployToAll(envGroupYaml.getDeployToAll().getValue())
        .environmentPlanCreatorConfigs(envConfigs)
        .build();
  }

  private static void createEnvConfigs(
      List<EnvironmentPlanCreatorConfig> envConfigs, EnvironmentYamlV2 envV2Yaml, Environment environment) {
    String originalEnvYaml = environment.getYaml();

    // TODO: need to remove this once we have the migration for old env
    if (EmptyPredicate.isEmpty(originalEnvYaml)) {
      try {
        originalEnvYaml = YamlPipelineUtils.getYamlString(EnvironmentMapper.toNGEnvironmentConfig(environment));
      } catch (JsonProcessingException e) {
        throw new InvalidRequestException("Unable to convert environment to yaml");
      }
    }

    String mergedEnvYaml = originalEnvYaml;
    if (isNotEmpty(envV2Yaml.getEnvironmentInputs().getValue())) {
      mergedEnvYaml = EnvironmentPlanCreatorHelper.mergeEnvironmentInputs(
          originalEnvYaml, envV2Yaml.getEnvironmentInputs().getValue());
    }
    if (!envV2Yaml.getDeployToAll().getValue() && isEmpty(envV2Yaml.getGitOpsClusters().getValue())) {
      throw new InvalidRequestException("List of Gitops clusters must be provided because deployToAll is false for env "
          + environment.getIdentifier());
    }
    envConfigs.add(EnvironmentPlanCreatorConfigMapper.toEnvPlanCreatorConfigWithGitops(mergedEnvYaml, envV2Yaml, null));
  }
}
