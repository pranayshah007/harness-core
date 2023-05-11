/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher;

import static io.harness.rule.OwnerRule.UTSAV;
import static io.harness.rule.OwnerRule.VIKAS_S;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.dao.CEMetadataRecordDao;
import io.harness.ccm.commons.entities.batch.CEMetadataRecord;
import io.harness.ccm.setup.config.CESetUpConfig;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.app.MainConfiguration;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;

import dev.morphia.query.FieldEnd;
import dev.morphia.query.Query;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class DataFetcherUtilsTest extends CategoryTest {
  private static final String ACCOUNT_ID = "account_id";
  private static final String SAMPLE_ACCOUNT_ID = "sample_account_id";

  @Mock FeatureFlagService featureFlagService;
  @Mock TimeScaleDBService timeScaleDBService;
  @Mock MainConfiguration configuration;
  @Mock Connection connection;
  @Mock Statement statement;
  @Mock ResultSet resultSet;
  @Mock CEMetadataRecordDao metadataRecordDao;

  @InjectMocks private DataFetcherUtils dataFetcherUtils;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    when(timeScaleDBService.isValid()).thenReturn(true);
    when(timeScaleDBService.getDBConnection()).thenReturn(connection);
    when(connection.createStatement()).thenReturn(statement);
    when(statement.executeQuery(any())).thenReturn(resultSet);
    when(configuration.getCeSetUpConfig())
        .thenReturn(CESetUpConfig.builder().sampleAccountId(SAMPLE_ACCOUNT_ID).build());
  }

  @Test
  @Owner(developers = VIKAS_S)
  @Category(UnitTests.class)
  public void setIDFilterShouldPopulateQueryFieldProperly() {
    // WingsException should be thrown for Null Id filter.
    FieldEnd<? extends Query<?>> emptyQuery = mock(FieldEnd.class);
    QLIdFilter nullIdFilter = QLIdFilter.builder().build();
    assertThatThrownBy(() -> dataFetcherUtils.setIdFilter(emptyQuery, nullIdFilter)).isInstanceOf(WingsException.class);

    // WingsException should be thrown if filter value is null and operator is not NOT_NULL
    FieldEnd<? extends Query<?>> emptyValueQuery = mock(FieldEnd.class);
    QLIdFilter emptyValueFilter = QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(null).build();
    assertThatThrownBy(() -> dataFetcherUtils.setIdFilter(emptyValueQuery, emptyValueFilter))
        .isInstanceOf(WingsException.class);

    // IN filter should be set for IN type QLIdFilter.
    String[] idsForInOperator = new String[] {"id1"};
    QLIdFilter inOperatorFilter = QLIdFilter.builder().operator(QLIdOperator.IN).values(idsForInOperator).build();
    FieldEnd<? extends Query<?>> inQuery = mock(FieldEnd.class);
    dataFetcherUtils.setIdFilter(inQuery, inOperatorFilter);
    verify(inQuery, times(1)).in(Arrays.asList(idsForInOperator));

    // Equals filter should be set for EQUAL type QLIdFilter.
    String id = "id1";
    String[] idsForEqualOperatorTest = new String[] {id};
    QLIdFilter equalOperatorFilter =
        QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(idsForEqualOperatorTest).build();
    FieldEnd<? extends Query<?>> equalQuery = mock(FieldEnd.class);
    dataFetcherUtils.setIdFilter(equalQuery, equalOperatorFilter);
    verify(equalQuery, times(1)).equal(id);

    // Not Null filter should be set for EQUAL type QLIdFilter.
    QLIdFilter notNullOperatorFilter = QLIdFilter.builder().operator(QLIdOperator.NOT_NULL).values(null).build();
    FieldEnd<? extends Query<?>> notNullQuery = mock(FieldEnd.class);
    dataFetcherUtils.setIdFilter(notNullQuery, notNullOperatorFilter);
    verify(notNullQuery, times(1)).notEqual(null);

    // Like filter should be set for LIKE type QLIdFilter.
    String partialId = "id";
    String[] idsForLikeOperatorTest = new String[] {partialId};
    QLIdFilter likeOperatorFilter =
        QLIdFilter.builder().operator(QLIdOperator.LIKE).values(idsForLikeOperatorTest).build();
    FieldEnd<? extends Query<?>> likeQuery = mock(FieldEnd.class);
    dataFetcherUtils.setIdFilter(likeQuery, likeOperatorFilter);
    verify(likeQuery, times(1)).contains(partialId);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void shouldReturnActualAccountIdIfPSQLDataPresent() throws SQLException {
    when(featureFlagService.isEnabledReloadCache(eq(FeatureName.CE_SAMPLE_DATA_GENERATION), eq(ACCOUNT_ID)))
        .thenReturn(true);
    when(resultSet.next()).thenReturn(true).thenReturn(false);
    CEMetadataRecord ceMetadataRecord = CEMetadataRecord.builder().clusterDataConfigured(true).build();
    when(metadataRecordDao.getByAccountId(ACCOUNT_ID)).thenReturn(ceMetadataRecord);
    assertThat(dataFetcherUtils.fetchSampleAccountIdIfNoClusterData(ACCOUNT_ID)).isEqualTo(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void shouldReturnSampleAccountIdIfPSQLDataNotPresent() throws SQLException {
    when(featureFlagService.isEnabledReloadCache(eq(FeatureName.CE_SAMPLE_DATA_GENERATION), eq(ACCOUNT_ID)))
        .thenReturn(true);
    when(resultSet.next()).thenReturn(false);

    assertThat(dataFetcherUtils.fetchSampleAccountIdIfNoClusterData(ACCOUNT_ID)).isEqualTo(SAMPLE_ACCOUNT_ID);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void shouldCheckForPSQLDataAbsence() throws SQLException {
    when(resultSet.next()).thenReturn(false);

    assertThat(dataFetcherUtils.isAnyClusterDataPresent(ACCOUNT_ID)).isEqualTo(false);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void shouldCheckForPSQLDataPresence() throws SQLException {
    when(resultSet.next()).thenReturn(true).thenReturn(false);
    CEMetadataRecord ceMetadataRecord = CEMetadataRecord.builder().clusterDataConfigured(true).build();
    when(metadataRecordDao.getByAccountId(ACCOUNT_ID)).thenReturn(ceMetadataRecord);
    assertThat(dataFetcherUtils.isAnyClusterDataPresent(ACCOUNT_ID)).isEqualTo(true);
  }
}
