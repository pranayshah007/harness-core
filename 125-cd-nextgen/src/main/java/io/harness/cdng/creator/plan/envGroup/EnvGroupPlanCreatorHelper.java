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
import io.harness.cdng.envGroup.beans.EnvironmentGroupEntity;
import io.harness.cdng.envGroup.services.EnvironmentGroupService;
import io.harness.cdng.envGroup.yaml.EnvGroupPlanCreatorConfig;
import io.harness.cdng.envGroup.yaml.EnvGroupPlanCreatorConfig.EnvGroupPlanCreatorConfigBuilder;
import io.harness.cdng.envgroup.yaml.EnvironmentGroupYaml;
import io.harness.cdng.environment.helper.EnvironmentInfraFilterUtils;
import io.harness.cdng.environment.helper.EnvironmentPlanCreatorConfigMapper;
import io.harness.cdng.environment.helper.EnvironmentPlanCreatorHelper;
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
import graphql.VisibleForTesting;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDP)
@Singleton
public class EnvGroupPlanCreatorHelper {
  public static final int PAGE_SIZE = 1000;
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

    EnvironmentGroupEntity envGroupEntity = entity.get();
    // Do not use org and project from context. Env scope is inferred from env group scope. It's not independent.
    List<Environment> environments = environmentService.fetchesNonDeletedEnvironmentFromListOfIdentifiers(
        envGroupEntity.getAccountId(), envGroupEntity.getOrgIdentifier(), envGroupEntity.getProjectIdentifier(),
        envGroupEntity.getEnvIdentifiers());

    Map<String, Environment> envMapping =
        emptyIfNull(environments).stream().collect(Collectors.toMap(Environment::getIdentifier, Function.identity()));

    List<EnvironmentPlanCreatorConfig> envConfigs = new ArrayList<>();
    EnvGroupPlanCreatorConfigBuilder envGroupPlanCreatorConfigBuilder = EnvGroupPlanCreatorConfig.builder();

    if (!EnvironmentInfraFilterUtils.areFiltersPresent(envGroupYaml)) {
      List<EnvironmentYamlV2> envV2Yamls = envGroupYaml.getEnvironments().getValue();
      if (envGroupYaml.getDeployToAll().getValue()) {
        for (Environment env : environments) {
          if (env == null) {
            throw new InvalidRequestException(format("Environment %s not found in environment group %s",
                envGroupYaml.getEnvGroupRef().getValue(), envGroupEntity.getIdentifier()));
          }
          EnvironmentYamlV2 envV2Yaml = envV2Yamls.stream()
                                            .filter(e -> e.getEnvironmentRef().getValue().equals(env.getIdentifier()))
                                            .findAny()
                                            .orElse(null);

          createEnvConfigs(envConfigs, envV2Yaml, env);
        }
      } else {
        List<EnvironmentPlanCreatorConfig> envConfigsForEnvironments = new ArrayList<>();
        for (EnvironmentYamlV2 envV2Yaml : envV2Yamls) {
          Environment environment = envMapping.get(envV2Yaml.getEnvironmentRef().getValue());

          if (environment == null) {
            throw new InvalidRequestException(format("Environment %s not found in environment group %s",
                envGroupYaml.getEnvGroupRef().getValue(), envGroupEntity.getIdentifier()));
          }
          createEnvConfigs(envConfigsForEnvironments, envV2Yaml, environment);
        }
        envConfigs.addAll(envConfigsForEnvironments);
      }
    }

    envGroupPlanCreatorConfigBuilder.name(envGroupEntity.getName())
        .identifier(envGroupEntity.getIdentifier())
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .environmentGroupRef(envGroupYaml.getEnvGroupRef())
        .deployToAll(envGroupYaml.getDeployToAll().getValue())
        .environmentPlanCreatorConfigs(envConfigs);

    if (isEmpty(envConfigs)) {
      envGroupPlanCreatorConfigBuilder.environmentGroupYaml(envGroupYaml);
    }
    return envGroupPlanCreatorConfigBuilder.build();
  }

  @VisibleForTesting
  protected void createEnvConfigs(
      List<EnvironmentPlanCreatorConfig> envConfigs, EnvironmentYamlV2 envV2Yaml, Environment environment) {
    String originalEnvYaml = environment.getYaml();

    // TODO: need to remove this once we have the migration for old env
    if (EmptyPredicate.isEmpty(originalEnvYaml)) {
      try {
        originalEnvYaml = YamlPipelineUtils.getYamlString(EnvironmentMapper.toNGEnvironmentConfig(environment));
      } catch (JsonProcessingException e) {
        throw new InvalidRequestException("Unable to convert environment to yaml", e);
      }
    }

    String mergedEnvYaml = originalEnvYaml;
    if (isNotEmpty(envV2Yaml.getEnvironmentInputs().getValue())) {
      mergedEnvYaml = EnvironmentPlanCreatorHelper.mergeEnvironmentInputs(
          originalEnvYaml, envV2Yaml.getEnvironmentInputs().getValue());
    }
    if (!envV2Yaml.getDeployToAll().getValue() && isEmpty(envV2Yaml.getGitOpsClusters().getValue())) {
      throw new InvalidRequestException("List of GitOps clusters must be provided because deployToAll is false for env "
          + environment.getIdentifier());
    }
    envConfigs.add(EnvironmentPlanCreatorConfigMapper.toEnvPlanCreatorConfigWithGitops(mergedEnvYaml, envV2Yaml, null));
  }

  @VisibleForTesting
  protected void createEnvConfigsForFiltersFlow(
      List<EnvironmentPlanCreatorConfig> envConfigs, Environment environment, List<String> clusterRefs) {
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
    EnvironmentPlanCreatorConfig config = EnvironmentPlanCreatorConfigMapper.toEnvPlanCreatorConfigWithGitopsFromEnv(
        mergedEnvYaml, environment.getIdentifier(), null, clusterRefs);
    envConfigs.add(config);
  }
}
