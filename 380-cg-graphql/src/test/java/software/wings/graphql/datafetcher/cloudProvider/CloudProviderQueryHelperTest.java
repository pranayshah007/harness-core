/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.cloudProvider;

import static io.harness.rule.OwnerRule.ADARSH;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.category.element.UnitTests;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.graphql.schema.type.aggregation.QLEnumOperator;
import software.wings.graphql.schema.type.aggregation.cloudprovider.QLCEEnabledFilter;
import software.wings.graphql.schema.type.aggregation.cloudprovider.QLCloudProviderFilter;

import dev.morphia.query.Query;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class CloudProviderQueryHelperTest extends WingsBaseTest {
  @InjectMocks CloudProviderQueryHelper helper;

  @Mock Query queryMock;

  @Test(expected = WingsException.class)
  @Owner(developers = ADARSH)
  @Category(UnitTests.class)
  public void SetQuery_isCeEnabledFilter_invalidOperator() {
    Boolean[] value = {true};
    QLCEEnabledFilter isCeEnabledFilter = QLCEEnabledFilter.builder().operator(QLEnumOperator.IN).values(value).build();
    QLCloudProviderFilter qlCloudProviderFilter = QLCloudProviderFilter.builder()
                                                      .cloudProvider(null)
                                                      .cloudProviderType(null)
                                                      .isCEEnabled(isCeEnabledFilter)
                                                      .build();
    List<QLCloudProviderFilter> filters = new ArrayList<>();
    filters.add(qlCloudProviderFilter);
    helper.setQuery(filters, queryMock);
  }
  @Test(expected = WingsException.class)
  @Owner(developers = ADARSH)
  @Category(UnitTests.class)
  public void SetQuery_isCeEnabledFilter_multipleValues() {
    Boolean[] value = {true, true};
    QLCEEnabledFilter isCeEnabledFilter =
        QLCEEnabledFilter.builder().operator(QLEnumOperator.EQUALS).values(value).build();
    QLCloudProviderFilter qlCloudProviderFilter = QLCloudProviderFilter.builder()
                                                      .cloudProvider(null)
                                                      .cloudProviderType(null)
                                                      .isCEEnabled(isCeEnabledFilter)
                                                      .build();
    List<QLCloudProviderFilter> filters = new ArrayList<>();
    filters.add(qlCloudProviderFilter);
    helper.setQuery(filters, queryMock);
  }
  @Test(expected = WingsException.class)
  @Owner(developers = ADARSH)
  @Category(UnitTests.class)
  public void SetQuery_isCeEnabledFilter_nullOperator() {
    Boolean[] value = {true};
    QLCEEnabledFilter isCeEnabledFilter = QLCEEnabledFilter.builder().operator(null).values(value).build();
    QLCloudProviderFilter qlCloudProviderFilter = QLCloudProviderFilter.builder()
                                                      .cloudProvider(null)
                                                      .cloudProviderType(null)
                                                      .isCEEnabled(isCeEnabledFilter)
                                                      .build();
    List<QLCloudProviderFilter> filters = new ArrayList<>();
    filters.add(qlCloudProviderFilter);
    helper.setQuery(filters, queryMock);
  }
  @Test
  @Owner(developers = ADARSH)
  @Category(UnitTests.class)
  public void SetQuery_isCeEnabledFilter_Query() {
    Boolean[] value = {true};
    QLCEEnabledFilter isCeEnabledFilter =
        QLCEEnabledFilter.builder().operator(QLEnumOperator.EQUALS).values(value).build();
    QLCloudProviderFilter qlCloudProviderFilter = QLCloudProviderFilter.builder()
                                                      .cloudProvider(null)
                                                      .cloudProviderType(null)
                                                      .isCEEnabled(isCeEnabledFilter)
                                                      .build();
    List<QLCloudProviderFilter> filters = new ArrayList<>();
    filters.add(qlCloudProviderFilter);
    helper.setQuery(filters, queryMock);
    verify(queryMock, times(1)).filter("value.ccmConfig.cloudCostEnabled", true);
  }
}
