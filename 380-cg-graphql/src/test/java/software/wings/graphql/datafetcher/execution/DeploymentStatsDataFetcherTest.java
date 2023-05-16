/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.execution;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.AADITI;
import static io.harness.rule.OwnerRule.ABHINAV_MITTAL;
import static io.harness.rule.OwnerRule.ALEXANDRU_CIOFU;
import static io.harness.rule.OwnerRule.MILOS;
import static io.harness.rule.OwnerRule.RAMA;
import static io.harness.rule.OwnerRule.RUSHABH;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.WingsBaseTest;
import software.wings.beans.EntityType;
import software.wings.beans.Service;
import software.wings.beans.Service.ServiceKeys;
import software.wings.beans.Workflow;
import software.wings.beans.Workflow.WorkflowKeys;
import software.wings.dl.WingsPersistence;
import software.wings.graphql.datafetcher.execution.DeploymentStatsQueryMetaData.DeploymentMetaDataFields;
import software.wings.graphql.datafetcher.execution.DeploymentStatsQueryMetaData.ResultType;
import software.wings.graphql.datafetcher.tag.TagHelper;
import software.wings.graphql.schema.type.QLEnvironmentType;
import software.wings.graphql.schema.type.aggregation.QLAggregatedData;
import software.wings.graphql.schema.type.aggregation.QLCountAggregateOperation;
import software.wings.graphql.schema.type.aggregation.QLData;
import software.wings.graphql.schema.type.aggregation.QLDataPoint;
import software.wings.graphql.schema.type.aggregation.QLEnumOperator;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;
import software.wings.graphql.schema.type.aggregation.QLSinglePointData;
import software.wings.graphql.schema.type.aggregation.QLSortOrder;
import software.wings.graphql.schema.type.aggregation.QLStackedData;
import software.wings.graphql.schema.type.aggregation.QLStackedDataPoint;
import software.wings.graphql.schema.type.aggregation.QLStackedTimeSeriesData;
import software.wings.graphql.schema.type.aggregation.QLStackedTimeSeriesDataPoint;
import software.wings.graphql.schema.type.aggregation.QLStringFilter;
import software.wings.graphql.schema.type.aggregation.QLStringOperator;
import software.wings.graphql.schema.type.aggregation.QLTimeAggregationType;
import software.wings.graphql.schema.type.aggregation.QLTimeFilter;
import software.wings.graphql.schema.type.aggregation.QLTimeOperator;
import software.wings.graphql.schema.type.aggregation.QLTimeSeriesAggregation;
import software.wings.graphql.schema.type.aggregation.QLTimeSeriesData;
import software.wings.graphql.schema.type.aggregation.QLTimeSeriesDataPoint;
import software.wings.graphql.schema.type.aggregation.deployment.QLDeploymentAggregation;
import software.wings.graphql.schema.type.aggregation.deployment.QLDeploymentEntityAggregation;
import software.wings.graphql.schema.type.aggregation.deployment.QLDeploymentFilter;
import software.wings.graphql.schema.type.aggregation.deployment.QLDeploymentSortCriteria;
import software.wings.graphql.schema.type.aggregation.deployment.QLDeploymentSortType;
import software.wings.graphql.schema.type.aggregation.deployment.QLDeploymentTagAggregation;
import software.wings.graphql.schema.type.aggregation.deployment.QLDeploymentTagFilter;
import software.wings.graphql.schema.type.aggregation.deployment.QLDeploymentTagType;
import software.wings.graphql.schema.type.aggregation.environment.QLEnvironmentTypeFilter;
import software.wings.graphql.schema.type.aggregation.service.QLDeploymentType;
import software.wings.graphql.schema.type.aggregation.service.QLDeploymentTypeFilter;
import software.wings.graphql.schema.type.aggregation.tag.QLTagInput;
import software.wings.graphql.schema.type.aggregation.workflow.QLOrchestrationWorkflowType;
import software.wings.graphql.schema.type.aggregation.workflow.QLOrchestrationWorkflowTypeFilter;

import com.google.inject.Inject;
import de.danielbechler.util.Collections;
import dev.morphia.query.FieldEnd;
import dev.morphia.query.Query;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import lombok.experimental.FieldNameConstants;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

@OwnedBy(CDC)
@FieldNameConstants(innerTypeName = "DeploymentStatsDataFetcherTestKeys")
public class DeploymentStatsDataFetcherTest extends WingsBaseTest {
  @Mock TimeScaleDBService timeScaleDBService;
  @Mock QLStatsHelper statsHelper;
  @Mock TagHelper tagHelper;
  @Mock ResultSet resultSet;
  @Mock WingsPersistence wingsPersistence;
  @Inject @InjectMocks software.wings.graphql.datafetcher.execution.DeploymentStatsDataFetcher dataFetcher;

  final int[] count = {0};
  final int[] intVal = {0};
  final long[] longVal = {0};
  final int[] stringVal = {0};
  final int[] tagVal = {0};
  final long currentTime = System.currentTimeMillis();
  final long[] calendar = {currentTime};

  @Before
  public void setUp() throws SQLException {
    Connection mockConnection = mock(Connection.class);
    Statement mockStatement = mock(Statement.class);
    when(timeScaleDBService.getDBConnection()).thenReturn(mockConnection);
    when(timeScaleDBService.isValid()).thenReturn(true);
    when(mockConnection.createStatement()).thenReturn(mockStatement);
    when(mockStatement.executeQuery(anyString())).thenReturn(resultSet);
    resetValues();
    mockStatHelper();
    mockResultSet();
    mockTagHelper();
  }

  private void mockTagHelper() {
    when(tagHelper.getEntityIdsFromTags(anyString(), anyList(), ArgumentMatchers.any(EntityType.class)))
        .thenReturn(Collections.setOf(asList("DATA1", "DATA2")));
  }

  private String ACCOUNTID;
  private String SERVICE1;
  private String SERVICE2;

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testQueries() {
    QLDeploymentFilter arrayIdFilter =
        QLDeploymentFilter.builder()
            .service(QLIdFilter.builder()
                         .operator(QLIdOperator.IN)
                         .values(new String[] {
                             DeploymentStatsDataFetcherTestKeys.SERVICE1, DeploymentStatsDataFetcherTestKeys.SERVICE2})
                         .build())
            .build();
    QLDeploymentFilter beforeTimeFilter =
        QLDeploymentFilter.builder()
            .endTime(QLTimeFilter.builder().operator(QLTimeOperator.AFTER).value(1564612564000L).build())
            .build();

    QLDeploymentFilter afterTimeFilter =
        QLDeploymentFilter.builder()
            .endTime(QLTimeFilter.builder().operator(QLTimeOperator.BEFORE).value(1564612869000L).build())
            .build();
    QLDeploymentFilter arrayStringFilter = QLDeploymentFilter.builder()
                                               .status(QLStringFilter.builder()
                                                           .operator(QLStringOperator.IN)
                                                           .values(new String[] {"SUCCESS", "FAILURE"})
                                                           .build())
                                               .build();

    QLDeploymentFilter arrayEnvTypeFilter =
        QLDeploymentFilter.builder()
            .environmentType(QLEnvironmentTypeFilter.builder()
                                 .operator(QLEnumOperator.IN)
                                 .values(new QLEnvironmentType[] {QLEnvironmentType.PROD})
                                 .build())
            .build();
    DeploymentStatsQueryMetaData queryMetaData =
        dataFetcher.formQueryWithNonHStoreGroupBy(DeploymentStatsDataFetcherTestKeys.ACCOUNTID, null,
            asList(arrayIdFilter, beforeTimeFilter, afterTimeFilter, arrayStringFilter, arrayEnvTypeFilter),
            asList(QLDeploymentEntityAggregation.Application), null, null, false);

    assertThat(queryMetaData.getQuery())
        .isEqualTo(
            "SELECT COUNT(*) AS COUNT,t0.APPID FROM DEPLOYMENT t0 WHERE (t0.PARENT_EXECUTION IS NULL) AND ((t0.SERVICES @>'{SERVICE1}') OR (t0.SERVICES @>'{SERVICE2}')) AND (t0.ENDTIME >= '2019-07-31T22:36:04Z') AND (t0.ENDTIME <= '2019-07-31T22:41:09Z') AND (t0.STATUS IN ('SUCCESS','FAILURE') ) AND (t0.ENVTYPES @>'{PROD}') AND (t0.ACCOUNTID = 'ACCOUNTID') AND (t0.APPID IS NOT NULL) GROUP BY t0.APPID");

    assertThat(queryMetaData.getResultType()).isEqualTo(ResultType.AGGREGATE_DATA);

    queryMetaData = dataFetcher.formQueryWithNonHStoreGroupBy(DeploymentStatsDataFetcherTestKeys.ACCOUNTID, null,
        asList(arrayIdFilter, beforeTimeFilter, afterTimeFilter, arrayStringFilter, arrayEnvTypeFilter), null, null,
        null, false);

    assertThat(queryMetaData.getQuery())
        .isEqualTo(
            "SELECT COUNT(*) AS COUNT FROM DEPLOYMENT t0 WHERE (t0.PARENT_EXECUTION IS NULL) AND ((t0.SERVICES @>'{SERVICE1}') OR (t0.SERVICES @>'{SERVICE2}')) AND (t0.ENDTIME >= '2019-07-31T22:36:04Z') AND (t0.ENDTIME <= '2019-07-31T22:41:09Z') AND (t0.STATUS IN ('SUCCESS','FAILURE') ) AND (t0.ENVTYPES @>'{PROD}') AND (t0.ACCOUNTID = 'ACCOUNTID')");
    assertThat(queryMetaData.getResultType()).isEqualTo(ResultType.SINGLE_POINT);

    queryMetaData = dataFetcher.formQueryWithNonHStoreGroupBy(DeploymentStatsDataFetcherTestKeys.ACCOUNTID, null,
        asList(arrayIdFilter, beforeTimeFilter, afterTimeFilter, arrayStringFilter, arrayEnvTypeFilter),
        asList(QLDeploymentEntityAggregation.Application, QLDeploymentEntityAggregation.Environment), null, null,
        false);

    assertThat(queryMetaData.getQuery())
        .isEqualTo(
            "SELECT COUNT(*) AS COUNT,t0.APPID,ENVID FROM DEPLOYMENT t0, unnest(ENVIRONMENTS) ENVID WHERE (t0.PARENT_EXECUTION IS NULL) AND ((t0.SERVICES @>'{SERVICE1}') OR (t0.SERVICES @>'{SERVICE2}')) AND (t0.ENDTIME >= '2019-07-31T22:36:04Z') AND (t0.ENDTIME <= '2019-07-31T22:41:09Z') AND (t0.STATUS IN ('SUCCESS','FAILURE') ) AND (t0.ENVTYPES @>'{PROD}') AND (t0.ACCOUNTID = 'ACCOUNTID') AND (t0.APPID IS NOT NULL) GROUP BY t0.APPID,ENVID");
    assertThat(queryMetaData.getResultType()).isEqualTo(ResultType.STACKED_BAR_CHART);

    queryMetaData = dataFetcher.formQueryWithNonHStoreGroupBy(DeploymentStatsDataFetcherTestKeys.ACCOUNTID, null,
        asList(arrayIdFilter, beforeTimeFilter, afterTimeFilter, arrayStringFilter, arrayEnvTypeFilter),
        asList(QLDeploymentEntityAggregation.Application),
        QLTimeSeriesAggregation.builder()
            .timeAggregationType(QLTimeAggregationType.HOUR)
            .timeAggregationValue(1)
            .build(),
        null, false);

    assertThat(queryMetaData.getQuery())
        .isEqualTo(
            "SELECT COUNT(*) AS COUNT,time_bucket('1 hours',endtime) AS TIME_BUCKET,t0.APPID FROM DEPLOYMENT t0 WHERE (t0.PARENT_EXECUTION IS NULL) AND ((t0.SERVICES @>'{SERVICE1}') OR (t0.SERVICES @>'{SERVICE2}')) AND (t0.ENDTIME >= '2019-07-31T22:36:04Z') AND (t0.ENDTIME <= '2019-07-31T22:41:09Z') AND (t0.STATUS IN ('SUCCESS','FAILURE') ) AND (t0.ENVTYPES @>'{PROD}') AND (t0.ACCOUNTID = 'ACCOUNTID') AND (t0.APPID IS NOT NULL) GROUP BY TIME_BUCKET,t0.APPID ORDER BY TIME_BUCKET ASC");
    assertThat(queryMetaData.getResultType()).isEqualTo(ResultType.STACKED_TIME_SERIES);

    queryMetaData = dataFetcher.formQueryWithNonHStoreGroupBy(DeploymentStatsDataFetcherTestKeys.ACCOUNTID, null,
        asList(arrayIdFilter, beforeTimeFilter, afterTimeFilter, arrayStringFilter, arrayEnvTypeFilter), null,
        QLTimeSeriesAggregation.builder()
            .timeAggregationType(QLTimeAggregationType.HOUR)
            .timeAggregationValue(1)
            .build(),
        null, false);

    assertThat(queryMetaData.getQuery())
        .isEqualTo(
            "SELECT COUNT(*) AS COUNT,time_bucket('1 hours',endtime) AS TIME_BUCKET FROM DEPLOYMENT t0 WHERE (t0.PARENT_EXECUTION IS NULL) AND ((t0.SERVICES @>'{SERVICE1}') OR (t0.SERVICES @>'{SERVICE2}')) AND (t0.ENDTIME >= '2019-07-31T22:36:04Z') AND (t0.ENDTIME <= '2019-07-31T22:41:09Z') AND (t0.STATUS IN ('SUCCESS','FAILURE') ) AND (t0.ENVTYPES @>'{PROD}') AND (t0.ACCOUNTID = 'ACCOUNTID') GROUP BY TIME_BUCKET ORDER BY TIME_BUCKET ASC");
    assertThat(queryMetaData.getResultType()).isEqualTo(ResultType.TIME_SERIES);

    QLDeploymentFilter appTagFilter =
        QLDeploymentFilter.builder()
            .tag(QLDeploymentTagFilter.builder()
                     .entityType(QLDeploymentTagType.APPLICATION)
                     .tags(asList(QLTagInput.builder().name("TAG1").value("DATA1").build()))
                     .build())
            .build();

    QLDeploymentFilter serviceTagFilter =
        QLDeploymentFilter.builder()
            .tag(QLDeploymentTagFilter.builder()
                     .entityType(QLDeploymentTagType.SERVICE)
                     .tags(asList(QLTagInput.builder().name("TAG1").value("DATA1").build()))
                     .build())
            .build();

    QLDeploymentFilter envTagFilter =
        QLDeploymentFilter.builder()
            .tag(QLDeploymentTagFilter.builder()
                     .entityType(QLDeploymentTagType.ENVIRONMENT)
                     .tags(asList(QLTagInput.builder().name("TAG1").value("DATA1").build()))
                     .build())
            .build();

    queryMetaData = dataFetcher.formQueryWithNonHStoreGroupBy(DeploymentStatsDataFetcherTestKeys.ACCOUNTID, null,
        asList(arrayIdFilter, beforeTimeFilter, afterTimeFilter, arrayStringFilter, arrayEnvTypeFilter, appTagFilter,
            serviceTagFilter, envTagFilter),
        null,
        QLTimeSeriesAggregation.builder()
            .timeAggregationType(QLTimeAggregationType.HOUR)
            .timeAggregationValue(1)
            .build(),
        null, false);

    assertThat(queryMetaData.getQuery())
        .isEqualTo(
            "SELECT COUNT(*) AS COUNT,time_bucket('1 hours',endtime) AS TIME_BUCKET FROM DEPLOYMENT t0 WHERE (t0.PARENT_EXECUTION IS NULL) AND ((t0.SERVICES @>'{SERVICE1}') OR (t0.SERVICES @>'{SERVICE2}')) AND (t0.ENDTIME >= '2019-07-31T22:36:04Z') AND (t0.ENDTIME <= '2019-07-31T22:41:09Z') AND (t0.STATUS IN ('SUCCESS','FAILURE') ) AND (t0.ENVTYPES @>'{PROD}') AND (t0.APPID IN ('DATA1','DATA2') ) AND ((t0.SERVICES @>'{DATA1}') OR (t0.SERVICES @>'{DATA2}')) AND ((t0.ENVIRONMENTS @>'{DATA1}') OR (t0.ENVIRONMENTS @>'{DATA2}')) AND (t0.ACCOUNTID = 'ACCOUNTID') GROUP BY TIME_BUCKET ORDER BY TIME_BUCKET ASC");
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testSingleDataPoint() {
    try {
      DeploymentStatsQueryMetaData metaData =
          DeploymentStatsQueryMetaData.builder().fieldNames(asList(DeploymentMetaDataFields.COUNT)).build();

      when(resultSet.next()).then((Answer<Boolean>) invocation -> {
        switch (count[0]) {
          case 0:
            count[0]++;
            return true;
          default:
            return false;
        }
      });

      when(resultSet.getInt(anyString())).thenReturn(10);
      when(resultSet.getLong(anyString())).thenReturn(20L);

      resetValues();

      QLData qlData = dataFetcher.fetch(DeploymentStatsDataFetcherTestKeys.ACCOUNTID,
          QLDeploymentAggregationFunction.builder().count(QLCountAggregateOperation.SUM).build(), new ArrayList<>(),
          new ArrayList<>(), new ArrayList<>(), null);

      assertThat(qlData.getClass()).isEqualTo(QLSinglePointData.class);
      QLSinglePointData singlePointData = (QLSinglePointData) qlData;
      validateSingleDataPoint(singlePointData, 10);

      resetValues();

      metaData = DeploymentStatsQueryMetaData.builder().fieldNames(asList(DeploymentMetaDataFields.DURATION)).build();
      qlData = dataFetcher.getData(DeploymentStatsDataFetcherTestKeys.ACCOUNTID,
          QLDeploymentAggregationFunction.builder().duration(QLDurationAggregateOperation.MAX).build(),
          asList(QLDeploymentFilter.builder()
                     .tag(QLDeploymentTagFilter.builder()
                              .entityType(QLDeploymentTagType.APPLICATION)
                              .tags(asList(QLTagInput.builder().name("TAG1").value("VALUE").build()))
                              .build())
                     .build(),
              QLDeploymentFilter.builder()
                  .tag(QLDeploymentTagFilter.builder()
                           .entityType(QLDeploymentTagType.SERVICE)
                           .tags(asList(QLTagInput.builder().name("TAG1").value("VALUE").build()))
                           .build())
                  .build(),
              QLDeploymentFilter.builder()
                  .tag(QLDeploymentTagFilter.builder()
                           .entityType(QLDeploymentTagType.ENVIRONMENT)
                           .tags(asList(QLTagInput.builder().name("TAG1").value("VALUE").build()))
                           .build())
                  .build()),
          new ArrayList<>(), new ArrayList<>(), false);

      assertThat(qlData.getClass()).isEqualTo(QLSinglePointData.class);
      singlePointData = (QLSinglePointData) qlData;
      validateSingleDataPoint(singlePointData, 20L);

      resetValues();
      try {
        metaData = DeploymentStatsQueryMetaData.builder().fieldNames(asList(DeploymentMetaDataFields.APPID)).build();
        qlData = dataFetcher.generateSinglePointData(metaData, resultSet);
        fail("Expected an exception");
      } catch (RuntimeException e) {
        assertThat(e).hasMessage(
            "Single Data Type data type not supported " + DeploymentMetaDataFields.APPID.getDataType());
      }

    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testAggregateData() {
    try {
      QLData qlData = dataFetcher.fetch(DeploymentStatsDataFetcherTestKeys.ACCOUNTID,
          QLDeploymentAggregationFunction.builder().count(QLCountAggregateOperation.SUM).build(), new ArrayList<>(),
          asList(
              QLDeploymentAggregation.builder().entityAggregation(QLDeploymentEntityAggregation.Environment).build()),
          new ArrayList<>(), null);

      assertThat(qlData).isInstanceOf(QLAggregatedData.class);
      QLAggregatedData aggregatedData = (QLAggregatedData) qlData;
      assertThat(aggregatedData.getDataPoints().size()).isEqualTo(5);
      validateAggregateDataPoint(aggregatedData, 0, 10, "DATA0", "DATA0NAME");
      validateAggregateDataPoint(aggregatedData, 1, 11, "DATA1", "DATA1NAME");
      validateAggregateDataPoint(aggregatedData, 2, 12, "DATA2", "DATA2NAME");
      validateAggregateDataPoint(aggregatedData, 3, 13, "DATA3", "DATA3NAME");
      validateAggregateDataPoint(aggregatedData, 4, 14, "DATA4", "DATA4NAME");

    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  private void mockStatHelper() {
    when(statsHelper.getEntityName(ArgumentMatchers.any(DeploymentMetaDataFields.class), anyString()))
        .thenAnswer(new Answer<String>() {
          @Override
          public String answer(InvocationOnMock invocation) throws Throwable {
            return invocation.getArguments()[1] + "NAME";
          }
        });
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testStackedData() {
    try {
      QLStackedData qlData = (QLStackedData) dataFetcher.fetch(DeploymentStatsDataFetcherTestKeys.ACCOUNTID,
          QLDeploymentAggregationFunction.builder().count(QLCountAggregateOperation.SUM).build(), new ArrayList<>(),
          asList(QLDeploymentAggregation.builder().entityAggregation(QLDeploymentEntityAggregation.Service).build(),
              QLDeploymentAggregation.builder().entityAggregation(QLDeploymentEntityAggregation.Application).build()),
          new ArrayList<>(), null);

      assertThat(qlData.getDataPoints().size()).isEqualTo(5);

      validateStackedDataPointKey(qlData.getDataPoints().get(0), "DATA0", "DATA0NAME");
      assertThat(qlData.getDataPoints().get(0).getValues().size()).isEqualTo(1);
      validateQLDataPoint(qlData.getDataPoints().get(0).getValues().get(0), "DATA1", "DATA1NAME", 10);

      validateStackedDataPointKey(qlData.getDataPoints().get(1), "DATA2", "DATA2NAME");
      assertThat(qlData.getDataPoints().get(1).getValues().size()).isEqualTo(1);
      validateQLDataPoint(qlData.getDataPoints().get(1).getValues().get(0), "DATA3", "DATA3NAME", 11);

      validateStackedDataPointKey(qlData.getDataPoints().get(2), "DATA4", "DATA4NAME");
      assertThat(qlData.getDataPoints().get(2).getValues().size()).isEqualTo(1);
      validateQLDataPoint(qlData.getDataPoints().get(2).getValues().get(0), "DATA5", "DATA5NAME", 12);

      validateStackedDataPointKey(qlData.getDataPoints().get(3), "DATA6", "DATA6NAME");
      assertThat(qlData.getDataPoints().get(3).getValues().size()).isEqualTo(1);
      validateQLDataPoint(qlData.getDataPoints().get(3).getValues().get(0), "DATA7", "DATA7NAME", 13);

      validateStackedDataPointKey(qlData.getDataPoints().get(4), "DATA8", "DATA8NAME");
      assertThat(qlData.getDataPoints().get(4).getValues().size()).isEqualTo(1);
      validateQLDataPoint(qlData.getDataPoints().get(4).getValues().get(0), "DATA9", "DATA9NAME", 14);

      resetValues();

      qlData = (QLStackedData) dataFetcher.fetch(DeploymentStatsDataFetcherTestKeys.ACCOUNTID,
          QLDeploymentAggregationFunction.builder().count(QLCountAggregateOperation.SUM).build(), new ArrayList<>(),
          asList(QLDeploymentAggregation.builder().entityAggregation(QLDeploymentEntityAggregation.Status).build(),
              QLDeploymentAggregation.builder().entityAggregation(QLDeploymentEntityAggregation.Application).build()),
          asList(QLDeploymentSortCriteria.builder()
                     .sortOrder(QLSortOrder.DESCENDING)
                     .sortType(QLDeploymentSortType.Count)
                     .build()),
          null);

      validateStackedDataPointKey(qlData.getDataPoints().get(4), "DATA0", "DATA0NAME");
      assertThat(qlData.getDataPoints().get(4).getValues().size()).isEqualTo(1);
      validateQLDataPoint(qlData.getDataPoints().get(4).getValues().get(0), "DATA1", "DATA1NAME", 10);

      validateStackedDataPointKey(qlData.getDataPoints().get(3), "DATA2", "DATA2NAME");
      assertThat(qlData.getDataPoints().get(3).getValues().size()).isEqualTo(1);
      validateQLDataPoint(qlData.getDataPoints().get(3).getValues().get(0), "DATA3", "DATA3NAME", 11);

      validateStackedDataPointKey(qlData.getDataPoints().get(2), "DATA4", "DATA4NAME");
      assertThat(qlData.getDataPoints().get(2).getValues().size()).isEqualTo(1);
      validateQLDataPoint(qlData.getDataPoints().get(2).getValues().get(0), "DATA5", "DATA5NAME", 12);

      validateStackedDataPointKey(qlData.getDataPoints().get(1), "DATA6", "DATA6NAME");
      assertThat(qlData.getDataPoints().get(1).getValues().size()).isEqualTo(1);
      validateQLDataPoint(qlData.getDataPoints().get(1).getValues().get(0), "DATA7", "DATA7NAME", 13);

      validateStackedDataPointKey(qlData.getDataPoints().get(0), "DATA8", "DATA8NAME");
      assertThat(qlData.getDataPoints().get(0).getValues().size()).isEqualTo(1);
      validateQLDataPoint(qlData.getDataPoints().get(0).getValues().get(0), "DATA9", "DATA9NAME", 14);

    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  private void resetValues() {
    count[0] = 0;
    intVal[0] = 0;
    longVal[0] = 0;
    stringVal[0] = 0;
    tagVal[0] = 0;
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testTimeSeriesData() {
    try {
      QLTimeSeriesData timeSeriesData =
          (QLTimeSeriesData) dataFetcher.fetch(DeploymentStatsDataFetcherTestKeys.ACCOUNTID,
              QLDeploymentAggregationFunction.builder().count(QLCountAggregateOperation.SUM).build(), new ArrayList<>(),
              asList(QLDeploymentAggregation.builder()
                         .timeAggregation(QLTimeSeriesAggregation.builder()
                                              .timeAggregationType(QLTimeAggregationType.DAY)
                                              .timeAggregationValue(1)
                                              .build())
                         .build()),
              new ArrayList<>(), null);

      validateTimeSeriesDataPoint(timeSeriesData.getDataPoints().get(0), 10, currentTime + 3600000 * 1);
      validateTimeSeriesDataPoint(timeSeriesData.getDataPoints().get(1), 11, currentTime + 3600000 * 2);
      validateTimeSeriesDataPoint(timeSeriesData.getDataPoints().get(2), 12, currentTime + 3600000 * 3);
      validateTimeSeriesDataPoint(timeSeriesData.getDataPoints().get(3), 13, currentTime + 3600000 * 4);
      validateTimeSeriesDataPoint(timeSeriesData.getDataPoints().get(4), 14, currentTime + 3600000 * 5);

    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  private void returnResultSet(int limit) throws SQLException {
    when(resultSet.next()).then((Answer<Boolean>) invocation -> {
      if (count[0] < limit) {
        count[0]++;
        return true;
      }
      return false;
    });
  }

  private void mockResultSet() throws SQLException {
    when(resultSet.getInt(anyString())).thenAnswer((Answer<Integer>) invocation -> 10 + intVal[0]++);
    when(resultSet.getLong(anyString())).thenAnswer((Answer<Long>) invocation -> 20L + longVal[0]++);
    when(resultSet.getString(anyString())).thenAnswer((Answer<String>) invocation -> "DATA" + stringVal[0]++);
    when(resultSet.getTimestamp(anyString(), any(Calendar.class))).thenAnswer((Answer<Timestamp>) invocation -> {
      calendar[0] = calendar[0] + 3600000;
      return new Timestamp(calendar[0]);
    });
    when(resultSet.getString("tag")).thenAnswer((Answer<String>) invocation -> "DATA-TAG" + tagVal[0]++);
    returnResultSet(5);
    resetValues();
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testStackedTimeSeriesDataForCount() {
    testStackedTimeSeriesData(QLDeploymentAggregationFunction.builder().count(QLCountAggregateOperation.SUM).build());
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testStackedTimeSeriesDataForInstancesDeployed() {
    testStackedTimeSeriesData(
        QLDeploymentAggregationFunction.builder().instancesDeployed(QLCountAggregateOperation.SUM).build());
  }

  private void testStackedTimeSeriesData(QLDeploymentAggregationFunction deploymentAggregationFunction) {
    try {
      QLStackedTimeSeriesData data = (QLStackedTimeSeriesData) dataFetcher.fetch(
          DeploymentStatsDataFetcherTestKeys.ACCOUNTID, deploymentAggregationFunction, new ArrayList<>(),
          asList(QLDeploymentAggregation.builder()
                     .timeAggregation(QLTimeSeriesAggregation.builder()
                                          .timeAggregationType(QLTimeAggregationType.DAY)
                                          .timeAggregationValue(1)
                                          .build())
                     .entityAggregation(QLDeploymentEntityAggregation.EnvironmentType)
                     .build()),
          new ArrayList<>(), null);

      validateStackedTimeSeriesDataPoint(data.getData().get(0), currentTime + 3600000 * 1);
      validateStackedTimeSeriesDataPoint(data.getData().get(1), currentTime + 3600000 * 2);
      validateStackedTimeSeriesDataPoint(data.getData().get(2), currentTime + 3600000 * 3);
      validateStackedTimeSeriesDataPoint(data.getData().get(3), currentTime + 3600000 * 4);
      validateStackedTimeSeriesDataPoint(data.getData().get(4), currentTime + 3600000 * 5);
      validateQLDataPoint(data.getData().get(0).getValues().get(0), "DATA0", "DATA0NAME", 10);
      validateQLDataPoint(data.getData().get(1).getValues().get(0), "DATA1", "DATA1NAME", 11);
      validateQLDataPoint(data.getData().get(2).getValues().get(0), "DATA2", "DATA2NAME", 12);
      validateQLDataPoint(data.getData().get(3).getValues().get(0), "DATA3", "DATA3NAME", 13);
      validateQLDataPoint(data.getData().get(4).getValues().get(0), "DATA4", "DATA4NAME", 14);

    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  private void validateStackedTimeSeriesDataPoint(QLStackedTimeSeriesDataPoint dataPoint, long timeStamp) {
    assertThat(dataPoint.getTime()).isEqualTo(timeStamp);
  }

  private void validateTimeSeriesDataPoint(QLTimeSeriesDataPoint dataPoint, int value, long timeStamp) {
    assertThat(dataPoint.getValue()).isEqualTo(value);
    assertThat(dataPoint.getTime()).isEqualTo(timeStamp);
  }

  private void validateStackedDataPointKey(QLStackedDataPoint dataPoint, String id, String name) {
    assertThat(dataPoint.getKey().getId()).isEqualTo(id);
    assertThat(dataPoint.getKey().getName()).isEqualTo(name);
  }

  private void validateQLDataPoint(QLDataPoint dataPoint, String id, String name, int value) {
    assertThat(dataPoint.getKey().getId()).isEqualTo(id);
    assertThat(dataPoint.getKey().getName()).isEqualTo(name);
    assertThat(dataPoint.getValue()).isEqualTo(value);
  }

  private void validateAggregateDataPoint(QLAggregatedData aggregatedData, int i, int i2, String s, String s2) {
    assertThat(aggregatedData.getDataPoints().get(i).getValue()).isEqualTo(i2);
    assertThat(aggregatedData.getDataPoints().get(i).getKey().getType())
        .isEqualTo(DeploymentMetaDataFields.ENVID.name());
    assertThat(aggregatedData.getDataPoints().get(i).getKey().getId()).isEqualTo(s);
    assertThat(aggregatedData.getDataPoints().get(i).getKey().getName()).isEqualTo(s2);
  }

  private void validateSingleDataPoint(QLSinglePointData singlePointData, Number l) {
    assertThat(singlePointData.getDataPoint().getKey().getId()).isEqualTo(dataFetcher.getEntityType());
    assertThat(singlePointData.getDataPoint().getKey().getName()).isEqualTo(dataFetcher.getEntityType());
    assertThat(singlePointData.getDataPoint().getKey().getType()).isEqualTo(dataFetcher.getEntityType());
    assertThat(singlePointData.getDataPoint().getValue()).isEqualTo(l);
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void assertGroupByTag() {
    assertThat(dataFetcher.getGroupByEntityFromTag(
                   QLDeploymentTagAggregation.builder().entityType(QLDeploymentTagType.APPLICATION).build()))
        .isEqualTo(QLDeploymentEntityAggregation.Application);
    assertThat(dataFetcher.getGroupByEntityFromTag(
                   QLDeploymentTagAggregation.builder().entityType(QLDeploymentTagType.SERVICE).build()))
        .isEqualTo(QLDeploymentEntityAggregation.Service);

    assertThat(dataFetcher.getGroupByEntityFromTag(
                   QLDeploymentTagAggregation.builder().entityType(QLDeploymentTagType.ENVIRONMENT).build()))
        .isEqualTo(QLDeploymentEntityAggregation.Environment);
    assertThat(dataFetcher.getGroupByEntityFromTag(
                   QLDeploymentTagAggregation.builder().entityType(QLDeploymentTagType.DEPLOYMENT).build()))
        .isEqualTo(QLDeploymentEntityAggregation.Deployment);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testQueriesWithDeploymentTags() {
    QLDeploymentFilter beforeTimeFilter =
        QLDeploymentFilter.builder()
            .endTime(QLTimeFilter.builder().operator(QLTimeOperator.AFTER).value(1564612564000L).build())
            .build();

    QLDeploymentFilter afterTimeFilter =
        QLDeploymentFilter.builder()
            .endTime(QLTimeFilter.builder().operator(QLTimeOperator.BEFORE).value(1564612869000L).build())
            .build();

    QLDeploymentFilter deploymentTagFilter =
        QLDeploymentFilter.builder()
            .tag(QLDeploymentTagFilter.builder()
                     .entityType(QLDeploymentTagType.DEPLOYMENT)
                     .tags(asList(QLTagInput.builder().name("commitId").value("").build()))
                     .build())
            .build();

    QLDeploymentFilter appTagFilter = QLDeploymentFilter.builder()
                                          .tag(QLDeploymentTagFilter.builder()
                                                   .entityType(QLDeploymentTagType.APPLICATION)
                                                   .tags(asList(QLTagInput.builder().name("foo").value("").build()))
                                                   .build())
                                          .build();

    QLDeploymentFilter arrayStringFilter = QLDeploymentFilter.builder()
                                               .status(QLStringFilter.builder()
                                                           .operator(QLStringOperator.IN)
                                                           .values(new String[] {"SUCCESS", "FAILURE"})
                                                           .build())
                                               .build();

    DeploymentStatsQueryMetaData queryMetaData =
        dataFetcher.formQueryWithHStoreGroupBy(DeploymentStatsDataFetcherTestKeys.ACCOUNTID, null,
            asList(beforeTimeFilter, afterTimeFilter), asList(QLDeploymentEntityAggregation.Deployment),
            asList(QLDeploymentTagAggregation.builder()
                       .entityType(QLDeploymentTagType.DEPLOYMENT)
                       .tagName("commitId")
                       .build()),
            null, null, false);

    assertThat(queryMetaData.getQuery())
        .isEqualTo(
            "SELECT COUNT(*) AS COUNT,concat('commitId:',values) as tag FROM (SELECT (each(TAGS)).key AS key ,(each(TAGS)).value::text AS values FROM deployment t0 WHERE (EXISTS (SELECT * FROM skeys(t0.TAGS) as x(tagname) WHERE ((x.tagname='commitId') AND (t0.ENDTIME >= '2019-07-31T22:36:04Z') AND (t0.ENDTIME <= '2019-07-31T22:41:09Z') AND (t0.ACCOUNTID = 'ACCOUNTID'))))) as tab1 WHERE (t0.PARENT_EXECUTION IS NULL) AND (tab1.key='commitId') GROUP BY tab1.values");

    assertThat(queryMetaData.getResultType()).isEqualTo(ResultType.AGGREGATE_DATA);

    queryMetaData = dataFetcher.formQueryWithHStoreGroupBy(DeploymentStatsDataFetcherTestKeys.ACCOUNTID, null,
        asList(beforeTimeFilter, afterTimeFilter, deploymentTagFilter, arrayStringFilter),
        asList(QLDeploymentEntityAggregation.Deployment),
        asList(QLDeploymentTagAggregation.builder()
                   .entityType(QLDeploymentTagType.DEPLOYMENT)
                   .tagName("commitId")
                   .build()),
        null, null, false);

    assertThat(queryMetaData.getQuery())
        .isEqualTo(
            "SELECT COUNT(*) AS COUNT,concat('commitId:',values) as tag FROM (SELECT (each(TAGS)).key AS key ,(each(TAGS)).value::text AS values FROM deployment t0 WHERE (EXISTS (SELECT * FROM skeys(t0.TAGS) as x(tagname) WHERE ((x.tagname='commitId') AND (t0.ENDTIME >= '2019-07-31T22:36:04Z') AND (t0.ENDTIME <= '2019-07-31T22:41:09Z') AND (t0.tags ->'commitId' IS NOT NULL) AND (t0.STATUS IN ('SUCCESS','FAILURE') ) AND (t0.ACCOUNTID = 'ACCOUNTID'))))) as tab1 WHERE (t0.PARENT_EXECUTION IS NULL) AND (tab1.key='commitId') GROUP BY tab1.values");

    assertThat(queryMetaData.getResultType()).isEqualTo(ResultType.AGGREGATE_DATA);

    queryMetaData = dataFetcher.formQueryWithHStoreGroupBy(DeploymentStatsDataFetcherTestKeys.ACCOUNTID, null,
        asList(beforeTimeFilter, afterTimeFilter, arrayStringFilter, deploymentTagFilter),
        asList(QLDeploymentEntityAggregation.Deployment, QLDeploymentEntityAggregation.Environment),
        asList(QLDeploymentTagAggregation.builder()
                   .entityType(QLDeploymentTagType.DEPLOYMENT)
                   .tagName("commitId")
                   .build()),
        null, null, false);

    assertThat(queryMetaData.getQuery())
        .isEqualTo(
            "SELECT COUNT(*) AS COUNT,concat('commitId:',values) as tag,ENVID FROM (SELECT (each(TAGS)).key AS key ,(each(TAGS)).value::text AS values,unnest(ENVIRONMENTS) ENVID FROM deployment t0 WHERE (EXISTS (SELECT * FROM skeys(t0.TAGS) as x(tagname) WHERE ((x.tagname='commitId') AND (t0.ENDTIME >= '2019-07-31T22:36:04Z') AND (t0.ENDTIME <= '2019-07-31T22:41:09Z') AND (t0.STATUS IN ('SUCCESS','FAILURE') ) AND (t0.tags ->'commitId' IS NOT NULL) AND (t0.ACCOUNTID = 'ACCOUNTID'))))) as tab1 WHERE (t0.PARENT_EXECUTION IS NULL) AND (tab1.key='commitId') GROUP BY tab1.values,ENVID");
    assertThat(queryMetaData.getResultType()).isEqualTo(ResultType.STACKED_BAR_CHART);

    queryMetaData = dataFetcher.formQueryWithNonHStoreGroupBy(DeploymentStatsDataFetcherTestKeys.ACCOUNTID, null,
        asList(arrayStringFilter, beforeTimeFilter, afterTimeFilter, deploymentTagFilter),
        asList(QLDeploymentEntityAggregation.Application),
        QLTimeSeriesAggregation.builder()
            .timeAggregationType(QLTimeAggregationType.DAY)
            .timeAggregationValue(1)
            .build(),
        null, false);

    assertThat(queryMetaData.getQuery())
        .isEqualTo(
            "SELECT COUNT(*) AS COUNT,time_bucket('1 days',endtime) AS TIME_BUCKET,t0.APPID FROM DEPLOYMENT t0 WHERE (t0.PARENT_EXECUTION IS NULL) AND (t0.STATUS IN ('SUCCESS','FAILURE') ) AND (t0.ENDTIME >= '2019-07-31T22:36:04Z') AND (t0.ENDTIME <= '2019-07-31T22:41:09Z') AND (t0.tags ->'commitId' IS NOT NULL) AND (t0.ACCOUNTID = 'ACCOUNTID') AND (t0.APPID IS NOT NULL) GROUP BY TIME_BUCKET,t0.APPID ORDER BY TIME_BUCKET ASC");
    assertThat(queryMetaData.getResultType()).isEqualTo(ResultType.STACKED_TIME_SERIES);

    queryMetaData = dataFetcher.formQueryWithNonHStoreGroupBy(DeploymentStatsDataFetcherTestKeys.ACCOUNTID, null,
        asList(beforeTimeFilter, afterTimeFilter, appTagFilter, deploymentTagFilter), null,
        QLTimeSeriesAggregation.builder()
            .timeAggregationType(QLTimeAggregationType.HOUR)
            .timeAggregationValue(1)
            .build(),
        null, false);

    assertThat(queryMetaData.getQuery())
        .isEqualTo(
            "SELECT COUNT(*) AS COUNT,time_bucket('1 hours',endtime) AS TIME_BUCKET FROM DEPLOYMENT t0 WHERE (t0.PARENT_EXECUTION IS NULL) AND (t0.ENDTIME >= '2019-07-31T22:36:04Z') AND (t0.ENDTIME <= '2019-07-31T22:41:09Z') AND (t0.APPID IN ('DATA1','DATA2') ) AND (t0.tags ->'commitId' IS NOT NULL) AND (t0.ACCOUNTID = 'ACCOUNTID') GROUP BY TIME_BUCKET ORDER BY TIME_BUCKET ASC");
    assertThat(queryMetaData.getResultType()).isEqualTo(ResultType.TIME_SERIES);

    queryMetaData = dataFetcher.formQueryWithNonHStoreGroupBy(DeploymentStatsDataFetcherTestKeys.ACCOUNTID,
        QLDeploymentAggregationFunction.builder().duration(QLDurationAggregateOperation.AVERAGE).build(),
        asList(beforeTimeFilter, afterTimeFilter, deploymentTagFilter), null, null, null, false);

    assertThat(queryMetaData.getQuery())
        .isEqualTo(
            "SELECT AVG(t0.DURATION) AS DURATION FROM DEPLOYMENT t0 WHERE (t0.PARENT_EXECUTION IS NULL) AND (t0.ENDTIME >= '2019-07-31T22:36:04Z') AND (t0.ENDTIME <= '2019-07-31T22:41:09Z') AND (t0.tags ->'commitId' IS NOT NULL) AND (t0.ACCOUNTID = 'ACCOUNTID')");
    assertThat(queryMetaData.getResultType()).isEqualTo(ResultType.SINGLE_POINT);

    queryMetaData = dataFetcher.formQueryWithHStoreGroupBy(DeploymentStatsDataFetcherTestKeys.ACCOUNTID,
        QLDeploymentAggregationFunction.builder().duration(QLDurationAggregateOperation.MAX).build(),
        asList(beforeTimeFilter, afterTimeFilter, arrayStringFilter), asList(QLDeploymentEntityAggregation.Deployment),
        asList(QLDeploymentTagAggregation.builder()
                   .entityType(QLDeploymentTagType.DEPLOYMENT)
                   .tagName("commitId")
                   .build()),
        QLTimeSeriesAggregation.builder()
            .timeAggregationType(QLTimeAggregationType.DAY)
            .timeAggregationValue(1)
            .build(),
        null, false);

    assertThat(queryMetaData.getQuery())
        .isEqualTo(
            "SELECT MAX(DURATION) AS DURATION,TIME_BUCKET,concat('commitId:',values) as tag FROM (SELECT (each(TAGS)).key AS key ,(each(TAGS)).value::text AS values,DURATION,time_bucket('1 days',endtime) AS TIME_BUCKET FROM deployment t0 WHERE (EXISTS (SELECT * FROM skeys(t0.TAGS) as x(tagname) WHERE ((x.tagname='commitId') AND (t0.ENDTIME >= '2019-07-31T22:36:04Z') AND (t0.ENDTIME <= '2019-07-31T22:41:09Z') AND (t0.STATUS IN ('SUCCESS','FAILURE') ) AND (t0.ACCOUNTID = 'ACCOUNTID'))))) as tab1 WHERE (t0.PARENT_EXECUTION IS NULL) AND (tab1.key='commitId') GROUP BY tab1.values,TIME_BUCKET ORDER BY TIME_BUCKET ASC");
    assertThat(queryMetaData.getResultType()).isEqualTo(ResultType.STACKED_TIME_SERIES);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testSingleDataPointWithDeploymentTagFilter() {
    try {
      DeploymentStatsQueryMetaData metaData =
          DeploymentStatsQueryMetaData.builder().fieldNames(asList(DeploymentMetaDataFields.COUNT)).build();

      when(resultSet.next()).then((Answer<Boolean>) invocation -> {
        switch (count[0]) {
          case 0:
            count[0]++;
            return true;
          default:
            return false;
        }
      });

      when(resultSet.getInt(anyString())).thenReturn(10);
      when(resultSet.getLong(anyString())).thenReturn(20L);

      resetValues();

      QLData qlData = dataFetcher.getData(DeploymentStatsDataFetcherTestKeys.ACCOUNTID,
          QLDeploymentAggregationFunction.builder().count(QLCountAggregateOperation.SUM).build(),
          asList(QLDeploymentFilter.builder()
                     .tag(QLDeploymentTagFilter.builder()
                              .entityType(QLDeploymentTagType.DEPLOYMENT)
                              .tags(asList(QLTagInput.builder().name("TAG1").value("VALUE").build()))
                              .build())
                     .build()),
          new ArrayList<>(), new ArrayList<>(), false);

      assertThat(qlData.getClass()).isEqualTo(QLSinglePointData.class);
      QLSinglePointData singlePointData = (QLSinglePointData) qlData;
      validateSingleDataPoint(singlePointData, 10);

      resetValues();
      try {
        metaData = DeploymentStatsQueryMetaData.builder().fieldNames(asList(DeploymentMetaDataFields.APPID)).build();
        qlData = dataFetcher.generateSinglePointData(metaData, resultSet);
        fail("Expected an exception");
      } catch (RuntimeException e) {
        assertThat(e).hasMessage(
            "Single Data Type data type not supported " + DeploymentMetaDataFields.APPID.getDataType());
      }

    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testAggregateDataWithDeploymentTagFilter() {
    try {
      QLData qlData = dataFetcher.fetch(DeploymentStatsDataFetcherTestKeys.ACCOUNTID,
          QLDeploymentAggregationFunction.builder().count(QLCountAggregateOperation.SUM).build(),
          asList(QLDeploymentFilter.builder()
                     .tag(QLDeploymentTagFilter.builder()
                              .entityType(QLDeploymentTagType.DEPLOYMENT)
                              .tags(asList(QLTagInput.builder().name("commitId").value("12345").build()))
                              .build())
                     .build()),
          asList(QLDeploymentAggregation.builder()
                     .tagAggregation(QLDeploymentTagAggregation.builder()
                                         .entityType(QLDeploymentTagType.DEPLOYMENT)
                                         .tagName("commitId")
                                         .build())
                     .build()),
          new ArrayList<>(), null);

      assertThat(qlData).isInstanceOf(QLAggregatedData.class);
      QLAggregatedData aggregatedData = (QLAggregatedData) qlData;
      assertThat(aggregatedData.getDataPoints().size()).isEqualTo(5);
      validateAggregateDataPointForTags(aggregatedData, 0, 10, "DATA-TAG0", "DATA-TAG0");
      validateAggregateDataPointForTags(aggregatedData, 1, 11, "DATA-TAG1", "DATA-TAG1");
      validateAggregateDataPointForTags(aggregatedData, 2, 12, "DATA-TAG2", "DATA-TAG2");
      validateAggregateDataPointForTags(aggregatedData, 3, 13, "DATA-TAG3", "DATA-TAG3");
      validateAggregateDataPointForTags(aggregatedData, 4, 14, "DATA-TAG4", "DATA-TAG4");

    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  private void validateAggregateDataPointForTags(QLAggregatedData aggregatedData, int i, int i2, String s, String s2) {
    assertThat(aggregatedData.getDataPoints().get(i).getValue()).isEqualTo(i2);
    assertThat(aggregatedData.getDataPoints().get(i).getKey().getType())
        .isEqualTo(DeploymentMetaDataFields.TAGS.name());
    assertThat(aggregatedData.getDataPoints().get(i).getKey().getId()).isEqualTo(s);
    assertThat(aggregatedData.getDataPoints().get(i).getKey().getName()).isEqualTo(s2);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testStackedDataWithDeploymentTagFilter() {
    try {
      QLStackedData qlData = (QLStackedData) dataFetcher.fetch(DeploymentStatsDataFetcherTestKeys.ACCOUNTID,
          QLDeploymentAggregationFunction.builder().count(QLCountAggregateOperation.SUM).build(), new ArrayList<>(),
          asList(QLDeploymentAggregation.builder().entityAggregation(QLDeploymentEntityAggregation.Application).build(),
              QLDeploymentAggregation.builder()
                  .tagAggregation(QLDeploymentTagAggregation.builder()
                                      .entityType(QLDeploymentTagType.DEPLOYMENT)
                                      .tagName("commitId")
                                      .build())
                  .build()),
          new ArrayList<>(), null);

      assertThat(qlData.getDataPoints().size()).isEqualTo(5);

      validateStackedDataPointKey(qlData.getDataPoints().get(0), "DATA0", "DATA0NAME");
      assertThat(qlData.getDataPoints().get(0).getValues().size()).isEqualTo(1);
      validateQLDataPoint(qlData.getDataPoints().get(0).getValues().get(0), "DATA-TAG0", "DATA-TAG0", 10);

      validateStackedDataPointKey(qlData.getDataPoints().get(1), "DATA1", "DATA1NAME");
      assertThat(qlData.getDataPoints().get(1).getValues().size()).isEqualTo(1);
      validateQLDataPoint(qlData.getDataPoints().get(1).getValues().get(0), "DATA-TAG1", "DATA-TAG1", 11);

      validateStackedDataPointKey(qlData.getDataPoints().get(2), "DATA2", "DATA2NAME");
      assertThat(qlData.getDataPoints().get(2).getValues().size()).isEqualTo(1);
      validateQLDataPoint(qlData.getDataPoints().get(2).getValues().get(0), "DATA-TAG2", "DATA-TAG2", 12);

      validateStackedDataPointKey(qlData.getDataPoints().get(3), "DATA3", "DATA3NAME");
      assertThat(qlData.getDataPoints().get(3).getValues().size()).isEqualTo(1);
      validateQLDataPoint(qlData.getDataPoints().get(3).getValues().get(0), "DATA-TAG3", "DATA-TAG3", 13);

      validateStackedDataPointKey(qlData.getDataPoints().get(4), "DATA4", "DATA4NAME");
      assertThat(qlData.getDataPoints().get(4).getValues().size()).isEqualTo(1);
      validateQLDataPoint(qlData.getDataPoints().get(4).getValues().get(0), "DATA-TAG4", "DATA-TAG4", 14);

      resetValues();

      qlData = (QLStackedData) dataFetcher.fetch(DeploymentStatsDataFetcherTestKeys.ACCOUNTID,
          QLDeploymentAggregationFunction.builder().count(QLCountAggregateOperation.SUM).build(), new ArrayList<>(),
          asList(QLDeploymentAggregation.builder()
                     .tagAggregation(QLDeploymentTagAggregation.builder()
                                         .entityType(QLDeploymentTagType.DEPLOYMENT)
                                         .tagName("commitId")
                                         .build())
                     .build(),
              QLDeploymentAggregation.builder().entityAggregation(QLDeploymentEntityAggregation.Application).build()),
          asList(QLDeploymentSortCriteria.builder()
                     .sortOrder(QLSortOrder.DESCENDING)
                     .sortType(QLDeploymentSortType.Count)
                     .build()),
          null);

      validateStackedDataPointKey(qlData.getDataPoints().get(4), "DATA-TAG0", "DATA-TAG0NAME");
      assertThat(qlData.getDataPoints().get(4).getValues().size()).isEqualTo(1);
      validateQLDataPoint(qlData.getDataPoints().get(4).getValues().get(0), "DATA0", "DATA0NAME", 10);

      validateStackedDataPointKey(qlData.getDataPoints().get(3), "DATA-TAG1", "DATA-TAG1NAME");
      assertThat(qlData.getDataPoints().get(3).getValues().size()).isEqualTo(1);
      validateQLDataPoint(qlData.getDataPoints().get(3).getValues().get(0), "DATA1", "DATA1NAME", 11);

      validateStackedDataPointKey(qlData.getDataPoints().get(2), "DATA-TAG2", "DATA-TAG2NAME");
      assertThat(qlData.getDataPoints().get(2).getValues().size()).isEqualTo(1);
      validateQLDataPoint(qlData.getDataPoints().get(2).getValues().get(0), "DATA2", "DATA2NAME", 12);

      validateStackedDataPointKey(qlData.getDataPoints().get(1), "DATA-TAG3", "DATA-TAG3NAME");
      assertThat(qlData.getDataPoints().get(1).getValues().size()).isEqualTo(1);
      validateQLDataPoint(qlData.getDataPoints().get(1).getValues().get(0), "DATA3", "DATA3NAME", 13);

      validateStackedDataPointKey(qlData.getDataPoints().get(0), "DATA-TAG4", "DATA-TAG4NAME");
      assertThat(qlData.getDataPoints().get(0).getValues().size()).isEqualTo(1);
      validateQLDataPoint(qlData.getDataPoints().get(0).getValues().get(0), "DATA4", "DATA4NAME", 14);

    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testQueriesWithoutTimeFilters() {
    QLDeploymentFilter arrayIdFilter =
        QLDeploymentFilter.builder()
            .service(QLIdFilter.builder()
                         .operator(QLIdOperator.IN)
                         .values(new String[] {
                             DeploymentStatsDataFetcherTestKeys.SERVICE1, DeploymentStatsDataFetcherTestKeys.SERVICE2})
                         .build())
            .build();

    QLDeploymentFilter arrayStringFilter = QLDeploymentFilter.builder()
                                               .status(QLStringFilter.builder()
                                                           .operator(QLStringOperator.IN)
                                                           .values(new String[] {"SUCCESS", "FAILURE"})
                                                           .build())
                                               .build();

    QLDeploymentFilter arrayEnvTypeFilter =
        QLDeploymentFilter.builder()
            .environmentType(QLEnvironmentTypeFilter.builder()
                                 .operator(QLEnumOperator.IN)
                                 .values(new QLEnvironmentType[] {QLEnvironmentType.PROD})
                                 .build())
            .build();

    DeploymentStatsQueryMetaData queryMetaData =
        dataFetcher.formQueryWithNonHStoreGroupBy(DeploymentStatsDataFetcherTestKeys.ACCOUNTID, null,
            asList(arrayIdFilter, arrayStringFilter, arrayEnvTypeFilter),
            asList(QLDeploymentEntityAggregation.Application), null, null, false);

    assertThat(queryMetaData.getQuery()).contains("t0.ENDTIME >=");
    assertThat(queryMetaData.getQuery()).contains("t0.ENDTIME <=");
    assertThat(queryMetaData.getQuery()).doesNotContain("t0.STARTTIME >=");
    assertThat(queryMetaData.getQuery()).doesNotContain("t0.STARTTIME <=");
    assertThat(queryMetaData.getResultType()).isEqualTo(ResultType.AGGREGATE_DATA);
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testQueriesWithOneStartTimeFilters() {
    QLDeploymentFilter arrayIdFilter =
        QLDeploymentFilter.builder()
            .service(QLIdFilter.builder()
                         .operator(QLIdOperator.IN)
                         .values(new String[] {
                             DeploymentStatsDataFetcherTestKeys.SERVICE1, DeploymentStatsDataFetcherTestKeys.SERVICE2})
                         .build())
            .build();

    QLDeploymentFilter beforeStartTimeFilter =
        QLDeploymentFilter.builder()
            .startTime(QLTimeFilter.builder().operator(QLTimeOperator.AFTER).value(1564612564000L).build())
            .build();

    QLDeploymentFilter arrayStringFilter = QLDeploymentFilter.builder()
                                               .status(QLStringFilter.builder()
                                                           .operator(QLStringOperator.IN)
                                                           .values(new String[] {"RUNNING", "PAUSED"})
                                                           .build())
                                               .build();

    QLDeploymentFilter arrayEnvTypeFilter =
        QLDeploymentFilter.builder()
            .environmentType(QLEnvironmentTypeFilter.builder()
                                 .operator(QLEnumOperator.IN)
                                 .values(new QLEnvironmentType[] {QLEnvironmentType.PROD})
                                 .build())
            .build();

    DeploymentStatsQueryMetaData queryMetaData =
        dataFetcher.formQueryWithNonHStoreGroupBy(DeploymentStatsDataFetcherTestKeys.ACCOUNTID, null,
            asList(arrayIdFilter, beforeStartTimeFilter, arrayStringFilter, arrayEnvTypeFilter),
            asList(QLDeploymentEntityAggregation.Application), null, null, false);

    assertThat(queryMetaData.getQuery())
        .isEqualTo(
            "SELECT COUNT(*) AS COUNT,t0.APPID FROM DEPLOYMENT t0 WHERE (t0.PARENT_EXECUTION IS NULL) AND (t0.STARTTIME <= '2019-08-07T22:36:04Z') AND ((t0.SERVICES @>'{SERVICE1}') OR (t0.SERVICES @>'{SERVICE2}')) AND (t0.STARTTIME >= '2019-07-31T22:36:04Z') AND (t0.STATUS IN ('RUNNING','PAUSED') ) AND (t0.ENVTYPES @>'{PROD}') AND (t0.ACCOUNTID = 'ACCOUNTID') AND (t0.APPID IS NOT NULL) GROUP BY t0.APPID");
    assertThat(queryMetaData.getQuery()).doesNotContain("t0.ENDTIME >=");
    assertThat(queryMetaData.getQuery()).doesNotContain("t0.ENDTIME <=");
    assertThat(queryMetaData.getResultType()).isEqualTo(ResultType.AGGREGATE_DATA);
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testQueriesWithOneEndTimeFilters() {
    QLDeploymentFilter arrayIdFilter =
        QLDeploymentFilter.builder()
            .service(QLIdFilter.builder()
                         .operator(QLIdOperator.IN)
                         .values(new String[] {
                             DeploymentStatsDataFetcherTestKeys.SERVICE1, DeploymentStatsDataFetcherTestKeys.SERVICE2})
                         .build())
            .build();

    QLDeploymentFilter afterEndTimeFilter =
        QLDeploymentFilter.builder()
            .endTime(QLTimeFilter.builder().operator(QLTimeOperator.BEFORE).value(1564612869000L).build())
            .build();

    QLDeploymentFilter arrayStringFilter =
        QLDeploymentFilter.builder()
            .status(QLStringFilter.builder()
                        .operator(QLStringOperator.IN)
                        .values(new String[] {"ABORTED", "ERROR", "EXPIRED", "REJECTED"})
                        .build())
            .build();

    QLDeploymentFilter arrayEnvTypeFilter =
        QLDeploymentFilter.builder()
            .environmentType(QLEnvironmentTypeFilter.builder()
                                 .operator(QLEnumOperator.IN)
                                 .values(new QLEnvironmentType[] {QLEnvironmentType.PROD})
                                 .build())
            .build();

    DeploymentStatsQueryMetaData queryMetaData =
        dataFetcher.formQueryWithNonHStoreGroupBy(DeploymentStatsDataFetcherTestKeys.ACCOUNTID, null,
            asList(arrayIdFilter, afterEndTimeFilter, arrayStringFilter, arrayEnvTypeFilter),
            asList(QLDeploymentEntityAggregation.Application), null, null, false);

    assertThat(queryMetaData.getQuery())
        .isEqualTo(
            "SELECT COUNT(*) AS COUNT,t0.APPID FROM DEPLOYMENT t0 WHERE (t0.PARENT_EXECUTION IS NULL) AND (t0.ENDTIME >= '2019-07-24T22:41:09Z') AND ((t0.SERVICES @>'{SERVICE1}') OR (t0.SERVICES @>'{SERVICE2}')) AND (t0.ENDTIME <= '2019-07-31T22:41:09Z') AND (t0.STATUS IN ('ABORTED','ERROR','EXPIRED','REJECTED') ) AND (t0.ENVTYPES @>'{PROD}') AND (t0.ACCOUNTID = 'ACCOUNTID') AND (t0.APPID IS NOT NULL) GROUP BY t0.APPID");
    assertThat(queryMetaData.getQuery()).doesNotContain("t0.STARTTIME >=");
    assertThat(queryMetaData.getQuery()).doesNotContain("t0.STARTTIME <=");
    assertThat(queryMetaData.getResultType()).isEqualTo(ResultType.AGGREGATE_DATA);
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testQueriesWithOneStartAndEndTimeFilters() {
    QLDeploymentFilter arrayIdFilter =
        QLDeploymentFilter.builder()
            .service(QLIdFilter.builder()
                         .operator(QLIdOperator.IN)
                         .values(new String[] {
                             DeploymentStatsDataFetcherTestKeys.SERVICE1, DeploymentStatsDataFetcherTestKeys.SERVICE2})
                         .build())
            .build();

    QLDeploymentFilter afterStartTimeFilter =
        QLDeploymentFilter.builder()
            .startTime(QLTimeFilter.builder().operator(QLTimeOperator.AFTER).value(1564612564000L).build())
            .build();

    QLDeploymentFilter beforeEndTimeFilter =
        QLDeploymentFilter.builder()
            .endTime(QLTimeFilter.builder().operator(QLTimeOperator.BEFORE).value(1564612869000L).build())
            .build();

    QLDeploymentFilter arrayStringFilter = QLDeploymentFilter.builder()
                                               .status(QLStringFilter.builder()
                                                           .operator(QLStringOperator.IN)
                                                           .values(new String[] {"SUCCESS", "FAILURE"})
                                                           .build())
                                               .build();

    QLDeploymentFilter arrayEnvTypeFilter =
        QLDeploymentFilter.builder()
            .environmentType(QLEnvironmentTypeFilter.builder()
                                 .operator(QLEnumOperator.IN)
                                 .values(new QLEnvironmentType[] {QLEnvironmentType.PROD})
                                 .build())
            .build();

    DeploymentStatsQueryMetaData queryMetaData =
        dataFetcher.formQueryWithNonHStoreGroupBy(DeploymentStatsDataFetcherTestKeys.ACCOUNTID, null,
            asList(arrayIdFilter, afterStartTimeFilter, beforeEndTimeFilter, arrayStringFilter, arrayEnvTypeFilter),
            asList(QLDeploymentEntityAggregation.Application), null, null, false);

    assertThat(queryMetaData.getQuery())
        .isEqualTo(
            "SELECT COUNT(*) AS COUNT,t0.APPID FROM DEPLOYMENT t0 WHERE (t0.PARENT_EXECUTION IS NULL) AND ((t0.SERVICES @>'{SERVICE1}') OR (t0.SERVICES @>'{SERVICE2}')) AND (t0.STARTTIME >= '2019-07-31T22:36:04Z') AND (t0.ENDTIME <= '2019-07-31T22:41:09Z') AND (t0.STATUS IN ('SUCCESS','FAILURE') ) AND (t0.ENVTYPES @>'{PROD}') AND (t0.ACCOUNTID = 'ACCOUNTID') AND (t0.APPID IS NOT NULL) GROUP BY t0.APPID");
    assertThat(queryMetaData.getQuery()).contains("t0.STARTTIME >=");
    assertThat(queryMetaData.getQuery()).contains("t0.ENDTIME <=");
    assertThat(queryMetaData.getQuery()).doesNotContain("t0.STARTTIME <=");
    assertThat(queryMetaData.getQuery()).doesNotContain("t0.ENDTIME >=");
    assertThat(queryMetaData.getResultType()).isEqualTo(ResultType.AGGREGATE_DATA);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testIfTimeScaleDbIsNotValid() {
    when(timeScaleDBService.isValid()).thenReturn(false);

    assertThatThrownBy(()
                           -> dataFetcher.fetch(DeploymentStatsDataFetcherTestKeys.ACCOUNTID,
                               QLDeploymentAggregationFunction.builder().count(QLCountAggregateOperation.SUM).build(),
                               new ArrayList<>(),
                               asList(QLDeploymentAggregation.builder()
                                          .entityAggregation(QLDeploymentEntityAggregation.Environment)
                                          .build()),
                               new ArrayList<>(), null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Error while fetching deployment data")
        .hasCauseInstanceOf(InvalidRequestException.class)
        .hasRootCauseMessage("Cannot process request");
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testQueriesWithDeploymentTagFilter() {
    QLDeploymentFilter arrayIdFilter =
        QLDeploymentFilter.builder()
            .service(QLIdFilter.builder()
                         .operator(QLIdOperator.IN)
                         .values(new String[] {
                             DeploymentStatsDataFetcherTestKeys.SERVICE1, DeploymentStatsDataFetcherTestKeys.SERVICE2})
                         .build())
            .build();
    QLDeploymentFilter beforeTimeFilter =
        QLDeploymentFilter.builder()
            .endTime(QLTimeFilter.builder().operator(QLTimeOperator.AFTER).value(1564612564000L).build())
            .build();

    QLDeploymentFilter afterTimeFilter =
        QLDeploymentFilter.builder()
            .endTime(QLTimeFilter.builder().operator(QLTimeOperator.BEFORE).value(1564612869000L).build())
            .build();
    QLDeploymentFilter arrayStringFilter = QLDeploymentFilter.builder()
                                               .status(QLStringFilter.builder()
                                                           .operator(QLStringOperator.IN)
                                                           .values(new String[] {"SUCCESS", "FAILURE"})
                                                           .build())
                                               .build();

    QLDeploymentFilter arrayEnvTypeFilter =
        QLDeploymentFilter.builder()
            .environmentType(QLEnvironmentTypeFilter.builder()
                                 .operator(QLEnumOperator.IN)
                                 .values(new QLEnvironmentType[] {QLEnvironmentType.PROD})
                                 .build())
            .build();

    QLDeploymentFilter deploymentTagFilter =
        QLDeploymentFilter.builder()
            .tag(QLDeploymentTagFilter.builder()
                     .entityType(QLDeploymentTagType.ENVIRONMENT)
                     .tags(asList(QLTagInput.builder().name("TAG1").value("DATA1").build()))
                     .build())
            .build();

    DeploymentStatsQueryMetaData queryMetaData =
        dataFetcher.formQueryWithNonHStoreGroupBy(DeploymentStatsDataFetcherTestKeys.ACCOUNTID, null,
            asList(arrayIdFilter, beforeTimeFilter, afterTimeFilter, arrayStringFilter, arrayEnvTypeFilter,
                deploymentTagFilter),
            null,
            QLTimeSeriesAggregation.builder()
                .timeAggregationType(QLTimeAggregationType.HOUR)
                .timeAggregationValue(1)
                .build(),
            null, false);

    assertThat(queryMetaData.getQuery())
        .isEqualTo(
            "SELECT COUNT(*) AS COUNT,time_bucket('1 hours',endtime) AS TIME_BUCKET FROM DEPLOYMENT t0 WHERE (t0.PARENT_EXECUTION IS NULL) AND ((t0.SERVICES @>'{SERVICE1}') OR (t0.SERVICES @>'{SERVICE2}')) AND (t0.ENDTIME >= '2019-07-31T22:36:04Z') AND (t0.ENDTIME <= '2019-07-31T22:41:09Z') AND (t0.STATUS IN ('SUCCESS','FAILURE') ) AND (t0.ENVTYPES @>'{PROD}') AND ((t0.ENVIRONMENTS @>'{DATA1}') OR (t0.ENVIRONMENTS @>'{DATA2}')) AND (t0.ACCOUNTID = 'ACCOUNTID') GROUP BY TIME_BUCKET ORDER BY TIME_BUCKET ASC");
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testGenerateAggregateData() {
    try {
      DeploymentStatsQueryMetaData metaData =
          DeploymentStatsQueryMetaData.builder()
              .fieldNames(asList(DeploymentMetaDataFields.COUNT, DeploymentMetaDataFields.ENVID))
              .groupByFields(asList(DeploymentMetaDataFields.ENVID))
              .resultType(ResultType.AGGREGATE_DATA)
              .filters(new ArrayList<>())
              .build();

      QLAggregatedData aggregatedData = dataFetcher.generateAggregateData(metaData, resultSet);

      validateAggregateDataPoint(aggregatedData, 0, 10, "DATA0", "DATA0NAME");
      validateAggregateDataPoint(aggregatedData, 1, 11, "DATA1", "DATA1NAME");
      validateAggregateDataPoint(aggregatedData, 2, 12, "DATA2", "DATA2NAME");
      validateAggregateDataPoint(aggregatedData, 3, 13, "DATA3", "DATA3NAME");
      validateAggregateDataPoint(aggregatedData, 4, 14, "DATA4", "DATA4NAME");

      resetValues();

      metaData = DeploymentStatsQueryMetaData.builder()
                     .fieldNames(asList(
                         DeploymentMetaDataFields.COUNT, DeploymentMetaDataFields.ENVID, DeploymentMetaDataFields.TAGS))
                     .groupByFields(asList(DeploymentMetaDataFields.ENVID))
                     .resultType(ResultType.AGGREGATE_DATA)
                     .filters(new ArrayList<>())
                     .build();

      aggregatedData = dataFetcher.generateAggregateData(metaData, resultSet);

      validateAggregateDataPointForTags(aggregatedData, 0, 10, "DATA-TAG0", "DATA-TAG0");
      validateAggregateDataPointForTags(aggregatedData, 1, 11, "DATA-TAG1", "DATA-TAG1");
      validateAggregateDataPointForTags(aggregatedData, 2, 12, "DATA-TAG2", "DATA-TAG2");
      validateAggregateDataPointForTags(aggregatedData, 3, 13, "DATA-TAG3", "DATA-TAG3");
      validateAggregateDataPointForTags(aggregatedData, 4, 14, "DATA-TAG4", "DATA-TAG4");

      resetValues();

    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  // Fix
  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testGenerateStackedTimeSeriesData() {
    try {
      DeploymentStatsQueryMetaData metaData =
          DeploymentStatsQueryMetaData.builder()
              .fieldNames(asList(
                  DeploymentMetaDataFields.COUNT, DeploymentMetaDataFields.TIME_SERIES, DeploymentMetaDataFields.ENVID))
              .groupByFields(asList(DeploymentMetaDataFields.ENVID))
              .resultType(ResultType.TIME_SERIES)
              .filters(new ArrayList<>())
              .build();

      QLStackedTimeSeriesData stackedTimeSeriesData = dataFetcher.generateStackedTimeSeriesData(metaData, resultSet);

      validateStackedTimeSeriesDataPoint(stackedTimeSeriesData.getData().get(0), currentTime + 3600000 * 1);
      validateStackedTimeSeriesDataPoint(stackedTimeSeriesData.getData().get(1), currentTime + 3600000 * 2);
      validateStackedTimeSeriesDataPoint(stackedTimeSeriesData.getData().get(2), currentTime + 3600000 * 3);
      validateStackedTimeSeriesDataPoint(stackedTimeSeriesData.getData().get(3), currentTime + 3600000 * 4);
      validateStackedTimeSeriesDataPoint(stackedTimeSeriesData.getData().get(4), currentTime + 3600000 * 5);
      validateQLDataPoint(stackedTimeSeriesData.getData().get(0).getValues().get(0), "DATA0", "DATA0NAME", 10);
      validateQLDataPoint(stackedTimeSeriesData.getData().get(1).getValues().get(0), "DATA1", "DATA1NAME", 11);
      validateQLDataPoint(stackedTimeSeriesData.getData().get(2).getValues().get(0), "DATA2", "DATA2NAME", 12);
      validateQLDataPoint(stackedTimeSeriesData.getData().get(3).getValues().get(0), "DATA3", "DATA3NAME", 13);
      validateQLDataPoint(stackedTimeSeriesData.getData().get(4).getValues().get(0), "DATA4", "DATA4NAME", 14);

      resetValues();

    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testGenerateStackedBarChartData() {
    try {
      DeploymentStatsQueryMetaData metaData =
          DeploymentStatsQueryMetaData.builder()
              .fieldNames(asList(
                  DeploymentMetaDataFields.COUNT, DeploymentMetaDataFields.ENVID, DeploymentMetaDataFields.APPID))
              .groupByFields(asList(DeploymentMetaDataFields.ENVID, DeploymentMetaDataFields.APPID))
              .resultType(ResultType.STACKED_TIME_SERIES)
              .filters(new ArrayList<>())
              .sortCriteria(asList(QLDeploymentSortCriteria.builder()
                                       .sortOrder(QLSortOrder.ASCENDING)
                                       .sortType(QLDeploymentSortType.Count)
                                       .build()))
              .build();

      QLStackedData stackedData = dataFetcher.generateStackedBarChartData(metaData, resultSet);

      validateStackedDataPointKey(stackedData.getDataPoints().get(0), "DATA0", "DATA0NAME");
      assertThat(stackedData.getDataPoints().get(0).getValues().size()).isEqualTo(1);
      validateQLDataPoint(stackedData.getDataPoints().get(0).getValues().get(0), "DATA1", "DATA1NAME", 10);

      validateStackedDataPointKey(stackedData.getDataPoints().get(1), "DATA2", "DATA2NAME");
      assertThat(stackedData.getDataPoints().get(1).getValues().size()).isEqualTo(1);
      validateQLDataPoint(stackedData.getDataPoints().get(1).getValues().get(0), "DATA3", "DATA3NAME", 11);

      validateStackedDataPointKey(stackedData.getDataPoints().get(2), "DATA4", "DATA4NAME");
      assertThat(stackedData.getDataPoints().get(2).getValues().size()).isEqualTo(1);
      validateQLDataPoint(stackedData.getDataPoints().get(2).getValues().get(0), "DATA5", "DATA5NAME", 12);

      validateStackedDataPointKey(stackedData.getDataPoints().get(3), "DATA6", "DATA6NAME");
      assertThat(stackedData.getDataPoints().get(3).getValues().size()).isEqualTo(1);
      validateQLDataPoint(stackedData.getDataPoints().get(3).getValues().get(0), "DATA7", "DATA7NAME", 13);

      validateStackedDataPointKey(stackedData.getDataPoints().get(4), "DATA8", "DATA8NAME");
      assertThat(stackedData.getDataPoints().get(4).getValues().size()).isEqualTo(1);
      validateQLDataPoint(stackedData.getDataPoints().get(4).getValues().get(0), "DATA9", "DATA9NAME", 14);

      resetValues();

      metaData = DeploymentStatsQueryMetaData.builder()
                     .fieldNames(asList(DeploymentMetaDataFields.COUNT, DeploymentMetaDataFields.ENVID,
                         DeploymentMetaDataFields.APPID))
                     .groupByFields(asList(DeploymentMetaDataFields.ENVID, DeploymentMetaDataFields.APPID))
                     .resultType(ResultType.STACKED_TIME_SERIES)
                     .filters(new ArrayList<>())
                     .sortCriteria(asList(QLDeploymentSortCriteria.builder()
                                              .sortOrder(QLSortOrder.DESCENDING)
                                              .sortType(QLDeploymentSortType.Count)
                                              .build()))
                     .build();

      stackedData = dataFetcher.generateStackedBarChartData(metaData, resultSet);

      validateStackedDataPointKey(stackedData.getDataPoints().get(4), "DATA0", "DATA0NAME");
      assertThat(stackedData.getDataPoints().get(4).getValues().size()).isEqualTo(1);
      validateQLDataPoint(stackedData.getDataPoints().get(4).getValues().get(0), "DATA1", "DATA1NAME", 10);

      validateStackedDataPointKey(stackedData.getDataPoints().get(3), "DATA2", "DATA2NAME");
      assertThat(stackedData.getDataPoints().get(3).getValues().size()).isEqualTo(1);
      validateQLDataPoint(stackedData.getDataPoints().get(3).getValues().get(0), "DATA3", "DATA3NAME", 11);

      validateStackedDataPointKey(stackedData.getDataPoints().get(2), "DATA4", "DATA4NAME");
      assertThat(stackedData.getDataPoints().get(2).getValues().size()).isEqualTo(1);
      validateQLDataPoint(stackedData.getDataPoints().get(2).getValues().get(0), "DATA5", "DATA5NAME", 12);

      validateStackedDataPointKey(stackedData.getDataPoints().get(1), "DATA6", "DATA6NAME");
      assertThat(stackedData.getDataPoints().get(1).getValues().size()).isEqualTo(1);
      validateQLDataPoint(stackedData.getDataPoints().get(1).getValues().get(0), "DATA7", "DATA7NAME", 13);

      validateStackedDataPointKey(stackedData.getDataPoints().get(0), "DATA8", "DATA8NAME");
      assertThat(stackedData.getDataPoints().get(0).getValues().size()).isEqualTo(1);
      validateQLDataPoint(stackedData.getDataPoints().get(0).getValues().get(0), "DATA9", "DATA9NAME", 14);

      resetValues();

      metaData = DeploymentStatsQueryMetaData.builder()
                     .fieldNames(asList(
                         DeploymentMetaDataFields.COUNT, DeploymentMetaDataFields.APPID, DeploymentMetaDataFields.TAGS))
                     .groupByFields(asList(DeploymentMetaDataFields.ENVID, DeploymentMetaDataFields.TAGS))
                     .resultType(ResultType.STACKED_TIME_SERIES)
                     .filters(new ArrayList<>())
                     .sortCriteria(asList(QLDeploymentSortCriteria.builder()
                                              .sortOrder(QLSortOrder.ASCENDING)
                                              .sortType(QLDeploymentSortType.Count)
                                              .build()))
                     .build();

      stackedData = dataFetcher.generateStackedBarChartData(metaData, resultSet);

      validateStackedDataPointKey(stackedData.getDataPoints().get(0), "DATA0", "DATA0NAME");
      assertThat(stackedData.getDataPoints().get(0).getValues().size()).isEqualTo(1);
      validateQLDataPoint(stackedData.getDataPoints().get(0).getValues().get(0), "DATA-TAG0", "DATA-TAG0", 10);

      validateStackedDataPointKey(stackedData.getDataPoints().get(1), "DATA1", "DATA1NAME");
      assertThat(stackedData.getDataPoints().get(1).getValues().size()).isEqualTo(1);
      validateQLDataPoint(stackedData.getDataPoints().get(1).getValues().get(0), "DATA-TAG1", "DATA-TAG1", 11);

      validateStackedDataPointKey(stackedData.getDataPoints().get(2), "DATA2", "DATA2NAME");
      assertThat(stackedData.getDataPoints().get(2).getValues().size()).isEqualTo(1);
      validateQLDataPoint(stackedData.getDataPoints().get(2).getValues().get(0), "DATA-TAG2", "DATA-TAG2", 12);

      validateStackedDataPointKey(stackedData.getDataPoints().get(3), "DATA3", "DATA3NAME");
      assertThat(stackedData.getDataPoints().get(3).getValues().size()).isEqualTo(1);
      validateQLDataPoint(stackedData.getDataPoints().get(3).getValues().get(0), "DATA-TAG3", "DATA-TAG3", 13);

      validateStackedDataPointKey(stackedData.getDataPoints().get(4), "DATA4", "DATA4NAME");
      assertThat(stackedData.getDataPoints().get(4).getValues().size()).isEqualTo(1);
      validateQLDataPoint(stackedData.getDataPoints().get(4).getValues().get(0), "DATA-TAG4", "DATA-TAG4", 14);

      resetValues();

      metaData = DeploymentStatsQueryMetaData.builder()
                     .fieldNames(asList(
                         DeploymentMetaDataFields.COUNT, DeploymentMetaDataFields.APPID, DeploymentMetaDataFields.TAGS))
                     .groupByFields(asList(DeploymentMetaDataFields.TAGS, DeploymentMetaDataFields.APPID))
                     .resultType(ResultType.STACKED_TIME_SERIES)
                     .filters(new ArrayList<>())
                     .sortCriteria(asList(QLDeploymentSortCriteria.builder()
                                              .sortOrder(QLSortOrder.DESCENDING)
                                              .sortType(QLDeploymentSortType.Count)
                                              .build()))
                     .build();

      stackedData = dataFetcher.generateStackedBarChartData(metaData, resultSet);

      validateStackedDataPointKey(stackedData.getDataPoints().get(4), "DATA-TAG0", "DATA-TAG0NAME");
      assertThat(stackedData.getDataPoints().get(4).getValues().size()).isEqualTo(1);
      validateQLDataPoint(stackedData.getDataPoints().get(4).getValues().get(0), "DATA0", "DATA0NAME", 10);

      validateStackedDataPointKey(stackedData.getDataPoints().get(3), "DATA-TAG1", "DATA-TAG1NAME");
      assertThat(stackedData.getDataPoints().get(3).getValues().size()).isEqualTo(1);
      validateQLDataPoint(stackedData.getDataPoints().get(3).getValues().get(0), "DATA1", "DATA1NAME", 11);

      validateStackedDataPointKey(stackedData.getDataPoints().get(2), "DATA-TAG2", "DATA-TAG2NAME");
      assertThat(stackedData.getDataPoints().get(2).getValues().size()).isEqualTo(1);
      validateQLDataPoint(stackedData.getDataPoints().get(2).getValues().get(0), "DATA2", "DATA2NAME", 12);

      validateStackedDataPointKey(stackedData.getDataPoints().get(1), "DATA-TAG3", "DATA-TAG3NAME");
      assertThat(stackedData.getDataPoints().get(1).getValues().size()).isEqualTo(1);
      validateQLDataPoint(stackedData.getDataPoints().get(1).getValues().get(0), "DATA3", "DATA3NAME", 13);

      validateStackedDataPointKey(stackedData.getDataPoints().get(0), "DATA-TAG4", "DATA-TAG4NAME");
      assertThat(stackedData.getDataPoints().get(0).getValues().size()).isEqualTo(1);
      validateQLDataPoint(stackedData.getDataPoints().get(0).getValues().get(0), "DATA4", "DATA4NAME", 14);

      resetValues();

    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  @Test
  @Owner(developers = ALEXANDRU_CIOFU)
  @Category(UnitTests.class)
  public void testDeploymentStatsDataFetcherDeploymentFilters() {
    QLDeploymentFilter beforeTimeFilter =
        QLDeploymentFilter.builder()
            .endTime(QLTimeFilter.builder().operator(QLTimeOperator.AFTER).value(1564612564000L).build())
            .build();

    QLDeploymentFilter afterTimeFilter =
        QLDeploymentFilter.builder()
            .endTime(QLTimeFilter.builder().operator(QLTimeOperator.BEFORE).value(1564612869000L).build())
            .build();

    QLDeploymentType[] qlDeploymentTypes = new QLDeploymentType[] {QLDeploymentType.KUBERNETES, QLDeploymentType.SSH};

    QLDeploymentFilter deploymentTypesFilter =
        QLDeploymentFilter.builder()
            .deploymentType(
                QLDeploymentTypeFilter.builder().operator(QLEnumOperator.IN).values(qlDeploymentTypes).build())
            .build();

    software.wings.graphql.datafetcher.execution.DeploymentStatsDataFetcher dataFetcherSpy = Mockito.spy(dataFetcher);
    doReturn(Arrays.asList(new String[] {"sid1", "sid2"}))
        .when(dataFetcherSpy)
        .getServiceIds(DeploymentStatsDataFetcherTestKeys.ACCOUNTID,
            new ArrayList<>(Arrays.asList(QLDeploymentType.KUBERNETES.name(), QLDeploymentType.SSH.name())));

    DeploymentStatsQueryMetaData queryMetaData =
        dataFetcherSpy.formQueryWithNonHStoreGroupBy(DeploymentStatsDataFetcherTestKeys.ACCOUNTID, null,
            asList(beforeTimeFilter, afterTimeFilter, deploymentTypesFilter), null,
            QLTimeSeriesAggregation.builder()
                .timeAggregationType(QLTimeAggregationType.HOUR)
                .timeAggregationValue(1)
                .build(),
            null, false);

    assertThat(queryMetaData.getQuery())
        .isEqualTo(
            "SELECT COUNT(*) AS COUNT,time_bucket('1 hours',endtime) AS TIME_BUCKET FROM DEPLOYMENT t0 WHERE (t0.PARENT_EXECUTION IS NULL) AND ((t0.SERVICES @>'{sid1}') OR (t0.SERVICES @>'{sid2}')) AND (t0.ENDTIME >= '2019-07-31T22:36:04Z') AND (t0.ENDTIME <= '2019-07-31T22:41:09Z') AND (t0.ACCOUNTID = 'ACCOUNTID') GROUP BY TIME_BUCKET ORDER BY TIME_BUCKET ASC");
  }

  @Test
  @Owner(developers = ALEXANDRU_CIOFU)
  @Category(UnitTests.class)
  public void testDeploymentStatsDataFetcherWorkflowFilters() {
    QLDeploymentFilter beforeTimeFilter =
        QLDeploymentFilter.builder()
            .endTime(QLTimeFilter.builder().operator(QLTimeOperator.AFTER).value(1564612564000L).build())
            .build();

    QLDeploymentFilter afterTimeFilter =
        QLDeploymentFilter.builder()
            .endTime(QLTimeFilter.builder().operator(QLTimeOperator.BEFORE).value(1564612869000L).build())
            .build();

    QLOrchestrationWorkflowType[] qlWorkflowTypes =
        new QLOrchestrationWorkflowType[] {QLOrchestrationWorkflowType.CANARY};

    QLDeploymentFilter workflowTypesFilter =
        QLDeploymentFilter.builder()
            .orchestrationWorkflowType(
                QLOrchestrationWorkflowTypeFilter.builder().operator(QLEnumOperator.IN).values(qlWorkflowTypes).build())
            .build();

    software.wings.graphql.datafetcher.execution.DeploymentStatsDataFetcher dataFetcherSpy = Mockito.spy(dataFetcher);
    doReturn(Arrays.asList(new String[] {"wid1", "wid2"}))
        .when(dataFetcherSpy)
        .getWorkflowIds(DeploymentStatsDataFetcherTestKeys.ACCOUNTID,
            new ArrayList<>(Arrays.asList(QLOrchestrationWorkflowType.CANARY.name())));

    DeploymentStatsQueryMetaData queryMetaData =
        dataFetcherSpy.formQueryWithNonHStoreGroupBy(DeploymentStatsDataFetcherTestKeys.ACCOUNTID, null,
            asList(beforeTimeFilter, afterTimeFilter, workflowTypesFilter), null,
            QLTimeSeriesAggregation.builder()
                .timeAggregationType(QLTimeAggregationType.HOUR)
                .timeAggregationValue(1)
                .build(),
            null, false);

    assertThat(queryMetaData.getQuery())
        .isEqualTo(
            "SELECT COUNT(*) AS COUNT,time_bucket('1 hours',endtime) AS TIME_BUCKET FROM DEPLOYMENT t0 WHERE (t0.PARENT_EXECUTION IS NULL) AND ((t0.WORKFLOWS @>'{wid1}') OR (t0.WORKFLOWS @>'{wid2}')) AND (t0.ENDTIME >= '2019-07-31T22:36:04Z') AND (t0.ENDTIME <= '2019-07-31T22:41:09Z') AND (t0.ACCOUNTID = 'ACCOUNTID') GROUP BY TIME_BUCKET ORDER BY TIME_BUCKET ASC");
  }

  @Test
  @Owner(developers = ALEXANDRU_CIOFU)
  @Category(UnitTests.class)
  public void testDeploymentStatsDataFetcherDeploymentAndWorkflowFilters() {
    QLDeploymentFilter beforeTimeFilter =
        QLDeploymentFilter.builder()
            .endTime(QLTimeFilter.builder().operator(QLTimeOperator.AFTER).value(1564612564000L).build())
            .build();

    QLDeploymentFilter afterTimeFilter =
        QLDeploymentFilter.builder()
            .endTime(QLTimeFilter.builder().operator(QLTimeOperator.BEFORE).value(1564612869000L).build())
            .build();

    QLDeploymentType[] qlDeploymentTypes = new QLDeploymentType[] {QLDeploymentType.KUBERNETES, QLDeploymentType.SSH};

    QLDeploymentFilter deploymentTypesFilter =
        QLDeploymentFilter.builder()
            .deploymentType(
                QLDeploymentTypeFilter.builder().operator(QLEnumOperator.IN).values(qlDeploymentTypes).build())
            .build();

    DeploymentStatsDataFetcher dataFetcherSpy = Mockito.spy(dataFetcher);
    doReturn(Arrays.asList(new String[] {"sid1", "sid2"}))
        .when(dataFetcherSpy)
        .getServiceIds(DeploymentStatsDataFetcherTestKeys.ACCOUNTID,
            new ArrayList<>(Arrays.asList(QLDeploymentType.KUBERNETES.name(), QLDeploymentType.SSH.name())));

    QLOrchestrationWorkflowType[] qlOrchestrationWorkflowTypes =
        new QLOrchestrationWorkflowType[] {QLOrchestrationWorkflowType.CANARY};

    QLDeploymentFilter workflowTypesFilter = QLDeploymentFilter.builder()
                                                 .orchestrationWorkflowType(QLOrchestrationWorkflowTypeFilter.builder()
                                                                                .operator(QLEnumOperator.IN)
                                                                                .values(qlOrchestrationWorkflowTypes)
                                                                                .build())
                                                 .build();

    doReturn(Arrays.asList(new String[] {"wid1", "wid2"}))
        .when(dataFetcherSpy)
        .getWorkflowIds(DeploymentStatsDataFetcherTestKeys.ACCOUNTID,
            new ArrayList<>(Arrays.asList(QLOrchestrationWorkflowType.CANARY.name())));

    DeploymentStatsQueryMetaData queryMetaData =
        dataFetcherSpy.formQueryWithNonHStoreGroupBy(DeploymentStatsDataFetcherTestKeys.ACCOUNTID, null,
            asList(beforeTimeFilter, afterTimeFilter, deploymentTypesFilter, workflowTypesFilter), null,
            QLTimeSeriesAggregation.builder()
                .timeAggregationType(QLTimeAggregationType.HOUR)
                .timeAggregationValue(1)
                .build(),
            null, false);

    assertThat(queryMetaData.getQuery())
        .isEqualTo(
            "SELECT COUNT(*) AS COUNT,time_bucket('1 hours',endtime) AS TIME_BUCKET FROM DEPLOYMENT t0 WHERE (t0.PARENT_EXECUTION IS NULL) AND ((t0.WORKFLOWS @>'{wid1}') OR (t0.WORKFLOWS @>'{wid2}')) AND ((t0.SERVICES @>'{sid1}') OR (t0.SERVICES @>'{sid2}')) AND (t0.ENDTIME >= '2019-07-31T22:36:04Z') AND (t0.ENDTIME <= '2019-07-31T22:41:09Z') AND (t0.ACCOUNTID = 'ACCOUNTID') GROUP BY TIME_BUCKET ORDER BY TIME_BUCKET ASC");
  }

  @Test
  @Owner(developers = ALEXANDRU_CIOFU)
  @Category(UnitTests.class)
  public void testGetServiceIds() {
    List<String> deploymentTypes = Arrays.asList(new String[] {"KUBERNETES", "SSH"});

    Query<Service> serviceQuery = mock(Query.class);
    doReturn(serviceQuery).when(wingsPersistence).createQuery(Service.class);

    doReturn(serviceQuery).when(serviceQuery).filter(eq(ServiceKeys.accountId), anyString());

    FieldEnd<Service> fieldEnd = mock(FieldEnd.class);
    doReturn(fieldEnd).when(serviceQuery).field(ServiceKeys.deploymentType);

    doReturn(serviceQuery).when(fieldEnd).in(deploymentTypes);

    List<Service> services = new ArrayList<>();
    services.add(new Service());
    services.get(0).setUuid("sid1");
    doReturn(services).when(serviceQuery).asList();

    List<String> expectedServiceIds =
        dataFetcher.getServiceIds(DeploymentStatsDataFetcherTestKeys.ACCOUNTID, deploymentTypes);

    assertThat(expectedServiceIds).isEqualTo(Arrays.asList(new String[] {"sid1"}));
  }

  @Test
  @Owner(developers = ALEXANDRU_CIOFU)
  @Category(UnitTests.class)
  public void testGetWorkflowIds() {
    List<String> workflowTypes = Arrays.asList(new String[] {"BUILD"});

    Query<Workflow> workflowQuery = mock(Query.class);
    doReturn(workflowQuery).when(wingsPersistence).createQuery(Workflow.class);

    doReturn(workflowQuery).when(workflowQuery).filter(eq(WorkflowKeys.accountId), anyString());

    FieldEnd<Service> fieldEnd = mock(FieldEnd.class);
    doReturn(fieldEnd).when(workflowQuery).field(WorkflowKeys.orchestrationWorkflowType);

    doReturn(workflowQuery).when(fieldEnd).in(workflowTypes);

    List<Workflow> workflows = new ArrayList<>();
    workflows.add(new Workflow());
    workflows.get(0).setUuid("wid1");
    doReturn(workflows).when(workflowQuery).asList();

    List<String> expectedWorkflowIds =
        dataFetcher.getWorkflowIds(DeploymentStatsDataFetcherTestKeys.ACCOUNTID, workflowTypes);

    assertThat(expectedWorkflowIds).isEqualTo(Arrays.asList(new String[] {"wid1"}));
  }

  @Test
  @Owner(developers = ALEXANDRU_CIOFU)
  @Category(UnitTests.class)
  public void testFormQueryWithNonHStoreGroupByRollbackCount() {
    QLDeploymentFilter beforeTimeFilter =
        QLDeploymentFilter.builder()
            .endTime(QLTimeFilter.builder().operator(QLTimeOperator.AFTER).value(1564612564000L).build())
            .build();

    QLDeploymentFilter afterTimeFilter =
        QLDeploymentFilter.builder()
            .endTime(QLTimeFilter.builder().operator(QLTimeOperator.BEFORE).value(1564612869000L).build())
            .build();

    List<QLDeploymentFilter> filters = new ArrayList<>();
    filters.add(beforeTimeFilter);
    filters.add(afterTimeFilter);

    DeploymentStatsQueryMetaData queryMetaData = dataFetcher.formQueryWithNonHStoreGroupBy(
        DeploymentStatsDataFetcherTestKeys.ACCOUNTID,
        QLDeploymentAggregationFunction.builder().rollbackCount(QLCountAggregateOperation.SUM).build(), filters, null,
        QLTimeSeriesAggregation.builder()
            .timeAggregationType(QLTimeAggregationType.HOUR)
            .timeAggregationValue(1)
            .build(),
        null, false);

    assertThat(queryMetaData.getQuery())
        .isEqualTo(
            "SELECT COUNT(*) AS ROLLBACK_COUNT,time_bucket('1 hours',endtime) AS TIME_BUCKET FROM DEPLOYMENT t0 WHERE (t0.PARENT_EXECUTION IS NULL) AND (t0.ENDTIME >= '2019-07-31T22:36:04Z') AND (t0.ENDTIME <= '2019-07-31T22:41:09Z') AND (t0.ROLLBACK_DURATION > 0) AND (t0.ACCOUNTID = 'ACCOUNTID') GROUP BY TIME_BUCKET ORDER BY TIME_BUCKET ASC");
  }

  @Test
  @Owner(developers = ALEXANDRU_CIOFU)
  @Category(UnitTests.class)
  public void testFormQueryWithHStoreGroupByRollbackCount() {
    QLDeploymentFilter beforeTimeFilter =
        QLDeploymentFilter.builder()
            .endTime(QLTimeFilter.builder().operator(QLTimeOperator.AFTER).value(1564612564000L).build())
            .build();

    QLDeploymentFilter afterTimeFilter =
        QLDeploymentFilter.builder()
            .endTime(QLTimeFilter.builder().operator(QLTimeOperator.BEFORE).value(1564612869000L).build())
            .build();

    List<QLDeploymentFilter> filters = new ArrayList<>();
    filters.add(beforeTimeFilter);
    filters.add(afterTimeFilter);

    DeploymentStatsQueryMetaData queryMetaData =
        dataFetcher.formQueryWithHStoreGroupBy(DeploymentStatsDataFetcherTestKeys.ACCOUNTID,
            QLDeploymentAggregationFunction.builder().rollbackCount(QLCountAggregateOperation.SUM).build(), filters,
            null, null,
            QLTimeSeriesAggregation.builder()
                .timeAggregationType(QLTimeAggregationType.DAY)
                .timeAggregationValue(1)
                .build(),
            null, false);

    assertThat(queryMetaData.getQuery())
        .isEqualTo(
            "SELECT COUNT(*) AS ROLLBACK_COUNT,TIME_BUCKET FROM (SELECT  FROM deployment t0 WHERE (EXISTS (SELECT * FROM skeys(t0.) as x(tagname) WHERE ((x.tagname='') AND (t0.ENDTIME >= '2019-07-31T22:36:04Z') AND (t0.ENDTIME <= '2019-07-31T22:41:09Z') AND (t0.ROLLBACK_DURATION > 0) AND (t0.ACCOUNTID = 'ACCOUNTID'))))) as tab1 WHERE (t0.PARENT_EXECUTION IS NULL) GROUP BY TIME_BUCKET ORDER BY TIME_BUCKET ASC");
  }
}
