/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.instance;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.rule.OwnerRule.ALEXANDRU_CIOFU;
import static io.harness.rule.OwnerRule.RAMA;

import static software.wings.graphql.schema.type.instance.QLInstanceType.PHYSICAL_HOST_INSTANCE;

import static com.google.common.collect.Lists.newArrayList;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EnvironmentType;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.api.DeploymentType;
import software.wings.beans.BuildWorkflow;
import software.wings.beans.Service;
import software.wings.beans.User;
import software.wings.beans.Workflow.WorkflowBuilder;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.graphql.datafetcher.AbstractDataFetcherTestBase;
import software.wings.graphql.schema.type.QLEnvironmentType;
import software.wings.graphql.schema.type.aggregation.QLAggregatedData;
import software.wings.graphql.schema.type.aggregation.QLData;
import software.wings.graphql.schema.type.aggregation.QLDataPoint;
import software.wings.graphql.schema.type.aggregation.QLEnumOperator;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;
import software.wings.graphql.schema.type.aggregation.QLSinglePointData;
import software.wings.graphql.schema.type.aggregation.QLStackedData;
import software.wings.graphql.schema.type.aggregation.QLStackedDataPoint;
import software.wings.graphql.schema.type.aggregation.QLStackedTimeSeriesData;
import software.wings.graphql.schema.type.aggregation.QLStackedTimeSeriesDataPoint;
import software.wings.graphql.schema.type.aggregation.QLTimeAggregationType;
import software.wings.graphql.schema.type.aggregation.QLTimeFilter;
import software.wings.graphql.schema.type.aggregation.QLTimeOperator;
import software.wings.graphql.schema.type.aggregation.QLTimeSeriesAggregation;
import software.wings.graphql.schema.type.aggregation.QLTimeSeriesData;
import software.wings.graphql.schema.type.aggregation.QLTimeSeriesDataPoint;
import software.wings.graphql.schema.type.aggregation.environment.QLEnvironmentTypeFilter;
import software.wings.graphql.schema.type.aggregation.instance.QLInstanceAggregation;
import software.wings.graphql.schema.type.aggregation.instance.QLInstanceEntityAggregation;
import software.wings.graphql.schema.type.aggregation.instance.QLInstanceFilter;
import software.wings.graphql.schema.type.aggregation.instance.QLInstanceTagAggregation;
import software.wings.graphql.schema.type.aggregation.instance.QLInstanceTagFilter;
import software.wings.graphql.schema.type.aggregation.instance.QLInstanceTagType;
import software.wings.graphql.schema.type.aggregation.service.QLDeploymentType;
import software.wings.graphql.schema.type.aggregation.service.QLDeploymentTypeFilter;
import software.wings.graphql.schema.type.aggregation.tag.QLTagInput;
import software.wings.graphql.schema.type.aggregation.workflow.QLOrchestrationWorkflowType;
import software.wings.graphql.schema.type.aggregation.workflow.QLOrchestrationWorkflowTypeFilter;
import software.wings.security.UserThreadLocal;

import com.google.inject.Inject;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import lombok.experimental.FieldNameConstants;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.stubbing.Answer;

@OwnedBy(DX)
@FieldNameConstants(innerTypeName = "InstanceStatsDataFetcherTestKeys")
public class InstanceStatsDataFetcherTest extends AbstractDataFetcherTestBase {
  @Mock TimeScaleDBService timeScaleDBService;
  @Inject @InjectMocks InstanceTimeSeriesDataHelper instanceTimeSeriesDataHelper;
  @Inject @InjectMocks InstanceStatsDataFetcher dataFetcher;

  @Mock Statement statement;
  @Mock ResultSet resultSet;
  final int[] count = {0};
  final int[] intVal = {0};
  final long[] longVal = {0};
  final int[] stringVal = {0};
  final long currentTime = System.currentTimeMillis();
  final long[] calendar = {currentTime};

  @Before
  public void setup() throws SQLException {
    User user = testUtils.createUser(testUtils.createAccount());
    UserThreadLocal.set(user);

    // Account1
    createAccount(ACCOUNT1_ID, getLicenseInfo());
    createApp(ACCOUNT1_ID, APP1_ID_ACCOUNT1, APP1_ID_ACCOUNT1, TAG_TEAM, TAG_VALUE_TEAM1);
    createService(ACCOUNT1_ID, APP1_ID_ACCOUNT1, SERVICE1_ID_APP1_ACCOUNT1, SERVICE1_ID_APP1_ACCOUNT1, TAG_MODULE,
        TAG_VALUE_MODULE1);
    createService(ACCOUNT1_ID, APP1_ID_ACCOUNT1, SERVICE2_ID_APP1_ACCOUNT1, SERVICE2_ID_APP1_ACCOUNT1, TAG_MODULE,
        TAG_VALUE_MODULE1);
    createEnv(ACCOUNT1_ID, APP1_ID_ACCOUNT1, ENV1_ID_APP1_ACCOUNT1, ENV1_ID_APP1_ACCOUNT1, TAG_ENVTYPE, TAG_VALUE_PROD);
    createEnv(
        ACCOUNT1_ID, APP1_ID_ACCOUNT1, ENV2_ID_APP1_ACCOUNT1, ENV2_ID_APP1_ACCOUNT1, TAG_ENVTYPE, TAG_VALUE_NON_PROD);

    createCloudProvider(ACCOUNT1_ID, APP1_ID_ACCOUNT1, CLOUD_PROVIDER1_ID_ACCOUNT1, CLOUD_PROVIDER1_ID_ACCOUNT1);
    createCloudProvider(ACCOUNT1_ID, APP1_ID_ACCOUNT1, CLOUD_PROVIDER2_ID_ACCOUNT1, CLOUD_PROVIDER2_ID_ACCOUNT1);

    createInstance(ACCOUNT1_ID, APP1_ID_ACCOUNT1, ENV1_ID_APP1_ACCOUNT1, SERVICE1_ID_APP1_ACCOUNT1,
        EnvironmentType.PROD, INSTANCE1_SERVICE1_ENV1_APP1_ACCOUNT1, CLOUD_PROVIDER1_ID_ACCOUNT1);
    createInstance(ACCOUNT1_ID, APP1_ID_ACCOUNT1, ENV1_ID_APP1_ACCOUNT1, SERVICE1_ID_APP1_ACCOUNT1,
        EnvironmentType.PROD, INSTANCE2_SERVICE1_ENV1_APP1_ACCOUNT1, CLOUD_PROVIDER1_ID_ACCOUNT1);
    createInstance(ACCOUNT1_ID, APP1_ID_ACCOUNT1, ENV2_ID_APP1_ACCOUNT1, SERVICE1_ID_APP1_ACCOUNT1,
        EnvironmentType.NON_PROD, INSTANCE3_SERVICE1_ENV2_APP1_ACCOUNT1, CLOUD_PROVIDER2_ID_ACCOUNT1);
    createInstance(ACCOUNT1_ID, APP1_ID_ACCOUNT1, ENV2_ID_APP1_ACCOUNT1, SERVICE2_ID_APP1_ACCOUNT1,
        EnvironmentType.NON_PROD, INSTANCE4_SERVICE2_ENV2_APP1_ACCOUNT1, CLOUD_PROVIDER2_ID_ACCOUNT1);

    createApp(ACCOUNT1_ID, APP2_ID_ACCOUNT1, APP2_ID_ACCOUNT1, TAG_TEAM, TAG_VALUE_TEAM2);
    createService(ACCOUNT1_ID, APP2_ID_ACCOUNT1, SERVICE3_ID_APP2_ACCOUNT1, SERVICE3_ID_APP2_ACCOUNT1, TAG_MODULE,
        TAG_VALUE_MODULE2);
    createEnv(ACCOUNT1_ID, APP2_ID_ACCOUNT1, ENV3_ID_APP2_ACCOUNT1, ENV3_ID_APP2_ACCOUNT1, TAG_ENVTYPE, TAG_VALUE_PROD);
    createEnv(
        ACCOUNT1_ID, APP2_ID_ACCOUNT1, ENV4_ID_APP2_ACCOUNT1, ENV4_ID_APP2_ACCOUNT1, TAG_ENVTYPE, TAG_VALUE_NON_PROD);
    createInstance(ACCOUNT1_ID, APP2_ID_ACCOUNT1, ENV3_ID_APP2_ACCOUNT1, SERVICE3_ID_APP2_ACCOUNT1,
        EnvironmentType.PROD, INSTANCE5_SERVICE3_ENV3_APP2_ACCOUNT1, CLOUD_PROVIDER1_ID_ACCOUNT1);
    createInstance(ACCOUNT1_ID, APP2_ID_ACCOUNT1, ENV4_ID_APP2_ACCOUNT1, SERVICE3_ID_APP2_ACCOUNT1,
        EnvironmentType.PROD, INSTANCE6_SERVICE3_ENV4_APP2_ACCOUNT1, CLOUD_PROVIDER2_ID_ACCOUNT1);

    // Account2
    createAccount(ACCOUNT2_ID, getLicenseInfo());
    createApp(ACCOUNT2_ID, APP3_ID_ACCOUNT2, APP3_ID_ACCOUNT2, TAG_TEAM, TAG_VALUE_TEAM1);
    createService(ACCOUNT2_ID, APP3_ID_ACCOUNT2, SERVICE4_ID_APP3_ACCOUNT2, SERVICE4_ID_APP3_ACCOUNT2, TAG_MODULE,
        TAG_VALUE_MODULE2);
    createEnv(ACCOUNT2_ID, APP3_ID_ACCOUNT2, ENV5_ID_APP3_ACCOUNT2, ENV5_ID_APP3_ACCOUNT2, TAG_ENVTYPE, TAG_VALUE_PROD);
    createEnv(
        ACCOUNT2_ID, APP3_ID_ACCOUNT2, ENV6_ID_APP3_ACCOUNT2, ENV6_ID_APP3_ACCOUNT2, TAG_ENVTYPE, TAG_VALUE_NON_PROD);

    createCloudProvider(ACCOUNT2_ID, APP3_ID_ACCOUNT2, CLOUD_PROVIDER3_ID_ACCOUNT2, CLOUD_PROVIDER3_ID_ACCOUNT2);

    createInstance(ACCOUNT2_ID, APP3_ID_ACCOUNT2, ENV5_ID_APP3_ACCOUNT2, SERVICE4_ID_APP3_ACCOUNT2,
        EnvironmentType.PROD, INSTANCE7_SERVICE4_ENV5_APP3_ACCOUNT2, CLOUD_PROVIDER3_ID_ACCOUNT2);
    createInstance(ACCOUNT2_ID, APP3_ID_ACCOUNT2, ENV6_ID_APP3_ACCOUNT2, SERVICE4_ID_APP3_ACCOUNT2,
        EnvironmentType.NON_PROD, INSTANCE8_SERVICE4_ENV6_APP3_ACCOUNT2, CLOUD_PROVIDER3_ID_ACCOUNT2);

    Connection mockConnection = mock(Connection.class);
    Statement mockStatement = mock(Statement.class);
    when(timeScaleDBService.getDBConnection()).thenReturn(mockConnection);
    when(timeScaleDBService.isValid()).thenReturn(true);
    when(mockConnection.createStatement()).thenReturn(mockStatement);
    when(mockStatement.executeQuery(anyString())).thenReturn(resultSet);
    resetValues();
    mockResultSet();
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testSinglePointDataWithNoFilter() {
    // No Filter
    QLData qlData = dataFetcher.fetch(ACCOUNT1_ID, null, null, null, null, null);
    assertSinglePointData(qlData, 6L);

    qlData = dataFetcher.fetch(ACCOUNT2_ID, null, null, null, null, null);
    assertSinglePointData(qlData, 2L);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testAppFilter() {
    // With Filter
    QLInstanceFilter appFilter =
        QLInstanceFilter.builder()
            .application(
                QLIdFilter.builder().values(new String[] {APP1_ID_ACCOUNT1}).operator(QLIdOperator.EQUALS).build())
            .build();
    QLData qlData = dataFetcher.fetch(ACCOUNT1_ID, null, newArrayList(appFilter), null, null, null);
    assertSinglePointData(qlData, 4L);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testEnvFilter() {
    QLInstanceFilter envFilter =
        QLInstanceFilter.builder()
            .environment(
                QLIdFilter.builder().values(new String[] {ENV1_ID_APP1_ACCOUNT1}).operator(QLIdOperator.EQUALS).build())
            .build();
    QLData qlData = dataFetcher.fetch(ACCOUNT1_ID, null, newArrayList(envFilter), null, null, null);
    assertSinglePointData(qlData, 2L);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testEnvAndServiceFilters() {
    QLInstanceFilter env2Filter =
        QLInstanceFilter.builder()
            .environment(
                QLIdFilter.builder().values(new String[] {ENV2_ID_APP1_ACCOUNT1}).operator(QLIdOperator.EQUALS).build())
            .build();
    QLInstanceFilter serviceFilter = QLInstanceFilter.builder()
                                         .service(QLIdFilter.builder()
                                                      .values(new String[] {SERVICE1_ID_APP1_ACCOUNT1})
                                                      .operator(QLIdOperator.EQUALS)
                                                      .build())
                                         .build();
    QLData qlData = dataFetcher.fetch(ACCOUNT1_ID, null, newArrayList(serviceFilter, env2Filter), null, null, null);
    assertSinglePointData(qlData, 1L);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testServiceFilter() {
    QLInstanceFilter serviceFilter = QLInstanceFilter.builder()
                                         .service(QLIdFilter.builder()
                                                      .values(new String[] {SERVICE1_ID_APP1_ACCOUNT1})
                                                      .operator(QLIdOperator.EQUALS)
                                                      .build())
                                         .build();
    QLData qlData = dataFetcher.fetch(ACCOUNT1_ID, null, newArrayList(serviceFilter), null, null, null);
    assertSinglePointData(qlData, 3L);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testTagFilter() {
    QLInstanceFilter prodTagFilter =
        QLInstanceFilter.builder()
            .tag(QLInstanceTagFilter.builder()
                     .entityType(QLInstanceTagType.ENVIRONMENT)
                     .tags(newArrayList(QLTagInput.builder().name(TAG_ENVTYPE).value(TAG_VALUE_PROD).build()))
                     .build())
            .build();
    QLData qlData = dataFetcher.fetch(ACCOUNT1_ID, null, newArrayList(prodTagFilter), null, null, null);
    assertSinglePointData(qlData, 3L);

    qlData = dataFetcher.fetch(ACCOUNT2_ID, null, newArrayList(prodTagFilter), null, null, null);
    assertSinglePointData(qlData, 1L);

    QLInstanceFilter nonProdTagFilter =
        QLInstanceFilter.builder()
            .tag(QLInstanceTagFilter.builder()
                     .entityType(QLInstanceTagType.ENVIRONMENT)
                     .tags(newArrayList(QLTagInput.builder().name(TAG_ENVTYPE).value(TAG_VALUE_NON_PROD).build()))
                     .build())
            .build();
    qlData = dataFetcher.fetch(ACCOUNT1_ID, null, newArrayList(nonProdTagFilter), null, null, null);
    assertSinglePointData(qlData, 3L);

    qlData = dataFetcher.fetch(ACCOUNT2_ID, null, newArrayList(nonProdTagFilter), null, null, null);
    assertSinglePointData(qlData, 1L);

    QLInstanceFilter appTagFilter =
        QLInstanceFilter.builder()
            .tag(QLInstanceTagFilter.builder()
                     .entityType(QLInstanceTagType.APPLICATION)
                     .tags(newArrayList(QLTagInput.builder().name(TAG_TEAM).value(TAG_VALUE_TEAM1).build()))
                     .build())
            .build();
    qlData = dataFetcher.fetch(ACCOUNT1_ID, null, newArrayList(appTagFilter), null, null, null);
    assertSinglePointData(qlData, 4L);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testAllTagsFilter() {
    QLInstanceFilter allEnvTypeTagFilter =
        QLInstanceFilter.builder()
            .tag(QLInstanceTagFilter.builder()
                     .entityType(QLInstanceTagType.ENVIRONMENT)
                     .tags(newArrayList(QLTagInput.builder().name(TAG_ENVTYPE).build()))
                     .build())
            .build();
    QLData qlData = dataFetcher.fetch(ACCOUNT1_ID, null, newArrayList(allEnvTypeTagFilter), null, null, null);
    assertSinglePointData(qlData, 6L);

    qlData = dataFetcher.fetch(ACCOUNT2_ID, null, newArrayList(allEnvTypeTagFilter), null, null, null);
    assertSinglePointData(qlData, 2L);

    allEnvTypeTagFilter =
        QLInstanceFilter.builder()
            .tag(QLInstanceTagFilter.builder()
                     .entityType(QLInstanceTagType.ENVIRONMENT)
                     .tags(newArrayList(QLTagInput.builder().name(TAG_ENVTYPE).value(TAG_VALUE_NON_PROD).build(),
                         QLTagInput.builder().name(TAG_ENVTYPE).value(TAG_VALUE_PROD).build()))
                     .build())
            .build();
    qlData = dataFetcher.fetch(ACCOUNT1_ID, null, newArrayList(allEnvTypeTagFilter), null, null, null);
    assertSinglePointData(qlData, 6L);

    qlData = dataFetcher.fetch(ACCOUNT2_ID, null, newArrayList(allEnvTypeTagFilter), null, null, null);
    assertSinglePointData(qlData, 2L);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testAppAndServiceFilters() {
    // With multiple filters
    QLInstanceFilter appFilter =
        QLInstanceFilter.builder()
            .application(
                QLIdFilter.builder().values(new String[] {APP1_ID_ACCOUNT1}).operator(QLIdOperator.EQUALS).build())
            .build();
    QLInstanceFilter serviceFilter = QLInstanceFilter.builder()
                                         .service(QLIdFilter.builder()
                                                      .values(new String[] {SERVICE1_ID_APP1_ACCOUNT1})
                                                      .operator(QLIdOperator.EQUALS)
                                                      .build())
                                         .build();
    QLData qlData = dataFetcher.fetch(ACCOUNT1_ID, null, newArrayList(appFilter, serviceFilter), null, null, null);
    assertSinglePointData(qlData, 3L);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testMultipleORFilters() {
    // With multiple OR filters
    QLInstanceFilter appFilter = QLInstanceFilter.builder()
                                     .application(QLIdFilter.builder()
                                                      .values(new String[] {APP1_ID_ACCOUNT1, APP2_ID_ACCOUNT1})
                                                      .operator(QLIdOperator.IN)
                                                      .build())
                                     .build();
    QLData qlData = dataFetcher.fetch(ACCOUNT1_ID, null, newArrayList(appFilter), null, null, null);
    assertSinglePointData(qlData, 6L);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testAppOneLevelAggregation() {
    // One level Aggregation
    QLInstanceAggregation firstLevelAggregation =
        QLInstanceAggregation.builder().entityAggregation(QLInstanceEntityAggregation.Application).build();
    QLData qlData = dataFetcher.fetch(ACCOUNT1_ID, null, null, newArrayList(firstLevelAggregation), null, null);
    QLAggregatedData aggregatedData = assertOneLevelAggregatedData(qlData, 2);
    assertThat(aggregatedData.getDataPoints().get(0).getKey().getId()).isEqualTo(APP1_ID_ACCOUNT1);
    assertThat(aggregatedData.getDataPoints().get(0).getValue()).isEqualTo(4);
    assertThat(aggregatedData.getDataPoints().get(1).getKey().getId()).isEqualTo(APP2_ID_ACCOUNT1);
    assertThat(aggregatedData.getDataPoints().get(1).getValue()).isEqualTo(2);

    qlData = dataFetcher.fetch(ACCOUNT2_ID, null, null, newArrayList(firstLevelAggregation), null, null);
    aggregatedData = assertOneLevelAggregatedData(qlData, 1);
    assertThat(aggregatedData.getDataPoints().get(0).getKey().getId()).isEqualTo(APP3_ID_ACCOUNT2);
    assertThat(aggregatedData.getDataPoints().get(0).getValue()).isEqualTo(2);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testServiceOneLevelAggregation() {
    QLInstanceFilter appFilter =
        QLInstanceFilter.builder()
            .application(
                QLIdFilter.builder().values(new String[] {APP1_ID_ACCOUNT1}).operator(QLIdOperator.EQUALS).build())
            .build();
    // One level Aggregation
    QLInstanceAggregation firstLevelAggregation =
        QLInstanceAggregation.builder().entityAggregation(QLInstanceEntityAggregation.Service).build();
    QLData qlData =
        dataFetcher.fetch(ACCOUNT1_ID, null, newArrayList(appFilter), newArrayList(firstLevelAggregation), null, null);
    QLAggregatedData aggregatedData = assertOneLevelAggregatedData(qlData, 2);
    assertThat(aggregatedData.getDataPoints().get(0).getKey().getId()).isEqualTo(SERVICE1_ID_APP1_ACCOUNT1);
    assertThat(aggregatedData.getDataPoints().get(0).getValue()).isEqualTo(3);
    assertThat(aggregatedData.getDataPoints().get(1).getKey().getId()).isEqualTo(SERVICE2_ID_APP1_ACCOUNT1);
    assertThat(aggregatedData.getDataPoints().get(1).getValue()).isEqualTo(1);

    qlData = dataFetcher.fetch(ACCOUNT2_ID, null, null, newArrayList(firstLevelAggregation), null, null);
    aggregatedData = assertOneLevelAggregatedData(qlData, 1);
    assertThat(aggregatedData.getDataPoints().get(0).getKey().getId()).isEqualTo(SERVICE4_ID_APP3_ACCOUNT2);
    assertThat(aggregatedData.getDataPoints().get(0).getValue()).isEqualTo(2);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testEnvOneLevelAggregation() {
    QLInstanceFilter appFilter =
        QLInstanceFilter.builder()
            .application(
                QLIdFilter.builder().values(new String[] {APP1_ID_ACCOUNT1}).operator(QLIdOperator.EQUALS).build())
            .build();
    // One level Aggregation
    QLInstanceAggregation firstLevelAggregation =
        QLInstanceAggregation.builder().entityAggregation(QLInstanceEntityAggregation.Environment).build();
    QLData qlData =
        dataFetcher.fetch(ACCOUNT1_ID, null, newArrayList(appFilter), newArrayList(firstLevelAggregation), null, null);
    QLAggregatedData aggregatedData = assertOneLevelAggregatedData(qlData, 2);
    assertThat(aggregatedData.getDataPoints().get(0).getKey().getId()).isEqualTo(ENV1_ID_APP1_ACCOUNT1);
    assertThat(aggregatedData.getDataPoints().get(0).getValue()).isEqualTo(2);
    assertThat(aggregatedData.getDataPoints().get(1).getKey().getId()).isEqualTo(ENV2_ID_APP1_ACCOUNT1);
    assertThat(aggregatedData.getDataPoints().get(1).getValue()).isEqualTo(2);

    qlData = dataFetcher.fetch(ACCOUNT2_ID, null, null, newArrayList(firstLevelAggregation), null, null);
    aggregatedData = assertOneLevelAggregatedData(qlData, 2);
    assertThat(aggregatedData.getDataPoints().get(0).getKey().getId()).isEqualTo(ENV5_ID_APP3_ACCOUNT2);
    assertThat(aggregatedData.getDataPoints().get(0).getValue()).isEqualTo(1);
    assertThat(aggregatedData.getDataPoints().get(1).getKey().getId()).isEqualTo(ENV6_ID_APP3_ACCOUNT2);
    assertThat(aggregatedData.getDataPoints().get(1).getValue()).isEqualTo(1);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testCloudProviderOneLevelAggregation() {
    // One level Aggregation
    QLInstanceAggregation firstLevelAggregation =
        QLInstanceAggregation.builder().entityAggregation(QLInstanceEntityAggregation.CloudProvider).build();
    QLData qlData = dataFetcher.fetch(ACCOUNT1_ID, null, null, newArrayList(firstLevelAggregation), null, null);
    QLAggregatedData aggregatedData = assertOneLevelAggregatedData(qlData, 2);
    assertThat(aggregatedData.getDataPoints().get(0).getKey().getId()).isEqualTo(CLOUD_PROVIDER1_ID_ACCOUNT1);
    assertThat(aggregatedData.getDataPoints().get(0).getValue()).isEqualTo(3);
    assertThat(aggregatedData.getDataPoints().get(1).getKey().getId()).isEqualTo(CLOUD_PROVIDER2_ID_ACCOUNT1);
    assertThat(aggregatedData.getDataPoints().get(1).getValue()).isEqualTo(3);

    qlData = dataFetcher.fetch(ACCOUNT2_ID, null, null, newArrayList(firstLevelAggregation), null, null);
    aggregatedData = assertOneLevelAggregatedData(qlData, 1);
    assertThat(aggregatedData.getDataPoints().get(0).getKey().getId()).isEqualTo(CLOUD_PROVIDER3_ID_ACCOUNT2);
    assertThat(aggregatedData.getDataPoints().get(0).getValue()).isEqualTo(2);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testAppTagOneLevelAggregation() {
    // One level Aggregation
    QLInstanceAggregation firstLevelAggregation =
        QLInstanceAggregation.builder()
            .tagAggregation(
                QLInstanceTagAggregation.builder().entityType(QLInstanceTagType.APPLICATION).tagName(TAG_TEAM).build())
            .build();
    QLData qlData = dataFetcher.fetch(ACCOUNT1_ID, null, null, newArrayList(firstLevelAggregation), null, null);
    QLAggregatedData aggregatedData = assertOneLevelAggregatedData(qlData, 2);
    assertThat(aggregatedData.getDataPoints().get(0).getKey().getId()).isEqualTo(APP1_ID_ACCOUNT1);
    assertThat(aggregatedData.getDataPoints().get(0).getValue()).isEqualTo(4);
    assertThat(aggregatedData.getDataPoints().get(1).getKey().getId()).isEqualTo(APP2_ID_ACCOUNT1);
    assertThat(aggregatedData.getDataPoints().get(1).getValue()).isEqualTo(2);

    QLData qlDataPostFetch = dataFetcher.postFetch(ACCOUNT1_ID, newArrayList(firstLevelAggregation), qlData);
    QLAggregatedData aggregatedDataPostFetch = assertOneLevelAggregatedData(qlDataPostFetch, 2);
    assertThat(aggregatedDataPostFetch.getDataPoints().get(0).getKey().getId()).isEqualTo(TAG_TEAM1);
    assertThat(aggregatedDataPostFetch.getDataPoints().get(0).getValue()).isEqualTo(4);
    assertThat(aggregatedDataPostFetch.getDataPoints().get(1).getKey().getId()).isEqualTo(TAG_TEAM2);
    assertThat(aggregatedDataPostFetch.getDataPoints().get(1).getValue()).isEqualTo(2);

    qlData = dataFetcher.fetch(ACCOUNT2_ID, null, null, newArrayList(firstLevelAggregation), null, null);
    aggregatedData = assertOneLevelAggregatedData(qlData, 1);
    assertThat(aggregatedData.getDataPoints().get(0).getKey().getId()).isEqualTo(APP3_ID_ACCOUNT2);
    assertThat(aggregatedData.getDataPoints().get(0).getValue()).isEqualTo(2);

    qlDataPostFetch = dataFetcher.postFetch(ACCOUNT2_ID, newArrayList(firstLevelAggregation), qlData);
    aggregatedDataPostFetch = assertOneLevelAggregatedData(qlDataPostFetch, 1);
    assertThat(aggregatedDataPostFetch.getDataPoints().get(0).getKey().getId()).isEqualTo(TAG_TEAM1);
    assertThat(aggregatedDataPostFetch.getDataPoints().get(0).getValue()).isEqualTo(2);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testServiceTagOneLevelAggregation() {
    // One level Aggregation
    QLInstanceAggregation firstLevelAggregation =
        QLInstanceAggregation.builder()
            .tagAggregation(
                QLInstanceTagAggregation.builder().entityType(QLInstanceTagType.SERVICE).tagName(TAG_MODULE).build())
            .build();
    QLData qlData = dataFetcher.fetch(ACCOUNT1_ID, null, null, newArrayList(firstLevelAggregation), null, null);
    QLAggregatedData aggregatedData = assertOneLevelAggregatedData(qlData, 3);
    assertThat(aggregatedData.getDataPoints().get(0).getKey().getId()).isEqualTo(SERVICE1_ID_APP1_ACCOUNT1);
    assertThat(aggregatedData.getDataPoints().get(0).getValue()).isEqualTo(3);
    assertThat(aggregatedData.getDataPoints().get(1).getKey().getId()).isEqualTo(SERVICE2_ID_APP1_ACCOUNT1);
    assertThat(aggregatedData.getDataPoints().get(1).getValue()).isEqualTo(1);
    assertThat(aggregatedData.getDataPoints().get(2).getKey().getId()).isEqualTo(SERVICE3_ID_APP2_ACCOUNT1);
    assertThat(aggregatedData.getDataPoints().get(2).getValue()).isEqualTo(2);

    QLInstanceFilter appFilter =
        QLInstanceFilter.builder()
            .application(
                QLIdFilter.builder().values(new String[] {APP1_ID_ACCOUNT1}).operator(QLIdOperator.EQUALS).build())
            .build();
    qlData =
        dataFetcher.fetch(ACCOUNT1_ID, null, newArrayList(appFilter), newArrayList(firstLevelAggregation), null, null);
    aggregatedData = assertOneLevelAggregatedData(qlData, 2);
    assertThat(aggregatedData.getDataPoints().get(0).getKey().getId()).isEqualTo(SERVICE1_ID_APP1_ACCOUNT1);
    assertThat(aggregatedData.getDataPoints().get(0).getValue()).isEqualTo(3);
    assertThat(aggregatedData.getDataPoints().get(1).getKey().getId()).isEqualTo(SERVICE2_ID_APP1_ACCOUNT1);
    assertThat(aggregatedData.getDataPoints().get(1).getValue()).isEqualTo(1);

    QLInstanceFilter serviceTagFilter =
        QLInstanceFilter.builder()
            .tag(QLInstanceTagFilter.builder()
                     .entityType(QLInstanceTagType.SERVICE)
                     .tags(newArrayList(QLTagInput.builder().name(TAG_MODULE).value(TAG_VALUE_MODULE1).build()))
                     .build())
            .build();
    qlData = dataFetcher.fetch(
        ACCOUNT1_ID, null, newArrayList(serviceTagFilter), newArrayList(firstLevelAggregation), null, null);
    aggregatedData = assertOneLevelAggregatedData(qlData, 2);
    assertThat(aggregatedData.getDataPoints().get(0).getKey().getId()).isEqualTo(SERVICE1_ID_APP1_ACCOUNT1);
    assertThat(aggregatedData.getDataPoints().get(0).getValue()).isEqualTo(3);
    assertThat(aggregatedData.getDataPoints().get(1).getKey().getId()).isEqualTo(SERVICE2_ID_APP1_ACCOUNT1);
    assertThat(aggregatedData.getDataPoints().get(1).getValue()).isEqualTo(1);

    QLData qlDataPostFetch = dataFetcher.postFetch(ACCOUNT1_ID, newArrayList(firstLevelAggregation), qlData);
    QLAggregatedData aggregatedDataPostFetch = assertOneLevelAggregatedData(qlDataPostFetch, 1);
    assertThat(aggregatedDataPostFetch.getDataPoints().get(0).getKey().getId()).isEqualTo(TAG_MODULE1);
    assertThat(aggregatedDataPostFetch.getDataPoints().get(0).getValue()).isEqualTo(4.0);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testEnvTagOneLevelAggregation() {
    // One level Aggregation
    QLInstanceAggregation firstLevelAggregation = QLInstanceAggregation.builder()
                                                      .tagAggregation(QLInstanceTagAggregation.builder()
                                                                          .entityType(QLInstanceTagType.ENVIRONMENT)
                                                                          .tagName(TAG_ENVTYPE)
                                                                          .build())
                                                      .build();
    QLData qlData = dataFetcher.fetch(ACCOUNT1_ID, null, null, newArrayList(firstLevelAggregation), null, null);
    QLAggregatedData aggregatedData = assertOneLevelAggregatedData(qlData, 4);
    assertThat(aggregatedData.getDataPoints().get(0).getKey().getId()).isEqualTo(ENV1_ID_APP1_ACCOUNT1);
    assertThat(aggregatedData.getDataPoints().get(0).getValue()).isEqualTo(2);
    assertThat(aggregatedData.getDataPoints().get(1).getKey().getId()).isEqualTo(ENV2_ID_APP1_ACCOUNT1);
    assertThat(aggregatedData.getDataPoints().get(1).getValue()).isEqualTo(2);
    assertThat(aggregatedData.getDataPoints().get(2).getKey().getId()).isEqualTo(ENV3_ID_APP2_ACCOUNT1);
    assertThat(aggregatedData.getDataPoints().get(2).getValue()).isEqualTo(1);
    assertThat(aggregatedData.getDataPoints().get(3).getKey().getId()).isEqualTo(ENV4_ID_APP2_ACCOUNT1);
    assertThat(aggregatedData.getDataPoints().get(3).getValue()).isEqualTo(1);

    QLInstanceFilter prodTagFilter =
        QLInstanceFilter.builder()
            .tag(QLInstanceTagFilter.builder()
                     .entityType(QLInstanceTagType.ENVIRONMENT)
                     .tags(newArrayList(QLTagInput.builder().name(TAG_ENVTYPE).value(TAG_VALUE_PROD).build()))
                     .build())
            .build();

    qlData = dataFetcher.fetch(
        ACCOUNT1_ID, null, newArrayList(prodTagFilter), newArrayList(firstLevelAggregation), null, null);
    aggregatedData = assertOneLevelAggregatedData(qlData, 2);
    assertThat(aggregatedData.getDataPoints().get(0).getKey().getId()).isEqualTo(ENV1_ID_APP1_ACCOUNT1);
    assertThat(aggregatedData.getDataPoints().get(0).getValue()).isEqualTo(2);
    assertThat(aggregatedData.getDataPoints().get(1).getKey().getId()).isEqualTo(ENV3_ID_APP2_ACCOUNT1);
    assertThat(aggregatedData.getDataPoints().get(1).getValue()).isEqualTo(1);

    QLData qlDataPostFetch = dataFetcher.postFetch(ACCOUNT1_ID, newArrayList(firstLevelAggregation), qlData);
    QLAggregatedData aggregatedDataPostFetch = assertOneLevelAggregatedData(qlDataPostFetch, 1);
    assertThat(aggregatedDataPostFetch.getDataPoints().get(0).getKey().getId()).isEqualTo(TAG_PROD);
    assertThat(aggregatedDataPostFetch.getDataPoints().get(0).getValue()).isEqualTo(3.0);
  }

  private void mockResultSet() throws SQLException {
    Connection connection = mock(Connection.class);
    statement = mock(Statement.class);
    resultSet = mock(ResultSet.class);
    when(timeScaleDBService.getDBConnection()).thenReturn(connection);
    when(connection.createStatement()).thenReturn(statement);
    when(statement.executeQuery(anyString())).thenReturn(resultSet);
    //    when(resultSet.next()).thenReturn(resultSet);

    when(resultSet.getInt(anyString())).thenAnswer((Answer<Integer>) invocation -> 10 + intVal[0]++);

    when(resultSet.getInt(anyString())).thenAnswer((Answer<Integer>) invocation -> 10 + intVal[0]++);
    when(resultSet.getLong(anyString())).thenAnswer((Answer<Long>) invocation -> 20L + longVal[0]++);
    when(resultSet.getString(anyString())).thenAnswer((Answer<String>) invocation -> "DATA" + stringVal[0]++);
    when(resultSet.getTimestamp(anyString())).thenAnswer((Answer<Timestamp>) invocation -> {
      calendar[0] = calendar[0] + 3600000;
      return new Timestamp(calendar[0]);
    });
    returnResultSet(5);
  }

  private void resetValues() {
    count[0] = 0;
    intVal[0] = 0;
    longVal[0] = 0;
    stringVal[0] = 0;
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

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testHourlyOneLevelAggregation() throws SQLException {
    long millis = 1234455555233L;
    QLInstanceFilter fromFilter =
        QLInstanceFilter.builder()
            .createdAt(QLTimeFilter.builder().operator(QLTimeOperator.AFTER).value(millis - 5 * 60 * 60 * 1000).build())
            .build();

    QLInstanceFilter toFilter =
        QLInstanceFilter.builder()
            .createdAt(QLTimeFilter.builder().operator(QLTimeOperator.BEFORE).value(millis).build())
            .build();

    // One level Aggregation
    QLInstanceAggregation firstLevelAggregation =
        QLInstanceAggregation.builder()
            .timeAggregation(QLTimeSeriesAggregation.builder()
                                 .timeAggregationType(QLTimeAggregationType.HOUR)
                                 .timeAggregationValue(1)
                                 .build())
            .build();
    QLData qlData = dataFetcher.fetch(
        ACCOUNT1_ID, null, newArrayList(fromFilter, toFilter), newArrayList(firstLevelAggregation), null, null);
    assertThat(qlData).isInstanceOf(QLTimeSeriesData.class);
    QLTimeSeriesData timeSeriesData = (QLTimeSeriesData) qlData;
    List<QLTimeSeriesDataPoint> timeSeriesDataPoints = timeSeriesData.getDataPoints();
    assertThat(timeSeriesDataPoints).isNotEmpty();
    assertThat(timeSeriesDataPoints.size()).isEqualTo(5);

    ArgumentCaptor<String> queryArgumentCaptor = ArgumentCaptor.forClass(String.class);
    verify(statement).executeQuery(queryArgumentCaptor.capture());
    String query = queryArgumentCaptor.getValue();
    assertThat(query).isNotNull();
    assertThat(query).isEqualTo(QUERY1);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testAppHourlyTwoLevelAggregation() throws SQLException {
    QLInstanceFilter appFilter =
        QLInstanceFilter.builder()
            .application(
                QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(new String[] {APP1_ID_ACCOUNT1}).build())
            .build();

    QLInstanceAggregation firstLevelAggregation =
        QLInstanceAggregation.builder()
            .timeAggregation(QLTimeSeriesAggregation.builder()
                                 .timeAggregationType(QLTimeAggregationType.HOUR)
                                 .timeAggregationValue(1)
                                 .build())
            .build();

    QLInstanceAggregation secondLevelAggregation =
        QLInstanceAggregation.builder().entityAggregation(QLInstanceEntityAggregation.Application).build();
    testTimeBasedTwoLevelAggregation(newArrayList(appFilter), firstLevelAggregation, secondLevelAggregation, QUERY2);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testServiceDailyTwoLevelAggregation() throws SQLException {
    QLInstanceFilter serviceFilter =
        QLInstanceFilter.builder()
            .service(QLIdFilter.builder()
                         .operator(QLIdOperator.IN)
                         .values(new String[] {SERVICE1_ID_APP1_ACCOUNT1, SERVICE2_ID_APP1_ACCOUNT1})
                         .build())
            .build();

    QLInstanceAggregation firstLevelAggregation =
        QLInstanceAggregation.builder()
            .timeAggregation(QLTimeSeriesAggregation.builder()
                                 .timeAggregationType(QLTimeAggregationType.DAY)
                                 .timeAggregationValue(1)
                                 .build())
            .build();
    QLInstanceAggregation secondLevelAggregation =
        QLInstanceAggregation.builder().entityAggregation(QLInstanceEntityAggregation.Service).build();
    testTimeBasedTwoLevelAggregation(
        newArrayList(serviceFilter), firstLevelAggregation, secondLevelAggregation, QUERY3);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testCloudProviderDailyTwoLevelAggregation() throws SQLException {
    QLInstanceFilter cloudProviderFilter =
        QLInstanceFilter.builder()
            .cloudProvider(QLIdFilter.builder()
                               .operator(QLIdOperator.IN)
                               .values(new String[] {CLOUD_PROVIDER1_ID_ACCOUNT1, CLOUD_PROVIDER2_ID_ACCOUNT1})
                               .build())
            .build();
    QLInstanceAggregation firstLevelAggregation =
        QLInstanceAggregation.builder()
            .timeAggregation(QLTimeSeriesAggregation.builder()
                                 .timeAggregationType(QLTimeAggregationType.DAY)
                                 .timeAggregationValue(1)
                                 .build())
            .build();
    QLInstanceAggregation secondLevelAggregation =
        QLInstanceAggregation.builder().entityAggregation(QLInstanceEntityAggregation.CloudProvider).build();
    testTimeBasedTwoLevelAggregation(
        newArrayList(cloudProviderFilter), firstLevelAggregation, secondLevelAggregation, QUERY4);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testDailyEnvTwoLevelAggregation() throws SQLException {
    QLInstanceFilter envFilter =
        QLInstanceFilter.builder()
            .environment(
                QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(new String[] {ENV1_ID_APP1_ACCOUNT1}).build())
            .build();

    QLInstanceAggregation firstLevelAggregation =
        QLInstanceAggregation.builder().entityAggregation(QLInstanceEntityAggregation.Environment).build();
    QLInstanceAggregation secondLevelAggregation =
        QLInstanceAggregation.builder()
            .timeAggregation(QLTimeSeriesAggregation.builder()
                                 .timeAggregationType(QLTimeAggregationType.DAY)
                                 .timeAggregationValue(1)
                                 .build())
            .build();

    testTimeBasedTwoLevelAggregation(newArrayList(envFilter), firstLevelAggregation, secondLevelAggregation, QUERY5);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testAppTagDailyTwoLevelAggregation() throws SQLException {
    QLInstanceFilter prodTagFilter =
        QLInstanceFilter.builder()
            .tag(QLInstanceTagFilter.builder()
                     .entityType(QLInstanceTagType.ENVIRONMENT)
                     .tags(newArrayList(QLTagInput.builder().name(TAG_ENVTYPE).value(TAG_VALUE_PROD).build()))
                     .build())
            .build();

    QLInstanceAggregation firstLevelAggregation =
        QLInstanceAggregation.builder()
            .timeAggregation(QLTimeSeriesAggregation.builder()
                                 .timeAggregationType(QLTimeAggregationType.DAY)
                                 .timeAggregationValue(1)
                                 .build())
            .build();
    QLInstanceAggregation secondLevelAggregation =
        QLInstanceAggregation.builder()
            .tagAggregation(
                QLInstanceTagAggregation.builder().entityType(QLInstanceTagType.APPLICATION).tagName(TAG_TEAM).build())
            .build();

    testTimeBasedTwoLevelAggregation(
        newArrayList(prodTagFilter), firstLevelAggregation, secondLevelAggregation, QUERY6);
  }

  private void testTimeBasedTwoLevelAggregation(List<QLInstanceFilter> instanceFilters,
      QLInstanceAggregation firstLevelAggregation, QLInstanceAggregation secondLevelAggregation, String expectedQuery)
      throws SQLException {
    long millis = 1234455555233L;
    QLInstanceFilter fromFilter =
        QLInstanceFilter.builder()
            .createdAt(QLTimeFilter.builder().operator(QLTimeOperator.AFTER).value(millis - 5 * 60 * 60 * 1000).build())
            .build();

    QLInstanceFilter toFilter =
        QLInstanceFilter.builder()
            .createdAt(QLTimeFilter.builder().operator(QLTimeOperator.BEFORE).value(millis).build())
            .build();

    if (instanceFilters == null) {
      instanceFilters = new ArrayList<>();
    }

    instanceFilters.add(fromFilter);
    instanceFilters.add(toFilter);
    QLData qlData = dataFetcher.fetch(
        ACCOUNT1_ID, null, instanceFilters, newArrayList(firstLevelAggregation, secondLevelAggregation), null, null);
    assertThat(qlData).isInstanceOf(QLStackedTimeSeriesData.class);
    QLStackedTimeSeriesData stackedTimeSeriesData = (QLStackedTimeSeriesData) qlData;
    List<QLStackedTimeSeriesDataPoint> stackedTimeSeriesDataData = stackedTimeSeriesData.getData();
    assertThat(stackedTimeSeriesDataData).isNotEmpty();
    assertThat(stackedTimeSeriesDataData.size()).isEqualTo(5);

    ArgumentCaptor<String> queryArgumentCaptor = ArgumentCaptor.forClass(String.class);
    verify(statement).executeQuery(queryArgumentCaptor.capture());
    String query = queryArgumentCaptor.getValue();
    assertThat(query).isNotNull();
    assertThat(query).isEqualTo(expectedQuery);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testAppServiceTwoLevelAggregation() {
    // One level Aggregation
    QLInstanceAggregation firstLevelAggregation =
        QLInstanceAggregation.builder().entityAggregation(QLInstanceEntityAggregation.Application).build();
    QLInstanceAggregation secondLevelAggregation =
        QLInstanceAggregation.builder().entityAggregation(QLInstanceEntityAggregation.Service).build();
    QLData qlData = dataFetcher.fetch(
        ACCOUNT1_ID, null, null, newArrayList(firstLevelAggregation, secondLevelAggregation), null, null);
    QLStackedData stackedData = assertTwoLevelAggregatedData(qlData, 2);

    QLStackedDataPoint stackedDataPoint1 = stackedData.getDataPoints().get(0);
    assertThat(stackedDataPoint1.getKey().getId()).isEqualTo(APP1_ID_ACCOUNT1);
    List<QLDataPoint> stackedDataPoint1Values = stackedDataPoint1.getValues();
    assertThat(stackedDataPoint1Values.size()).isEqualTo(2);

    QLDataPoint dataPoint1 = stackedDataPoint1Values.get(0);
    assertThat(dataPoint1.getKey().getId()).isEqualTo(SERVICE1_ID_APP1_ACCOUNT1);
    assertThat(dataPoint1.getValue()).isEqualTo(3L);

    QLDataPoint dataPoint2 = stackedDataPoint1Values.get(1);
    assertThat(dataPoint2.getKey().getId()).isEqualTo(SERVICE2_ID_APP1_ACCOUNT1);
    assertThat(dataPoint2.getValue()).isEqualTo(1L);

    QLStackedDataPoint stackedDataPoint2 = stackedData.getDataPoints().get(1);
    assertThat(stackedDataPoint2.getKey().getId()).isEqualTo(APP2_ID_ACCOUNT1);
    List<QLDataPoint> stackedDataPoint2Values = stackedDataPoint2.getValues();
    assertThat(stackedDataPoint2Values.size()).isEqualTo(1);

    QLDataPoint dataPoint4 = stackedDataPoint2Values.get(0);
    assertThat(dataPoint4.getKey().getId()).isEqualTo(SERVICE3_ID_APP2_ACCOUNT1);
    assertThat(dataPoint4.getValue()).isEqualTo(2L);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testAppEnvTwoLevelAggregation() {
    // One level Aggregation
    QLInstanceAggregation firstLevelAggregation =
        QLInstanceAggregation.builder().entityAggregation(QLInstanceEntityAggregation.Application).build();
    QLInstanceAggregation secondLevelAggregation =
        QLInstanceAggregation.builder().entityAggregation(QLInstanceEntityAggregation.Environment).build();
    QLData qlData = dataFetcher.fetch(
        ACCOUNT1_ID, null, null, newArrayList(firstLevelAggregation, secondLevelAggregation), null, null);
    QLStackedData stackedData = assertTwoLevelAggregatedData(qlData, 2);

    QLStackedDataPoint stackedDataPoint1 = stackedData.getDataPoints().get(0);
    assertThat(stackedDataPoint1.getKey().getId()).isEqualTo(APP1_ID_ACCOUNT1);
    List<QLDataPoint> stackedDataPoint1Values = stackedDataPoint1.getValues();
    assertThat(stackedDataPoint1Values.size()).isEqualTo(2);

    QLDataPoint dataPoint1 = stackedDataPoint1Values.get(0);
    assertThat(dataPoint1.getKey().getId()).isEqualTo(ENV1_ID_APP1_ACCOUNT1);
    assertThat(dataPoint1.getValue()).isEqualTo(2L);

    QLDataPoint dataPoint2 = stackedDataPoint1Values.get(1);
    assertThat(dataPoint2.getKey().getId()).isEqualTo(ENV2_ID_APP1_ACCOUNT1);
    assertThat(dataPoint2.getValue()).isEqualTo(2L);

    QLStackedDataPoint stackedDataPoint2 = stackedData.getDataPoints().get(1);
    assertThat(stackedDataPoint2.getKey().getId()).isEqualTo(APP2_ID_ACCOUNT1);
    List<QLDataPoint> stackedDataPoint2Values = stackedDataPoint2.getValues();
    assertThat(stackedDataPoint2Values.size()).isEqualTo(2);

    QLDataPoint dataPoint4 = stackedDataPoint2Values.get(0);
    assertThat(dataPoint4.getKey().getId()).isEqualTo(ENV3_ID_APP2_ACCOUNT1);
    assertThat(dataPoint4.getValue()).isEqualTo(1L);

    QLDataPoint dataPoint5 = stackedDataPoint2Values.get(1);
    assertThat(dataPoint5.getKey().getId()).isEqualTo(ENV4_ID_APP2_ACCOUNT1);
    assertThat(dataPoint5.getValue()).isEqualTo(1L);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testAppCloudProviderTwoLevelAggregation() {
    // One level Aggregation
    QLInstanceAggregation firstLevelAggregation =
        QLInstanceAggregation.builder().entityAggregation(QLInstanceEntityAggregation.Application).build();
    QLInstanceAggregation secondLevelAggregation =
        QLInstanceAggregation.builder().entityAggregation(QLInstanceEntityAggregation.CloudProvider).build();
    QLData qlData = dataFetcher.fetch(
        ACCOUNT1_ID, null, null, newArrayList(firstLevelAggregation, secondLevelAggregation), null, null);
    QLStackedData stackedData = assertTwoLevelAggregatedData(qlData, 2);

    QLStackedDataPoint stackedDataPoint1 = stackedData.getDataPoints().get(0);
    assertThat(stackedDataPoint1.getKey().getId()).isEqualTo(APP1_ID_ACCOUNT1);
    List<QLDataPoint> stackedDataPoint1Values = stackedDataPoint1.getValues();
    assertThat(stackedDataPoint1Values.size()).isEqualTo(2);

    QLDataPoint dataPoint1 = stackedDataPoint1Values.get(0);
    assertThat(dataPoint1.getKey().getId()).isEqualTo(CLOUD_PROVIDER1_ID_ACCOUNT1);
    assertThat(dataPoint1.getValue()).isEqualTo(2L);

    QLDataPoint dataPoint2 = stackedDataPoint1Values.get(1);
    assertThat(dataPoint2.getKey().getId()).isEqualTo(CLOUD_PROVIDER2_ID_ACCOUNT1);
    assertThat(dataPoint2.getValue()).isEqualTo(2L);

    QLStackedDataPoint stackedDataPoint2 = stackedData.getDataPoints().get(1);
    assertThat(stackedDataPoint2.getKey().getId()).isEqualTo(APP2_ID_ACCOUNT1);
    List<QLDataPoint> stackedDataPoint2Values = stackedDataPoint2.getValues();
    assertThat(stackedDataPoint2Values.size()).isEqualTo(2);

    QLDataPoint dataPoint4 = stackedDataPoint2Values.get(0);
    assertThat(dataPoint4.getKey().getId()).isEqualTo(CLOUD_PROVIDER1_ID_ACCOUNT1);
    assertThat(dataPoint4.getValue()).isEqualTo(1L);

    QLDataPoint dataPoint5 = stackedDataPoint2Values.get(1);
    assertThat(dataPoint5.getKey().getId()).isEqualTo(CLOUD_PROVIDER2_ID_ACCOUNT1);
    assertThat(dataPoint5.getValue()).isEqualTo(1L);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testAppInstanceTypeTwoLevelAggregation() {
    // One level Aggregation
    QLInstanceAggregation firstLevelAggregation =
        QLInstanceAggregation.builder().entityAggregation(QLInstanceEntityAggregation.Application).build();
    QLInstanceAggregation secondLevelAggregation =
        QLInstanceAggregation.builder().entityAggregation(QLInstanceEntityAggregation.InstanceType).build();
    QLData qlData = dataFetcher.fetch(
        ACCOUNT1_ID, null, null, newArrayList(firstLevelAggregation, secondLevelAggregation), null, null);
    QLStackedData stackedData = assertTwoLevelAggregatedData(qlData, 2);

    QLStackedDataPoint stackedDataPoint1 = stackedData.getDataPoints().get(0);
    assertThat(stackedDataPoint1.getKey().getId()).isEqualTo(APP1_ID_ACCOUNT1);
    List<QLDataPoint> stackedDataPoint1Values = stackedDataPoint1.getValues();
    assertThat(stackedDataPoint1Values.size()).isEqualTo(1);

    QLDataPoint dataPoint1 = stackedDataPoint1Values.get(0);
    assertThat(dataPoint1.getKey().getId()).isEqualTo(PHYSICAL_HOST_INSTANCE.name());
    assertThat(dataPoint1.getValue()).isEqualTo(4L);

    QLStackedDataPoint stackedDataPoint2 = stackedData.getDataPoints().get(1);
    assertThat(stackedDataPoint2.getKey().getId()).isEqualTo(APP2_ID_ACCOUNT1);
    List<QLDataPoint> stackedDataPoint2Values = stackedDataPoint2.getValues();
    assertThat(stackedDataPoint2Values.size()).isEqualTo(1);

    QLDataPoint dataPoint4 = stackedDataPoint2Values.get(0);
    assertThat(dataPoint4.getKey().getId()).isEqualTo(PHYSICAL_HOST_INSTANCE.name());
    assertThat(dataPoint4.getValue()).isEqualTo(2L);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testAppTagServiceTwoLevelAggregation() {
    // One level Aggregation
    QLInstanceAggregation firstLevelAggregation =
        QLInstanceAggregation.builder()
            .tagAggregation(
                QLInstanceTagAggregation.builder().tagName(TAG_TEAM).entityType(QLInstanceTagType.APPLICATION).build())
            .build();
    QLInstanceAggregation secondLevelAggregation =
        QLInstanceAggregation.builder().entityAggregation(QLInstanceEntityAggregation.Service).build();

    QLData qlData = dataFetcher.fetch(
        ACCOUNT1_ID, null, null, newArrayList(firstLevelAggregation, secondLevelAggregation), null, null);
    QLStackedData stackedData = assertTwoLevelAggregatedData(qlData, 2);

    QLStackedDataPoint stackedDataPoint1 = stackedData.getDataPoints().get(0);
    assertThat(stackedDataPoint1.getKey().getId()).isEqualTo(APP1_ID_ACCOUNT1);
    List<QLDataPoint> stackedDataPoint1Values = stackedDataPoint1.getValues();
    assertThat(stackedDataPoint1Values.size()).isEqualTo(2);

    QLDataPoint dataPoint1 = stackedDataPoint1Values.get(0);
    assertThat(dataPoint1.getKey().getId()).isEqualTo(SERVICE1_ID_APP1_ACCOUNT1);
    assertThat(dataPoint1.getValue()).isEqualTo(3L);

    QLDataPoint dataPoint2 = stackedDataPoint1Values.get(1);
    assertThat(dataPoint2.getKey().getId()).isEqualTo(SERVICE2_ID_APP1_ACCOUNT1);
    assertThat(dataPoint2.getValue()).isEqualTo(1L);

    QLStackedDataPoint stackedDataPoint2 = stackedData.getDataPoints().get(1);
    assertThat(stackedDataPoint2.getKey().getId()).isEqualTo(APP2_ID_ACCOUNT1);
    List<QLDataPoint> stackedDataPoint2Values = stackedDataPoint2.getValues();
    assertThat(stackedDataPoint2Values.size()).isEqualTo(1);

    QLDataPoint dataPoint4 = stackedDataPoint2Values.get(0);
    assertThat(dataPoint4.getKey().getId()).isEqualTo(SERVICE3_ID_APP2_ACCOUNT1);
    assertThat(dataPoint4.getValue()).isEqualTo(2L);

    QLData qlDataPostFetch =
        dataFetcher.postFetch(ACCOUNT1_ID, newArrayList(firstLevelAggregation, secondLevelAggregation), qlData);
    stackedData = assertTwoLevelAggregatedData(qlDataPostFetch, 2);

    stackedDataPoint1 = stackedData.getDataPoints().get(0);
    assertThat(stackedDataPoint1.getKey().getId()).isEqualTo(TAG_TEAM1);
    assertThat(stackedDataPoint1.getKey().getType()).isEqualTo("TAG");
    stackedDataPoint1Values = stackedDataPoint1.getValues();
    assertThat(stackedDataPoint1Values.size()).isEqualTo(2);

    dataPoint1 = stackedDataPoint1Values.get(0);
    assertThat(dataPoint1.getKey().getId()).isEqualTo(SERVICE1_ID_APP1_ACCOUNT1);
    assertThat(dataPoint1.getValue()).isEqualTo(3L);

    dataPoint2 = stackedDataPoint1Values.get(1);
    assertThat(dataPoint2.getKey().getId()).isEqualTo(SERVICE2_ID_APP1_ACCOUNT1);
    assertThat(dataPoint2.getValue()).isEqualTo(1L);

    stackedDataPoint2 = stackedData.getDataPoints().get(1);
    assertThat(stackedDataPoint2.getKey().getId()).isEqualTo(TAG_TEAM2);
    assertThat(stackedDataPoint2.getKey().getType()).isEqualTo("TAG");
    stackedDataPoint2Values = stackedDataPoint2.getValues();
    assertThat(stackedDataPoint2Values.size()).isEqualTo(1);

    dataPoint4 = stackedDataPoint2Values.get(0);
    assertThat(dataPoint4.getKey().getId()).isEqualTo(SERVICE3_ID_APP2_ACCOUNT1);
    assertThat(dataPoint4.getValue()).isEqualTo(2L);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testAppTagServiceTagTwoLevelAggregation() {
    // One level Aggregation
    QLInstanceAggregation firstLevelAggregation =
        QLInstanceAggregation.builder()
            .tagAggregation(
                QLInstanceTagAggregation.builder().tagName(TAG_TEAM).entityType(QLInstanceTagType.APPLICATION).build())
            .build();
    QLInstanceAggregation secondLevelAggregation =
        QLInstanceAggregation.builder()
            .tagAggregation(
                QLInstanceTagAggregation.builder().tagName(TAG_MODULE).entityType(QLInstanceTagType.SERVICE).build())
            .build();

    QLData qlData = dataFetcher.fetch(
        ACCOUNT1_ID, null, null, newArrayList(firstLevelAggregation, secondLevelAggregation), null, null);
    QLStackedData stackedData = assertTwoLevelAggregatedData(qlData, 2);

    QLStackedDataPoint stackedDataPoint1 = stackedData.getDataPoints().get(0);
    assertThat(stackedDataPoint1.getKey().getId()).isEqualTo(APP1_ID_ACCOUNT1);
    List<QLDataPoint> stackedDataPoint1Values = stackedDataPoint1.getValues();
    assertThat(stackedDataPoint1Values.size()).isEqualTo(2);

    QLDataPoint dataPoint1 = stackedDataPoint1Values.get(0);
    assertThat(dataPoint1.getKey().getId()).isEqualTo(SERVICE1_ID_APP1_ACCOUNT1);
    assertThat(dataPoint1.getValue()).isEqualTo(3L);

    QLDataPoint dataPoint2 = stackedDataPoint1Values.get(1);
    assertThat(dataPoint2.getKey().getId()).isEqualTo(SERVICE2_ID_APP1_ACCOUNT1);
    assertThat(dataPoint2.getValue()).isEqualTo(1L);

    QLStackedDataPoint stackedDataPoint2 = stackedData.getDataPoints().get(1);
    assertThat(stackedDataPoint2.getKey().getId()).isEqualTo(APP2_ID_ACCOUNT1);
    List<QLDataPoint> stackedDataPoint2Values = stackedDataPoint2.getValues();
    assertThat(stackedDataPoint2Values.size()).isEqualTo(1);

    QLDataPoint dataPoint4 = stackedDataPoint2Values.get(0);
    assertThat(dataPoint4.getKey().getId()).isEqualTo(SERVICE3_ID_APP2_ACCOUNT1);
    assertThat(dataPoint4.getValue()).isEqualTo(2L);

    QLData qlDataPostFetch =
        dataFetcher.postFetch(ACCOUNT1_ID, newArrayList(firstLevelAggregation, secondLevelAggregation), qlData);
    stackedData = assertTwoLevelAggregatedData(qlDataPostFetch, 2);

    stackedDataPoint1 = stackedData.getDataPoints().get(0);
    assertThat(stackedDataPoint1.getKey().getId()).isEqualTo(TAG_TEAM1);
    assertThat(stackedDataPoint1.getKey().getType()).isEqualTo("TAG");
    stackedDataPoint1Values = stackedDataPoint1.getValues();
    assertThat(stackedDataPoint1Values.size()).isEqualTo(1);

    dataPoint1 = stackedDataPoint1Values.get(0);
    assertThat(dataPoint1.getKey().getId()).isEqualTo(TAG_MODULE1);
    assertThat(dataPoint1.getKey().getType()).isEqualTo("TAG");
    assertThat(dataPoint1.getValue()).isEqualTo(4.0);

    stackedDataPoint2 = stackedData.getDataPoints().get(1);
    assertThat(stackedDataPoint2.getKey().getId()).isEqualTo(TAG_TEAM2);
    assertThat(stackedDataPoint2.getKey().getType()).isEqualTo("TAG");
    stackedDataPoint2Values = stackedDataPoint2.getValues();
    assertThat(stackedDataPoint2Values.size()).isEqualTo(1);

    dataPoint4 = stackedDataPoint2Values.get(0);
    assertThat(dataPoint4.getKey().getId()).isEqualTo(TAG_MODULE2);
    assertThat(dataPoint4.getKey().getType()).isEqualTo("TAG");
    assertThat(dataPoint4.getValue()).isEqualTo(2L);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testAppEnvTagTwoLevelAggregation() {
    // One level Aggregation
    QLInstanceAggregation firstLevelAggregation =
        QLInstanceAggregation.builder().entityAggregation(QLInstanceEntityAggregation.Application).build();

    QLInstanceAggregation secondLevelAggregation = QLInstanceAggregation.builder()
                                                       .tagAggregation(QLInstanceTagAggregation.builder()
                                                                           .tagName(TAG_ENVTYPE)
                                                                           .entityType(QLInstanceTagType.ENVIRONMENT)
                                                                           .build())
                                                       .build();

    QLData qlData = dataFetcher.fetch(
        ACCOUNT1_ID, null, null, newArrayList(firstLevelAggregation, secondLevelAggregation), null, null);
    QLStackedData stackedData = assertTwoLevelAggregatedData(qlData, 2);

    QLStackedDataPoint stackedDataPoint1 = stackedData.getDataPoints().get(0);
    assertThat(stackedDataPoint1.getKey().getId()).isEqualTo(APP1_ID_ACCOUNT1);
    List<QLDataPoint> stackedDataPoint1Values = stackedDataPoint1.getValues();
    assertThat(stackedDataPoint1Values.size()).isEqualTo(2);

    QLDataPoint dataPoint1 = stackedDataPoint1Values.get(0);
    assertThat(dataPoint1.getKey().getId()).isEqualTo(ENV1_ID_APP1_ACCOUNT1);
    assertThat(dataPoint1.getValue()).isEqualTo(2L);

    QLDataPoint dataPoint2 = stackedDataPoint1Values.get(1);
    assertThat(dataPoint2.getKey().getId()).isEqualTo(ENV2_ID_APP1_ACCOUNT1);
    assertThat(dataPoint2.getValue()).isEqualTo(2L);

    QLStackedDataPoint stackedDataPoint2 = stackedData.getDataPoints().get(1);
    assertThat(stackedDataPoint2.getKey().getId()).isEqualTo(APP2_ID_ACCOUNT1);
    List<QLDataPoint> stackedDataPoint2Values = stackedDataPoint2.getValues();
    assertThat(stackedDataPoint2Values.size()).isEqualTo(2);

    QLDataPoint dataPoint3 = stackedDataPoint2Values.get(0);
    assertThat(dataPoint3.getKey().getId()).isEqualTo(ENV3_ID_APP2_ACCOUNT1);
    assertThat(dataPoint3.getValue()).isEqualTo(1L);

    QLData qlDataPostFetch =
        dataFetcher.postFetch(ACCOUNT1_ID, newArrayList(firstLevelAggregation, secondLevelAggregation), qlData);
    QLStackedData stackedDataPostFetch = assertTwoLevelAggregatedData(qlDataPostFetch, 2);

    stackedDataPoint1 = stackedDataPostFetch.getDataPoints().get(0);
    assertThat(stackedDataPoint1.getKey().getId()).isEqualTo(APP1_ID_ACCOUNT1);
    stackedDataPoint1Values = stackedDataPoint1.getValues();
    assertThat(stackedDataPoint1Values.size()).isEqualTo(2);

    dataPoint1 = stackedDataPoint1Values.get(0);
    assertThat(dataPoint1.getKey().getId()).isEqualTo(TAG_PROD);
    assertThat(dataPoint1.getValue()).isEqualTo(2L);

    dataPoint2 = stackedDataPoint1Values.get(1);
    assertThat(dataPoint2.getKey().getId()).isEqualTo(TAG_NON_PROD);
    assertThat(dataPoint2.getValue()).isEqualTo(2L);

    stackedDataPoint2 = stackedDataPostFetch.getDataPoints().get(1);
    assertThat(stackedDataPoint2.getKey().getId()).isEqualTo(APP2_ID_ACCOUNT1);
    stackedDataPoint2Values = stackedDataPoint2.getValues();
    assertThat(stackedDataPoint2Values.size()).isEqualTo(2);

    dataPoint3 = stackedDataPoint2Values.get(0);
    assertThat(dataPoint3.getKey().getId()).isEqualTo(TAG_PROD);
    assertThat(dataPoint3.getValue()).isEqualTo(1L);

    QLDataPoint dataPoint4 = stackedDataPoint2Values.get(1);
    assertThat(dataPoint4.getKey().getId()).isEqualTo(TAG_NON_PROD);
    assertThat(dataPoint4.getValue()).isEqualTo(1L);
  }

  private QLStackedData assertTwoLevelAggregatedData(QLData qlData, int firstLevelCount) {
    assertThat(qlData).isInstanceOf(QLStackedData.class);
    QLStackedData stackedData = (QLStackedData) qlData;
    assertThat(stackedData).isNotNull();
    assertThat(stackedData.getDataPoints()).hasSize(firstLevelCount);
    return stackedData;
  }

  private QLAggregatedData assertOneLevelAggregatedData(QLData qlData, int count) {
    assertThat(qlData).isInstanceOf(QLAggregatedData.class);
    QLAggregatedData aggregatedData = (QLAggregatedData) qlData;
    assertThat(aggregatedData).isNotNull();
    assertThat(aggregatedData.getDataPoints()).hasSize(count);
    return aggregatedData;
  }

  private void assertSinglePointData(QLData qlData, long count) {
    assertTrue(qlData instanceof QLSinglePointData);
    QLSinglePointData singlePointData = (QLSinglePointData) qlData;
    assertThat(singlePointData).isNotNull();
    assertThat(singlePointData.getDataPoint()).isNotNull();
    assertThat(singlePointData.getDataPoint().getValue()).isEqualTo(count);
  }

  @Test
  @Owner(developers = ALEXANDRU_CIOFU)
  @Category(UnitTests.class)
  public void testEnvironmentTypeFilter() {
    instanceService.save(
        Instance.builder().accountId(ACCOUNT1_ID).appId(APP1_ID_ACCOUNT1).envType(EnvironmentType.PROD).build());
    instanceService.save(
        Instance.builder().accountId(ACCOUNT1_ID).appId(APP2_ID_ACCOUNT1).envType(EnvironmentType.NON_PROD).build());

    QLInstanceFilter envTypeFilter = QLInstanceFilter.builder()
                                         .environmentType(QLEnvironmentTypeFilter.builder()
                                                              .values(new QLEnvironmentType[] {QLEnvironmentType.PROD})
                                                              .operator(QLEnumOperator.IN)
                                                              .build())
                                         .build();
    QLData qlData = dataFetcher.fetch(ACCOUNT1_ID, null, newArrayList(envTypeFilter), null, null, null);

    assertSinglePointData(qlData, 5L);
  }

  @Test
  @Owner(developers = ALEXANDRU_CIOFU)
  @Category(UnitTests.class)
  public void testDeploynmentTypeFilter() {
    instanceService.save(Instance.builder().accountId(ACCOUNT1_ID).appId(APP1_ID_ACCOUNT1).serviceId("sId3").build());
    instanceService.save(Instance.builder().accountId(ACCOUNT1_ID).appId(APP2_ID_ACCOUNT1).serviceId("sId4").build());
    serviceResourceService.save(Service.builder()
                                    .name("serviceName3")
                                    .uuid("sId3")
                                    .accountId(ACCOUNT1_ID)
                                    .appId(APP1_ID_ACCOUNT1)
                                    .deploymentType(DeploymentType.SSH)
                                    .build());
    serviceResourceService.save(Service.builder()
                                    .name("serviceName4")
                                    .uuid("sId4")
                                    .accountId(ACCOUNT1_ID)
                                    .appId(APP2_ID_ACCOUNT1)
                                    .deploymentType(DeploymentType.KUBERNETES)
                                    .build());

    QLInstanceFilter deploymentTypeFilter =
        QLInstanceFilter.builder()
            .deploymentType(QLDeploymentTypeFilter.builder()
                                .values(new QLDeploymentType[] {QLDeploymentType.SSH})
                                .operator(QLEnumOperator.IN)
                                .build())
            .build();
    QLData qlData = dataFetcher.fetch(ACCOUNT1_ID, null, newArrayList(deploymentTypeFilter), null, null, null);
    assertSinglePointData(qlData, 1L);
  }

  @Test
  @Owner(developers = ALEXANDRU_CIOFU)
  @Category(UnitTests.class)
  public void testWorkflowTypeFilter() {
    instanceService.save(
        Instance.builder().accountId(ACCOUNT1_ID).appId(APP1_ID_ACCOUNT1).lastWorkflowExecutionId("wid1").build());
    instanceService.save(
        Instance.builder().accountId(ACCOUNT1_ID).appId(APP2_ID_ACCOUNT1).lastWorkflowExecutionId("wid2").build());
    workflowService.createWorkflow(
        WorkflowBuilder.aWorkflow()
            .accountId(ACCOUNT1_ID)
            .appId(APP1_ID_ACCOUNT1)
            .name(APP1_ID_ACCOUNT1)
            .orchestrationWorkflow(
                BuildWorkflow.BuildOrchestrationWorkflowBuilder.aBuildOrchestrationWorkflow().build())
            .uuid("wid1")
            .build());
    workflowService.createWorkflow(
        WorkflowBuilder.aWorkflow()
            .accountId(ACCOUNT1_ID)
            .appId(APP2_ID_ACCOUNT1)
            .name(APP2_ID_ACCOUNT1)
            .orchestrationWorkflow(
                BuildWorkflow.BuildOrchestrationWorkflowBuilder.aBuildOrchestrationWorkflow().build())
            .uuid("wid2")
            .build());

    QLInstanceFilter workflowTypeFilter =
        QLInstanceFilter.builder()
            .orchestrationWorkflowType(
                QLOrchestrationWorkflowTypeFilter.builder()
                    .values(new QLOrchestrationWorkflowType[] {QLOrchestrationWorkflowType.BUILD})
                    .operator(QLEnumOperator.IN)
                    .build())
            .build();
    QLData qlData = dataFetcher.fetch(ACCOUNT1_ID, null, newArrayList(workflowTypeFilter), null, null, null);
    assertSinglePointData(qlData, 2L);
  }

  @Test
  @Owner(developers = ALEXANDRU_CIOFU)
  @Category(UnitTests.class)
  public void testEnvTypeFilterDeploynmentTypeFilterWorkflowTypeFilter() {
    QLInstanceFilter envTypeFilter = QLInstanceFilter.builder()
                                         .environmentType(QLEnvironmentTypeFilter.builder()
                                                              .values(new QLEnvironmentType[] {QLEnvironmentType.PROD})
                                                              .operator(QLEnumOperator.IN)
                                                              .build())
                                         .build();
    QLInstanceFilter deploymentTypeFilter =
        QLInstanceFilter.builder()
            .deploymentType(QLDeploymentTypeFilter.builder()
                                .values(new QLDeploymentType[] {QLDeploymentType.KUBERNETES})
                                .operator(QLEnumOperator.IN)
                                .build())
            .build();
    QLInstanceFilter workflowTypeFilter =
        QLInstanceFilter.builder()
            .orchestrationWorkflowType(
                QLOrchestrationWorkflowTypeFilter.builder()
                    .values(new QLOrchestrationWorkflowType[] {QLOrchestrationWorkflowType.BUILD})
                    .operator(QLEnumOperator.IN)
                    .build())
            .build();
    QLData qlData = dataFetcher.fetch(
        ACCOUNT2_ID, null, newArrayList(envTypeFilter, deploymentTypeFilter, workflowTypeFilter), null, null, null);
    assertSinglePointData(qlData, 0L);
  }
}
