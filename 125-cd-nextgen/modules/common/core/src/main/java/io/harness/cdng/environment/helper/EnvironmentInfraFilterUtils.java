/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.environment.helper;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.envgroup.yaml.EnvironmentGroupYaml;
import io.harness.cdng.environment.filters.Entity;
import io.harness.cdng.environment.filters.FilterType;
import io.harness.cdng.environment.filters.FilterYaml;
import io.harness.cdng.environment.filters.TagsFilter;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.environment.yaml.EnvironmentsYaml;
import io.harness.cdng.gitops.entity.Cluster;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.pms.yaml.ParameterField;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

@Slf4j
@OwnedBy(HarnessTeam.GITOPS)
public class EnvironmentInfraFilterUtils {
  /**
   * @param environments - List of environments
   * @param filterYamls  - List of FilterYamls
   * @return Applies filters on Environments Entity. Returns the same list of no filter is applied.
   * Throws exception if environments qualify after applying filters
   */
  public static Set<Environment> applyFiltersOnEnvs(Set<Environment> environments, Iterable<FilterYaml> filterYamls) {
    Set<Environment> setOfFilteredEnvs = new HashSet<>();

    for (FilterYaml filterYaml : filterYamls) {
      if (filterYaml.getEntities().contains(Entity.environments)) {
        setOfFilteredEnvs.addAll(processFilterYamlForEnvironments(filterYaml, environments));
      }
    }
    if (isEmpty(setOfFilteredEnvs)) {
      String tags = getTagsString(filterYamls);
      throw new InvalidRequestException(
          String.format("No Environments are eligible for deployment due to applied filters for tags - %s", tags));
    }
    return setOfFilteredEnvs;
  }

  @VisibleForTesting
  static String getTagsString(Iterable<FilterYaml> filterYamls) {
    List<String> tagList = new ArrayList<>();
    for (FilterYaml filterYaml : filterYamls) {
      if (filterYaml.getEntities().contains(Entity.environments)) {
        if (FilterType.tags == filterYaml.getType()) {
          tagList.add(getTagString((TagsFilter) filterYaml.getSpec()).toString());
        }
      }
    }
    return String.join(",", tagList);
  }

  private static StringBuilder getTagString(TagsFilter tagsFilter) {
    StringBuilder tags = new StringBuilder();
    tags.append("[").append(tagsFilter.getMatchType().getValue()).append("-");

    if (tagsFilter.getTags().getValue() == null) {
      return new StringBuilder();
    }
    List<String> tagList = new ArrayList<>();
    for (Map.Entry<String, String> tag : tagsFilter.getTags().getValue().entrySet()) {
      StringBuilder tagString = new StringBuilder();
      tagString.append(tag.getKey());
      if (isNotEmpty(tag.getValue())) {
        tagString.append(":").append(tag.getValue());
      }
      tagList.add(tagString.toString());
    }

    tags.append(String.join(",", tagList)).append("]");
    return tags;
  }

  /**
   * @param filterYaml - Contains the information of filters along with it's type
   * @param envs       - List of environments to apply filters on
   * @return - List of filtered Environments
   */
  @VisibleForTesting
  static Set<Environment> processFilterYamlForEnvironments(FilterYaml filterYaml, Set<Environment> envs) {
    if (FilterType.all.name().equals(filterYaml.getType().name())) {
      return envs;
    }
    Set<Environment> filteredEnvs = new HashSet<>();
    if (FilterType.tags.equals(filterYaml.getType())) {
      TagsFilter tagsFilter = (TagsFilter) filterYaml.getSpec();
      filteredEnvs =
          envs.stream()
              .filter(environment -> FilterTagsUtils.areTagsFilterMatching(environment.getTags(), tagsFilter))
              .collect(Collectors.toSet());
    }

    return filteredEnvs;
  }

  static Set<InfrastructureEntity> processFilterYamlForInfraStructures(
      FilterYaml filterYaml, Set<InfrastructureEntity> infras) {
    if (filterYaml.getType().name().equals(FilterType.all.name())) {
      return infras;
    }
    // filter env that match all tags
    Set<InfrastructureEntity> filteredInfras = new HashSet<>();
    if (filterYaml.getType().equals(FilterType.tags)) {
      TagsFilter tagsFilter = (TagsFilter) filterYaml.getSpec();
      filteredInfras = infras.stream()
                           .filter(infra -> FilterTagsUtils.areTagsFilterMatching(infra.getTags(), tagsFilter))
                           .collect(Collectors.toSet());
    }

    return filteredInfras;
  }

  public static Set<InfrastructureEntity> applyFilteringOnInfras(
      Iterable<FilterYaml> filterYamls, Set<InfrastructureEntity> infras) {
    Set<InfrastructureEntity> setOfFilteredInfras = new HashSet<>();

    for (FilterYaml filterYaml : filterYamls) {
      if (filterYaml.getEntities().contains(Entity.infrastructures)) {
        setOfFilteredInfras.addAll(processFilterYamlForInfraStructures(filterYaml, infras));
      }
    }

    if (isEmpty(setOfFilteredInfras)) {
      log.info("No Environments are eligible for deployment due to applied filters");
    }
    return setOfFilteredInfras;
  }

  /**
   * @param filterYaml     - Contains the information of filters along with it's type
   * @param gitopsClusters - List of clusters to apply filters on
   * @param ngClusters     - Cluster Entity containing tag information for applying filtering
   * @return - List of filtered Clusters
   */
  public static List<Cluster> processFilterYamlForGitOpsClusters(
      FilterYaml filterYaml, Set<io.harness.gitops.models.Cluster> gitopsClusters, List<Cluster> ngClusters) {
    if (FilterType.all.name().equals(filterYaml.getType().name())) {
      return new ArrayList<>(ngClusters);
    }
    // TODO: Simplify this bit, should just do filtering for gitops clusters
    Map<String, List<Cluster>> idToClusterMap = new HashMap<>();
    for (Cluster ngCluster : ngClusters) {
      List<Cluster> clusters = idToClusterMap.getOrDefault(ngCluster.getClusterRef(), new ArrayList<>());
      clusters.add(ngCluster);
      idToClusterMap.put(ngCluster.getClusterRef(), clusters);
    }

    List<Cluster> filteredClusters = new ArrayList<>();
    if (FilterType.tags.equals(filterYaml.getType())) {
      TagsFilter tagsFilter = (TagsFilter) filterYaml.getSpec();
      filteredClusters =
          gitopsClusters.stream()
              .filter(cluster
                  -> FilterTagsUtils.areTagsFilterMatching(TagMapper.convertToList(cluster.getTags()), tagsFilter))
              .flatMap(cluster -> CollectionUtils.emptyIfNull(idToClusterMap.get(cluster.fetchRef())).stream())
              .collect(Collectors.toList());
    }
    return filteredClusters;
  }

  /**
   * @param filterYamls - List of FilterYamls
   * @param ngClusters  - Map of clusterRef to NG GitOps Cluster Entity
   * @param clusters    - List of NG GitOpsClusters
   * @return Applies Filters on GitOpsClusters. Returns the same list of no filter is applied.
   * Throws exception if no clusters qualify after applying filters.
   */
  public static Set<Cluster> applyFilteringOnClusters(
      Iterable<FilterYaml> filterYamls, List<Cluster> ngClusters, Set<io.harness.gitops.models.Cluster> clusters) {
    Set<Cluster> setOfFilteredCls = new HashSet<>();

    for (FilterYaml filterYaml : filterYamls) {
      if (filterYaml.getEntities().contains(Entity.gitOpsClusters)) {
        setOfFilteredCls.addAll(processFilterYamlForGitOpsClusters(filterYaml, clusters, ngClusters));
      }
    }
    if (isEmpty(setOfFilteredCls)) {
      log.info("No GitOps cluster is eligible after applying filters");
    }
    return setOfFilteredCls;
  }

  public static boolean areFiltersPresent(EnvironmentsYaml environmentsYaml) {
    return environmentsYaml != null
        && ((ParameterField.isNotNull(environmentsYaml.getFilters())
                && isNotEmpty(environmentsYaml.getFilters().getValue()))
            || areFiltersSetOnIndividualEnvironments(environmentsYaml));
  }

  public static boolean areFiltersSetOnIndividualEnvironments(EnvironmentsYaml environmentsYaml) {
    if (ParameterField.isNull(environmentsYaml.getValues())) {
      return false;
    }
    List<EnvironmentYamlV2> envV2YamlsWithFilters =
        EnvironmentInfraFilterHelper.getEnvYamlV2WithFilters(environmentsYaml.getValues());
    return isNotEmpty(envV2YamlsWithFilters);
  }

  public static boolean areFiltersPresent(EnvironmentGroupYaml environmentGroupYaml) {
    return environmentGroupYaml != null
        && ((ParameterField.isNotNull(environmentGroupYaml.getFilters())
                && isNotEmpty(environmentGroupYaml.getFilters().getValue()))
            || areFiltersSetOnIndividualEnvironments(environmentGroupYaml.getEnvironments()));
  }

  public static boolean areFiltersSetOnIndividualEnvironments(
      ParameterField<List<EnvironmentYamlV2>> environmentYamlV2s) {
    if (ParameterField.isNull(environmentYamlV2s) || EmptyPredicate.isEmpty(environmentYamlV2s.getValue())) {
      return false;
    }
    List<EnvironmentYamlV2> envV2YamlsWithFilters =
        EnvironmentInfraFilterHelper.getEnvYamlV2WithFilters(environmentYamlV2s);
    return isNotEmpty(envV2YamlsWithFilters);
  }
}
