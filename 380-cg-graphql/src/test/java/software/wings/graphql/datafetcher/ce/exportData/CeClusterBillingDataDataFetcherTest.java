/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.ce.exportData;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.rule.OwnerRule.SHUBHANSHU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.ccm.cluster.dao.K8sWorkloadDao;
import io.harness.ccm.commons.entities.k8s.K8sWorkload;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.beans.User;
import software.wings.graphql.datafetcher.AbstractDataFetcherTestBase;
import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.graphql.datafetcher.ce.exportData.dto.QLCEAggregation;
import software.wings.graphql.datafetcher.ce.exportData.dto.QLCEAggregationFunction;
import software.wings.graphql.datafetcher.ce.exportData.dto.QLCECost;
import software.wings.graphql.datafetcher.ce.exportData.dto.QLCEData;
import software.wings.graphql.datafetcher.ce.exportData.dto.QLCEDataEntry.CEDataEntryKeys;
import software.wings.graphql.datafetcher.ce.exportData.dto.QLCEEcsEntity.CEEcsEntityKeys;
import software.wings.graphql.datafetcher.ce.exportData.dto.QLCEEntityGroupBy;
import software.wings.graphql.datafetcher.ce.exportData.dto.QLCEFilter;
import software.wings.graphql.datafetcher.ce.exportData.dto.QLCEFilterType;
import software.wings.graphql.datafetcher.ce.exportData.dto.QLCEGroupBy;
import software.wings.graphql.datafetcher.ce.exportData.dto.QLCEHarnessEntity.CEHarnessEntityKeys;
import software.wings.graphql.datafetcher.ce.exportData.dto.QLCEK8sEntity.CEK8sEntityKeys;
import software.wings.graphql.datafetcher.ce.exportData.dto.QLCELabelAggregation;
import software.wings.graphql.datafetcher.ce.exportData.dto.QLCESort;
import software.wings.graphql.datafetcher.ce.exportData.dto.QLCESortType;
import software.wings.graphql.datafetcher.ce.exportData.dto.QLCETagAggregation;
import software.wings.graphql.datafetcher.ce.exportData.dto.QLCETagFilter;
import software.wings.graphql.datafetcher.ce.exportData.dto.QLCETagType;
import software.wings.graphql.datafetcher.tag.TagHelper;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;
import software.wings.graphql.schema.type.aggregation.QLSortOrder;
import software.wings.graphql.schema.type.aggregation.QLTimeFilter;
import software.wings.graphql.schema.type.aggregation.QLTimeOperator;
import software.wings.graphql.schema.type.aggregation.tag.QLTagInput;
import software.wings.security.UserThreadLocal;
import software.wings.service.intfc.ce.CeAccountExpirationChecker;

import com.google.inject.Inject;
import graphql.execution.MergedSelectionSet;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingFieldSelectionSet;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.SelectedField;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.stubbing.Answer;

@TargetModule(HarnessModule._375_CE_GRAPHQL)
@OwnedBy(CE)
public class CeClusterBillingDataDataFetcherTest extends AbstractDataFetcherTestBase {
  @Mock TimeScaleDBService timeScaleDBService;
  @Mock private DataFetcherUtils utils;
  @Mock TagHelper tagHelper;
  @Mock private DataFetchingEnvironment environment;
  @Mock private K8sWorkloadDao k8sWorkloadDao;
  @Mock CeAccountExpirationChecker accountChecker;
  @Mock CEExportDataNodeAndPodDetailsHelper nodeAndPodDetailsHelper;
  @InjectMocks CEExportDataQueryBuilder queryBuilder;
  @Inject @InjectMocks CeClusterBillingDataDataFetcher ceClusterBillingDataDataFetcher;

  @Mock Statement statement;
  @Mock ResultSet resultSet;

  final int[] count = {0};
  final double[] doubleVal = {0};
  final long currentTime = System.currentTimeMillis();
  final long[] calendar = {currentTime};
  private static Integer LIMIT = Integer.MAX_VALUE - 1;
  private static Integer OFFSET = 0;
  private static boolean INCLUDE_OTHERS = true;
  private static boolean SKIP_ROUND_OFF = false;
  private static long ONE_DAY_MILLIS = 86400000;
  private List<String> selectedFields = new ArrayList<>();
  private static final String SELECT = "select";

  private static final DataFetchingFieldSelectionSet mockSelectionSet = new DataFetchingFieldSelectionSet() {
    public MergedSelectionSet get() {
      return MergedSelectionSet.newMergedSelectionSet().build();
    }
    public Map<String, Map<String, Object>> getArguments() {
      Map<String, Map<String, Object>> returnArguments = new HashMap<>();
      Map<String, Object> selectedLabels = new HashMap<>();
      selectedLabels.put("labels", Collections.singleton(LABEL_NAME));
      returnArguments.put(SELECT, selectedLabels);
      return returnArguments;
    }
    public Map<String, GraphQLFieldDefinition> getDefinitions() {
      return Collections.emptyMap();
    }
    public boolean contains(String fieldGlobPattern) {
      return false;
    }
    public SelectedField getField(String fieldName) {
      return null;
    }
    public List<SelectedField> getFields() {
      return Collections.emptyList();
    }
    public List<SelectedField> getFields(String fieldGlobPattern) {
      return Collections.emptyList();
    }
  };

  @Before
  public void setup() throws SQLException {
    User user = testUtils.createUser(testUtils.createAccount());
    UserThreadLocal.set(user);

    // Account1
    createAccount(ACCOUNT1_ID, getLicenseInfo());
    createApp(ACCOUNT1_ID, APP1_ID_ACCOUNT1, APP1_ID_ACCOUNT1, TAG_TEAM, TAG_VALUE_TEAM1);
    Map<String, String> labels = new HashMap<>();
    labels.put(LABEL_NAME, LABEL_VALUE);
    k8sWorkloadDao.save(getTestWorkload(WORKLOAD_NAME_ACCOUNT1, labels));
    Set<String> workloadNames = new HashSet<>();
    workloadNames.add(WORKLOAD_NAME_ACCOUNT1);
    when(k8sWorkloadDao.list(ACCOUNT1_ID, workloadNames))
        .thenReturn(Collections.singletonList(getTestWorkload(WORKLOAD_NAME_ACCOUNT1, labels)));
    populateSelectedFields(selectedFields);
    Connection mockConnection = mock(Connection.class);
    Statement mockStatement = mock(Statement.class);
    when(timeScaleDBService.getDBConnection()).thenReturn(mockConnection);
    when(timeScaleDBService.isValid()).thenReturn(true);
    when(mockConnection.createStatement()).thenReturn(mockStatement);
    when(mockStatement.executeQuery(anyString())).thenReturn(resultSet);
    doNothing().when(accountChecker).checkIsCeEnabled(anyString());
    when(nodeAndPodDetailsHelper.isNodeAndPodQuery(anyList())).thenReturn(false);
    mockResultSet();
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testGetBillingTrendWhenDbIsInvalid() {
    when(timeScaleDBService.isValid()).thenReturn(false);
    List<QLCEAggregation> aggregationFunction = Arrays.asList(makeCostAggregation(QLCECost.TOTALCOST));
    assertThatThrownBy(()
                           -> ceClusterBillingDataDataFetcher.fetchSelectedFields(ACCOUNT1_ID, aggregationFunction,
                               Collections.EMPTY_LIST, Collections.EMPTY_LIST, Collections.EMPTY_LIST, LIMIT, OFFSET,
                               SKIP_ROUND_OFF, environment))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testFetchMethodInBillingDataDataFetcher() {
    Long filterTime = 0L;

    List<QLCEAggregation> aggregationFunction = Arrays.asList(makeCostAggregation(QLCECost.TOTALCOST));
    List<QLCEFilter> filters = new ArrayList<>();
    filters.add(makeStartTimeFilter(filterTime));
    filters.add(makeEndTimeFilter(ONE_DAY_MILLIS));
    filters.add(makeEntityFilter(new String[] {APP1_ID_ACCOUNT1}, QLCEFilterType.Application));
    filters.add(makeEntityFilter(new String[] {SERVICE1_ID_APP1_ACCOUNT1}, QLCEFilterType.Service));
    filters.add(makeEntityFilter(new String[] {ENV1_ID_APP1_ACCOUNT1}, QLCEFilterType.Environment));

    List<QLCEGroupBy> groupBy = Arrays.asList(makeEntityGroupBy(QLCEEntityGroupBy.Application),
        makeEntityGroupBy(QLCEEntityGroupBy.Service), makeEntityGroupBy(QLCEEntityGroupBy.Environment));
    List<QLCESort> sortCriteria = Arrays.asList(makeSortingCriteria(QLSortOrder.DESCENDING, QLCESortType.TOTALCOST));

    QLCEData data = (QLCEData) ceClusterBillingDataDataFetcher.fetchSelectedFields(
        ACCOUNT1_ID, aggregationFunction, filters, groupBy, sortCriteria, LIMIT, OFFSET, SKIP_ROUND_OFF, environment);

    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getHarness().getApplication()).isEqualTo(APP1_ID_ACCOUNT1);
    assertThat(data.getData().get(0).getHarness().getService()).isEqualTo(SERVICE1_ID_APP1_ACCOUNT1);
    assertThat(data.getData().get(0).getHarness().getEnvironment()).isEqualTo(ENV1_ID_APP1_ACCOUNT1);
    assertThat(data.getData().get(0).getTotalCost()).isEqualTo(10.0);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testFetchMethodForKubernetesEntitiesInBillingDataDataFetcher() {
    Long filterTime = 0L;

    List<QLCEAggregation> aggregationFunction = new ArrayList<>();
    aggregationFunction.add(makeCostAggregation(QLCECost.IDLECOST));
    aggregationFunction.add(makeCostAggregation(QLCECost.TOTALCOST));
    aggregationFunction.add(makeCostAggregation(QLCECost.UNALLOCATEDCOST));
    List<QLCEFilter> filters = new ArrayList<>();
    filters.add(makeStartTimeFilter(filterTime));
    filters.add(makeEndTimeFilter(ONE_DAY_MILLIS));
    filters.add(makeEntityFilter(new String[] {CLUSTER1_ID}, QLCEFilterType.Cluster));
    filters.add(makeEntityFilter(new String[] {NAMESPACE1}, QLCEFilterType.Namespace));
    filters.add(makeEntityFilter(new String[] {WORKLOAD_NAME_ACCOUNT1}, QLCEFilterType.Workload));
    filters.add(makeEntityFilter(new String[] {}, QLCEFilterType.Workload));
    filters.add(makeEntityFilter(new String[] {""}, QLCEFilterType.Node));

    List<QLCEGroupBy> groupBy =
        Arrays.asList(makeEntityGroupBy(QLCEEntityGroupBy.Cluster), makeEntityGroupBy(QLCEEntityGroupBy.Namespace),
            makeEntityGroupBy(QLCEEntityGroupBy.Workload), makeEntityGroupBy(QLCEEntityGroupBy.Node));
    List<QLCESort> sortCriteria = Arrays.asList(makeSortingCriteria(QLSortOrder.DESCENDING, QLCESortType.IDLECOST));

    QLCEData data = (QLCEData) ceClusterBillingDataDataFetcher.fetchSelectedFields(
        ACCOUNT1_ID, aggregationFunction, filters, groupBy, sortCriteria, LIMIT, OFFSET, SKIP_ROUND_OFF, environment);

    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getCluster()).isEqualTo(CLUSTER1_ID);
    assertThat(data.getData().get(0).getK8s().getNamespace()).isEqualTo(NAMESPACE1);
    assertThat(data.getData().get(0).getK8s().getWorkload()).isEqualTo(WORKLOAD_NAME_ACCOUNT1);
    assertThat(data.getData().get(0).getK8s().getNode()).isEqualTo("");
    assertThat(data.getData().get(0).getTotalCost()).isEqualTo(10.0);
    assertThat(data.getData().get(0).getIdleCost()).isEqualTo(5.0);
    assertThat(data.getData().get(0).getUnallocatedCost()).isEqualTo(4.0);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testFetchMethodForEcsEntitiesInBillingDataDataFetcher() {
    Long filterTime = 0L;

    List<QLCEAggregation> aggregationFunction = new ArrayList<>();
    aggregationFunction.add(makeCostAggregation(QLCECost.IDLECOST));
    aggregationFunction.add(makeCostAggregation(QLCECost.TOTALCOST));
    aggregationFunction.add(makeCostAggregation(QLCECost.UNALLOCATEDCOST));
    List<QLCEFilter> filters = new ArrayList<>();
    filters.add(makeStartTimeFilter(filterTime));
    filters.add(makeEndTimeFilter(ONE_DAY_MILLIS));
    filters.add(makeEntityFilter(new String[] {CLUSTER1_ID}, QLCEFilterType.Cluster));
    filters.add(makeEntityFilter(new String[] {CLOUD_SERVICE_NAME_ACCOUNT1}, QLCEFilterType.EcsService));
    filters.add(makeEntityFilter(new String[] {LAUNCH_TYPE1}, QLCEFilterType.LaunchType));
    filters.add(makeEntityFilter(new String[] {TASK1}, QLCEFilterType.Task));

    List<QLCEGroupBy> groupBy = Arrays.asList(makeEntityGroupBy(QLCEEntityGroupBy.EcsService),
        makeEntityGroupBy(QLCEEntityGroupBy.LaunchType), makeEntityGroupBy(QLCEEntityGroupBy.Task));
    List<QLCESort> sortCriteria =
        Arrays.asList(makeSortingCriteria(QLSortOrder.DESCENDING, QLCESortType.UNALLOCATEDCOST));

    QLCEData data = (QLCEData) ceClusterBillingDataDataFetcher.fetchSelectedFields(
        ACCOUNT1_ID, aggregationFunction, filters, groupBy, sortCriteria, LIMIT, OFFSET, SKIP_ROUND_OFF, environment);

    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getEcs().getService()).isEqualTo(CLOUD_SERVICE_NAME_ACCOUNT1);
    assertThat(data.getData().get(0).getEcs().getLaunchType()).isEqualTo(LAUNCH_TYPE1);
    assertThat(data.getData().get(0).getEcs().getTaskId()).isEqualTo(TASK1);
    assertThat(data.getData().get(0).getTotalCost()).isEqualTo(10.0);
    assertThat(data.getData().get(0).getIdleCost()).isEqualTo(5.0);
    assertThat(data.getData().get(0).getUnallocatedCost()).isEqualTo(4.0);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testFetchMethodForGroupByNoneInBillingDataDataFetcher() {
    Long filterTime = 0L;

    List<QLCEAggregation> aggregationFunction = new ArrayList<>();
    aggregationFunction.add(makeCostAggregation(QLCECost.IDLECOST));
    aggregationFunction.add(makeCostAggregation(QLCECost.TOTALCOST));
    aggregationFunction.add(makeCostAggregation(QLCECost.UNALLOCATEDCOST));
    List<QLCEFilter> filters = new ArrayList<>();
    filters.add(makeStartTimeFilter(filterTime));
    filters.add(makeEndTimeFilter(ONE_DAY_MILLIS));

    List<QLCESort> sortCriteria =
        Arrays.asList(makeSortingCriteria(QLSortOrder.DESCENDING, QLCESortType.UNALLOCATEDCOST));

    QLCEData data = ceClusterBillingDataDataFetcher.getData(ACCOUNT1_ID, filters, aggregationFunction,
        Collections.emptyList(), sortCriteria, LIMIT, OFFSET, SKIP_ROUND_OFF, selectedFields, Collections.emptyList());

    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getCluster()).isEqualTo(CLUSTER1_ID);
    assertThat(data.getData().get(0).getTotalCost()).isEqualTo(10.0);
    assertThat(data.getData().get(0).getIdleCost()).isEqualTo(5.0);
    assertThat(data.getData().get(0).getUnallocatedCost()).isEqualTo(4.0);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testFetchMethodForGroupByTagInBillingDataDataFetcher() {
    Long filterTime = 0L;

    List<QLCEAggregation> aggregationFunction = new ArrayList<>();
    aggregationFunction.add(makeCostAggregation(QLCECost.IDLECOST));
    aggregationFunction.add(makeCostAggregation(QLCECost.TOTALCOST));
    aggregationFunction.add(makeCostAggregation(QLCECost.UNALLOCATEDCOST));
    List<QLCEFilter> filters = new ArrayList<>();
    filters.add(makeStartTimeFilter(filterTime));
    filters.add(makeEndTimeFilter(ONE_DAY_MILLIS));

    List<QLCEGroupBy> groupBy = Arrays.asList(makeLabelGroupBy(LABEL_NAME));

    List<QLCESort> sortCriteria =
        Arrays.asList(makeSortingCriteria(QLSortOrder.DESCENDING, QLCESortType.UNALLOCATEDCOST));

    QLCEData data = (QLCEData) ceClusterBillingDataDataFetcher.fetchSelectedFields(ACCOUNT1_ID, aggregationFunction,
        filters, Collections.emptyList(), sortCriteria, LIMIT, OFFSET, SKIP_ROUND_OFF, environment);

    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getTotalCost()).isEqualTo(10.0);
    assertThat(data.getData().get(0).getIdleCost()).isEqualTo(5.0);
    assertThat(data.getData().get(0).getUnallocatedCost()).isEqualTo(4.0);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testFetchMethodForGroupByLabelInBillingDataDataFetcher() {
    Long filterTime = 0L;

    List<QLCEAggregation> aggregationFunction = new ArrayList<>();
    aggregationFunction.add(makeCostAggregation(QLCECost.IDLECOST));
    aggregationFunction.add(makeCostAggregation(QLCECost.TOTALCOST));
    aggregationFunction.add(makeCostAggregation(QLCECost.UNALLOCATEDCOST));
    List<QLCEFilter> filters = new ArrayList<>();
    filters.add(makeStartTimeFilter(filterTime));
    filters.add(makeEndTimeFilter(ONE_DAY_MILLIS));

    List<QLCEGroupBy> groupBy = Arrays.asList(makeLabelGroupBy(LABEL_NAME));

    List<QLCESort> sortCriteria =
        Arrays.asList(makeSortingCriteria(QLSortOrder.DESCENDING, QLCESortType.UNALLOCATEDCOST));

    QLCEData data = (QLCEData) ceClusterBillingDataDataFetcher.fetchSelectedFields(
        ACCOUNT1_ID, aggregationFunction, filters, groupBy, sortCriteria, LIMIT, OFFSET, SKIP_ROUND_OFF, environment);

    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getK8s().getWorkload()).isEqualTo(WORKLOAD_NAME_ACCOUNT1);
    assertThat(data.getData().get(0).getTotalCost()).isEqualTo(10.0);
    assertThat(data.getData().get(0).getIdleCost()).isEqualTo(5.0);
    assertThat(data.getData().get(0).getUnallocatedCost()).isEqualTo(4.0);

    data = (QLCEData) ceClusterBillingDataDataFetcher.postFetch(
        ACCOUNT1_ID, groupBy, aggregationFunction, sortCriteria, data, LIMIT, INCLUDE_OTHERS);
    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getLabelName()).isEqualTo(LABEL_NAME);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testFetchMethodForSelectLabelsInBillingDataDataFetcher() {
    Long filterTime = 0L;

    List<QLCEAggregation> aggregationFunction = new ArrayList<>();
    aggregationFunction.add(makeCostAggregation(QLCECost.TOTALCOST));
    List<QLCEFilter> filters = new ArrayList<>();
    filters.add(makeStartTimeFilter(filterTime));
    filters.add(makeEndTimeFilter(ONE_DAY_MILLIS));
    filters.add(makeEntityFilter(new String[] {NAMESPACE1}, QLCEFilterType.Namespace));
    filters.add(makeEntityFilter(new String[] {WORKLOAD_NAME_ACCOUNT1}, QLCEFilterType.Workload));

    List<QLCEGroupBy> groupBy =
        Arrays.asList(makeEntityGroupBy(QLCEEntityGroupBy.Namespace), makeEntityGroupBy(QLCEEntityGroupBy.Workload));
    List<QLCESort> sortCriteria = Arrays.asList(makeSortingCriteria(QLSortOrder.DESCENDING, QLCESortType.TOTALCOST));

    QLCEData data = ceClusterBillingDataDataFetcher.getData(ACCOUNT1_ID, filters, aggregationFunction, groupBy,
        sortCriteria, LIMIT, OFFSET, SKIP_ROUND_OFF, Collections.emptyList(), Collections.singletonList(LABEL_NAME));

    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getK8s().getNamespace()).isEqualTo(NAMESPACE1);
    assertThat(data.getData().get(0).getK8s().getWorkload()).isEqualTo(WORKLOAD_NAME_ACCOUNT1);
    assertThat(data.getData().get(0).getK8s().getSelectedLabels().get(0).getName()).isEqualTo(LABEL_NAME);
    assertThat(data.getData().get(0).getK8s().getSelectedLabels().get(0).getValue()).isEqualTo(LABEL_VALUE);
    assertThat(data.getData().get(0).getK8s().getNode()).isEqualTo("");
    assertThat(data.getData().get(0).getTotalCost()).isEqualTo(10.0);
  }

  private void mockResultSet() throws SQLException {
    Connection connection = mock(Connection.class);
    statement = mock(Statement.class);
    resultSet = mock(ResultSet.class);
    when(timeScaleDBService.getDBConnection()).thenReturn(connection);
    when(connection.createStatement()).thenReturn(statement);
    when(statement.executeQuery(anyString())).thenReturn(resultSet);
    when(environment.getSelectionSet()).thenReturn(mockSelectionSet);

    when(resultSet.getDouble("BILLINGAMOUNT")).thenAnswer((Answer<Double>) invocation -> 10.0);
    when(resultSet.getDouble("COST")).thenAnswer((Answer<Double>) invocation -> 10.0);
    when(resultSet.getDouble("ACTUALIDLECOST")).thenAnswer((Answer<Double>) invocation -> 5.0);
    when(resultSet.getDouble("UNALLOCATEDCOST")).thenAnswer((Answer<Double>) invocation -> 4.0);
    when(resultSet.getDouble("SYSTEMCOST")).thenAnswer((Answer<Double>) invocation -> 2.0);
    when(resultSet.getDouble("MAXCPUUTILIZATION")).thenAnswer((Answer<Double>) invocation -> 0.5);
    when(resultSet.getDouble("MAXMEMORYUTILIZATION")).thenAnswer((Answer<Double>) invocation -> 0.5);
    when(resultSet.getDouble("AVGCPUUTILIZATION")).thenAnswer((Answer<Double>) invocation -> 0.4);
    when(resultSet.getDouble("AVGMEMORYUTILIZATION")).thenAnswer((Answer<Double>) invocation -> 0.4);
    when(resultSet.getDouble("CPUREQUEST")).thenAnswer((Answer<Double>) invocation -> 1024.0);
    when(resultSet.getDouble("MEMORYREQUEST")).thenAnswer((Answer<Double>) invocation -> 2048.0);
    when(resultSet.getDouble("CPULIMIT")).thenAnswer((Answer<Double>) invocation -> 1024.0);
    when(resultSet.getDouble("MEMORYLIMIT")).thenAnswer((Answer<Double>) invocation -> 2048.0);
    when(resultSet.getString("APPID")).thenAnswer((Answer<String>) invocation -> APP1_ID_ACCOUNT1);
    when(resultSet.getString("SERVICEID")).thenAnswer((Answer<String>) invocation -> SERVICE1_ID_APP1_ACCOUNT1);
    when(resultSet.getString("ENVID")).thenAnswer((Answer<String>) invocation -> ENV1_ID_APP1_ACCOUNT1);
    when(resultSet.getString("WORKLOADNAME")).thenAnswer((Answer<String>) invocation -> WORKLOAD_NAME_ACCOUNT1);
    when(resultSet.getString("WORKLOADTYPE")).thenAnswer((Answer<String>) invocation -> WORKLOAD_TYPE_ACCOUNT1);
    when(resultSet.getString("CLUSTERTYPE")).thenAnswer((Answer<String>) invocation -> CLUSTER_TYPE1);
    when(resultSet.getString("CLUSTERID")).thenAnswer((Answer<String>) invocation -> CLUSTER1_ID);
    when(resultSet.getString("CLUSTERNAME")).thenAnswer((Answer<String>) invocation -> CLUSTER1_NAME);
    when(resultSet.getString("REGION")).thenAnswer((Answer<String>) invocation -> REGION1);
    when(resultSet.getString("NAMESPACE")).thenAnswer((Answer<String>) invocation -> NAMESPACE1);
    when(resultSet.getString("CLOUDSERVICENAME"))
        .thenAnswer((Answer<String>) invocation -> CLOUD_SERVICE_NAME_ACCOUNT1);
    when(resultSet.getString("LAUNCHTYPE")).thenAnswer((Answer<String>) invocation -> LAUNCH_TYPE1);
    when(resultSet.getString("TASKID")).thenAnswer((Answer<String>) invocation -> TASK1);
    when(resultSet.getString("INSTANCEID"))
        .thenAnswer((Answer<String>) invocation -> INSTANCE1_SERVICE1_ENV1_APP1_ACCOUNT1);
    when(resultSet.getString("INSTANCETYPE")).thenAnswer((Answer<String>) invocation -> "K8S_NODE");

    when(resultSet.getTimestamp(
             CEExportDataQueryMetadata.CEExportDataMetadataFields.STARTTIME.getFieldName(), utils.getDefaultCalendar()))
        .thenAnswer((Answer<Timestamp>) invocation -> {
          calendar[0] = calendar[0] + 3600000;
          return new Timestamp(calendar[0]);
        });
    returnResultSet(1);
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

  private K8sWorkload getTestWorkload(String workloadName, Map<String, String> labels) {
    return K8sWorkload.builder()
        .accountId(ACCOUNT1_ID)
        .clusterId(CLUSTER1_ID)
        .settingId(SETTING_ID1)
        .kind("WORKLOAD_KIND")
        .labels(labels)
        .name(workloadName)
        .namespace(NAMESPACE1)
        .uid("UID")
        .uuid("UUID")
        .build();
  }

  private void populateSelectedFields(List<String> selectedFields) {
    selectedFields.add(CEHarnessEntityKeys.application);
    selectedFields.add(CEHarnessEntityKeys.service);
    selectedFields.add(CEHarnessEntityKeys.environment);
    selectedFields.add(CEDataEntryKeys.totalCost);
    selectedFields.add(CEDataEntryKeys.idleCost);
    selectedFields.add(CEDataEntryKeys.unallocatedCost);
    selectedFields.add(CEDataEntryKeys.systemCost);
    selectedFields.add(CEDataEntryKeys.avgCpuUtilization);
    selectedFields.add(CEDataEntryKeys.avgMemoryUtilization);
    selectedFields.add(CEDataEntryKeys.cpuRequest);
    selectedFields.add(CEDataEntryKeys.cpuLimit);
    selectedFields.add(CEDataEntryKeys.memoryLimit);
    selectedFields.add(CEDataEntryKeys.memoryRequest);
    selectedFields.add(CEDataEntryKeys.region);
    selectedFields.add(CEDataEntryKeys.instanceType);
    selectedFields.add(CEDataEntryKeys.clusterType);
    selectedFields.add(CEDataEntryKeys.cluster);
    selectedFields.add(CEDataEntryKeys.startTime);
    selectedFields.add(CEEcsEntityKeys.service);
    selectedFields.add(CEEcsEntityKeys.launchType);
    selectedFields.add(CEEcsEntityKeys.taskId);
    selectedFields.add(CEK8sEntityKeys.workload);
    selectedFields.add(CEK8sEntityKeys.namespace);
    selectedFields.add(CEK8sEntityKeys.node);
    selectedFields.add(CEK8sEntityKeys.pod);
  }

  public QLCEAggregation makeCostAggregation(QLCECost costType) {
    return QLCEAggregation.builder().function(QLCEAggregationFunction.SUM).cost(costType).build();
  }

  public QLCEGroupBy makeEntityGroupBy(QLCEEntityGroupBy entity) {
    return QLCEGroupBy.builder().entity(entity).build();
  }

  public QLCEGroupBy makeTagGroupBy(QLCETagType entityType, String tagName) {
    QLCETagAggregation tagAggregation = QLCETagAggregation.builder().tagName(tagName).entityType(entityType).build();
    return QLCEGroupBy.builder().tagAggregation(tagAggregation).build();
  }

  private QLCEGroupBy makeLabelGroupBy(String labelName) {
    return QLCEGroupBy.builder().labelAggregation(QLCELabelAggregation.builder().name(labelName).build()).build();
  }

  public QLCEFilter makeStartTimeFilter(Long filterTime) {
    QLTimeFilter timeFilter = QLTimeFilter.builder().operator(QLTimeOperator.AFTER).value(filterTime).build();
    return QLCEFilter.builder().startTime(timeFilter).build();
  }

  public QLCEFilter makeEndTimeFilter(Long filterTime) {
    QLTimeFilter timeFilter = QLTimeFilter.builder().operator(QLTimeOperator.BEFORE).value(filterTime).build();
    return QLCEFilter.builder().endTime(timeFilter).build();
  }

  public QLCEFilter makeEntityFilter(String[] values, QLCEFilterType filterType) {
    QLIdFilter filter = QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(values).build();
    switch (filterType) {
      case Application:
        return QLCEFilter.builder().application(filter).build();
      case Service:
        return QLCEFilter.builder().service(filter).build();
      case Environment:
        return QLCEFilter.builder().environment(filter).build();
      case Node:
        return QLCEFilter.builder().node(filter).build();
      case Pod:
        return QLCEFilter.builder().pod(filter).build();
      case InstanceType:
        return QLCEFilter.builder().instanceType(filter).build();
      case EcsService:
        return QLCEFilter.builder().ecsService(filter).build();
      case LaunchType:
        return QLCEFilter.builder().launchType(filter).build();
      case Task:
        return QLCEFilter.builder().task(filter).build();
      case Workload:
        return QLCEFilter.builder().workload(filter).build();
      case Namespace:
        return QLCEFilter.builder().namespace(filter).build();
      case Cluster:
        return QLCEFilter.builder().cluster(filter).build();
      default:
        return null;
    }
  }

  public QLCEFilter makeTagFilter(String tagName, String tagValue, QLCETagType entityType) {
    List<QLTagInput> tagInput = Arrays.asList(QLTagInput.builder().name(tagName).value(tagValue).build());
    return QLCEFilter.builder().tag(QLCETagFilter.builder().tags(tagInput).entityType(entityType).build()).build();
  }

  public QLCESort makeSortingCriteria(QLSortOrder order, QLCESortType type) {
    return QLCESort.builder().order(order).sortType(type).build();
  }
}
