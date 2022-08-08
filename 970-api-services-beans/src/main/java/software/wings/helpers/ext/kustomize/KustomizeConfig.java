/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.helpers.ext.kustomize;

import static io.harness.annotations.dev.HarnessTeam.PL;

import static org.apache.commons.lang3.StringUtils.EMPTY;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.data.validator.Trimmed;

import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TargetModule(HarnessModule._970_API_SERVICES_BEANS)
@OwnedBy(PL)
public class KustomizeConfig {
  @Trimmed private String pluginRootDir;
  @Builder.Default @Trimmed private String kustomizeDirPath = EMPTY;

  @Nullable
  public static KustomizeConfig cloneFrom(@Nullable KustomizeConfig config) {
    if (config == null) {
      return null;
    }
    return KustomizeConfig.builder()
        .pluginRootDir(config.getPluginRootDir())
        .kustomizeDirPath(config.getKustomizeDirPath())
        .build();
  }
}
