/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.environment.helper;

import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.cdng.envGroup.beans.EnvironmentGroupEntity;
import io.harness.cdng.envGroup.services.EnvironmentGroupService;
import io.harness.cdng.envgroup.yaml.EnvironmentGroupYaml;
import io.harness.cdng.environment.filters.FilterType;
import io.harness.cdng.environment.filters.FilterYaml;
import io.harness.cdng.environment.filters.TagsFilter;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.environment.yaml.EnvironmentsYaml;
import io.harness.cdng.gitops.service.ClusterService;
import io.harness.cdng.gitops.steps.EnvClusterRefs;
import io.harness.cdng.gitops.yaml.ClusterYaml;
import io.harness.cdng.infra.yaml.InfraStructureDefinitionYaml;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.data.structure.CollectionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.gitops.models.Cluster;
import io.harness.gitops.models.ClusterQuery;
import io.harness.gitops.remote.GitopsResourceClient;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.Environment.EnvironmentKeys;
import io.harness.ng.core.environment.mappers.EnvironmentFilterHelper;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.utils.CoreCriteriaUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.utils.RetryUtils;
import io.harness.utils.ScopeWiseIds;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import retrofit2.Response;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
@Singleton
public class EnvironmentInfraFilterHelper {
  public static final int PAGE_SIZE = 1000;
  public static final String SERVICE_TAGS = "<+service.tags>";
  @Inject private GitopsResourceClient gitopsResourceClient;
  @Inject private ClusterService clusterService;
  @Inject private EnvironmentService environmentService;
  @Inject private EnvironmentFilterHelper environmentFilterHelper;

  @Inject private InfrastructureEntityService infrastructureEntityService;
  @Inject private NGFeatureFlagHelperService featureFlagHelperService;
  @Inject private EnvironmentGroupService environmentGroupService;

  private static final RetryPolicy<Object> retryPolicyForGitopsClustersFetch = RetryUtils.getRetryPolicy(
      "Error getting clusters from Harness Gitops..retrying", "Failed to fetch clusters from Harness Gitops",
      Collections.singletonList(IOException.class), Duration.ofMillis(10), 3, log);

  /**
   * @param accountId
   * @param orgId
   * @param projectId
   * @param clsRefs   - List of clusters for fetching tag information
   * @return Fetch GitOps Clusters from GitOpsService. Throw exception if unable to connect to gitOpsService or if no
   * clusters are returned
   */
  public List<io.harness.gitops.models.Cluster> fetchClustersFromGitOps(
      String accountId, String orgId, String projectId, Set<String> clsRefs) {
    List<io.harness.gitops.models.Cluster> clusters = new ArrayList<>();
    ScopeWiseIds scopeWiseIds = IdentifierRefHelper.getScopeWiseIds(accountId, orgId, projectId, clsRefs);
    clusters.addAll(getClusters(accountId, orgId, projectId, scopeWiseIds.getProjectScopedIds()));
    clusters.addAll(getClusters(accountId, orgId, null, scopeWiseIds.getOrgScopedIds()));
    clusters.addAll(getClusters(accountId, null, null, scopeWiseIds.getAccountScopedIds()));
    return clusters;
  }

  @NotNull
  private List<Cluster> getClusters(String accountId, String orgId, String projectId, List<String> clsRefs) {
    if (isEmpty(clsRefs)) {
      return new ArrayList<>();
    }
    Map<String, Object> filter = ImmutableMap.of("identifier", ImmutableMap.of("$in", clsRefs));
    final ClusterQuery query = ClusterQuery.builder()
                                   .accountId(accountId)
                                   .orgIdentifier(orgId)
                                   .projectIdentifier(projectId)
                                   .pageIndex(0)
                                   .pageSize(clsRefs.size())
                                   .filter(filter)
                                   .build();
    final Response<PageResponse<Cluster>> response =
        Failsafe.with(retryPolicyForGitopsClustersFetch).get(() -> gitopsResourceClient.listClusters(query).execute());

    if (response.isSuccessful() && response.body() != null) {
      return CollectionUtils.emptyIfNull(response.body().getContent());
    } else {
      log.error("Failed to fetch clusters from gitops-service {}", response.errorBody());
      throw new InvalidRequestException("Failed to fetch clusters from gitops-service, cannot apply filter");
    }
  }

  /**
   * @param accountIdentifier
   * @param orgIdentifier
   * @param projectIdentifier
   * @param envRefs
   * @return Fetch NGGitOps Clusters. These are clusters that are linked in Environments section. Throw Exception if no
   * clusters are linked.
   */
  public List<io.harness.cdng.gitops.entity.Cluster> getNGClusters(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, List<String> envRefs) {
    return clusterService.listAcrossEnv(0, PAGE_SIZE, accountIdentifier, orgIdentifier, projectIdentifier, envRefs);
  }

  public Set<Environment> getAllEnvironmentsFromAllScopes(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    Set<Environment> envs = new HashSet<>();

    envs.addAll(getAllEnvs(accountIdentifier, orgIdentifier, projectIdentifier));
    envs.addAll(getAllEnvs(accountIdentifier, orgIdentifier, null));
    envs.addAll(getAllEnvs(accountIdentifier, null, null));

    if (isEmpty(envs)) {
      throw new InvalidRequestException("No environments found at Project/Org/Account Levels", WingsException.USER);
    }
    return envs;
  }

  @NotNull
  private Set<Environment> getAllEnvs(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    Criteria criteria =
        CoreCriteriaUtils.createCriteriaForGetList(accountIdentifier, orgIdentifier, projectIdentifier, false);
    PageRequest pageRequest = PageRequest.of(0, PAGE_SIZE, Sort.by(Sort.Direction.DESC, EnvironmentKeys.createdAt));
    Page<Environment> envPageResponse = environmentService.list(criteria, pageRequest);
    if (isNotEmpty(envPageResponse.getContent())) {
      return new HashSet<>(envPageResponse.getContent());
    }
    return new HashSet<>();
  }

  public Set<InfrastructureEntity> getInfrastructureForEnvironmentList(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String envRef, ServiceDefinitionType deploymentType) {
    List<InfrastructureEntity> infrastructureEntityList =
        infrastructureEntityService.getAllInfrastructureFromEnvRefAndDeploymentType(
            accountIdentifier, orgIdentifier, projectIdentifier, envRef, deploymentType);
    return new HashSet<>(infrastructureEntityList);
  }

  public static List<EnvironmentYamlV2> getEnvYamlV2WithFilters(
      ParameterField<List<EnvironmentYamlV2>> environmentYamlV2s) {
    return environmentYamlV2s.getValue()
        .stream()
        .filter(eg -> ParameterField.isNotNull(eg.getFilters()))
        .collect(Collectors.toList());
  }

  public boolean isServiceTagsExpressionPresent(EnvironmentsYaml environments) {
    if (environments == null) {
      return false;
    }
    ParameterField<List<FilterYaml>> filters = environments.getFilters();
    if (ParameterField.isNotNull(filters) && isServiceTagsExpressionPresent(filters)) {
      return true;
    }
    ParameterField<List<EnvironmentYamlV2>> environmentsValues = environments.getValues();
    return ParameterField.isNotNull(environmentsValues)
        && isServiceTagsExpressionPresent(environmentsValues.getValue());
  }

  private boolean isServiceTagsExpressionPresent(List<EnvironmentYamlV2> environments) {
    if (isEmpty(environments)) {
      return false;
    }
    return environments.stream()
        .map(EnvironmentYamlV2::getFilters)
        .anyMatch(filters -> ParameterField.isNotNull(filters) && isServiceTagsExpressionPresent(filters));
  }

  private boolean isServiceTagsExpressionPresent(ParameterField<List<FilterYaml>> filters) {
    if (ParameterField.isNull(filters) || isEmpty(filters.getValue())) {
      return false;
    }
    for (FilterYaml filterYaml : filters.getValue()) {
      if (FilterType.tags == filterYaml.getType()) {
        ParameterField<Map<String, String>> tags = ((TagsFilter) filterYaml.getSpec()).getTags();
        if (tags.isExpression() && tags.getExpressionValue().equals(SERVICE_TAGS)) {
          return true;
        }
      }
    }
    return false;
  }

  public boolean isServiceTagsExpressionPresent(EnvironmentGroupYaml environmentGroup) {
    if (environmentGroup == null) {
      return false;
    }
    ParameterField<List<FilterYaml>> filters = environmentGroup.getFilters();
    if (isServiceTagsExpressionPresent(filters)) {
      return true;
    }
    ParameterField<List<EnvironmentYamlV2>> environments = environmentGroup.getEnvironments();
    return ParameterField.isNotNull(environments) && isServiceTagsExpressionPresent(environments.getValue());
  }

  public void processEnvInfraFiltering(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      EnvironmentsYaml environments, EnvironmentGroupYaml environmentGroup, ServiceDefinitionType deploymentType) {
    if (featureFlagHelperService.isEnabled(accountIdentifier, FeatureName.CDS_FILTER_INFRA_CLUSTERS_ON_TAGS)) {
      if (EnvironmentInfraFilterUtils.areFiltersPresent(environments)) {
        Set<Environment> allPossibleEnvs =
            getAllEnvironmentsFromAllScopes(accountIdentifier, orgIdentifier, projectIdentifier);
        List<EnvironmentYamlV2> finalyamlV2List =
            processFilteringForEnvironmentsLevelFilters(accountIdentifier, orgIdentifier, projectIdentifier,
                environments.getFilters(), environments.getValues(), allPossibleEnvs, deploymentType);
        // Set the filtered envYamlV2 in the environments yaml so normal processing continues
        environments.setValues(ParameterField.createValueField(finalyamlV2List));
      }

      // If deploying to environment group with filters
      if (EnvironmentInfraFilterUtils.areFiltersPresent(environmentGroup)) {
        final Optional<EnvironmentGroupEntity> entity = environmentGroupService.get(
            accountIdentifier, orgIdentifier, projectIdentifier, environmentGroup.getEnvGroupRef().getValue(), false);

        if (entity.isEmpty()) {
          throw new InvalidRequestException(
              format("No environment group found with %s identifier in %s project in %s org",
                  environmentGroup.getEnvGroupRef().getValue(), projectIdentifier, orgIdentifier));
        }

        List<Environment> allPossibleEnvs =
            environmentService.fetchesNonDeletedEnvironmentFromListOfIdentifiers(accountIdentifier,
                entity.get().getOrgIdentifier(), entity.get().getProjectIdentifier(), entity.get().getEnvIdentifiers());

        updateEnvironmentsWithEnvGroupScope(environmentGroup.getEnvGroupRef(), environmentGroup.getEnvironments());
        List<EnvironmentYamlV2> finalyamlV2List = processFilteringForEnvironmentsLevelFilters(accountIdentifier,
            orgIdentifier, projectIdentifier, environmentGroup.getFilters(), environmentGroup.getEnvironments(),
            new HashSet<>(allPossibleEnvs), deploymentType);
        // Set the filtered envYamlV2 in the environmentGroup yaml so normal processing continues
        environmentGroup.setEnvironments(ParameterField.createValueField(finalyamlV2List));
      }
    } else {
      if (EnvironmentInfraFilterUtils.areFiltersPresent(environments)
          || EnvironmentInfraFilterUtils.areFiltersPresent(environmentGroup)) {
        throw new InvalidRequestException(
            "Pipeline contains filters but Feature Flag: [CDS_FILTER_INFRA_CLUSTERS_ON_TAGS] is disabled. Please enable the FF or remove Filters.",
            WingsException.USER);
      }
    }
  }

  @NotNull
  private List<EnvironmentYamlV2> processFilteringForEnvironmentsLevelFilters(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, ParameterField<List<FilterYaml>> filters,
      ParameterField<List<EnvironmentYamlV2>> envYamls, Set<Environment> allPossibleEnvs,
      ServiceDefinitionType deploymentType) {
    List<EnvironmentYamlV2> finalyamlV2List;
    Set<EnvironmentYamlV2> envsLevelEnvironmentYamlV2 = new LinkedHashSet<>();
    if (ParameterField.isNotNull(filters) && isNotEmpty(filters.getValue())) {
      List<EnvironmentYamlV2> filteredEnvList = processEnvironmentInfraFilters(
          accountIdentifier, orgIdentifier, projectIdentifier, filters.getValue(), allPossibleEnvs, deploymentType);
      envsLevelEnvironmentYamlV2.addAll(filteredEnvList);
      return new ArrayList<>(envsLevelEnvironmentYamlV2);
    }

    // Process filtering at individual Environment level
    Set<EnvironmentYamlV2> individualEnvironmentYamlV2 = new LinkedHashSet<>();
    if (EnvironmentInfraFilterUtils.areFiltersSetOnIndividualEnvironments(envYamls)) {
      individualEnvironmentYamlV2.addAll(processFiltersOnIndividualEnvironmentsLevel(
          accountIdentifier, orgIdentifier, projectIdentifier, getEnvYamlV2WithFilters(envYamls), deploymentType));
    }

    // Merge with envs with fixed infras
    finalyamlV2List = getFinalEnvsList(envYamls.getValue(), new ArrayList<>(individualEnvironmentYamlV2));

    if (isEmpty(finalyamlV2List)) {
      throw new InvalidRequestException("No Infrastructures found after applying filtering");
    }
    return finalyamlV2List;
  }

  @NotNull
  private static List<EnvironmentYamlV2> getFinalEnvsList(
      List<EnvironmentYamlV2> envsFromYaml, List<EnvironmentYamlV2> mergedFilteredEnvs) {
    List<EnvironmentYamlV2> finalyamlV2List = new ArrayList<>();
    if (isNotEmpty(envsFromYaml)) {
      for (EnvironmentYamlV2 e : envsFromYaml) {
        List<EnvironmentYamlV2> list = mergedFilteredEnvs.stream()
                                           .filter(in -> in.getEnvironmentRef().equals(e.getEnvironmentRef()))
                                           .collect(Collectors.toList());
        if (isNotEmpty(list) || ParameterField.isNull(e.getInfrastructureDefinitions())
            || isEmpty(e.getInfrastructureDefinitions().getValue())) {
          continue;
        }
        finalyamlV2List.add(e);
      }
    }
    finalyamlV2List.addAll(mergedFilteredEnvs);
    return finalyamlV2List;
  }

  private List<EnvironmentYamlV2> processEnvironmentInfraFilters(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, List<FilterYaml> filterYamls, Set<Environment> allPossibleEnvs,
      ServiceDefinitionType deploymentType) {
    // Apply filters on environments
    Set<Environment> filteredEnvs = EnvironmentInfraFilterUtils.applyFiltersOnEnvs(allPossibleEnvs, filterYamls);

    // Get All InfraDefinitions
    List<EnvironmentYamlV2> environmentYamlV2List = new ArrayList<>();
    for (Environment env : filteredEnvs) {
      environmentYamlV2List.addAll(getEnvYamlV2AfterFiltering(
          accountIdentifier, orgIdentifier, projectIdentifier, filterYamls, env.fetchRef(), deploymentType));
    }
    if (isEmpty(environmentYamlV2List)) {
      throw new InvalidRequestException("No Infrastructures found after applying filtering");
    }
    return environmentYamlV2List;
  }

  private List<EnvironmentYamlV2> getEnvYamlV2AfterFiltering(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, List<FilterYaml> filterYamls, String envRef, ServiceDefinitionType deploymentType) {
    List<EnvironmentYamlV2> environmentYamlV2List = new ArrayList<>();
    Set<InfrastructureEntity> infrastructureEntitySet = getInfrastructureForEnvironmentList(
        accountIdentifier, orgIdentifier, projectIdentifier, envRef, deploymentType);

    if (isNotEmpty(infrastructureEntitySet)) {
      List<EnvironmentYamlV2> temp = filterInfras(filterYamls, envRef, infrastructureEntitySet);
      environmentYamlV2List.addAll(temp);
    }
    return environmentYamlV2List;
  }

  public List<EnvironmentYamlV2> filterInfras(
      List<FilterYaml> filterYamls, String env, Set<InfrastructureEntity> infrastructureEntitySet) {
    List<EnvironmentYamlV2> environmentYamlV2List = new ArrayList<>();
    Set<InfrastructureEntity> filteredInfras =
        EnvironmentInfraFilterUtils.applyFilteringOnInfras(filterYamls, infrastructureEntitySet);

    if (isNotEmpty(filteredInfras)) {
      List<InfraStructureDefinitionYaml> infraDefYamlList =
          filteredInfras.stream().map(this::createInfraDefinitionYaml).collect(Collectors.toList());

      EnvironmentYamlV2 environmentYamlV2 =
          EnvironmentYamlV2.builder()
              .environmentRef(ParameterField.createValueField(env))
              .infrastructureDefinitions(ParameterField.createValueField(infraDefYamlList))
              .build();

      environmentYamlV2List.add(environmentYamlV2);
    }
    return environmentYamlV2List;
  }

  @VisibleForTesting
  protected InfraStructureDefinitionYaml createInfraDefinitionYaml(InfrastructureEntity infrastructureEntity) {
    return InfraStructureDefinitionYaml.builder()
        .identifier(ParameterField.createValueField(infrastructureEntity.getIdentifier()))
        .build();
  }

  private List<EnvironmentYamlV2> processFiltersOnIndividualEnvironmentsLevel(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, List<EnvironmentYamlV2> envV2YamlsWithFilters,
      ServiceDefinitionType deploymentType) {
    return envV2YamlsWithFilters.stream()
        .flatMap(envYamlV2
            -> getEnvYamlV2AfterFiltering(accountIdentifier, orgIdentifier, projectIdentifier,
                envYamlV2.getFilters().getValue(), envYamlV2.getEnvironmentRef().getValue(), deploymentType)
                   .stream())
        .collect(Collectors.toList());
  }

  public void resolveServiceTags(ParameterField<List<FilterYaml>> filters, List<NGTag> serviceTags) {
    if (isEmpty(serviceTags)) {
      return;
    }
    if (ParameterField.isNotNull(filters) && isNotEmpty(filters.getValue())) {
      for (FilterYaml filterYaml : filters.getValue()) {
        if (filterYaml.getType().equals(FilterType.tags)) {
          TagsFilter tagsFilter = (TagsFilter) filterYaml.getSpec();
          if (tagsFilter.getTags().isExpression() && tagsFilter.getTags().getExpressionValue().contains(SERVICE_TAGS)) {
            tagsFilter.setTags(ParameterField.createValueField(getTagsMap(serviceTags)));
          }
        }
      }
    }
  }

  private Map<String, String> getTagsMap(List<NGTag> ngTagList) {
    Map<String, String> tags = new LinkedHashMap<>();
    for (NGTag ngTag : ngTagList) {
      tags.put(ngTag.getKey(), ngTag.getValue());
    }
    return tags;
  }

  public static List<NGTag> getNGTags(Map<String, String> tags) {
    if (tags == null) {
      return Collections.emptyList();
    }
    List<NGTag> tagsList = new ArrayList<>();
    for (Map.Entry<String, String> tag : tags.entrySet()) {
      tagsList.add(NGTag.builder().key(tag.getKey()).value(tag.getValue()).build());
    }
    return tagsList;
  }

  public List<EnvClusterRefs> filterEnvGroupAndClusters(EnvironmentGroupYaml envGroupYaml, List<NGTag> serviceTags,
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    final Optional<EnvironmentGroupEntity> entity = environmentGroupService.get(
        accountIdentifier, orgIdentifier, projectIdentifier, envGroupYaml.getEnvGroupRef().getValue(), false);

    if (entity.isEmpty()) {
      throw new InvalidRequestException(format("No environment group found with %s identifier in %s project in %s org",
          envGroupYaml.getEnvGroupRef().getValue(), projectIdentifier, orgIdentifier));
    }

    EnvironmentGroupEntity environmentGroupEntity = entity.get();

    if (ParameterField.isNotNull(envGroupYaml.getFilters()) && isNotEmpty(envGroupYaml.getFilters().getValue())) {
      List<Environment> allPossibleEnvs = environmentService.fetchesNonDeletedEnvironmentFromListOfIdentifiers(
          accountIdentifier, environmentGroupEntity.getOrgIdentifier(), environmentGroupEntity.getProjectIdentifier(),
          environmentGroupEntity.getEnvIdentifiers());
      return processFiltersOnAllEnvs(
          serviceTags, accountIdentifier, orgIdentifier, projectIdentifier, allPossibleEnvs, envGroupYaml.getFilters());
    }
    updateEnvironmentsWithEnvGroupScope(envGroupYaml.getEnvGroupRef(), envGroupYaml.getEnvironments());
    return processClusterFiltersInEnvs(
        serviceTags, accountIdentifier, orgIdentifier, projectIdentifier, envGroupYaml.getEnvironments());
  }

  private void updateEnvironmentsWithEnvGroupScope(
      @javax.validation.constraints.NotNull ParameterField<String> envGroupRef,
      ParameterField<List<EnvironmentYamlV2>> environments) {
    if (ParameterField.isNull(environments) || isEmpty(environments.getValue())) {
      return;
    }
    for (EnvironmentYamlV2 environmentYamlV2 : environments.getValue()) {
      ParameterField<String> environmentRef = environmentYamlV2.getEnvironmentRef();
      if (!ParameterField.isNull(environmentRef) && isNotEmpty(environmentRef.getValue())) {
        String envRefWithScope = EnvironmentStepsUtils.inheritEnvGroupScope(environmentRef.getValue(), envGroupRef);
        environmentRef.setValue(envRefWithScope);
      }
    }
  }

  @NotNull
  private List<EnvClusterRefs> processClusterFiltersInEnvs(List<NGTag> serviceTags, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, ParameterField<List<EnvironmentYamlV2>> envYamls) {
    List<String> envRefs = envYamls.getValue()
                               .stream()
                               .map(environmentYamlV2 -> environmentYamlV2.getEnvironmentRef().getValue())
                               .collect(Collectors.toList());

    List<Environment> environments = environmentService.fetchesNonDeletedEnvironmentFromListOfRefs(
        accountIdentifier, orgIdentifier, projectIdentifier, envRefs);

    if (isEmpty(environments)) {
      throw new InvalidRequestException("No environments found for refs: " + envRefs);
    }

    Map<String, Environment> envMapping =
        emptyIfNull(environments).stream().collect(Collectors.toMap(Environment::fetchRef, Function.identity()));

    List<EnvClusterRefs> envClusterRefs = new ArrayList<>();
    for (EnvironmentYamlV2 environmentYamlV2 : envYamls.getValue()) {
      if (ParameterField.isNotNull(environmentYamlV2.getFilters())
          && isNotEmpty(environmentYamlV2.getFilters().getValue())) {
        // This code can be optimized in future by batching for all envs with filters
        envClusterRefs.addAll(processClusterFiltersInEnv(environmentYamlV2, serviceTags, accountIdentifier,
            orgIdentifier, projectIdentifier, envMapping.get(environmentYamlV2.getEnvironmentRef().getValue())));
      } else {
        envClusterRefs.add(getEnvClusterRef(envMapping, environmentYamlV2));
      }
    }
    if (isEmpty(envClusterRefs)) {
      throw new InvalidRequestException("No Clusters found after applying filtering.");
    }
    return envClusterRefs;
  }

  private EnvClusterRefs getEnvClusterRef(Map<String, Environment> envMapping, EnvironmentYamlV2 envYamlV2) {
    Environment environment = envMapping.get(envYamlV2.getEnvironmentRef().getValue());
    return EnvClusterRefs.builder()
        .envRef(environment.fetchRef())
        .envName(environment.getName())
        .envType(environment.getType().name())
        .clusterRefs(new HashSet<>(getClusterRefs(envYamlV2)))
        .deployToAll(envYamlV2.getDeployToAll().getValue())
        .build();
  }

  public List<String> getClusterRefs(EnvironmentYamlV2 environmentV2) {
    if (!environmentV2.getDeployToAll().getValue()) {
      return environmentV2.getGitOpsClusters()
          .getValue()
          .stream()
          .map(ClusterYaml::getIdentifier)
          .map(ParameterField::getValue)
          .collect(Collectors.toList());
    }
    return new ArrayList<>();
  }

  private List<EnvClusterRefs> processClusterFiltersInEnv(EnvironmentYamlV2 envYamlV2, List<NGTag> serviceTags,
      String accountIdentifier, String orgIdentifier, String projectIdentifier, Environment environment) {
    List<FilterYaml> filterYamls = envYamlV2.getFilters().getValue();
    resolveServiceTags(envYamlV2.getFilters(), serviceTags);

    List<io.harness.cdng.gitops.entity.Cluster> ngClusters = getNGClusters(
        accountIdentifier, orgIdentifier, projectIdentifier, Arrays.asList(envYamlV2.getEnvironmentRef().getValue()));

    Set<String> clsRefs =
        ngClusters.stream().map(io.harness.cdng.gitops.entity.Cluster::getClusterRef).collect(Collectors.toSet());

    List<EnvClusterRefs> envClusterRefs = new ArrayList<>();

    if (isEmpty(clsRefs)) {
      log.info(String.format("No clusters found in environment: [%s]", envYamlV2.getEnvironmentRef().getValue()));
      return Collections.emptyList();
    }
    List<io.harness.gitops.models.Cluster> clusterList =
        fetchClustersFromGitOps(accountIdentifier, orgIdentifier, projectIdentifier, clsRefs);

    Set<io.harness.cdng.gitops.entity.Cluster> filteredClusters =
        EnvironmentInfraFilterUtils.applyFilteringOnClusters(filterYamls, ngClusters, new HashSet<>(clusterList));

    List<String> filteredClusterRefs = filteredClusters.stream()
                                           .map(io.harness.cdng.gitops.entity.Cluster::getClusterRef)
                                           .collect(Collectors.toList());

    if (isNotEmpty(filteredClusterRefs)) {
      envClusterRefs.add(EnvClusterRefs.builder()
                             .envRef(environment.fetchRef())
                             .envName(environment.getName())
                             .envType(environment.getType().name())
                             .clusterRefs(new HashSet<>(filteredClusterRefs))
                             .build());
    }

    return envClusterRefs;
  }

  @NotNull
  private List<EnvClusterRefs> processFiltersOnAllEnvs(List<NGTag> serviceTags, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, List<Environment> allEnvs,
      ParameterField<List<FilterYaml>> filters) {
    List<FilterYaml> filterYamls = filters.getValue();

    resolveServiceTags(filters, serviceTags);

    // Environment Filtering applied
    Set<Environment> filteredEnvs = EnvironmentInfraFilterUtils.applyFiltersOnEnvs(new HashSet<>(allEnvs), filterYamls);

    List<String> filteredEnvRefs = filteredEnvs.stream().map(Environment::fetchRef).collect(Collectors.toList());

    List<io.harness.cdng.gitops.entity.Cluster> ngclusters =
        getNGClusters(accountIdentifier, orgIdentifier, projectIdentifier, filteredEnvRefs);

    Set<String> clsRefs =
        ngclusters.stream().map(io.harness.cdng.gitops.entity.Cluster::getClusterRef).collect(Collectors.toSet());

    if (isEmpty(clsRefs)) {
      throw new InvalidRequestException("No clusters found in the filtered Environments");
    }
    List<Cluster> clusterList = fetchClustersFromGitOps(accountIdentifier, orgIdentifier, projectIdentifier, clsRefs);

    Set<io.harness.cdng.gitops.entity.Cluster> filteredClusters =
        EnvironmentInfraFilterUtils.applyFilteringOnClusters(filterYamls, ngclusters, new HashSet<>(clusterList));

    List<EnvClusterRefs> envClusterRefs = new ArrayList<>();
    for (Environment env : filteredEnvs) {
      List<io.harness.cdng.gitops.entity.Cluster> clustersInEnv =
          filteredClusters.stream()
              .filter(e -> e.getEnvRef() != null && e.fetchEnvRef().equals(env.fetchRef()))
              .collect(Collectors.toList());
      List<String> filteredClusterRefs =
          clustersInEnv.stream().map(io.harness.cdng.gitops.entity.Cluster::getClusterRef).collect(Collectors.toList());

      if (isNotEmpty(filteredClusterRefs)) {
        envClusterRefs.add(EnvClusterRefs.builder()
                               .envRef(env.fetchRef())
                               .envName(env.getName())
                               .envType(env.getType().name())
                               .clusterRefs(new HashSet<>(filteredClusterRefs))
                               .build());
      }
    }
    if (isEmpty(envClusterRefs)) {
      throw new InvalidRequestException("No Clusters found after applying filtering.");
    }
    return envClusterRefs;
  }

  public List<EnvClusterRefs> filterEnvsAndClusters(EnvironmentsYaml environmentsYaml, List<NGTag> serviceTags,
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    List<Environment> allPossibleEnvs =
        new ArrayList<>(getAllEnvironmentsFromAllScopes(accountIdentifier, orgIdentifier, projectIdentifier));

    if (ParameterField.isNotNull(environmentsYaml.getFilters())
        && isNotEmpty(environmentsYaml.getFilters().getValue())) {
      return processFiltersOnAllEnvs(serviceTags, accountIdentifier, orgIdentifier, projectIdentifier, allPossibleEnvs,
          environmentsYaml.getFilters());
    }
    return processClusterFiltersInEnvs(
        serviceTags, accountIdentifier, orgIdentifier, projectIdentifier, environmentsYaml.getValues());
  }
}
