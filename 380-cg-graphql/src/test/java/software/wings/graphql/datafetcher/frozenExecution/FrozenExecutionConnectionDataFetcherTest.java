/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.frozenExecution;

import static io.harness.rule.OwnerRule.VINICIUS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.beans.EnvironmentType;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.WorkflowType;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import software.wings.beans.EnvSummary;
import software.wings.beans.User;
import software.wings.beans.WorkflowExecution;
import software.wings.dl.WingsPersistence;
import software.wings.graphql.datafetcher.AbstractDataFetcherTestBase;
import software.wings.graphql.schema.query.QLPageQueryParameterImpl;
import software.wings.graphql.schema.type.QLEnvSummary;
import software.wings.graphql.schema.type.QLEnvironmentType;
import software.wings.graphql.schema.type.QLFrozenExecution;
import software.wings.graphql.schema.type.QLFrozenExecutionConnection;
import software.wings.graphql.schema.type.QLPipelineExecution;
import software.wings.graphql.schema.type.QLWorkflowExecution;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;
import software.wings.security.UserThreadLocal;

import com.google.inject.Inject;
import graphql.execution.MergedSelectionSet;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingFieldSelectionSet;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.SelectedField;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class FrozenExecutionConnectionDataFetcherTest extends AbstractDataFetcherTestBase {
  @Mock DataFetchingEnvironment dataFetchingEnvironment;
  @Inject WingsPersistence wingsPersistence;
  @InjectMocks @Inject FrozenExecutionConnectionDataFetcher frozenExecutionConnectionDataFetcher;

  private static final String ACCOUNT_ID = "accountId";
  private static final String APP_ID = "appId";
  private static final String ENV_ID = "envId";
  private static final String ENV_NAME = "envName";
  private static final EnvironmentType ENV_TYPE = EnvironmentType.PROD;
  private static final String SERVICE_ID = "serviceId";
  private static final String PIPELINE_SERVICE_ID = "serviceIdPipeline";
  private static final String EXECUTION_NAME = "name";
  private static final String[] REJECTED_BY_FREEZE_WINDOW_IDS = new String[] {"freezeId1"};
  private static final String[] REJECTED_BY_FREEZE_WINDOW_NAMES = new String[] {"freeze1"};
  private static final String PIPELINE_EXECUTION_ID = "pipelineExecutionId";

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

  private void createFrozenWorkflowExecution() {
    WorkflowExecution workflowExecution =
        WorkflowExecution.builder()
            .workflowType(WorkflowType.ORCHESTRATION)
            .status(ExecutionStatus.REJECTED)
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .name(EXECUTION_NAME)
            .envIds(Collections.singletonList(ENV_ID))
            .serviceIds(Collections.singletonList(SERVICE_ID))
            .environments(Collections.singletonList(
                EnvSummary.builder().name(ENV_NAME).uuid(ENV_ID).environmentType(ENV_TYPE).build()))
            .workflowType(WorkflowType.ORCHESTRATION)
            .rejectedByFreezeWindowIds(Arrays.asList(REJECTED_BY_FREEZE_WINDOW_IDS))
            .rejectedByFreezeWindowNames(Arrays.asList(REJECTED_BY_FREEZE_WINDOW_NAMES))
            .pipelineExecutionId(PIPELINE_EXECUTION_ID)
            .build();
    wingsPersistence.insert(workflowExecution);
  }

  private void createFrozenPipelineExecution() {
    WorkflowExecution pipelineExecution =
        WorkflowExecution.builder()
            .workflowType(WorkflowType.ORCHESTRATION)
            .status(ExecutionStatus.REJECTED)
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .name(EXECUTION_NAME)
            .envIds(Collections.singletonList(ENV_ID))
            .serviceIds(Collections.singletonList(PIPELINE_SERVICE_ID))
            .environments(Collections.singletonList(
                EnvSummary.builder().name(ENV_NAME).uuid(ENV_ID).environmentType(ENV_TYPE).build()))
            .workflowType(WorkflowType.PIPELINE)
            .rejectedByFreezeWindowIds(Arrays.asList(REJECTED_BY_FREEZE_WINDOW_IDS))
            .rejectedByFreezeWindowNames(Arrays.asList(REJECTED_BY_FREEZE_WINDOW_NAMES))
            .build();
    wingsPersistence.insert(pipelineExecution);
  }

  @Before
  public void setup() throws SQLException {
    User user = testUtils.createUser(testUtils.createAccount());
    UserThreadLocal.set(user);
    createAccount(ACCOUNT1_ID, getLicenseInfo());
    createFrozenWorkflowExecution();
    createFrozenPipelineExecution();
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testFetchFrozenWorkflowExecutionConnectionWithAllFilters() {
    // Test getting frozen workflow execution
    QLFrozenExecutionFilter frozenExecutionFilter =
        QLFrozenExecutionFilter.builder()
            .environment(QLIdFilter.builder().operator(QLIdOperator.IN).values(new String[] {ENV_ID}).build())
            .service(QLIdFilter.builder().operator(QLIdOperator.IN).values(new String[] {SERVICE_ID}).build())
            .rejectedByFreezeWindow(
                QLIdFilter.builder().operator(QLIdOperator.IN).values(REJECTED_BY_FREEZE_WINDOW_IDS).build())
            .build();

    List<QLFrozenExecutionFilter> connectorFilters = Collections.singletonList(frozenExecutionFilter);
    QLPageQueryParameterImpl pageQueryParams = QLPageQueryParameterImpl.builder()
                                                   .limit(100)
                                                   .selectionSet(mockSelectionSet)
                                                   .dataFetchingEnvironment(dataFetchingEnvironment)
                                                   .build();
    QLFrozenExecutionConnection connection =
        frozenExecutionConnectionDataFetcher.fetchConnection(connectorFilters, pageQueryParams, null);
    assertThat(connection).isNotNull();
    assertThat(connection.getNodes()).hasSize(1);
    assertThat(connection.getNodes().get(0)).isInstanceOf(QLFrozenExecution.class);
    QLFrozenExecution node = connection.getNodes().get(0);
    assertThat(node.getExecution()).isInstanceOf(QLWorkflowExecution.class);
    assertThat(node.getEnvironments()).hasSize(1);
    assertThat(node.getPipelineExecutionId()).isEqualTo(PIPELINE_EXECUTION_ID);
    QLEnvSummary envSummary = node.getEnvironments().get(0);
    assertThat(envSummary.getName()).isEqualTo(ENV_NAME);
    assertThat(envSummary.getId()).isEqualTo(ENV_ID);
    assertThat(envSummary.getType()).isEqualTo(QLEnvironmentType.PROD);
    assertThat(node.getServiceIds()).hasSize(1);
    String serviceId = node.getServiceIds().get(0);
    assertThat(serviceId).isEqualTo(SERVICE_ID);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testFetchFrozenWorkflowPipelineConnectionWithAllFilters() {
    // Test getting frozen workflow execution
    QLFrozenExecutionFilter frozenExecutionFilter =
        QLFrozenExecutionFilter.builder()
            .environment(QLIdFilter.builder().operator(QLIdOperator.IN).values(new String[] {ENV_ID}).build())
            .service(QLIdFilter.builder().operator(QLIdOperator.IN).values(new String[] {PIPELINE_SERVICE_ID}).build())
            .rejectedByFreezeWindow(
                QLIdFilter.builder().operator(QLIdOperator.IN).values(REJECTED_BY_FREEZE_WINDOW_IDS).build())
            .build();

    List<QLFrozenExecutionFilter> connectorFilters = Collections.singletonList(frozenExecutionFilter);
    QLPageQueryParameterImpl pageQueryParams = QLPageQueryParameterImpl.builder()
                                                   .limit(100)
                                                   .selectionSet(mockSelectionSet)
                                                   .dataFetchingEnvironment(dataFetchingEnvironment)
                                                   .build();
    QLFrozenExecutionConnection connection =
        frozenExecutionConnectionDataFetcher.fetchConnection(connectorFilters, pageQueryParams, null);
    assertThat(connection).isNotNull();
    assertThat(connection.getNodes()).hasSize(1);
    assertThat(connection.getNodes().get(0)).isInstanceOf(QLFrozenExecution.class);
    QLFrozenExecution node = connection.getNodes().get(0);
    assertThat(node.getExecution()).isInstanceOf(QLPipelineExecution.class);
    assertThat(node.getEnvironments()).hasSize(1);
    QLEnvSummary envSummary = node.getEnvironments().get(0);
    assertThat(envSummary.getName()).isEqualTo(ENV_NAME);
    assertThat(envSummary.getId()).isEqualTo(ENV_ID);
    assertThat(envSummary.getType()).isEqualTo(QLEnvironmentType.PROD);
    String serviceId = node.getServiceIds().get(0);
    assertThat(serviceId).isEqualTo(PIPELINE_SERVICE_ID);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testFetchWithoutRejectedByFreezeWindowFilter() {
    // Test getting frozen workflow execution
    QLFrozenExecutionFilter frozenExecutionFilter =
        QLFrozenExecutionFilter.builder()
            .environment(QLIdFilter.builder().operator(QLIdOperator.IN).values(new String[] {ENV_ID}).build())
            .service(QLIdFilter.builder().operator(QLIdOperator.IN).values(new String[] {PIPELINE_SERVICE_ID}).build())
            .build();

    List<QLFrozenExecutionFilter> connectorFilters = Collections.singletonList(frozenExecutionFilter);
    QLPageQueryParameterImpl pageQueryParams = QLPageQueryParameterImpl.builder()
                                                   .limit(100)
                                                   .selectionSet(mockSelectionSet)
                                                   .dataFetchingEnvironment(dataFetchingEnvironment)
                                                   .build();
    assertThatThrownBy(
        () -> frozenExecutionConnectionDataFetcher.fetchConnection(connectorFilters, pageQueryParams, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("rejectedByFreezeWindow filter is required");
  }
}
