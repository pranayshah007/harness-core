/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.execution;

import static io.harness.rule.OwnerRule.LUCAS_SALES;
import static io.harness.rule.OwnerRule.PRABU;
import static io.harness.rule.OwnerRule.VARDAN_BANSAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.beans.EnvironmentType;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.User;
import software.wings.dl.WingsPersistence;
import software.wings.graphql.datafetcher.AbstractDataFetcherTestBase;
import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.graphql.datafetcher.connector.ConnectorConnectionDataFetcher;
import software.wings.graphql.schema.query.QLPageQueryParameterImpl;
import software.wings.graphql.schema.type.QLEnvironmentType;
import software.wings.graphql.schema.type.QLExecutionConnection;
import software.wings.graphql.schema.type.QLPipelineExecution;
import software.wings.graphql.schema.type.QLWorkflowExecution;
import software.wings.graphql.schema.type.aggregation.QLEnumOperator;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;
import software.wings.graphql.schema.type.aggregation.QLNumberFilter;
import software.wings.graphql.schema.type.aggregation.QLNumberOperator;
import software.wings.graphql.schema.type.aggregation.QLTimeFilter;
import software.wings.graphql.schema.type.aggregation.QLTimeOperator;
import software.wings.graphql.schema.type.aggregation.deployment.QLDeploymentTagFilter;
import software.wings.graphql.schema.type.aggregation.deployment.QLDeploymentTagType;
import software.wings.graphql.schema.type.aggregation.environment.QLEnvironmentTypeFilter;
import software.wings.graphql.schema.type.aggregation.tag.QLTagInput;
import software.wings.graphql.utils.nameservice.NameService;
import software.wings.security.UserThreadLocal;
import software.wings.service.intfc.AppService;

import com.google.common.collect.ImmutableMap;
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
import java.util.function.Function;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

public class ExecutionConnectionDataFetcherTest extends AbstractDataFetcherTestBase {
  @Mock DataFetchingEnvironment dataFetchingEnvironment;
  @Mock AppService appService;
  @Inject WingsPersistence wingsPersistence;

  @Inject @InjectMocks ConnectorConnectionDataFetcher connectorConnectionDataFetcher;
  @InjectMocks @Inject ExecutionConnectionDataFetcher executionConnectionDataFetcher;
  @InjectMocks @Spy DataFetcherUtils dataFetcherUtils;

  String[] array = new String[1];
  String workflowExecutionId;
  String pipelineExecutionId;

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

  @Before
  public void setup() throws SQLException {
    User user = testUtils.createUser(testUtils.createAccount());
    UserThreadLocal.set(user);

    // Account1
    createAccount(ACCOUNT1_ID, getLicenseInfo());
    createApp(ACCOUNT1_ID, APP1_ID_ACCOUNT1, APP1_ID_ACCOUNT1, TAG_TEAM, TAG_VALUE_TEAM1);
    workflowExecutionId = createWorkflowExecution(ACCOUNT1_ID, APP1_ID_ACCOUNT1, WORKFLOW1, EnvironmentType.NON_PROD);
    pipelineExecutionId = createPipelineExecution(ACCOUNT1_ID, APP1_ID_ACCOUNT1, PIPELINE1, EnvironmentType.PROD);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testFetchWorkflowExecutionConnection() {
    array[0] = workflowExecutionId;
    QLIdFilter idFilter = QLIdFilter.builder().operator(QLIdOperator.IN).values(array).build();
    QLExecutionFilter executionFilter =
        QLExecutionFilter.builder()
            .execution(idFilter)
            .status(QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(new String[] {"SUCCESS"}).build())
            .application(
                QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(new String[] {APP1_ID_ACCOUNT1}).build())
            .creationTime(
                QLTimeFilter.builder().operator(QLTimeOperator.BEFORE).value(System.currentTimeMillis()).build())
            .workflow(QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(new String[] {WORKFLOW1}).build())
            .triggeredBy(QLIdFilter.builder().operator(QLIdOperator.NOT_IN).values(new String[] {"USER"}).build())
            .trigger(QLIdFilter.builder().operator(QLIdOperator.NOT_IN).values(new String[] {"TRIGGER"}).build())
            .environmentType(QLEnvironmentTypeFilter.builder()
                                 .operator(QLEnumOperator.IN)
                                 .values(new QLEnvironmentType[] {QLEnvironmentType.NON_PROD})
                                 .build())
            .build();
    when(dataFetchingEnvironment.getArguments())
        .thenReturn(Collections.singletonMap(ExecutionConnectionDataFetcher.INDIRECT_EXECUTION_FIELD, false));

    List<QLExecutionFilter> connectorFilters = Arrays.asList(executionFilter);
    QLPageQueryParameterImpl pageQueryParams = QLPageQueryParameterImpl.builder()
                                                   .limit(100)
                                                   .selectionSet(mockSelectionSet)
                                                   .dataFetchingEnvironment(dataFetchingEnvironment)
                                                   .build();
    QLExecutionConnection connection =
        executionConnectionDataFetcher.fetchConnection(connectorFilters, pageQueryParams, null);
    assertThat(connection).isNotNull();
    assertThat(connection.getNodes()).hasSize(1);
    assertThat(connection.getNodes().get(0)).isInstanceOf(QLWorkflowExecution.class);
    assertThat((((QLWorkflowExecution) connection.getNodes().get(0))).getWorkflowId()).isEqualTo(WORKFLOW1);

    QLTagInput tagInput = QLTagInput.builder().name("tag").value("value").build();
    List<QLTagInput> tagInputList = Arrays.asList(tagInput);
    List<QLDeploymentTagType> deploymentTagTypes = Arrays.asList(QLDeploymentTagType.values());

    for (QLDeploymentTagType tagType : deploymentTagTypes) {
      executionFilter =
          QLExecutionFilter.builder()
              .application(
                  QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(new String[] {APP1_ID_ACCOUNT1}).build())
              .tag(QLDeploymentTagFilter.builder().tags(tagInputList).entityType(tagType).build())
              .build();
      connection =
          executionConnectionDataFetcher.fetchConnection(Arrays.asList(executionFilter), pageQueryParams, null);
      assertThat(connection).isNotNull();
      assertThat(connection.getNodes()).isEmpty();
    }
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testFetchPipelineExecutionConnection() {
    array[0] = pipelineExecutionId;
    QLIdFilter idFilter = QLIdFilter.builder().operator(QLIdOperator.IN).values(array).build();
    QLExecutionFilter connectorFilter =
        QLExecutionFilter.builder()
            .execution(idFilter)
            .status(QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(new String[] {"SUCCESS"}).build())
            .application(
                QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(new String[] {APP1_ID_ACCOUNT1}).build())
            .creationTime(
                QLTimeFilter.builder().operator(QLTimeOperator.BEFORE).value(System.currentTimeMillis()).build())
            .service(QLIdFilter.builder().operator(QLIdOperator.NOT_IN).values(new String[] {"SERVICE"}).build())
            .cloudProvider(QLIdFilter.builder().operator(QLIdOperator.NOT_IN).values(new String[] {"CP"}).build())
            .environment(QLIdFilter.builder().operator(QLIdOperator.NOT_IN).values(new String[] {"ENV"}).build())
            .environmentType(QLEnvironmentTypeFilter.builder()
                                 .operator(QLEnumOperator.IN)
                                 .values(new QLEnvironmentType[] {QLEnvironmentType.PROD})
                                 .build())
            .pipelineExecutionId(
                QLIdFilter.builder().operator(QLIdOperator.NOT_IN).values(new String[] {"PIPE_EXEC"}).build())
            .pipeline(QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(new String[] {PIPELINE1}).build())
            .startTime(QLTimeFilter.builder().operator(QLTimeOperator.AFTER).value(0).build())
            .endTime(QLTimeFilter.builder().operator(QLTimeOperator.AFTER).value(0).build())
            .duration(QLNumberFilter.builder().operator(QLNumberOperator.GREATER_THAN).values(new Long[] {1L}).build())
            .build();
    when(dataFetchingEnvironment.getArguments())
        .thenReturn(Collections.singletonMap(ExecutionConnectionDataFetcher.INDIRECT_EXECUTION_FIELD, true));

    List<QLExecutionFilter> connectorFilters = Arrays.asList(connectorFilter);
    QLPageQueryParameterImpl pageQueryParams = QLPageQueryParameterImpl.builder()
                                                   .limit(100)
                                                   .selectionSet(mockSelectionSet)
                                                   .dataFetchingEnvironment(dataFetchingEnvironment)
                                                   .build();
    QLExecutionConnection connection =
        executionConnectionDataFetcher.fetchConnection(connectorFilters, pageQueryParams, null);
    assertThat(connection).isNotNull();
    assertThat(connection.getNodes()).hasSize(1);
    assertThat(connection.getNodes().get(0)).isInstanceOf(QLPipelineExecution.class);
    assertThat((((QLPipelineExecution) connection.getNodes().get(0))).getPipelineId()).isEqualTo(PIPELINE1);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testFetchConnectionWithEmptyFilters() {
    List<QLExecutionFilter> executionFilters = Collections.emptyList();
    QLPageQueryParameterImpl pageQueryParams = QLPageQueryParameterImpl.builder()
                                                   .limit(100)
                                                   .selectionSet(mockSelectionSet)
                                                   .dataFetchingEnvironment(dataFetchingEnvironment)
                                                   .build();
    when(appService.getAppIdsByAccountId(any())).thenReturn(Arrays.asList(APP1_ID_ACCOUNT1));
    when(dataFetchingEnvironment.getArguments())
        .thenReturn(Collections.singletonMap(ExecutionConnectionDataFetcher.INDIRECT_EXECUTION_FIELD, true));

    QLExecutionConnection connection =
        executionConnectionDataFetcher.fetchConnection(executionFilters, pageQueryParams, null);
    assertThat(connection).isNotNull();
    assertThat(connection.getNodes()).hasSize(2);
    QLWorkflowExecution workflowExecution = (QLWorkflowExecution) connection.getNodes()
                                                .stream()
                                                .filter(e -> e instanceof QLWorkflowExecution)
                                                .findFirst()
                                                .orElse(null);
    QLPipelineExecution pipelineExecution = (QLPipelineExecution) connection.getNodes()
                                                .stream()
                                                .filter(e -> e instanceof QLPipelineExecution)
                                                .findFirst()
                                                .orElse(null);
    assertThat(workflowExecution.getWorkflowId()).isEqualTo(WORKFLOW1);
    assertThat(pipelineExecution.getPipelineId()).isEqualTo(PIPELINE1);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testGenerateFilter() {
    doReturn("fieldValue").when(dataFetcherUtils).getFieldValue(any(), any());
    doReturn("source").when(dataFetchingEnvironment).getSource();
    doReturn("accountid").when(dataFetcherUtils).getAccountId(dataFetchingEnvironment);

    Map<String, Function<QLExecutionFilter, QLIdFilter>> filterMap =
        ImmutableMap.<String, Function<QLExecutionFilter, QLIdFilter>>builder()
            .put(NameService.application, QLExecutionFilter::getApplication)
            .put(NameService.environment, QLExecutionFilter::getEnvironment)
            .put(NameService.service, QLExecutionFilter::getService)
            .put(NameService.cloudProvider, QLExecutionFilter::getCloudProvider)
            .put(NameService.pipelineExecution, QLExecutionFilter::getPipelineExecutionId)
            .build();

    filterMap.forEach((key, value) -> {
      QLExecutionFilter executionFilter =
          executionConnectionDataFetcher.generateFilter(dataFetchingEnvironment, key, "value");
      assertThat(value.apply(executionFilter)).isNotNull();
      assertThat(value.apply(executionFilter).getOperator()).isEqualTo(QLIdOperator.EQUALS);
      assertThat(value.apply(executionFilter).getValues()).containsExactly("fieldValue");
    });
  }

  @Test
  @Owner(developers = VARDAN_BANSAL)
  @Category(UnitTests.class)
  public void testExecutionConnectionWithEnvironmentType() {
    array[0] = pipelineExecutionId;
    QLExecutionFilter executionFilter =
        QLExecutionFilter.builder()
            .application(
                QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(new String[] {APP1_ID_ACCOUNT1}).build())
            .environmentType(QLEnvironmentTypeFilter.builder()
                                 .operator(QLEnumOperator.IN)
                                 .values(new QLEnvironmentType[] {QLEnvironmentType.NON_PROD})
                                 .build())
            .build();
    when(dataFetchingEnvironment.getArguments())
        .thenReturn(Collections.singletonMap(ExecutionConnectionDataFetcher.INDIRECT_EXECUTION_FIELD, true));

    List<QLExecutionFilter> filters = Arrays.asList(executionFilter);
    QLPageQueryParameterImpl pageQueryParams = QLPageQueryParameterImpl.builder()
                                                   .limit(100)
                                                   .selectionSet(mockSelectionSet)
                                                   .dataFetchingEnvironment(dataFetchingEnvironment)
                                                   .build();
    QLExecutionConnection connection = executionConnectionDataFetcher.fetchConnection(filters, pageQueryParams, null);
    assertThat(connection).isNotNull();
    assertThat(connection.getNodes()).hasSize(1);
    assertThat(connection.getNodes().get(0)).isInstanceOf(QLWorkflowExecution.class);
    assertThat((((QLWorkflowExecution) connection.getNodes().get(0))).getWorkflowId()).isEqualTo(WORKFLOW1);
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testExecutionConnectionWithArtifactBuildNo() {
    array[0] = pipelineExecutionId;
    QLExecutionFilter executionFilter =
        QLExecutionFilter.builder()
            .application(
                QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(new String[] {APP1_ID_ACCOUNT1}).build())
            .artifactBuildNo(QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(new String[] {"1"}).build())
            .build();
    when(dataFetchingEnvironment.getArguments())
        .thenReturn(Collections.singletonMap(ExecutionConnectionDataFetcher.INDIRECT_EXECUTION_FIELD, true));

    List<QLExecutionFilter> filters = Arrays.asList(executionFilter);
    QLPageQueryParameterImpl pageQueryParams = QLPageQueryParameterImpl.builder()
                                                   .limit(100)
                                                   .selectionSet(mockSelectionSet)
                                                   .dataFetchingEnvironment(dataFetchingEnvironment)
                                                   .build();
    QLExecutionConnection connection = executionConnectionDataFetcher.fetchConnection(filters, pageQueryParams, null);
    assertThat(connection).isNotNull();
    assertThat(connection.getNodes()).hasSize(1);
    assertThat(connection.getNodes().get(0)).isInstanceOf(QLWorkflowExecution.class);
    assertThat((((QLWorkflowExecution) connection.getNodes().get(0))).getWorkflowId()).isEqualTo(WORKFLOW1);
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testExecutionConnectionWithHelmChartVersion() {
    array[0] = pipelineExecutionId;
    QLExecutionFilter executionFilter =
        QLExecutionFilter.builder()
            .application(
                QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(new String[] {APP1_ID_ACCOUNT1}).build())
            .helmChartVersion(
                QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(new String[] {"helm-1.23"}).build())
            .build();
    when(dataFetchingEnvironment.getArguments())
        .thenReturn(Collections.singletonMap(ExecutionConnectionDataFetcher.INDIRECT_EXECUTION_FIELD, true));

    List<QLExecutionFilter> filters = Arrays.asList(executionFilter);
    QLPageQueryParameterImpl pageQueryParams = QLPageQueryParameterImpl.builder()
                                                   .limit(100)
                                                   .selectionSet(mockSelectionSet)
                                                   .dataFetchingEnvironment(dataFetchingEnvironment)
                                                   .build();
    QLExecutionConnection connection = executionConnectionDataFetcher.fetchConnection(filters, pageQueryParams, null);
    assertThat(connection).isNotNull();
    assertThat(connection.getNodes()).hasSize(1);
    assertThat(connection.getNodes().get(0)).isInstanceOf(QLWorkflowExecution.class);
    assertThat((((QLWorkflowExecution) connection.getNodes().get(0))).getWorkflowId()).isEqualTo(WORKFLOW1);
  }
}
