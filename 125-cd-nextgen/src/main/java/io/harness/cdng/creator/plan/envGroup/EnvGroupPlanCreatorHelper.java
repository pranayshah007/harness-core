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
import io.harness.beans.FeatureName;
import io.harness.cdng.creator.plan.environment.EnvironmentPlanCreatorHelper;
import io.harness.cdng.envGroup.beans.EnvironmentGroupEntity;
import io.harness.cdng.envGroup.services.EnvironmentGroupService;
import io.harness.cdng.envGroup.yaml.EnvGroupPlanCreatorConfig;
import io.harness.cdng.envGroup.yaml.EnvGroupPlanCreatorConfig.EnvGroupPlanCreatorConfigBuilder;
import io.harness.cdng.envgroup.yaml.EnvironmentGroupYaml;
import io.harness.cdng.environment.filters.FilterYaml;
import io.harness.cdng.environment.helper.EnvironmentInfraFilterHelper;
import io.harness.cdng.environment.helper.EnvironmentPlanCreatorConfigMapper;
import io.harness.cdng.environment.yaml.EnvironmentPlanCreatorConfig;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.gitops.entity.Cluster;
import io.harness.cdng.gitops.service.ClusterService;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.mappers.EnvironmentMapper;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.yaml.ParameterField;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.utils.YamlPipelineUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import graphql.VisibleForTesting;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
  @Inject private EnvironmentInfraFilterHelper environmentInfraFilterHelper;
  @Inject private ClusterService clusterService;
  @Inject private NGFeatureFlagHelperService featureFlagHelperService;

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

    List<EnvironmentPlanCreatorConfig> envConfigs = new ArrayList<>();
    EnvGroupPlanCreatorConfigBuilder envGroupPlanCreatorConfigBuilder = EnvGroupPlanCreatorConfig.builder();

    // Filters are specified so no environment exists in the yaml.
    // Apply filtering based on provided filters on all environments and clusters in the envGroup
    // If no clusters are eligible then throw an exception.

    if (featureFlagHelperService.isEnabled(ctx.getAccountIdentifier(), FeatureName.CDS_FILTER_INFRA_CLUSTERS_ON_TAGS)
        && ParameterField.isNotNull(envGroupYaml.getFilters()) && isNotEmpty(envGroupYaml.getFilters().getValue())) {
      List<FilterYaml> filterYamls = envGroupYaml.getFilters().getValue();

      // Environment Filtering applied
      Set<Environment> filteredEnvs =
          environmentInfraFilterHelper.applyFiltersOnEnvs(new HashSet<>(environments), filterYamls);

      List<String> filteredEnvRefs = filteredEnvs.stream().map(Environment::getIdentifier).collect(Collectors.toList());

      Map<String, io.harness.cdng.gitops.entity.Cluster> clsToCluster =
          environmentInfraFilterHelper.getClusterRefToNGGitOpsClusterMap(
              accountIdentifier, orgIdentifier, projectIdentifier, filteredEnvRefs);

      Set<String> clsRefs = clsToCluster.values().stream().map(e -> e.getClusterRef()).collect(Collectors.toSet());

      // If no clusters exists for environment then we are done here
      if (isNotEmpty(clsRefs)) {
        List<io.harness.gitops.models.Cluster> clusterList = environmentInfraFilterHelper.fetchClustersFromGitOps(
            accountIdentifier, orgIdentifier, projectIdentifier, clsRefs);

        Set<io.harness.cdng.gitops.entity.Cluster> filteredClusters =
            environmentInfraFilterHelper.applyFilteringOnClusters(
                filterYamls, clsToCluster, new HashSet<>(clusterList));

        List<EnvironmentYamlV2> environmentYamlV2List = new ArrayList<>();
        for (Environment env : filteredEnvs) {
          List<Cluster> clustersInEnv =
              filteredClusters.stream().filter(e -> e.getEnvRef() != env.getIdentifier()).collect(Collectors.toList());
          List<String> filteredClusterRefs =
              clustersInEnv.stream().map(Cluster::getClusterRef).collect(Collectors.toList());

          createEnvConfigsForFiltersFlow(envConfigs, env, filteredClusterRefs);

          EnvironmentYamlV2 environmentYamlV2 =
              EnvironmentYamlV2.builder().environmentRef(ParameterField.createValueField(env.getIdentifier())).build();
          environmentYamlV2List.add(environmentYamlV2);
        }

        envGroupYaml.setEnvironments(ParameterField.createValueField(environmentYamlV2List));
      }
    } else {
      List<EnvironmentYamlV2> envV2Yamls = envGroupYaml.getEnvironments().getValue();
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

          if (featureFlagHelperService.isEnabled(
                  ctx.getAccountIdentifier(), FeatureName.CDS_FILTER_INFRA_CLUSTERS_ON_TAGS)
              && isNotEmpty(envV2Yaml.getFilters().getValue())) {
            processFiltersForEachEnvironment(
                accountIdentifier, orgIdentifier, projectIdentifier, envV2Yaml, envConfigs, environment);
          } else {
            if (environment == null) {
              throw new InvalidRequestException(format("Environment %s not found in environment group %s",
                  envGroupYaml.getEnvGroupRef().getValue(), entity.get().getIdentifier()));
            }
            createEnvConfigs(envConfigs, envV2Yaml, environment);
          }
        }
      }
    }

    return envGroupPlanCreatorConfigBuilder.name(entity.get().getName())
        .identifier(entity.get().getIdentifier())
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .environmentGroupRef(envGroupYaml.getEnvGroupRef())
        .deployToAll(envGroupYaml.getDeployToAll().getValue())
        .environmentPlanCreatorConfigs(envConfigs)
        .build();
  }

  private void processFiltersForEachEnvironment(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, EnvironmentYamlV2 envYamlV2, List<EnvironmentPlanCreatorConfig> envconfig,
      Environment environment) {
    List<FilterYaml> filterYamls = envYamlV2.getFilters().getValue();

    Map<String, io.harness.cdng.gitops.entity.Cluster> clsToCluster =
        environmentInfraFilterHelper.getClusterRefToNGGitOpsClusterMap(accountIdentifier, orgIdentifier,
            projectIdentifier, Arrays.asList(envYamlV2.getEnvironmentRef().getValue()));

    Set<String> clsRefs = clsToCluster.values().stream().map(e -> e.getClusterRef()).collect(Collectors.toSet());

    if (isEmpty(clsRefs)) {
      log.info("No clusters were qualified after applying filters");
    } else {
      List<io.harness.gitops.models.Cluster> clusterList = environmentInfraFilterHelper.fetchClustersFromGitOps(
          accountIdentifier, orgIdentifier, projectIdentifier, clsRefs);

      Set<io.harness.cdng.gitops.entity.Cluster> filteredClusters =
          environmentInfraFilterHelper.applyFilteringOnClusters(filterYamls, clsToCluster, new HashSet<>(clusterList));

      List<String> filteredClusterRefs =
          filteredClusters.stream().map(Cluster::getClusterRef).collect(Collectors.toList());

      createEnvConfigsForFiltersFlow(envconfig, environment, filteredClusterRefs);
    }
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
