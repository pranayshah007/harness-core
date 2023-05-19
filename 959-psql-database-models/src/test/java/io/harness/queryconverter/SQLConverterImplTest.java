/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.queryconverter;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.rule.OwnerRule.UTSAV;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.queryconverter.dto.GridRequest;
import io.harness.rule.Owner;
import io.harness.timescaledb.Tables;
import io.harness.timescaledb.tables.pojos.BillingData;
import io.harness.timescaledb.tables.pojos.CeRecommendations;
import io.harness.timescaledb.tables.records.BillingDataRecord;
import io.harness.timescaledb.tables.records.CeRecommendationsRecord;

import java.io.Serializable;
import java.sql.SQLException;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockResult;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CE)
public class SQLConverterImplTest extends CategoryTest {
  private static final BillingDataRecord billingDataRecord =
      new BillingDataRecord().setAccountid("kmp").setClusterid("c0").setBillingamount(100D);

  private MockDataProvider provider;
  private SQLConverterImpl sqlConverter;

  @Before
  public void setup() throws SQLException {
    provider = mock(MockDataProvider.class);
    MockConnection connection = new MockConnection(provider);
    DSLContext dslContext = DSL.using(connection, SQLDialect.POSTGRES);

    sqlConverter = new SQLConverterImpl(dslContext);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testBillingDataGridRequestOnly() throws Exception {
    MockResult mockResult0 = new MockResult(billingDataRecord);

    when(provider.execute(any())).thenReturn(new MockResult[] {mockResult0});

    List<? extends Serializable> actualResult =
        sqlConverter.convert(GridRequest.builder().entity("billing_data").build());

    assertBillingDataResult(actualResult);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testBillingDataWithExplicitTableName() throws Exception {
    MockResult mockResult0 = new MockResult(billingDataRecord);

    when(provider.execute(any())).thenReturn(new MockResult[] {mockResult0});

    List<? extends Serializable> actualResult =
        sqlConverter.convert(Tables.BILLING_DATA, GridRequest.builder().build());

    assertBillingDataResult(actualResult);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testBillingDataFetchInto() throws Exception {
    MockResult mockResult0 = new MockResult(billingDataRecord);

    when(provider.execute(any())).thenReturn(new MockResult[] {mockResult0});

    List<? extends Serializable> actualResult =
        sqlConverter.convert("billing_data", GridRequest.builder().build(), BillingData.class);

    assertBillingDataResult(actualResult);
  }

  private void assertBillingDataResult(List<? extends Serializable> actualResult) {
    assertThat(actualResult).isNotNull();
    assertThat(actualResult).hasSize(1);
    assertThat(actualResult.get(0)).isExactlyInstanceOf(BillingData.class);

    BillingData billingData0 = (BillingData) actualResult.get(0);
    assertThat(billingData0.getAccountid()).isEqualTo("kmp");
    assertThat(billingData0.getClusterid()).isEqualTo("c0");
    assertThat(billingData0.getBillingamount()).isCloseTo(100D, offset(1D));
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testCeRecommendation() throws Exception {
    MockResult mockResult = new MockResult(new CeRecommendationsRecord().setName("nginx"));

    when(provider.execute(any())).thenReturn(new MockResult[] {mockResult});

    List<? extends Serializable> actualResult =
        sqlConverter.convert("ce_recommendations", GridRequest.builder().build());

    assertThat(actualResult).isNotNull();
    assertThat(actualResult).hasSize(1);
    assertThat(actualResult.get(0)).isExactlyInstanceOf(CeRecommendations.class);

    CeRecommendations ceRecommendations = (CeRecommendations) actualResult.get(0);
    assertThat(ceRecommendations.getName()).isEqualTo("nginx");
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testSQLConverter_GetField() {
    // cameCase column
    assertThat(SQLConverter.getField("accountId", Tables.CE_RECOMMENDATIONS))
        .isEqualTo(Tables.CE_RECOMMENDATIONS.ACCOUNTID);

    // lowercase column
    assertThat(SQLConverter.getField("accountid", Tables.CE_RECOMMENDATIONS))
        .isEqualTo(Tables.CE_RECOMMENDATIONS.ACCOUNTID);

    // uppercase column
    assertThat(SQLConverter.getField("accountid", Tables.CE_RECOMMENDATIONS))
        .isEqualTo(Tables.CE_RECOMMENDATIONS.ACCOUNTID);

    // should throw runtime exception on no column present
    assertThatThrownBy(() -> SQLConverter.getField("ndmfbvd", Tables.CE_RECOMMENDATIONS))
        .isExactlyInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("doesnt exist");
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testSQLConverter_GetFieldWithStringTableName() {
    // cameCase column
    assertThat(SQLConverter.getField("accountId", "ce_Recommendations")).isEqualTo(Tables.CE_RECOMMENDATIONS.ACCOUNTID);

    // lowercase column
    assertThat(SQLConverter.getField("accountid", "ce_recommendations")).isEqualTo(Tables.CE_RECOMMENDATIONS.ACCOUNTID);

    // uppercase column
    assertThat(SQLConverter.getField("accountid", "CE_RECOMMENDATIONS")).isEqualTo(Tables.CE_RECOMMENDATIONS.ACCOUNTID);

    // should throw runtime exception on no column present
    assertThatThrownBy(() -> SQLConverter.getField("ndmfbvd", "ce_recommendations"))
        .isExactlyInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("doesnt exist");
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testSQLConverter_getTable() {
    // cameCase column
    assertThat(SQLConverter.getTable("ce_Recommendations")).isEqualTo(Tables.CE_RECOMMENDATIONS);

    // lowercase column
    assertThat(SQLConverter.getTable("ce_recommendations")).isEqualTo(Tables.CE_RECOMMENDATIONS);

    // uppercase column
    assertThat(SQLConverter.getTable("CE_RECOMMENDATIONS")).isEqualTo(Tables.CE_RECOMMENDATIONS);

    // should throw runtime exception on no column present
    assertThatThrownBy(() -> SQLConverter.getTable("ndmfbvd"))
        .isExactlyInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("doesnt exist");
  }
}
