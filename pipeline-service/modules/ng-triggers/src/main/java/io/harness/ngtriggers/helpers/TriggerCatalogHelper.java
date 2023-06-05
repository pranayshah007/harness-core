/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.helpers;

import io.harness.beans.FeatureName;
import io.harness.ngtriggers.beans.entity.metadata.catalog.TriggerCatalogItem;
import io.harness.ngtriggers.beans.entity.metadata.catalog.TriggerCatalogType;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.featureflagfilter.TriggerCatalogFilter;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.ejb.Singleton;

@Singleton
public class TriggerCatalogHelper {
  @Inject TriggerCatalogFilter enumFilter;

  public List<TriggerCatalogItem> getTriggerTypeToCategoryMapping(String accountIdentifier) {
    final Map<NGTriggerType, List<TriggerCatalogType>> triggerCategoryListMap =
        Arrays.stream(TriggerCatalogType.values())
            .filter(enumFilter.filter(accountIdentifier, FeatureName.CD_TRIGGER_V2))
            .filter(enumFilter.filter(accountIdentifier, FeatureName.CDS_GOOGLE_CLOUD_FUNCTION))
            .filter(enumFilter.filter(accountIdentifier, FeatureName.BAMBOO_ARTIFACT_NG))
            .collect(Collectors.groupingBy(catalogType -> TriggerCatalogType.getTriggerCategory(catalogType)));
    return triggerCategoryListMap.entrySet()
        .stream()
        .sorted(Map.Entry.comparingByKey())
        .map(entry
            -> TriggerCatalogItem.builder()
                   .category(entry.getKey())
                   .triggerCatalogType(new ArrayList<>(entry.getValue()))
                   .build())
        .collect(Collectors.toList());
  }
}
