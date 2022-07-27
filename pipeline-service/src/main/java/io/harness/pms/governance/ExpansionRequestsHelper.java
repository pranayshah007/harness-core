/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.governance;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.plan.ExpansionRequestType;
import io.harness.pms.contracts.plan.JsonExpansionInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.sdk.PmsSdkInstance;
import io.harness.pms.yaml.YAMLFieldNameConstants;

import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@Singleton
@Slf4j
public class ExpansionRequestsHelper {
  public Map<ModuleType, Set<String>> getExpandableFieldsPerService(List<PmsSdkInstance> activeInstances) {
    Map<ModuleType, Set<String>> expandableFieldsPerService = new HashMap<>();
    activeInstances.forEach(sdkInstance -> {
      String sdkInstanceName = sdkInstance.getName();
      ModuleType module = ModuleType.fromString(sdkInstanceName);
      List<JsonExpansionInfo> jsonExpansionInfo = sdkInstance.getJsonExpansionInfo();
      if (jsonExpansionInfo == null) {
        log.error("Module [" + sdkInstanceName
            + "] not registered properly with Pipeline Service. JsonExpansionInfo is null in PmsSdkInstance.");
        throw new InvalidRequestException("Module [" + sdkInstanceName
            + "] not registered properly with Pipeline Service. JsonExpansionInfo is null in PmsSdkInstance.");
      }
      Set<String> expandableKeys =
          jsonExpansionInfo.stream()
              .filter(expansionInfo -> expansionInfo.getExpansionType().equals(ExpansionRequestType.KEY))
              .map(JsonExpansionInfo::getKey)
              .collect(Collectors.toSet());
      if (EmptyPredicate.isNotEmpty(expandableKeys)) {
        expandableFieldsPerService.put(module, expandableKeys);
      }
    });
    return expandableFieldsPerService;
  }

  public Map<String, ModuleType> getTypeToService(List<PmsSdkInstance> activeInstances) {
    Map<String, ModuleType> typeToModule = new HashMap<>();
    activeInstances.forEach(sdkInstance -> {
      String sdkInstanceName = sdkInstance.getName();
      ModuleType module = ModuleType.fromString(sdkInstanceName);
      Map<String, Set<String>> supportedTypes = sdkInstance.getSupportedTypes();
      Set<String> supportedStageTypes = supportedTypes.getOrDefault(YAMLFieldNameConstants.STAGE, new HashSet<>());
      Set<String> supportedStepTypes = supportedTypes.getOrDefault(YAMLFieldNameConstants.STEP, new HashSet<>());
      supportedStageTypes.forEach(type -> typeToModule.put(type, module));
      supportedStepTypes.forEach(type -> typeToModule.put(type, module));
    });
    return typeToModule;
  }

  public List<LocalFQNExpansionInfo> getLocalFQNRequestMetadata(List<PmsSdkInstance> activeInstances) {
    List<LocalFQNExpansionInfo> localFQNExpansionInfo = new ArrayList<>();

    for (PmsSdkInstance sdkInstance : activeInstances) {
      String sdkInstanceName = sdkInstance.getName();
      ModuleType module = ModuleType.fromString(sdkInstanceName);
      if (sdkInstance.getJsonExpansionInfo() != null) {
        List<JsonExpansionInfo> jsonExpansionInfo =
            sdkInstance.getJsonExpansionInfo()
                .stream()
                .filter(info -> info.getExpansionType().equals(ExpansionRequestType.LOCAL_FQN))
                .filter(info -> info.getStageType().getStepCategory().equals(StepCategory.STAGE))
                .collect(Collectors.toList());
        jsonExpansionInfo.forEach(info
            -> localFQNExpansionInfo.add(LocalFQNExpansionInfo.builder()
                                             .module(module)
                                             .localFQN(info.getKey())
                                             .stageType(info.getStageType().getType())
                                             .build()));
      } else {
        log.error("Module [" + sdkInstanceName
            + "] not registered properly with Pipeline Service. JsonExpansionInfo is null in PmsSdkInstance.");
        throw new InvalidRequestException("Module [" + sdkInstanceName
            + "] not registered properly with Pipeline Service. JsonExpansionInfo is null in PmsSdkInstance.");
      }
    }
    return localFQNExpansionInfo;
  }
}
