/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.environment;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.environment.yaml.EnvironmentYaml;
import io.harness.data.structure.CollectionUtils;
import io.harness.steps.environment.EnvironmentOutcome;

import javax.annotation.Nonnull;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(CDC)
public class EnvironmentMapper {
  public io.harness.steps.environment.EnvironmentOutcome toOutcome(@Nonnull EnvironmentYaml environmentYaml) {
    return EnvironmentOutcome.builder()
        .identifier(environmentYaml.getIdentifier())
        .name(environmentYaml.getName() != null ? environmentYaml.getName() : "")
        .description(environmentYaml.getDescription() != null ? environmentYaml.getDescription().getValue() : "")
        .tags(CollectionUtils.emptyIfNull(environmentYaml.getTags()))
        .type(environmentYaml.getType())
        .build();
  }
}
