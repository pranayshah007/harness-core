/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.workflow;

import static io.harness.rule.OwnerRule.PRABU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.User;
import software.wings.beans.Workflow;
import software.wings.dl.WingsPersistence;
import software.wings.graphql.datafetcher.AbstractDataFetcherTestBase;
import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.graphql.datafetcher.pipeline.PipelineConnectionDataFetcher;
import software.wings.graphql.schema.query.QLPageQueryParameterImpl;
import software.wings.graphql.schema.type.QLWorkflow;
import software.wings.graphql.schema.type.QLWorkflowConnection;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;
import software.wings.graphql.schema.type.aggregation.tag.QLTagInput;
import software.wings.graphql.schema.type.aggregation.workflow.QLWorkflowFilter;
import software.wings.graphql.schema.type.aggregation.workflow.QLWorkflowTagFilter;
import software.wings.graphql.schema.type.aggregation.workflow.QLWorkflowTagType;
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
import org.mockito.Spy;

public class WorkflowConnectionDataFetcherTest extends AbstractDataFetcherTestBase {
  public static final String WORKFLOW = "WORKFLOW";
  @Mock DataFetchingEnvironment dataFetchingEnvironment;
  @Inject WingsPersistence wingsPersistence;

  @InjectMocks @Inject PipelineConnectionDataFetcher pipelineConnectionDataFetcher;
  @InjectMocks @Inject WorkflowConnectionDataFetcher workflowConnectionDataFetcher;
  @InjectMocks @Spy DataFetcherUtils dataFetcherUtils;

  Workflow workflow;

  String[] array = new String[1];

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
    workflow = createWorkflow(ACCOUNT1_ID, APP1_ID_ACCOUNT1, WORKFLOW);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testFetchConnection() {
    array[0] = workflow.getUuid();
    QLIdFilter idFilter = QLIdFilter.builder().operator(QLIdOperator.IN).values(array).build();
    QLTagInput tagInput = QLTagInput.builder().name("tag").value("value").build();
    List<QLTagInput> tagInputList = Arrays.asList(tagInput);
    QLWorkflowFilter workflowFilter =
        QLWorkflowFilter.builder()
            .workflow(idFilter)
            .application(QLIdFilter.builder().operator(QLIdOperator.IN).values(new String[] {APP1_ID_ACCOUNT1}).build())
            .build();
    List<QLWorkflowFilter> workflowFilters = Arrays.asList(workflowFilter);
    QLPageQueryParameterImpl pageQueryParams =
        QLPageQueryParameterImpl.builder().limit(100).selectionSet(mockSelectionSet).build();
    QLWorkflowConnection connection =
        workflowConnectionDataFetcher.fetchConnection(workflowFilters, pageQueryParams, null);
    assertThat(connection).isNotNull();
    assertThat(connection.getNodes()).hasSize(1);
    assertThat(connection.getNodes().get(0).getName()).isEqualTo(WORKFLOW);

    workflowFilter =
        QLWorkflowFilter.builder()
            .tag(QLWorkflowTagFilter.builder().tags(tagInputList).entityType(QLWorkflowTagType.APPLICATION).build())
            .build();
    connection = workflowConnectionDataFetcher.fetchConnection(Arrays.asList(workflowFilter), pageQueryParams, null);
    assertThat(connection).isNotNull();
    assertThat(connection.getNodes()).isEmpty();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testFetchConnectionWithEmptyFilters() {
    List<QLWorkflowFilter> workflowFilters = Collections.emptyList();
    QLPageQueryParameterImpl pageQueryParams =
        QLPageQueryParameterImpl.builder().limit(100).selectionSet(mockSelectionSet).build();

    QLWorkflowConnection connection =
        workflowConnectionDataFetcher.fetchConnection(workflowFilters, pageQueryParams, null);
    assertThat(connection).isNotNull();
    assertThat(connection.getNodes()).hasSize(3);
    assertThat(connection.getNodes().stream().map(QLWorkflow::getName))
        .containsExactlyInAnyOrder(WORKFLOW, "To-Do List K8s Canary", "To-Do List K8s Rolling");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testGenerateFilter() {
    doReturn("fieldValue").when(dataFetcherUtils).getFieldValue(any(), any());
    doReturn("source").when(dataFetchingEnvironment).getSource();
    doReturn("accountid").when(dataFetcherUtils).getAccountId(dataFetchingEnvironment);

    QLWorkflowFilter workflowFilter =
        workflowConnectionDataFetcher.generateFilter(dataFetchingEnvironment, "Workflow", "value");
    assertThat(workflowFilter.getWorkflow()).isNotNull();
    assertThat(workflowFilter.getWorkflow().getOperator()).isEqualTo(QLIdOperator.EQUALS);
    assertThat(workflowFilter.getWorkflow().getValues()).containsExactly("fieldValue");
    workflowFilter = workflowConnectionDataFetcher.generateFilter(dataFetchingEnvironment, "Application", "value");
    assertThat(workflowFilter.getApplication()).isNotNull();
  }
}
