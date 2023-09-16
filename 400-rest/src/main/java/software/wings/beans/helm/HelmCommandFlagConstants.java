/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.helm;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static software.wings.beans.appmanifest.StoreType.CUSTOM;
import static software.wings.beans.appmanifest.StoreType.HelmChartRepo;
import static software.wings.beans.appmanifest.StoreType.HelmSourceRepo;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.helm.HelmSubCommandType;
import io.harness.k8s.model.HelmVersion;

import software.wings.api.DeploymentType;
import software.wings.beans.appmanifest.StoreType;

import com.google.common.collect.ImmutableSet;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;

@OwnedBy(CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public final class HelmCommandFlagConstants {
  @Getter
  public enum HelmSubCommand {
    INSTALL(HelmSubCommandType.INSTALL, ImmutableSet.of(DeploymentType.HELM),
        ImmutableSet.of(HelmSourceRepo, HelmChartRepo, CUSTOM)),
    UPGRADE(HelmSubCommandType.UPGRADE, ImmutableSet.of(DeploymentType.HELM),
        ImmutableSet.of(HelmSourceRepo, HelmChartRepo, CUSTOM)),
    ROLLBACK(HelmSubCommandType.ROLLBACK, ImmutableSet.of(DeploymentType.HELM),
        ImmutableSet.of(HelmSourceRepo, HelmChartRepo, CUSTOM)),
    HISTORY(HelmSubCommandType.HISTORY, ImmutableSet.of(DeploymentType.HELM),
        ImmutableSet.of(HelmSourceRepo, HelmChartRepo, CUSTOM)),
    DELETE(HelmSubCommandType.DELETE, ImmutableSet.of(DeploymentType.HELM),
        ImmutableSet.of(HelmSourceRepo, HelmChartRepo, CUSTOM)),
    UNINSTALL(HelmSubCommandType.UNINSTALL, ImmutableSet.of(DeploymentType.HELM),
        ImmutableSet.of(HelmSourceRepo, HelmChartRepo, CUSTOM)),
    LIST(HelmSubCommandType.LIST, ImmutableSet.of(DeploymentType.HELM),
        ImmutableSet.of(HelmSourceRepo, HelmChartRepo, CUSTOM)),
    VERSION(HelmSubCommandType.VERSION, ImmutableSet.of(DeploymentType.HELM, DeploymentType.KUBERNETES),
        ImmutableSet.of(HelmSourceRepo, HelmChartRepo, CUSTOM)),
    PULL(HelmSubCommandType.PULL, ImmutableSet.of(DeploymentType.HELM, DeploymentType.KUBERNETES),
        ImmutableSet.of(HelmChartRepo, CUSTOM)),
    FETCH(HelmSubCommandType.FETCH, ImmutableSet.of(DeploymentType.HELM, DeploymentType.KUBERNETES),
        ImmutableSet.of(HelmChartRepo, CUSTOM)),
    TEMPLATE(HelmSubCommandType.TEMPLATE, ImmutableSet.of(DeploymentType.HELM, DeploymentType.KUBERNETES),
        ImmutableSet.of(HelmSourceRepo, HelmChartRepo, CUSTOM)),
    REPO_ADD(HelmSubCommandType.REPO_ADD, ImmutableSet.of(DeploymentType.HELM, DeploymentType.KUBERNETES),
        ImmutableSet.of(HelmChartRepo, CUSTOM)),
    REPO_UPDATE(HelmSubCommandType.REPO_UPDATE, ImmutableSet.of(DeploymentType.HELM, DeploymentType.KUBERNETES),
        ImmutableSet.of(HelmChartRepo, CUSTOM));

    private final HelmSubCommandType subCommandType;
    private final Set<DeploymentType> deploymentTypes;
    private final Set<StoreType> storeTypes;

    HelmSubCommand(HelmSubCommandType subCommandType, Set<DeploymentType> deploymentTypes, Set<StoreType> storeTypes) {
      this.subCommandType = subCommandType;
      this.deploymentTypes = deploymentTypes;
      this.storeTypes = storeTypes;
    }
  }

  public static Set<HelmSubCommand> getFilteredHelmSubCommands(
      HelmVersion version, DeploymentType deploymentType, StoreType storeType) {
    return Arrays.stream(HelmSubCommand.values())
        .filter(sc
            -> sc.getSubCommandType().getHelmVersions().contains(version)
                && sc.getDeploymentTypes().contains(deploymentType) && sc.getStoreTypes().contains(storeType))
        .collect(Collectors.toSet());
  }

  public static Set<HelmSubCommand> getHelmSubCommands(HelmVersion version) {
    return Arrays.stream(HelmSubCommand.values())
        .filter(sc -> sc.getSubCommandType().getHelmVersions().contains(version))
        .collect(Collectors.toSet());
  }
}
