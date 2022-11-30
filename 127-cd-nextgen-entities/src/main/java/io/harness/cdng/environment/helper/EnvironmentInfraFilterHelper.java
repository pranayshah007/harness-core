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
import io.harness.data.structure.CollectionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.gitops.models.Cluster;
import io.harness.gitops.models.ClusterQuery;
import io.harness.gitops.remote.GitopsResourceClient;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.utils.RetryUtils;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import retrofit2.Response;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
@Singleton
public class EnvironmentInfraFilterHelper {
  @Inject private GitopsResourceClient gitopsResourceClient;

  private static final RetryPolicy<Object> retryPolicyForGitopsClustersFetch = RetryUtils.getRetryPolicy(
      "Error getting clusters from Harness Gitops..retrying", "Failed to fetch clusters from Harness Gitops",
      Collections.singletonList(IOException.class), Duration.ofMillis(10), 3, log);

  public boolean areAllTagFiltersMatching(List<NGTag> envTags, List<NGTag> listOfTags) {
    List<NGTag> differences = envTags.stream().filter(t -> listOfTags.contains(t)).collect(Collectors.toList());
    return isNotEmpty(differences) ? true : false;
  }

  public boolean areAnyTagFiltersMatching(List<NGTag> envTags, List<NGTag> listOfTags) {
    List<NGTag> differences = envTags.stream().filter(t -> listOfTags.contains(t)).collect(Collectors.toList());
    // If number of tags in filter is equal to number of tags in
    // difference means there were no matches
    return differences.size() != listOfTags.size() ? true : false;
  }

  public List<Environment> processTagsFilterYamlForEnvironments(FilterYaml filterYaml, List<Environment> envs) {
    List<Environment> filteredEnvs = new ArrayList<>();
    // filter env that match all tags
    if (filterYaml.getType().equals(FilterType.tags)) {
      TagsFilter tagsFilter = (TagsFilter) filterYaml.getSpec();
      for (Environment environment : envs) {
        if (tagsFilter.getMatchType().name().equals("all")
            && areAllTagFiltersMatching(environment.getTags(), tagsFilter.getTags())) {
          filteredEnvs.add(environment);
        }
        if (tagsFilter.getMatchType().name().equals("any")
            && areAnyTagFiltersMatching(environment.getTags(), tagsFilter.getTags())) {
          filteredEnvs.add(environment);
        }
      }
    }

    if (filterYaml.getType().name().equals(FilterType.all)) {
      return envs;
    }
    return filteredEnvs;
  }

  public List<io.harness.cdng.gitops.entity.Cluster> processTagsFilterYamlForGitOpsClusters(FilterYaml filterYaml,
      List<Cluster> clusters, Map<String, io.harness.cdng.gitops.entity.Cluster> ngGitOpsClusters) {
    List<io.harness.cdng.gitops.entity.Cluster> filteredClusters = new ArrayList<>();
    // filter env that match all tags
    if (filterYaml.getType().equals(FilterType.tags)) {
      TagsFilter tagsFilter = (TagsFilter) filterYaml.getSpec();
      for (Cluster cluster : clusters) {
        if (tagsFilter.getMatchType().equals("all")
            && areAllTagFiltersMatching(TagMapper.convertToList(cluster.getTags()), tagsFilter.getTags())) {
          filteredClusters.add(ngGitOpsClusters.get(cluster.getIdentifier()));
        }
        if (tagsFilter.getMatchType().equals("any")
            && areAnyTagFiltersMatching(TagMapper.convertToList(cluster.getTags()), tagsFilter.getTags())) {
          filteredClusters.add(ngGitOpsClusters.get(cluster.getIdentifier()));
        }
      }
    }

    if (filterYaml.getType().equals(FilterType.all)) {
      return ngGitOpsClusters.values().stream().collect(Collectors.toList());
    }
    return filteredClusters;
  }

  public List<Environment> applyFiltersOnEnvs(
      List<Environment> environments, List<Environment> filteredEnvs, List<FilterYaml> filterYamls) {
    for (FilterYaml filterYaml : filterYamls) {
      if (filterYaml.getEntities().contains(Entity.environments)) {
        filteredEnvs = processTagsFilterYamlForEnvironments(filterYaml, environments);
      }
    }
    if (isEmpty(filteredEnvs)) {
      throw new InvalidRequestException("No Environments are eligible for deployment due to applied filters");
    }
    return filteredEnvs;
  }

  public List<io.harness.cdng.gitops.entity.Cluster> applyFilteringOnClusters(
      List<io.harness.cdng.gitops.entity.Cluster> filteredClusters, List<FilterYaml> filterYamls,
      Map<String, io.harness.cdng.gitops.entity.Cluster> clsToCluster, List<io.harness.gitops.models.Cluster> content) {
    for (FilterYaml filterYaml : filterYamls) {
      if (filterYaml.getEntities().contains(Entity.gitOpsClusters)) {
        filteredClusters = processTagsFilterYamlForGitOpsClusters(filterYaml, content, clsToCluster);
      }
    }

    if (isEmpty(filteredClusters)) {
      throw new InvalidRequestException("No GitOps cluster is eligible after applying filters");
    }
    return filteredClusters;
  }

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
}
