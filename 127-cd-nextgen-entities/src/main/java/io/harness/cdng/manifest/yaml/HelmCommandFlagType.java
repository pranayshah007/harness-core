/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml;

import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.helm.HelmSubCommandType;
import io.harness.ng.core.k8s.ServiceSpecType;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import lombok.Getter;

@Getter
public enum HelmCommandFlagType {
  Fetch(HelmSubCommandType.FETCH, ImmutableSet.of(ServiceSpecType.NATIVE_HELM, ServiceSpecType.KUBERNETES),
      ManifestStoreType.HelmChartRepo),
  Template(HelmSubCommandType.TEMPLATE, ImmutableSet.of(ServiceSpecType.NATIVE_HELM, ServiceSpecType.KUBERNETES),
      ManifestStoreType.HelmAllRepo),
  Pull(HelmSubCommandType.PULL, ImmutableSet.of(ServiceSpecType.NATIVE_HELM, ServiceSpecType.KUBERNETES),
      ManifestStoreType.HelmChartRepo),
  Install(HelmSubCommandType.INSTALL, ImmutableSet.of(ServiceSpecType.NATIVE_HELM), ManifestStoreType.HelmAllRepo),
  Upgrade(HelmSubCommandType.UPGRADE, ImmutableSet.of(ServiceSpecType.NATIVE_HELM), ManifestStoreType.HelmAllRepo),
  Rollback(HelmSubCommandType.ROLLBACK, ImmutableSet.of(ServiceSpecType.NATIVE_HELM), ManifestStoreType.HelmAllRepo),
  History(HelmSubCommandType.HISTORY, ImmutableSet.of(ServiceSpecType.NATIVE_HELM), ManifestStoreType.HelmAllRepo),
  Delete(HelmSubCommandType.DELETE, ImmutableSet.of(ServiceSpecType.NATIVE_HELM), ManifestStoreType.HelmAllRepo),
  Uninstall(HelmSubCommandType.UNINSTALL, ImmutableSet.of(ServiceSpecType.NATIVE_HELM), ManifestStoreType.HelmAllRepo),
  List(HelmSubCommandType.LIST, ImmutableSet.of(ServiceSpecType.NATIVE_HELM), ManifestStoreType.HelmAllRepo);

  private final HelmSubCommandType subCommandType;
  private final Set<String> serviceSpecTypes;
  private final Set<String> storeTypes;
  HelmCommandFlagType(HelmSubCommandType subCommandType, Set<String> serviceSpecTypes, Set<String> storeTypes) {
    this.subCommandType = subCommandType;
    this.serviceSpecTypes = serviceSpecTypes;
    this.storeTypes = storeTypes;
  }
}
