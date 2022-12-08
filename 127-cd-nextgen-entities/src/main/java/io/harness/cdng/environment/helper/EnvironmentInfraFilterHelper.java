/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.environment.helper;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.environment.filters.Entity;
import io.harness.cdng.environment.filters.FilterType;
import io.harness.cdng.environment.filters.FilterYaml;
import io.harness.cdng.environment.filters.TagsFilter;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.environment.yaml.EnvironmentsYaml;
import io.harness.cdng.gitops.service.ClusterService;
import io.harness.data.structure.CollectionUtils;
import io.harness.exception.InvalidRequestException;
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
import io.harness.ng.core.mapper.TagMapper;
import io.harness.pms.tags.TagUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.utils.RetryUtils;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
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
  public static final String TAGFILTER_MATCHTYPE_ALL = "all";
  public static final String TAGFILTER_MATCHTYPE_ANY = "any";
  @Inject private GitopsResourceClient gitopsResourceClient;
  @Inject private ClusterService clusterService;
  @Inject private EnvironmentService environmentService;
  @Inject private EnvironmentFilterHelper environmentFilterHelper;

  @Inject private InfrastructureEntityService infrastructureEntityService;

  private static final RetryPolicy<Object> retryPolicyForGitopsClustersFetch = RetryUtils.getRetryPolicy(
      "Error getting clusters from Harness Gitops..retrying", "Failed to fetch clusters from Harness Gitops",
      Collections.singletonList(IOException.class), Duration.ofMillis(10), 3, log);

  public boolean areAllTagFiltersMatching(List<NGTag> entityTags, List<NGTag> tagsInFilter) {
    // Safety check, if list is empty
    if (isEmpty(entityTags)) {
      return false;
    }

    int count = 0;
    for (NGTag tag : tagsInFilter) {
      if (entityTags.contains(tag)) {
        count++;
      }
    }
    return count != 0 && count == tagsInFilter.size();
  }

  public boolean areAnyTagFiltersMatching(List<NGTag> entityTags, List<NGTag> tagsInFilter) {
    if (isEmpty(entityTags)) {
      return false;
    }
    for (NGTag tag : entityTags) {
      if (tagsInFilter.contains(tag)) {
        return true;
      }
    }
    return false;
  }

  /**
   *
   * @param filterYaml - Contains the information of filters along with it's type
   * @param envs - List of environments to apply filters on
   * @return - List of filtered Environments
   */
  public Set<Environment> processTagsFilterYamlForEnvironments(FilterYaml filterYaml, Set<Environment> envs) {
    if (FilterType.all.name().equals(filterYaml.getType().name())) {
      return envs;
    }
    // filter env that match all tags
    Set<Environment> filteredEnvs = new HashSet<>();
    if (FilterType.tags.equals(filterYaml.getType())) {
      TagsFilter tagsFilter = (TagsFilter) filterYaml.getSpec();
      for (Environment environment : envs) {
        if (applyMatchAllFilter(environment.getTags(), tagsFilter)) {
          filteredEnvs.add(environment);
          continue;
        }
        if (applyMatchAnyFilter(environment.getTags(), tagsFilter)) {
          filteredEnvs.add(environment);
          continue;
        }
        if (isSupportedFilter(tagsFilter)) {
          throw new InvalidRequestException(
              String.format("TagFilter of type [%s] is not supported", tagsFilter.getMatchType().getValue().name()));
        }
      }
    }

    return filteredEnvs;
  }

  private boolean applyMatchAnyFilter(List<NGTag> entityTags, TagsFilter tagsFilter) {
    return tagsFilter.getMatchType().getValue().name().equals(TAGFILTER_MATCHTYPE_ANY)
        && areAnyTagFiltersMatching(entityTags, TagMapper.convertToList(tagsFilter.getTags().getValue()));
  }

  private boolean applyMatchAllFilter(List<NGTag> entityTags, TagsFilter tagsFilter) {
    Map<String, String> tagsMap = tagsFilter.getTags().getValue();
    // Remove UUID from tags
    TagUtils.removeUuidFromTags(tagsMap);
    return tagsFilter.getMatchType().getValue().name().equals(TAGFILTER_MATCHTYPE_ALL)
        && areAllTagFiltersMatching(entityTags, TagMapper.convertToList(tagsMap));
  }

  /**
   *
   * @param filterYaml - Contains the information of filters along with it's type
   * @param clusters - List of clusters to apply filters on
   * @param ngGitOpsClusters - Cluster Entity containing tag information for applying filtering
   * @return - List of filtered Clusters
   */
  public List<io.harness.cdng.gitops.entity.Cluster> processTagsFilterYamlForGitOpsClusters(FilterYaml filterYaml,
      Set<Cluster> clusters, Map<String, io.harness.cdng.gitops.entity.Cluster> ngGitOpsClusters) {
    if (FilterType.all.name().equals(filterYaml.getType().name())) {
      return ngGitOpsClusters.values().stream().collect(Collectors.toList());
    }

    List<io.harness.cdng.gitops.entity.Cluster> filteredClusters = new ArrayList<>();
    if (FilterType.tags.equals(filterYaml.getType())) {
      TagsFilter tagsFilter = (TagsFilter) filterYaml.getSpec();
      for (Cluster cluster : clusters) {
        if (applyMatchAllFilter(TagMapper.convertToList(cluster.getTags()), tagsFilter)) {
          filteredClusters.add(ngGitOpsClusters.get(cluster.getIdentifier()));
          continue;
        }
        if (applyMatchAnyFilter(TagMapper.convertToList(cluster.getTags()), tagsFilter)) {
          filteredClusters.add(ngGitOpsClusters.get(cluster.getIdentifier()));
          continue;
        }
        if (isSupportedFilter(tagsFilter)) {
          throw new InvalidRequestException(
              String.format("TagFilter of type [%s] is not supported", tagsFilter.getMatchType().getValue().name()));
        }
      }
    }
    return filteredClusters;
  }

  private static boolean isSupportedFilter(TagsFilter tagsFilter) {
    return !tagsFilter.getMatchType().getValue().name().equals(TAGFILTER_MATCHTYPE_ALL)
        && !tagsFilter.getMatchType().getValue().name().equals(TAGFILTER_MATCHTYPE_ANY);
  }

  /**
   *
   * @param environments - List of environments
   * @param filterYamls - List of FilterYamls
   * @return Applies filters on Environments Entity. Returns the same list of no filter is applied.
   * Throws exception if environments qualify after applying filters
   */
  public Set<Environment> applyFiltersOnEnvs(Set<Environment> environments, Iterable<FilterYaml> filterYamls) {
    Set<Environment> setOfFilteredEnvs = new HashSet<>();

    boolean filterOnEnvExists = false;
    for (FilterYaml filterYaml : filterYamls) {
      if (filterYaml.getEntities().contains(Entity.environments)) {
        filterOnEnvExists = true;
        setOfFilteredEnvs.addAll(processTagsFilterYamlForEnvironments(filterYaml, environments));
      }
    }

    if (!filterOnEnvExists) {
      setOfFilteredEnvs.addAll(environments);
    }

    if (isEmpty(setOfFilteredEnvs) && filterOnEnvExists) {
      log.info("No Environments are eligible for deployment due to applied filters");
    }
    return setOfFilteredEnvs;
  }

  /**
   *
   * @param filterYamls - List of FilterYamls
   * @param clsToCluster - Map of clusterRef to NG GitOps Cluster Entity
   * @param clusters - List of NG GitOpsClusters
   * @return Applies Filters on GitOpsClusters. Returns the same list of no filter is applied.
   * Throws exception if no clusters qualify after applying filters.
   */
  public Set<io.harness.cdng.gitops.entity.Cluster> applyFilteringOnClusters(Iterable<FilterYaml> filterYamls,
      Map<String, io.harness.cdng.gitops.entity.Cluster> clsToCluster, Set<io.harness.gitops.models.Cluster> clusters) {
    Set<io.harness.cdng.gitops.entity.Cluster> setOfFilteredCls = new HashSet<>();

    boolean filterOnClusterExists = false;
    for (FilterYaml filterYaml : filterYamls) {
      if (filterYaml.getEntities().contains(Entity.gitOpsClusters)) {
        setOfFilteredCls.addAll(processTagsFilterYamlForGitOpsClusters(filterYaml, clusters, clsToCluster));
        filterOnClusterExists = true;
      }
    }

    if (!filterOnClusterExists) {
      setOfFilteredCls.addAll(clsToCluster.values());
    }

    if (isEmpty(setOfFilteredCls) && filterOnClusterExists) {
      log.info("No GitOps cluster is eligible after applying filters");
    }
    return setOfFilteredCls;
  }

  /**
   * @param accountId
   * @param orgId
   * @param projectId
   * @param clsRefs - List of clusters for fetching tag information
   * @return Fetch GitOps Clusters from GitOpsService. Throw exception if unable to connect to gitOpsService or if no
   *     clusters are returned
   */
  public List<io.harness.gitops.models.Cluster> fetchClustersFromGitOps(
      String accountId, String orgId, String projectId, Set<String> clsRefs) {
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

    List<io.harness.gitops.models.Cluster> clusterList;
    if (response.isSuccessful() && response.body() != null) {
      clusterList = CollectionUtils.emptyIfNull(response.body().getContent());
    } else {
      throw new InvalidRequestException("Failed to fetch clusters from gitops-service, cannot apply filter");
    }
    return clusterList;
  }

  /**
   * @param accountIdentifier
   * @param orgIdentifier
   * @param projectIdentifier
   * @param envRefs
   * @return Fetch NGGitOps Clusters. These are clusters that are linked in Environments section. Throw Exception if no
   *     clusters are linked.
   */
  public Map<String, io.harness.cdng.gitops.entity.Cluster> getClusterRefToNGGitOpsClusterMap(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, List<String> envRefs) {
    Page<io.harness.cdng.gitops.entity.Cluster> clusters =
        clusterService.listAcrossEnv(0, PAGE_SIZE, accountIdentifier, orgIdentifier, projectIdentifier, envRefs);

    if (isEmpty(clusters.getContent())) {
      log.info("There are no gitOpsClusters linked to Environments");
    }

    Map<String, io.harness.cdng.gitops.entity.Cluster> clsToCluster = new HashMap<>();
    clusters.getContent().forEach(k -> clsToCluster.put(k.getClusterRef(), k));
    return clsToCluster;
  }

  public Set<Environment> getAllEnvironmentsInProject(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    // Fetch All Environments
    Criteria criteria = environmentFilterHelper.createCriteriaForGetList(
        accountIdentifier, orgIdentifier, projectIdentifier, false, "");

    PageRequest pageRequest = PageRequest.of(0, PAGE_SIZE, Sort.by(Sort.Direction.DESC, EnvironmentKeys.createdAt));
    Page<Environment> allEnvsInProject = environmentService.list(criteria, pageRequest);
    if (isEmpty(allEnvsInProject.getContent())) {
      throw new InvalidRequestException(
          "Filters are applied for environments, but no enviroments exists for the project");
    }
    return new HashSet<>(allEnvsInProject.getContent());
  }

  public Set<InfrastructureEntity> getInfrastructureForEnvironmentList(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String envIdentifier) {
    List<InfrastructureEntity> infrastructureEntityList =
        infrastructureEntityService.getAllInfrastructureFromEnvIdentifier(
            accountIdentifier, orgIdentifier, projectIdentifier, envIdentifier);
    return new HashSet<>(infrastructureEntityList);
  }

  public Set<InfrastructureEntity> processTagsFilterYamlForInfraStructures(
      FilterYaml filterYaml, Set<InfrastructureEntity> infras) {
    if (filterYaml.getType().name().equals(FilterType.all.name())) {
      return infras;
    }
    // filter env that match all tags
    Set<InfrastructureEntity> filteredInfras = new HashSet<>();
    if (filterYaml.getType().equals(FilterType.tags)) {
      TagsFilter tagsFilter = (TagsFilter) filterYaml.getSpec();
      for (InfrastructureEntity infra : infras) {
        if (applyMatchAllFilter(infra.getTags(), tagsFilter)) {
          filteredInfras.add(infra);
          continue;
        }
        if (applyMatchAnyFilter(infra.getTags(), tagsFilter)) {
          filteredInfras.add(infra);
          continue;
        }
        if (isSupportedFilter(tagsFilter)) {
          throw new InvalidRequestException(
              String.format("TagFilter of type [%s] is not supported", tagsFilter.getMatchType().getValue().name()));
        }
      }
    }

    return filteredInfras;
  }

  public Set<InfrastructureEntity> applyFilteringOnInfras(
      Iterable<FilterYaml> filterYamls, Set<InfrastructureEntity> infras) {
    Set<InfrastructureEntity> setOfFilteredInfras = new HashSet<>();

    boolean filterOnInfraExists = false;
    for (FilterYaml filterYaml : filterYamls) {
      if (filterYaml.getEntities().contains(Entity.infrastructures)) {
        filterOnInfraExists = true;
        setOfFilteredInfras.addAll(processTagsFilterYamlForInfraStructures(filterYaml, infras));
      }
    }

    if (!filterOnInfraExists) {
      setOfFilteredInfras.addAll(infras);
    }

    if (isEmpty(setOfFilteredInfras) && filterOnInfraExists) {
      log.info("No Environments are eligible for deployment due to applied filters");
    }
    return setOfFilteredInfras;
  }

  public boolean areFiltersPresent(EnvironmentsYaml environmentsYaml) {
    return (ParameterField.isNotNull(environmentsYaml.getFilters())
               && isNotEmpty(environmentsYaml.getFilters().getValue()))
        || areFiltersSetOnIndividualEnvironments(environmentsYaml);
  }

  public boolean areFiltersSetOnIndividualEnvironments(EnvironmentsYaml environmentsYaml) {
    if (ParameterField.isNull(environmentsYaml.getValues())) {
      return false;
    }
    List<EnvironmentYamlV2> envV2YamlsWithFilters = getEnvV2YamlsWithFilters(environmentsYaml);
    return isNotEmpty(envV2YamlsWithFilters);
  }

  public List<EnvironmentYamlV2> getEnvV2YamlsWithFilters(EnvironmentsYaml environmentsYaml) {
    return environmentsYaml.getValues()
        .getValue()
        .stream()
        .filter(e -> ParameterField.isNotNull(e.getFilters()))
        .collect(Collectors.toList());
  }
}
