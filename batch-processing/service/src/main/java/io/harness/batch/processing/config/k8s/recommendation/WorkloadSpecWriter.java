/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.config.k8s.recommendation;

import static java.util.Collections.emptyMap;
import static java.util.Optional.ofNullable;

import io.harness.batch.processing.service.intfc.InstanceInfoTimescaleDAO;
import io.harness.beans.FeatureName;
import io.harness.ccm.commons.beans.recommendation.ResourceId;
import io.harness.ccm.commons.entities.events.PublishedMessage;
import io.harness.ccm.commons.entities.k8s.recommendation.K8sWorkloadRecommendation;
import io.harness.ff.FeatureFlagService;
import io.harness.perpetualtask.k8s.watch.K8sWorkloadSpec;

import software.wings.graphql.datafetcher.ce.recommendation.entity.ContainerRecommendation;
import software.wings.graphql.datafetcher.ce.recommendation.entity.ResourceRequirement;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

@Component
class WorkloadSpecWriter implements ItemWriter<PublishedMessage> {
  private final WorkloadRecommendationDao workloadRecommendationDao;
  private final InstanceInfoTimescaleDAO instanceInfoTimescaleDAO;
  private final FeatureFlagService featureFlagService;

  WorkloadSpecWriter(WorkloadRecommendationDao workloadRecommendationDao,
      InstanceInfoTimescaleDAO instanceInfoTimescaleDAO, FeatureFlagService featureFlagService) {
    this.workloadRecommendationDao = workloadRecommendationDao;
    this.instanceInfoTimescaleDAO = instanceInfoTimescaleDAO;
    this.featureFlagService = featureFlagService;
  }

  @Override
  public void write(List<? extends PublishedMessage> items) {
    Map<ResourceId, K8sWorkloadRecommendation> workloadToRecommendation = new HashMap<>();
    for (PublishedMessage item : items) {
      String accountId = item.getAccountId();
      K8sWorkloadSpec k8sWorkloadSpec = (K8sWorkloadSpec) item.getMessage();

      ResourceId workloadId = ResourceId.builder()
                                  .accountId(accountId)
                                  .clusterId(k8sWorkloadSpec.getClusterId())
                                  .namespace(k8sWorkloadSpec.getNamespace())
                                  .name(k8sWorkloadSpec.getWorkloadName())
                                  .kind(k8sWorkloadSpec.getWorkloadKind())
                                  .build();

      if (featureFlagService.isEnabled(FeatureName.NODE_RECOMMENDATION_1, accountId)) {
        instanceInfoTimescaleDAO.insertIntoWorkloadInfo(accountId, k8sWorkloadSpec);
      }
      List<K8sWorkloadSpec.ContainerSpec> containerSpecs = k8sWorkloadSpec.getContainerSpecsList();
      Map<String, ResourceRequirement> containerCurrentResources =
          containerSpecs.stream().collect(Collectors.toMap(K8sWorkloadSpec.ContainerSpec::getName, e -> {
            Map<String, String> requestsMap = new HashMap<>(ofNullable(e.getRequestsMap()).orElse(emptyMap()));
            Map<String, String> limitsMap = ofNullable(e.getLimitsMap()).orElse(emptyMap());
            limitsMap.forEach(requestsMap::putIfAbsent);
            return ResourceRequirement.builder().requests(requestsMap).limits(limitsMap).build();
          }));

      K8sWorkloadRecommendation recommendation = workloadToRecommendation.computeIfAbsent(
          workloadId, workloadRecommendationDao::fetchRecommendationForWorkload);

      // Update the current fields, without removing the pre-existing guaranteed & burstable fields.
      Map<String, ContainerRecommendation> existingRecommendations = recommendation.getContainerRecommendations();
      Map<String, ContainerRecommendation> updatedRecommendations =
          containerCurrentResources.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
              currentResources
              -> ContainerRecommendation.builder()
                     .current(currentResources.getValue())
                     .guaranteed(Optional.ofNullable(existingRecommendations)
                                     .map(crMap -> crMap.get(currentResources.getKey()))
                                     .map(ContainerRecommendation::getGuaranteed)
                                     .orElse(null))
                     .burstable(Optional.ofNullable(existingRecommendations)
                                    .map(crMap -> crMap.get(currentResources.getKey()))
                                    .map(ContainerRecommendation::getBurstable)
                                    .orElse(null))
                     .percentileBased(Optional.ofNullable(existingRecommendations)
                                          .map(crMap -> crMap.get(currentResources.getKey()))
                                          .map(ContainerRecommendation::getPercentileBased)
                                          .orElse(new HashMap<>()))
                     .build()));
      recommendation.setContainerRecommendations(updatedRecommendations);
      recommendation.setDirty(true);
    }
    workloadToRecommendation.values().forEach(workloadRecommendationDao::save);
  }
}
