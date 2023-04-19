/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.query.instancebilling;

import static io.harness.rule.OwnerRule.UTSAV;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.graphql.core.explorergrid.InstanceBillingService;
import io.harness.ccm.graphql.utils.GraphQLUtils;
import io.harness.queryconverter.dto.GridRequest;
import io.harness.rule.Owner;
import io.harness.timescaledb.tables.pojos.BillingData;

import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class BillingDataQueryTest extends CategoryTest {
  private static final String ACCOUNT_ID = "accountId";

  @Mock GraphQLUtils graphQLUtils;
  @Mock InstanceBillingService instanceBillingService;
  @InjectMocks BillingDataQuery billingDataQuery;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    when(graphQLUtils.getAccountIdentifier(any())).thenReturn(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void billingDataBasicTest() {
    final BillingData billingData = new BillingData().setAccountid(ACCOUNT_ID).setBillingamount(100D);

    when(instanceBillingService.getBillingData(eq(ACCOUNT_ID), eq(GridRequest.builder().build())))
        .thenReturn(Collections.singletonList(billingData));

    List<BillingData> billingDataList = billingDataQuery.billingData(GridRequest.builder().build(), null);

    assertThat(billingDataList).hasSize(1);
    assertThat(billingDataList.get(0).getAccountid()).isEqualTo(ACCOUNT_ID);
    assertThat(billingDataList.get(0).getBillingamount()).isCloseTo(100D, offset(1D));
  }
}
