/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.helper;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.connector.ConnectorCatalogueItem;
import io.harness.connector.ConnectorCategory;
import io.harness.connector.ConnectorRegistryFactory;
import io.harness.connector.featureflagfilter.ConnectorEnumFilter;
import io.harness.delegate.beans.connector.ConnectorType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@OwnedBy(CDP)
@Singleton
public class CatalogueHelper {
  @Inject ConnectorEnumFilter enumFilter;

  public List<ConnectorCatalogueItem> getConnectorTypeToCategoryMapping(String accountIdentifier) {
    final Map<ConnectorCategory, List<ConnectorType>> connectorCategoryListMap =
        Arrays.stream(ConnectorType.values())
            .filter(enumFilter.filter(accountIdentifier, FeatureName.NG_SVC_ENV_REDESIGN))
            .filter(enumFilter.filter(accountIdentifier, FeatureName.BAMBOO_ARTIFACT_NG))
            .filter(enumFilter.filter(accountIdentifier, FeatureName.CDS_RANCHER_SUPPORT_NG))
            .collect(Collectors.groupingBy(ConnectorRegistryFactory::getConnectorCategory));
    return connectorCategoryListMap.entrySet()
        .stream()
        .sorted(Map.Entry.comparingByKey())
        .map(entry
            -> ConnectorCatalogueItem.builder()
                   .category(entry.getKey())
                   .connectors(new HashSet<>(entry.getValue()))
                   .build())
        .collect(Collectors.toList());
  }
}
