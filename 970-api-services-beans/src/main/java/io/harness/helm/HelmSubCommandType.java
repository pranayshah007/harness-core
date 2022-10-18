/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.helm;

import static io.harness.k8s.model.HelmVersion.V2;
import static io.harness.k8s.model.HelmVersion.V3;
import static io.harness.k8s.model.HelmVersion.V380;

import io.harness.k8s.model.HelmVersion;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import lombok.Getter;

@Getter
public enum HelmSubCommandType {
  INSTALL(ImmutableSet.of(HelmCliCommandType.INSTALL.name()), ImmutableSet.of(V2, V3, V380)),
  UPGRADE(ImmutableSet.of(HelmCliCommandType.UPGRADE.name()), ImmutableSet.of(V2, V3, V380)),
  ROLLBACK(ImmutableSet.of(HelmCliCommandType.ROLLBACK.name()), ImmutableSet.of(V2, V3, V380)),
  HISTORY(ImmutableSet.of(HelmCliCommandType.RELEASE_HISTORY.name()), ImmutableSet.of(V2, V3, V380)),
  DELETE(ImmutableSet.of(HelmCliCommandType.DELETE_RELEASE.name()), ImmutableSet.of(V2)),
  UNINSTALL(ImmutableSet.of(HelmCliCommandType.DELETE_RELEASE.name()), ImmutableSet.of(V3, V380)),
  LIST(ImmutableSet.of(HelmCliCommandType.LIST_RELEASE.name()), ImmutableSet.of(V2, V3, V380)),
  VERSION(ImmutableSet.of(HelmCliCommandType.VERSION.name()), ImmutableSet.of(V2, V3, V380)),
  PULL(ImmutableSet.of(HelmCliCommandType.FETCH.name()), ImmutableSet.of(V3, V380)),
  FETCH(ImmutableSet.of(HelmCliCommandType.FETCH.name()), ImmutableSet.of(V2)),
  TEMPLATE(
      ImmutableSet.of(HelmCliCommandType.RENDER_CHART.name(), HelmCliCommandType.RENDER_SPECIFIC_CHART_FILE.name()),
      ImmutableSet.of(V2, V3, V380)),
  REPO_ADD(ImmutableSet.of(HelmCliCommandType.REPO_ADD.name()), ImmutableSet.of(V3, V380)),
  REPO_UPDATE(ImmutableSet.of(HelmCliCommandType.REPO_UPDATE.name()), ImmutableSet.of(V3, V380));

  private final Set<String> commandTypes;
  private final Set<HelmVersion> helmVersions;

  HelmSubCommandType(Set<String> commandTypes, Set<HelmVersion> helmVersions) {
    this.commandTypes = commandTypes;
    this.helmVersions = helmVersions;
  }

  public static HelmSubCommandType getSubCommandType(String commandType, HelmVersion helmVersion) {
    for (HelmSubCommandType subCommandType : HelmSubCommandType.values()) {
      if (subCommandType.getCommandTypes().contains(commandType)) {
        return subCommandType.getHelmVersions().contains(helmVersion) ? subCommandType : null;
      }
    }

    return null;
  }
}
