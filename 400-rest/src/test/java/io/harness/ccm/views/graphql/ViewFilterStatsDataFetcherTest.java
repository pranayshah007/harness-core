/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.graphql;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.rule.OwnerRule.ROHIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.ccm.views.service.ViewsBillingService;
import io.harness.rule.Owner;

import software.wings.graphql.schema.type.aggregation.QLData;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@TargetModule(HarnessModule._375_CE_GRAPHQL)
@OwnedBy(CE)
public class ViewFilterStatsDataFetcherTest extends CategoryTest {
  public static final String UNIFIED_TABLE = "unifiedTable";
  public static final String ACCOUNT_ID = "accountId";
  public static final String FILTER_VALUE_1 = "filterValue1";
  public static final String FILTER_VALUE_2 = "filterValue2";
  @Inject @InjectMocks ViewFilterStatsDataFetcher viewFieldsDataFetcher;
  @Mock ViewsBillingService viewsBillingService;

  List<QLCEViewFilterWrapper> filters = new ArrayList<>();
  List<String> values = new ArrayList<>();

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    values.add(FILTER_VALUE_1);
    values.add(FILTER_VALUE_2);
    doReturn(values).when(viewsBillingService).getFilterValueStats(filters, 10, 0);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void fetch() {
    QLCEViewFilterData data =
        (QLCEViewFilterData) viewFieldsDataFetcher.fetch(ACCOUNT_ID, null, filters, null, null, 10, 0);
    assertThat(data.getValues()).isEqualTo(values);
    assertThat(data.getValues().get(0)).isEqualTo(FILTER_VALUE_1);
    assertThat(data.getValues().get(1)).isEqualTo(FILTER_VALUE_2);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void postFetch() {
    QLData data = viewFieldsDataFetcher.postFetch(ACCOUNT_ID, null, null, null, null, 10, true);
    assertThat(data).isNull();
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void fetchSelectedFields() {
    QLData data = viewFieldsDataFetcher.fetchSelectedFields(ACCOUNT_ID, null, null, null, null, 10, 0, false, null);
    assertThat(data).isNull();
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void getEntityType() {
    String entityType = viewFieldsDataFetcher.getEntityType();
    assertThat(entityType).isNull();
  }
}
