/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.billing;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.rule.OwnerRule.SHUBHANSHU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.ccm.cluster.dao.K8sWorkloadDao;
import io.harness.ccm.cluster.entities.K8sLabelFilter;
import io.harness.ccm.commons.entities.batch.InstanceData;
import io.harness.ccm.commons.entities.k8s.K8sWorkload;
import io.harness.ccm.commons.service.impl.InstanceDataServiceImpl;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.beans.HarnessTag;
import software.wings.beans.User;
import software.wings.graphql.datafetcher.AbstractDataFetcherTestBase;
import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;
import software.wings.graphql.schema.type.aggregation.QLSortOrder;
import software.wings.graphql.schema.type.aggregation.QLTimeFilter;
import software.wings.graphql.schema.type.aggregation.QLTimeOperator;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingDataFilter;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingDataLabelAggregation;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingDataTagAggregation;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingSortCriteria;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingSortType;
import software.wings.graphql.schema.type.aggregation.billing.QLCCMEntityGroupBy;
import software.wings.graphql.schema.type.aggregation.billing.QLCCMGroupBy;
import software.wings.graphql.schema.type.aggregation.billing.QLFilterValuesListData;
import software.wings.security.UserThreadLocal;
import software.wings.service.intfc.HarnessTagService;
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
public class BillingStatsFilterValuesDataFetcherTest extends AbstractDataFetcherTestBase {
  @Mock TimeScaleDBService timeScaleDBService;
  @Mock private DataFetcherUtils utils;
  @Mock private DataFetchingEnvironment environment;
  @Mock InstanceDataServiceImpl instanceDataService;
  @Mock K8sWorkloadDao k8sWorkloadDao;
  @Mock HarnessTagService harnessTagService;
  @Mock CeAccountExpirationChecker accountChecker;

  @Inject @InjectMocks BillingStatsFilterValuesDataFetcher billingStatsFilterValuesDataFetcher;

  @Mock Statement statement;
  @Mock ResultSet resultSet;

  private static final DataFetchingFieldSelectionSet mockSelectionSet = new DataFetchingFieldSelectionSet() {
    public MergedSelectionSet get() {
      return MergedSelectionSet.newMergedSelectionSet().build();
    }
    public Map<String, Map<String, Object>> getArguments() {
      return Collections.emptyMap();
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
      return Collections.singletonList(selectedField);
    }
    public List<SelectedField> getFields(String fieldGlobPattern) {
      return Collections.emptyList();
    }
  };

  private static final SelectedField selectedField = new SelectedField() {
    @Override
    public String getName() {
      return "total";
    }
    @Override
    public String getQualifiedName() {
      return null;
    }
    @Override
    public GraphQLFieldDefinition getFieldDefinition() {
      return null;
    }
    @Override
    public Map<String, Object> getArguments() {
      return null;
    }
    @Override
    public DataFetchingFieldSelectionSet getSelectionSet() {
      return null;
    }
  };

  final int[] count = {0};
  final double[] doubleVal = {0};
  private static final int LIMIT = 100;
  private static final int OFFSET = 0;
  private static final boolean SKIP_ROUND_OFF = false;
  private static final long currentTime = System.currentTimeMillis();

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
    K8sLabelFilter filter = K8sLabelFilter.builder()
                                .accountId(ACCOUNT1_ID)
                                .startTime(currentTime - 100)
                                .endTime(currentTime + 100)
                                .limit(1)
                                .offset(0)
                                .build();
    when(k8sWorkloadDao.listLabelKeys(filter)).thenReturn(Arrays.asList(LABEL_NAME));
    filter.setLabelName(LABEL_VALUE);
    when(k8sWorkloadDao.listLabelValues(filter)).thenReturn(Arrays.asList(LABEL_VALUE));
    Set<String> tagValues = new HashSet<>();
    tagValues.add(TAG_VALUE_MODULE1);
    when(harnessTagService.listTags(ACCOUNT1_ID))
        .thenReturn(Collections.singletonList(HarnessTag.builder().key(TAG_MODULE).allowedValues(tagValues).build()));
    when(harnessTagService.get(ACCOUNT1_ID, TAG_MODULE))
        .thenReturn(HarnessTag.builder().key(TAG_MODULE).allowedValues(tagValues).build());

    Connection mockConnection = mock(Connection.class);
    Statement mockStatement = mock(Statement.class);
    when(timeScaleDBService.getDBConnection()).thenReturn(mockConnection);
    when(timeScaleDBService.isValid()).thenReturn(true);
    when(mockConnection.createStatement()).thenReturn(mockStatement);
    when(mockStatement.executeQuery(anyString())).thenReturn(resultSet);
    doNothing().when(accountChecker).checkIsCeEnabled(anyString());
    resetValues();
    mockResultSet();
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testGetBillingStatsFiltersWhenDbIsInvalid() {
    when(timeScaleDBService.isValid()).thenReturn(false);
    assertThatThrownBy(()
                           -> billingStatsFilterValuesDataFetcher.fetchSelectedFields(ACCOUNT1_ID,
                               Collections.EMPTY_LIST, Collections.EMPTY_LIST, Collections.EMPTY_LIST,
                               Collections.EMPTY_LIST, LIMIT, OFFSET, SKIP_ROUND_OFF, environment))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testFetchMethodInBillingStatsFilterValuesDataFetcher() {
    List<QLBillingDataFilter> filters = new ArrayList<>();
    List<QLCCMGroupBy> groupBy =
        Arrays.asList(makeClusterEntityGroupBy(), makeClusterNameEntityGroupBy(), makeCloudProviderEntityGroupBy());
    List<QLBillingSortCriteria> sortCriteria = Arrays.asList(makeSortingCriteria(QLBillingSortType.Cluster));

    QLFilterValuesListData data =
        (QLFilterValuesListData) billingStatsFilterValuesDataFetcher.fetchSelectedFields(ACCOUNT1_ID,
            Collections.EMPTY_LIST, filters, groupBy, sortCriteria, LIMIT, OFFSET, SKIP_ROUND_OFF, environment);

    assertThat(sortCriteria.get(0).getSortType()).isEqualTo(QLBillingSortType.Cluster);
    assertThat(sortCriteria.get(0).getSortOrder()).isEqualTo(QLSortOrder.DESCENDING);
    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getClusters().get(0).getId()).isEqualTo(CLUSTER1_ID);
    assertThat(data.getData().get(0).getClusters().get(0).getName()).isEqualTo(CLUSTER1_ID);
    assertThat(data.getData().get(0).getClusters().get(0).getType()).isEqualTo(CLUSTER_TYPE1);
    assertThat(data.getData().get(0).getCloudProviders().get(0).getName()).isEqualTo(CLOUD_PROVIDER1_ID_ACCOUNT1);
    assertThat(data.getData().get(0).getCloudServiceNames().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getTaskIds().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getLaunchTypes().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getWorkloadNames().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getNamespaces().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getApplications().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getEnvironments().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getServices().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testFetchMethodInBillingStatsFilterValuesDataFetcherWithoutClusterNameGroupBy() {
    List<QLBillingDataFilter> filters = new ArrayList<>();
    List<QLCCMGroupBy> groupBy =
        Arrays.asList(makeClusterEntityGroupBy(), makeClusterTypeEntityGroupBy(), makeCloudProviderEntityGroupBy());
    List<QLBillingSortCriteria> sortCriteria = Arrays.asList(makeSortingCriteria(QLBillingSortType.CloudProvider));

    QLFilterValuesListData data =
        (QLFilterValuesListData) billingStatsFilterValuesDataFetcher.fetchSelectedFields(ACCOUNT1_ID,
            Collections.EMPTY_LIST, filters, groupBy, sortCriteria, LIMIT, OFFSET, SKIP_ROUND_OFF, environment);

    assertThat(sortCriteria.get(0).getSortType()).isEqualTo(QLBillingSortType.CloudProvider);
    assertThat(sortCriteria.get(0).getSortOrder()).isEqualTo(QLSortOrder.DESCENDING);
    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getClusters().get(0).getId()).isEqualTo(CLUSTER1_ID);
    assertThat(data.getData().get(0).getClusters().get(0).getName()).isEqualTo(CLUSTER1_ID);
    assertThat(data.getData().get(0).getClusters().get(0).getType()).isEqualTo(CLUSTER_TYPE1);
    assertThat(data.getData().get(0).getCloudProviders().get(0).getName()).isEqualTo(CLOUD_PROVIDER1_ID_ACCOUNT1);
    assertThat(data.getData().get(0).getCloudServiceNames().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getTaskIds().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getLaunchTypes().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getWorkloadNames().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getNamespaces().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getApplications().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getEnvironments().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getServices().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testFetchMethodInBillingStatsFilterValuesDataFetcherForApplications() {
    List<QLBillingDataFilter> filters = new ArrayList<>();
    List<QLCCMGroupBy> groupBy =
        Arrays.asList(makeApplicationEntityGroupBy(), makeServiceEntityGroupBy(), makeEnvironmentEntityGroupBy());
    List<QLBillingSortCriteria> sortCriteria = Arrays.asList(makeSortingCriteria(QLBillingSortType.Service));

    QLFilterValuesListData data =
        (QLFilterValuesListData) billingStatsFilterValuesDataFetcher.fetchSelectedFields(ACCOUNT1_ID,
            Collections.EMPTY_LIST, filters, groupBy, sortCriteria, LIMIT, OFFSET, SKIP_ROUND_OFF, environment);

    assertThat(sortCriteria.get(0).getSortType()).isEqualTo(QLBillingSortType.Service);
    assertThat(sortCriteria.get(0).getSortOrder()).isEqualTo(QLSortOrder.DESCENDING);
    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getApplications().get(0).getName()).isEqualTo(APP1_ID_ACCOUNT1);
    assertThat(data.getData().get(0).getApplications().get(0).getId()).isEqualTo(APP1_ID_ACCOUNT1);
    assertThat(data.getData().get(0).getEnvironments().get(0).getName()).isEqualTo(ENV1_ID_APP1_ACCOUNT1);
    assertThat(data.getData().get(0).getEnvironments().get(0).getId()).isEqualTo(ENV1_ID_APP1_ACCOUNT1);
    assertThat(data.getData().get(0).getServices().get(0).getName()).isEqualTo(SERVICE1_ID_APP1_ACCOUNT1);
    assertThat(data.getData().get(0).getServices().get(0).getId()).isEqualTo(SERVICE1_ID_APP1_ACCOUNT1);
    assertThat(data.getData().get(0).getNamespaces().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getTaskIds().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getLaunchTypes().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getWorkloadNames().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getNamespaces().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getCloudProviders().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getClusters().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testFetchMethodInBillingStatsFilterValuesDataFetcherForKubernetes() {
    String[] clusterValues = new String[] {CLUSTER1_ID};

    List<QLBillingDataFilter> filters = new ArrayList<>();
    filters.add(makeClusterFilter(clusterValues));
    List<QLCCMGroupBy> groupBy = Arrays.asList(makeWorkloadNameEntityGroupBy(), makeNamespaceEntityGroupBy());
    List<QLBillingSortCriteria> sortCriteria = Arrays.asList(makeSortingCriteria(QLBillingSortType.Workload));

    QLFilterValuesListData data =
        (QLFilterValuesListData) billingStatsFilterValuesDataFetcher.fetchSelectedFields(ACCOUNT1_ID,
            Collections.EMPTY_LIST, filters, groupBy, sortCriteria, LIMIT, OFFSET, SKIP_ROUND_OFF, environment);

    assertThat(filters.get(0).getCluster().getOperator()).isEqualTo(QLIdOperator.EQUALS);
    assertThat(filters.get(0).getCluster().getValues()).isEqualTo(clusterValues);
    assertThat(sortCriteria.get(0).getSortType()).isEqualTo(QLBillingSortType.Workload);
    assertThat(sortCriteria.get(0).getSortOrder()).isEqualTo(QLSortOrder.DESCENDING);
    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getWorkloadNames().get(0).getName()).isEqualTo(WORKLOAD_NAME_ACCOUNT1);
    assertThat(data.getData().get(0).getNamespaces().get(0).getName()).isEqualTo(NAMESPACE1);
    assertThat(data.getData().get(0).getCloudServiceNames().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getTaskIds().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getLaunchTypes().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getClusters().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getApplications().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getEnvironments().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getServices().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getCloudProviders().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testFetchMethodInBillingStatsFilterValuesDataFetcherForEcs() {
    String[] clusterValues = new String[] {CLUSTER1_ID};

    List<QLBillingDataFilter> filters = new ArrayList<>();
    filters.add(makeClusterFilter(clusterValues));
    List<QLCCMGroupBy> groupBy =
        Arrays.asList(makeCloudServiceNameEntityGroupBy(), makeTaskIdEntityGroupBy(), makeLaunchTypeEntityGroupBy());
    List<QLBillingSortCriteria> sortCriteria = Arrays.asList(makeSortingCriteria(QLBillingSortType.TaskId));

    QLFilterValuesListData data =
        (QLFilterValuesListData) billingStatsFilterValuesDataFetcher.fetchSelectedFields(ACCOUNT1_ID,
            Collections.EMPTY_LIST, filters, groupBy, sortCriteria, LIMIT, OFFSET, SKIP_ROUND_OFF, environment);

    assertThat(filters.get(0).getCluster().getOperator()).isEqualTo(QLIdOperator.EQUALS);
    assertThat(filters.get(0).getCluster().getValues()).isEqualTo(clusterValues);
    assertThat(sortCriteria.get(0).getSortType()).isEqualTo(QLBillingSortType.TaskId);
    assertThat(sortCriteria.get(0).getSortOrder()).isEqualTo(QLSortOrder.DESCENDING);
    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getCloudServiceNames().get(0).getName()).isEqualTo(CLOUD_SERVICE_NAME_ACCOUNT1);
    assertThat(data.getData().get(0).getTaskIds().get(0).getName()).isEqualTo(INSTANCE1_SERVICE1_ENV1_APP1_ACCOUNT1);
    assertThat(data.getData().get(0).getLaunchTypes().get(0).getName()).isEqualTo(LAUNCH_TYPE1);
    assertThat(data.getData().get(0).getWorkloadNames().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getNamespaces().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getClusters().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getApplications().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getEnvironments().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getServices().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getCloudProviders().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testFetchMethodInBillingStatsFilterValuesDataFetcherForLabels() {
    String[] clusterValues = new String[] {CLUSTER1_ID};
    String[] workloadNameValues = new String[] {WORKLOAD_NAME_ACCOUNT1};
    String[] namespaceValues = new String[] {NAMESPACE1};
    List<QLBillingDataFilter> filters = new ArrayList<>();
    filters.add(makeClusterFilter(clusterValues));
    filters.add(makeWorkloadNameFilter(workloadNameValues));
    filters.add(makeNamespaceFilter(namespaceValues));
    List<QLCCMGroupBy> groupBy = Arrays.asList(makeWorkloadNameEntityGroupBy());
    List<QLBillingSortCriteria> sortCriteria = Arrays.asList(makeSortingCriteria(QLBillingSortType.Workload));

    QLFilterValuesListData data =
        (QLFilterValuesListData) billingStatsFilterValuesDataFetcher.fetchSelectedFields(ACCOUNT1_ID,
            Collections.EMPTY_LIST, filters, groupBy, sortCriteria, LIMIT, OFFSET, SKIP_ROUND_OFF, environment);

    assertThat(sortCriteria.get(0).getSortType()).isEqualTo(QLBillingSortType.Workload);
    assertThat(sortCriteria.get(0).getSortOrder()).isEqualTo(QLSortOrder.DESCENDING);
    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getWorkloadNames().get(0).getName()).isEqualTo(WORKLOAD_NAME_ACCOUNT1);
    assertThat(data.getData().get(0).getApplications().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getEnvironments().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getServices().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getNamespaces().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getTaskIds().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getLaunchTypes().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getNamespaces().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getCloudProviders().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getClusters().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testFetchMethodInBillingStatsFilterValuesDataFetcherForNodes() {
    when(instanceDataService.fetchInstanceDataForGivenInstances(anyString(), anyString(), anyList()))
        .thenReturn(Collections.singletonList(mockInstanceData(INSTANCE1_SERVICE1_ENV1_APP1_ACCOUNT1)));
    when(instanceDataService.fetchInstanceDataForGivenInstances(eq(ACCOUNT1_ID), anyList()))
        .thenReturn(Collections.singletonList(mockInstanceData(INSTANCE1_SERVICE1_ENV1_APP1_ACCOUNT1)));

    String[] workloadNameValues = new String[] {WORKLOAD_NAME_ACCOUNT1};
    String[] namespaceValues = new String[] {NAMESPACE1};
    List<QLBillingDataFilter> filters = new ArrayList<>();
    filters.add(makeWorkloadNameFilter(workloadNameValues));
    filters.add(makeNamespaceFilter(namespaceValues));
    List<QLCCMGroupBy> groupBy =
        Arrays.asList(makeNodeEntityGroupBy(), makeWorkloadNameEntityGroupBy(), makeNamespaceEntityGroupBy());
    List<QLBillingSortCriteria> sortCriteria = Collections.emptyList();

    QLFilterValuesListData data =
        (QLFilterValuesListData) billingStatsFilterValuesDataFetcher.fetchSelectedFields(ACCOUNT1_ID,
            Collections.EMPTY_LIST, filters, groupBy, sortCriteria, LIMIT, OFFSET, SKIP_ROUND_OFF, environment);

    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getWorkloadNames().get(0).getName()).isEqualTo(WORKLOAD_NAME_ACCOUNT1);
    assertThat(data.getData().get(0).getNamespaces().get(0).getName()).isEqualTo(NAMESPACE1);
    assertThat(data.getData().get(0).getInstances().get(0).getName()).isEqualTo(INSTANCE1_SERVICE1_ENV1_APP1_ACCOUNT1);
    assertThat(data.getData().get(0).getCloudServiceNames().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getTaskIds().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getLaunchTypes().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getClusters().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getApplications().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getEnvironments().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getServices().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getCloudProviders().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testFetchMethodInBillingStatsFilterValuesDataFetcherForPods() {
    when(instanceDataService.fetchInstanceDataForGivenInstances(eq(ACCOUNT1_ID), anyList()))
        .thenReturn(Collections.singletonList(mockInstanceData(INSTANCE1_SERVICE1_ENV1_APP1_ACCOUNT1)));

    String[] workloadNameValues = new String[] {WORKLOAD_NAME_ACCOUNT1};
    String[] namespaceValues = new String[] {NAMESPACE1};
    List<QLBillingDataFilter> filters = new ArrayList<>();
    filters.add(makeWorkloadNameFilter(workloadNameValues));
    filters.add(makeNamespaceFilter(namespaceValues));
    List<QLCCMGroupBy> groupBy =
        Arrays.asList(makePodEntityGroupBy(), makeWorkloadNameEntityGroupBy(), makeNamespaceEntityGroupBy());
    List<QLBillingSortCriteria> sortCriteria = Collections.emptyList();

    QLFilterValuesListData data =
        (QLFilterValuesListData) billingStatsFilterValuesDataFetcher.fetchSelectedFields(ACCOUNT1_ID,
            Collections.EMPTY_LIST, filters, groupBy, sortCriteria, LIMIT, OFFSET, SKIP_ROUND_OFF, environment);

    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getWorkloadNames().get(0).getName()).isEqualTo(WORKLOAD_NAME_ACCOUNT1);
    assertThat(data.getData().get(0).getNamespaces().get(0).getName()).isEqualTo(NAMESPACE1);
    assertThat(data.getData().get(0).getInstances().get(0).getName()).isEqualTo(INSTANCE1_SERVICE1_ENV1_APP1_ACCOUNT1);
    assertThat(data.getData().get(0).getCloudServiceNames().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getTaskIds().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getLaunchTypes().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getClusters().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getApplications().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getEnvironments().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getServices().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getCloudProviders().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testFetchMethodInBillingStatsFilterValuesDataFetcherForSearch() {
    String[] namespaceValues = new String[] {NAMESPACE1};
    List<QLBillingDataFilter> filters = new ArrayList<>();
    filters.add(makeNamespaceFilterForSearch(namespaceValues));
    List<QLCCMGroupBy> groupBy = Arrays.asList(makeNamespaceEntityGroupBy());
    List<QLBillingSortCriteria> sortCriteria = Arrays.asList(makeSortingCriteria(QLBillingSortType.Namespace));

    QLFilterValuesListData data =
        (QLFilterValuesListData) billingStatsFilterValuesDataFetcher.fetchSelectedFields(ACCOUNT1_ID,
            Collections.EMPTY_LIST, filters, groupBy, sortCriteria, LIMIT, OFFSET, SKIP_ROUND_OFF, environment);

    assertThat(sortCriteria.get(0).getSortType()).isEqualTo(QLBillingSortType.Namespace);
    assertThat(sortCriteria.get(0).getSortOrder()).isEqualTo(QLSortOrder.DESCENDING);
    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getNamespaces().get(0).getName()).isEqualTo(NAMESPACE1);
    assertThat(data.getData().get(0).getCloudServiceNames().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getTaskIds().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getLaunchTypes().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getWorkloadNames().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getClusters().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getApplications().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getEnvironments().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getServices().size()).isEqualTo(0);
    assertThat(data.getTotal()).isEqualTo(100L);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testFetchMethodInBillingStatsFilterValuesDataFetcherForLabelSearch() {
    List<QLBillingDataFilter> filters = new ArrayList<>();
    filters.add(makeStartTimeFilter(currentTime - 86400000));
    filters.add(makeEndTimeFilter(currentTime - 86400000));
    List<QLCCMGroupBy> groupBy = Arrays.asList(makeLabelGroupBy());

    QLFilterValuesListData data = (QLFilterValuesListData) billingStatsFilterValuesDataFetcher.fetchSelectedFields(
        ACCOUNT1_ID, Collections.EMPTY_LIST, filters, groupBy, Collections.emptyList(), LIMIT, OFFSET, SKIP_ROUND_OFF,
        environment);

    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getK8sLabels().get(0).getName()).isEqualTo(LABEL_NAME);
    assertThat(data.getData().get(0).getCloudServiceNames().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getTaskIds().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getLaunchTypes().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getWorkloadNames().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getClusters().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getApplications().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getEnvironments().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getServices().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testFetchMethodInBillingStatsFilterValuesDataFetcherForTagSearch() {
    List<QLBillingDataFilter> filters = new ArrayList<>();
    filters.add(makeStartTimeFilter(currentTime - 86400000));
    filters.add(makeEndTimeFilter(currentTime - 86400000));
    List<QLCCMGroupBy> groupBy = Arrays.asList(makeTagGroupBy());

    QLFilterValuesListData data = (QLFilterValuesListData) billingStatsFilterValuesDataFetcher.fetchSelectedFields(
        ACCOUNT1_ID, Collections.EMPTY_LIST, filters, groupBy, Collections.emptyList(), LIMIT, OFFSET, SKIP_ROUND_OFF,
        environment);

    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getTags().get(0).getName()).isEqualTo(TAG_MODULE);
    assertThat(data.getData().get(0).getCloudServiceNames().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getTaskIds().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getLaunchTypes().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getWorkloadNames().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getClusters().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getApplications().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getEnvironments().size()).isEqualTo(0);
    assertThat(data.getData().get(0).getServices().size()).isEqualTo(0);
  }

  public QLBillingSortCriteria makeDescByTimeSortingCriteria() {
    return QLBillingSortCriteria.builder().sortOrder(QLSortOrder.DESCENDING).sortType(QLBillingSortType.Time).build();
  }

  public QLCCMGroupBy makeWorkloadNameEntityGroupBy() {
    QLCCMEntityGroupBy workloadNameGroupBy = QLCCMEntityGroupBy.WorkloadName;
    return QLCCMGroupBy.builder().entityGroupBy(workloadNameGroupBy).build();
  }

  public QLCCMGroupBy makeLaunchTypeEntityGroupBy() {
    QLCCMEntityGroupBy launchTypeGroupBy = QLCCMEntityGroupBy.LaunchType;
    return QLCCMGroupBy.builder().entityGroupBy(launchTypeGroupBy).build();
  }

  public QLCCMGroupBy makeNamespaceEntityGroupBy() {
    QLCCMEntityGroupBy namespaceGroupBy = QLCCMEntityGroupBy.Namespace;
    return QLCCMGroupBy.builder().entityGroupBy(namespaceGroupBy).build();
  }

  public QLCCMGroupBy makeTaskIdEntityGroupBy() {
    QLCCMEntityGroupBy instanceIdGroupBy = QLCCMEntityGroupBy.TaskId;
    return QLCCMGroupBy.builder().entityGroupBy(instanceIdGroupBy).build();
  }

  public QLCCMGroupBy makeCloudServiceNameEntityGroupBy() {
    QLCCMEntityGroupBy cloudServiceNameGroupBy = QLCCMEntityGroupBy.CloudServiceName;
    return QLCCMGroupBy.builder().entityGroupBy(cloudServiceNameGroupBy).build();
  }

  public QLCCMGroupBy makeClusterEntityGroupBy() {
    QLCCMEntityGroupBy clusterGroupBy = QLCCMEntityGroupBy.Cluster;
    return QLCCMGroupBy.builder().entityGroupBy(clusterGroupBy).build();
  }

  public QLCCMGroupBy makeClusterNameEntityGroupBy() {
    QLCCMEntityGroupBy clusterNameGroupBy = QLCCMEntityGroupBy.ClusterName;
    return QLCCMGroupBy.builder().entityGroupBy(clusterNameGroupBy).build();
  }

  public QLCCMGroupBy makeClusterTypeEntityGroupBy() {
    QLCCMEntityGroupBy clusterTypeGroupBy = QLCCMEntityGroupBy.ClusterType;
    return QLCCMGroupBy.builder().entityGroupBy(clusterTypeGroupBy).build();
  }

  public QLCCMGroupBy makeCloudProviderEntityGroupBy() {
    QLCCMEntityGroupBy cloudProviderGroupBy = QLCCMEntityGroupBy.CloudProvider;
    return QLCCMGroupBy.builder().entityGroupBy(cloudProviderGroupBy).build();
  }

  public QLCCMGroupBy makeApplicationEntityGroupBy() {
    QLCCMEntityGroupBy applicationGroupBy = QLCCMEntityGroupBy.Application;
    return QLCCMGroupBy.builder().entityGroupBy(applicationGroupBy).build();
  }

  public QLCCMGroupBy makeServiceEntityGroupBy() {
    QLCCMEntityGroupBy serviceGroupBy = QLCCMEntityGroupBy.Service;
    return QLCCMGroupBy.builder().entityGroupBy(serviceGroupBy).build();
  }

  public QLCCMGroupBy makeEnvironmentEntityGroupBy() {
    QLCCMEntityGroupBy environmentGroupBy = QLCCMEntityGroupBy.Environment;
    return QLCCMGroupBy.builder().entityGroupBy(environmentGroupBy).build();
  }

  public QLCCMGroupBy makeNodeEntityGroupBy() {
    QLCCMEntityGroupBy nodeGroupBy = QLCCMEntityGroupBy.Node;
    return QLCCMGroupBy.builder().entityGroupBy(nodeGroupBy).build();
  }

  public QLCCMGroupBy makePodEntityGroupBy() {
    QLCCMEntityGroupBy podGroupBy = QLCCMEntityGroupBy.Pod;
    return QLCCMGroupBy.builder().entityGroupBy(podGroupBy).build();
  }

  public QLCCMGroupBy makeLabelGroupBy() {
    QLBillingDataLabelAggregation labelAggregation = QLBillingDataLabelAggregation.builder().name(LABEL_NAME).build();
    return QLCCMGroupBy.builder().labelAggregation(labelAggregation).build();
  }

  public QLCCMGroupBy makeTagGroupBy() {
    QLBillingDataTagAggregation tagAggregation = QLBillingDataTagAggregation.builder().tagName(TAG_MODULE).build();
    return QLCCMGroupBy.builder().tagAggregation(tagAggregation).build();
  }

  public QLBillingDataFilter makeClusterFilter(String[] values) {
    QLIdFilter clusterFilter = QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(values).build();
    return QLBillingDataFilter.builder().cluster(clusterFilter).build();
  }

  public QLBillingDataFilter makeWorkloadNameFilter(String[] values) {
    QLIdFilter workloadNameFilter = QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(values).build();
    return QLBillingDataFilter.builder().workloadName(workloadNameFilter).build();
  }

  public QLBillingDataFilter makeNamespaceFilter(String[] values) {
    QLIdFilter namespaceFilter = QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(values).build();
    return QLBillingDataFilter.builder().namespace(namespaceFilter).build();
  }

  public QLBillingDataFilter makeNamespaceFilterForSearch(String[] values) {
    QLIdFilter namespaceFilter = QLIdFilter.builder().operator(QLIdOperator.LIKE).values(values).build();
    return QLBillingDataFilter.builder().namespace(namespaceFilter).build();
  }

  public QLBillingSortCriteria makeSortingCriteria(QLBillingSortType type) {
    return QLBillingSortCriteria.builder().sortOrder(QLSortOrder.DESCENDING).sortType(type).build();
  }

  public QLBillingDataFilter makeStartTimeFilter(Long filterTime) {
    QLTimeFilter timeFilter = QLTimeFilter.builder().operator(QLTimeOperator.AFTER).value(filterTime).build();
    return QLBillingDataFilter.builder().startTime(timeFilter).build();
  }

  public QLBillingDataFilter makeEndTimeFilter(Long filterTime) {
    QLTimeFilter timeFilter = QLTimeFilter.builder().operator(QLTimeOperator.BEFORE).value(filterTime).build();
    return QLBillingDataFilter.builder().endTime(timeFilter).build();
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

  private void mockResultSet() throws SQLException {
    Connection connection = mock(Connection.class);
    statement = mock(Statement.class);
    resultSet = mock(ResultSet.class);
    when(timeScaleDBService.getDBConnection()).thenReturn(connection);
    when(connection.createStatement()).thenReturn(statement);
    when(statement.executeQuery(anyString())).thenReturn(resultSet);
    when(environment.getSelectionSet()).thenReturn(mockSelectionSet);

    when(resultSet.getString("CLUSTERID")).thenAnswer((Answer<String>) invocation -> CLUSTER1_ID);
    when(resultSet.getString("CLUSTERNAME")).thenAnswer((Answer<String>) invocation -> CLUSTER1_NAME);
    when(resultSet.getString("CLUSTERTYPE")).thenAnswer((Answer<String>) invocation -> CLUSTER_TYPE1);
    when(resultSet.getString("APPID")).thenAnswer((Answer<String>) invocation -> APP1_ID_ACCOUNT1);
    when(resultSet.getString("ENVID")).thenAnswer((Answer<String>) invocation -> ENV1_ID_APP1_ACCOUNT1);
    when(resultSet.getString("CLOUDPROVIDERID")).thenAnswer((Answer<String>) invocation -> CLOUD_PROVIDER1_ID_ACCOUNT1);
    when(resultSet.getString("SERVICEID")).thenAnswer((Answer<String>) invocation -> SERVICE1_ID_APP1_ACCOUNT1);
    when(resultSet.getString("WORKLOADNAME")).thenAnswer((Answer<String>) invocation -> WORKLOAD_NAME_ACCOUNT1);
    when(resultSet.getString("NAMESPACE")).thenAnswer((Answer<String>) invocation -> NAMESPACE1);
    when(resultSet.getString("LAUNCHTYPE")).thenAnswer((Answer<String>) invocation -> LAUNCH_TYPE1);
    when(resultSet.getString("TASKID"))
        .thenAnswer((Answer<String>) invocation -> INSTANCE1_SERVICE1_ENV1_APP1_ACCOUNT1);
    when(resultSet.getString("CLOUDSERVICENAME"))
        .thenAnswer((Answer<String>) invocation -> CLOUD_SERVICE_NAME_ACCOUNT1);
    when(resultSet.getString("INSTANCEID"))
        .thenAnswer((Answer<String>) invocation -> INSTANCE1_SERVICE1_ENV1_APP1_ACCOUNT1);
    when(resultSet.getString("INSTANCENAME"))
        .thenAnswer((Answer<String>) invocation -> INSTANCE1_SERVICE1_ENV1_APP1_ACCOUNT1);
    when(resultSet.getLong("COUNT")).thenAnswer((Answer<Long>) invocation -> 100L);

    returnResultSet(5);
  }

  private void returnResultSet(int limit) throws SQLException {
    when(resultSet.next()).then((Answer<Boolean>) invocation -> {
      if (count[0] < limit) {
        count[0]++;
        return true;
      }
      count[0] = 0;
      return false;
    });
  }

  private void resetValues() {
    count[0] = 0;
    doubleVal[0] = 0;
  }

  private InstanceData mockInstanceData(String instanceId) {
    return InstanceData.builder()
        .instanceId(instanceId)
        .instanceName(instanceId)
        .accountId(ACCOUNT1_ID)
        .settingId(SETTING_ID1)
        .clusterName(CLUSTER1_NAME)
        .clusterId(CLUSTER1_ID)
        .build();
  }
}
