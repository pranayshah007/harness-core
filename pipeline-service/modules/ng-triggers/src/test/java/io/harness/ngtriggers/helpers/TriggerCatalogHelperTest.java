/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.helpers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.VINICIUS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ngtriggers.beans.entity.metadata.catalog.TriggerCatalogItem;
import io.harness.ngtriggers.beans.entity.metadata.catalog.TriggerCatalogType;
import io.harness.ngtriggers.featureflagfilter.TriggerCatalogFilter;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class TriggerCatalogHelperTest extends CategoryTest {
  @Mock private TriggerCatalogFilter triggerCatalogFilter;
  @InjectMocks private TriggerCatalogHelper triggerCatalogHelper;

  String accountId = "someAcct";

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetTriggerTypeToCategoryMapping() {
    when(triggerCatalogFilter.filter(any(), any())).thenReturn(object -> true);
    List<TriggerCatalogItem> expectedMapping =
        Arrays.stream(TriggerCatalogType.values())
            .collect(Collectors.groupingBy(catalogType -> TriggerCatalogType.getTriggerCategory(catalogType)))
            .entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry
                -> TriggerCatalogItem.builder()
                       .category(entry.getKey())
                       .triggerCatalogType(new ArrayList<>(entry.getValue()))
                       .build())
            .collect(Collectors.toList());
    List<TriggerCatalogItem> mapping = triggerCatalogHelper.getTriggerTypeToCategoryMapping(accountId);
    assertThat(mapping).isEqualTo(expectedMapping);
  }
}
