/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk;

import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.PlanCreationServiceGrpc;
import io.harness.pms.plan.creation.PlanCreatorServiceInfo;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class PmsSdkHelper {
  @Inject private Map<ModuleType, PlanCreationServiceGrpc.PlanCreationServiceBlockingStub> planCreatorServices;
  @Inject private PmsSdkInstanceService pmsSdkInstanceService;

  /**
   * Gets the list of registered services with their PlanCreatorServiceInfo object
   */
  public Map<String, PlanCreatorServiceInfo> getServices() {
    Map<String, Map<String, Set<String>>> sdkInstances = pmsSdkInstanceService.getInstanceNameToSupportedTypes();
    Map<String, PlanCreatorServiceInfo> services = new HashMap<>();
    if (EmptyPredicate.isNotEmpty(planCreatorServices) && EmptyPredicate.isNotEmpty(sdkInstances)) {
      sdkInstances.forEach((k, v) -> {
        if (planCreatorServices.containsKey(ModuleType.fromString(k))) {
          services.put(k, new PlanCreatorServiceInfo(v, planCreatorServices.get(ModuleType.fromString(k))));
        }
      });
    }
    return services;
  }

  /**
   * Checks if the service supports any of the dependency mentioned.
   */
  public static boolean containsSupportedDependencyByYamlPath(
      PlanCreatorServiceInfo serviceInfo, Dependencies dependencies) {
    if (dependencies == null || EmptyPredicate.isEmpty(dependencies.getDependenciesMap())) {
      return false;
    }

    YamlField fullYamlField;
    try {
      fullYamlField = YamlUtils.readTree(dependencies.getYaml());
    } catch (IOException ex) {
      String message = "Invalid yaml during plan creation";
      log.error(message, ex);
      throw new InvalidRequestException(message);
    }

    return dependencies.getDependenciesMap()
        .entrySet()
        .stream()
        .filter(
            entry -> containsSupportedSingleDependencyByYamlPath(serviceInfo, fullYamlField, entry, PipelineVersion.V0))
        .map(Map.Entry::getKey)
        .findFirst()
        .isPresent();
  }

  /**
   * Checks if the service supports any of the dependency mentioned.
   */
  public static boolean containsSupportedSingleDependencyByYamlPath(PlanCreatorServiceInfo serviceInfo,
      YamlField fullYamlField, Map.Entry<String, String> dependencyEntry, String harnessVersion) {
    if (dependencyEntry == null) {
      return false;
    }
    Map<String, Set<String>> supportedTypes = serviceInfo.getSupportedTypes();
    try {
      YamlField field = fullYamlField.fromYamlPath(dependencyEntry.getValue());
      return PlanCreatorUtils.supportsField(supportedTypes, field, harnessVersion);
    } catch (Exception ex) {
      String message = "Invalid yaml during plan creation for dependency path - " + dependencyEntry.getValue();
      log.error(message, ex);
      throw new InvalidRequestException(message);
    }
  }

  public static String getServiceAffinityForGivenDependency(
      Dependencies dependencies, Map.Entry<String, String> dependencyEntry) {
    String affinityService = null;
    Dependency dependency = dependencies.getDependencyMetadataMap().get(dependencyEntry.getKey());
    if (dependency != null) {
      affinityService = dependency.getServiceAffinity();
    }
    return affinityService;
  }

  public static boolean checkIfGivenServiceSupportsPath(Map.Entry<String, PlanCreatorServiceInfo> givenServiceInfo,
      Map.Entry<String, String> dependencyEntry, YamlField fullYamlField, String harnessVersion) {
    if (givenServiceInfo == null) {
      return false;
    }
    return containsSupportedSingleDependencyByYamlPath(
        givenServiceInfo.getValue(), fullYamlField, dependencyEntry, harnessVersion);
  }

  public static Dependencies createBatchDependency(Dependencies dependencies, Map<String, String> dependencyMap) {
    return Dependencies.newBuilder()
        .putAllDependencies(dependencyMap)
        .putAllDependencyMetadata(dependencies.getDependencyMetadataMap())
        .setYaml(dependencies.getYaml())
        .build();
  }

  public static boolean isPipelineService(Map.Entry<String, PlanCreatorServiceInfo> serviceInfo) {
    return serviceInfo.getKey().equals(ModuleType.PMS.name().toLowerCase());
  }

  public static boolean getServiceForGivenAffinity(
      Map.Entry<String, PlanCreatorServiceInfo> serviceInfo, String serviceName) {
    if (EmptyPredicate.isEmpty(serviceName)) {
      return false;
    }
    return serviceInfo.getKey().equals(serviceName.toLowerCase());
  }
}
