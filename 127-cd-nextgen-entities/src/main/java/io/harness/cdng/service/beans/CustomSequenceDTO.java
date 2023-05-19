/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.service.beans;

import io.harness.ng.core.environment.beans.EnvironmentType;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import org.jetbrains.annotations.NotNull;

@Data
@Builder
public class CustomSequenceDTO {
  @NotNull List<EnvAndEnvGroupCard> envAndEnvGroupCardList;

  @Data
  @Builder
  public static class EnvAndEnvGroupCard {
    @NotNull String name;
    @NotNull String identifier;
    boolean isEnvGroup;
    boolean isNew;
    List<EnvironmentType> environmentTypes;
  }
}
