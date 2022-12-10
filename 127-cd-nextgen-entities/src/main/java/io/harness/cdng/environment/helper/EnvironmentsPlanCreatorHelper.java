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

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.cdng.environment.bean.IndividualEnvData;
import io.harness.cdng.environment.filters.FilterYaml;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.environment.yaml.EnvironmentsPlanCreatorConfig;
import io.harness.cdng.environment.yaml.EnvironmentsYaml;
import io.harness.cdng.gitops.entity.Cluster;
import io.harness.cdng.gitops.service.ClusterService;
import io.harness.cdng.gitops.yaml.ClusterYaml;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.environment.beans.Environment;
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

    List<EnvironmentYamlV2> environmentYamlV2s = environmentsYaml.getValues().getValue();

    List<String> envRefs =
        environmentYamlV2s.stream().map(e -> e.getEnvironmentRef().getValue()).collect(Collectors.toList());

    List<Environment> environments = environmentService.fetchesNonDeletedEnvironmentFromListOfIdentifiers(
        accountIdentifier, orgIdentifier, projectIdentifier, envRefs);

    // To fetch the env name. This is required for populating GitOps ClusterRefs
    Map<String, Environment> envMapping =
        emptyIfNull(environments).stream().collect(Collectors.toMap(Environment::getIdentifier, Function.identity()));

    // Filters are specified so no environment exists in the yaml.
    // Apply filtering based on provided filters on all environments and clusters in the environments List
    // If no clusters are eligible then throw an exception.

    Set<IndividualEnvData> listEnvData = new HashSet<>();
    // Apply Filters
    if (featureFlagHelperService.isEnabled(ctx.getAccountIdentifier(), FeatureName.CDS_FILTER_INFRA_CLUSTERS_ON_TAGS)
        && environmentInfraFilterHelper.areFiltersPresent(environmentsYaml)) {
      // Process Environment level Filters
      Set<IndividualEnvData> envsLevelIndividualEnvData = new HashSet<>();
      Set<IndividualEnvData> individualEnvFiltering = new HashSet<>();
      if (isNotEmpty(environmentsYaml.getFilters().getValue())) {
        List<FilterYaml> filterYamls = environmentsYaml.getFilters().getValue();

        Set<Environment> allEnvsInProject = environmentInfraFilterHelper.getAllEnvironmentsInProject(
            accountIdentifier, orgIdentifier, projectIdentifier);

        // Apply filters on environments
        Set<Environment> filteredEnvs = environmentInfraFilterHelper.applyFiltersOnEnvs(allEnvsInProject, filterYamls);

        List<String> filteredEnvRefs =
            filteredEnvs.stream().map(Environment::getIdentifier).collect(Collectors.toList());

        // GetAll ClustersRefs
        Map<String, io.harness.cdng.gitops.entity.Cluster> clsToCluster =
            environmentInfraFilterHelper.getClusterRefToNGGitOpsClusterMap(
                accountIdentifier, orgIdentifier, projectIdentifier, filteredEnvRefs);

        // Apply filtering for clusterRefs for filtered environments
        List<EnvironmentYamlV2> environmentYamlV2List = new ArrayList<>();
        for (Environment env : filteredEnvs) {
          List<Cluster> clustersInEnv = clsToCluster.values()
                                            .stream()
                                            .filter(e -> e.getEnvRef().equals(env.getIdentifier()))
                                            .collect(Collectors.toList());
          if (isNotEmpty(clustersInEnv)) {
            buildIndividualEnvDataList(accountIdentifier, orgIdentifier, projectIdentifier, envsLevelIndividualEnvData,
                filterYamls, clsToCluster, env, clustersInEnv);

            EnvironmentYamlV2 environmentYamlV2 =
                EnvironmentYamlV2.builder()
                    .environmentRef(ParameterField.createValueField(env.getIdentifier()))
                    .build();
            environmentYamlV2List.add(environmentYamlV2);
          }
        }

        environmentsYaml.getValues().setValue(environmentYamlV2List);
      }

      // Process Individual environment level filters if they exist
      if (featureFlagHelperService.isEnabled(ctx.getAccountIdentifier(), FeatureName.CDS_FILTER_INFRA_CLUSTERS_ON_TAGS)
          && environmentInfraFilterHelper.areFiltersSetOnIndividualEnvironments(environmentsYaml)) {
        List<EnvironmentYamlV2> envV2YamlsWithFilters =
            environmentInfraFilterHelper.getEnvV2YamlsWithFilters(environmentsYaml);
        List<String> envRefsWithFilters =
            envV2YamlsWithFilters.stream().map(e -> e.getEnvironmentRef().getValue()).collect(Collectors.toList());

        Map<String, io.harness.cdng.gitops.entity.Cluster> clsToCluster =
            environmentInfraFilterHelper.getClusterRefToNGGitOpsClusterMap(
                accountIdentifier, orgIdentifier, projectIdentifier, envRefsWithFilters);

        for (EnvironmentYamlV2 environmentYamlV2 : envV2YamlsWithFilters) {
          List<Cluster> clustersInEnv =
              clsToCluster.values()
                  .stream()
                  .filter(e -> e.getEnvRef().equals(environmentYamlV2.getEnvironmentRef().getValue()))
                  .collect(Collectors.toList());
          if (isNotEmpty(clustersInEnv)) {
            buildIndividualEnvDataList(accountIdentifier, orgIdentifier, projectIdentifier, individualEnvFiltering,
                environmentYamlV2.getFilters().getValue(), clsToCluster,
                envMapping.get(environmentYamlV2.getEnvironmentRef().getValue()), clustersInEnv);
          }
        }
      }

      // Merge the two sets:
      for (IndividualEnvData envData : envsLevelIndividualEnvData) {
        List<IndividualEnvData> data = individualEnvFiltering.stream()
                                           .filter(ed -> ed.getEnvRef().equals(envData.getEnvRef()))
                                           .collect(Collectors.toList());
        if (isNotEmpty(data)) {
          continue;
        }
        listEnvData.add(envData);
      }
      listEnvData.addAll(individualEnvFiltering);
    }

    if (isNotEmpty(environmentYamlV2s)) {
      for (EnvironmentYamlV2 envV2Yaml : environmentYamlV2s) {
        if (isNotEmpty(envV2Yaml.getFilters().getValue())) {
          log.info("Environment contains filters. It must have been already processed");
          continue;
        }

        if (!envV2Yaml.getDeployToAll().getValue() && isEmpty(envV2Yaml.getGitOpsClusters().getValue())) {
          throw new InvalidRequestException("List of GitOps clusters must be provided");
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
    }
    return EnvironmentsPlanCreatorConfig.builder()
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .individualEnvDataList(new ArrayList<>(listEnvData))
        .build();
  }

  private void buildIndividualEnvDataList(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      Set<IndividualEnvData> listEnvData, List<FilterYaml> filterYamls, Map<String, Cluster> clsToCluster,
      Environment env, List<Cluster> clustersInEnv) {
    Set<String> clsRefs = clustersInEnv.stream().map(Cluster::getClusterRef).collect(Collectors.toSet());

    List<io.harness.gitops.models.Cluster> clusterList = environmentInfraFilterHelper.fetchClustersFromGitOps(
        accountIdentifier, orgIdentifier, projectIdentifier, clsRefs);

    Set<Cluster> filteredClusters =
        environmentInfraFilterHelper.applyFilteringOnClusters(filterYamls, clsToCluster, new HashSet<>(clusterList));
    Set<String> filteredClsRefs = filteredClusters.stream()
                                      .filter(c -> c.getEnvRef().equals(env.getIdentifier()))
                                      .map(Cluster::getClusterRef)
                                      .collect(Collectors.toSet());

    listEnvData.add(getIndividualEnvData(env.getIdentifier(), env.getName(), filteredClsRefs, false));
  }

  private static IndividualEnvData getIndividualEnvData(
      String envRef, String envName, Set<String> filteredClsRefs, boolean isDeployToAll) {
    return IndividualEnvData.builder()
        .envRef(envRef)
        .envName(envName)
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
