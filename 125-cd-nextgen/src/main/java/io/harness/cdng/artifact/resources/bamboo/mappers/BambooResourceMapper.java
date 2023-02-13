/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.resources.bamboo.mappers;

import io.harness.cdng.artifact.resources.bamboo.dtos.BambooPlanKeysDTO;
import io.harness.cdng.artifact.resources.bamboo.dtos.BambooPlanNames;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;

import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@UtilityClass
public class BambooResourceMapper {
  public BambooPlanKeysDTO toBambooJobDetailsDTO(ArtifactTaskExecutionResponse artifactTaskExecutionResponse) {
    return BambooPlanKeysDTO.builder()
        .planKeys(artifactTaskExecutionResponse.getPlans()
                      .entrySet()
                      .stream()
                      .map(e -> BambooPlanNames.builder().name(e.getKey()).value(e.getValue()).build())
                      .collect(Collectors.toList()))
        .build();
  }
}
