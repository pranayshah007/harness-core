/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.pipeline;

import static io.harness.rule.OwnerRule.PRABU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.Pipeline;
import software.wings.beans.User;
import software.wings.dl.WingsPersistence;
import software.wings.graphql.datafetcher.AbstractDataFetcherTestBase;
import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.graphql.schema.query.QLPageQueryParameterImpl;
import software.wings.graphql.schema.type.QLPipeline;
import software.wings.graphql.schema.type.QLPipelineConnection;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;
import software.wings.graphql.schema.type.aggregation.pipeline.QLPipelineFilter;
import software.wings.graphql.schema.type.aggregation.pipeline.QLPipelineTagFilter;
import software.wings.graphql.schema.type.aggregation.pipeline.QLPipelineTagType;
import software.wings.graphql.schema.type.aggregation.tag.QLTagInput;
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

public class PipelineConnectionDataFetcherTest extends AbstractDataFetcherTestBase {
  public static final String PIPELINE = "PIPELINE";
  @Mock DataFetchingEnvironment dataFetchingEnvironment;
  @Inject WingsPersistence wingsPersistence;

  @InjectMocks @Inject PipelineConnectionDataFetcher pipelineConnectionDataFetcher;
  @InjectMocks @Spy DataFetcherUtils dataFetcherUtils;

  Pipeline pipeline;

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
    pipeline = createPipeline(ACCOUNT1_ID, APP1_ID_ACCOUNT1, PIPELINE);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testFetchConnection() {
    array[0] = pipeline.getUuid();
    QLIdFilter idFilter = QLIdFilter.builder().operator(QLIdOperator.IN).values(array).build();
    QLTagInput tagInput = QLTagInput.builder().name("tag").value("value").build();
    List<QLTagInput> tagInputList = Arrays.asList(tagInput);
    QLPipelineFilter qlPipelineFilter =
        QLPipelineFilter.builder()
            .pipeline(idFilter)
            .application(QLIdFilter.builder().operator(QLIdOperator.IN).values(new String[] {APP1_ID_ACCOUNT1}).build())
            .build();
    List<QLPipelineFilter> pipelineFilters = Arrays.asList(qlPipelineFilter);
    QLPageQueryParameterImpl pageQueryParams =
        QLPageQueryParameterImpl.builder().limit(100).selectionSet(mockSelectionSet).build();
    QLPipelineConnection connection =
        pipelineConnectionDataFetcher.fetchConnection(pipelineFilters, pageQueryParams, null);
    assertThat(connection).isNotNull();
    assertThat(connection.getNodes()).hasSize(1);
    assertThat(connection.getNodes().get(0).getName()).isEqualTo(PIPELINE);

    qlPipelineFilter =
        QLPipelineFilter.builder()
            .tag(QLPipelineTagFilter.builder().tags(tagInputList).entityType(QLPipelineTagType.APPLICATION).build())
            .build();
    connection = pipelineConnectionDataFetcher.fetchConnection(Arrays.asList(qlPipelineFilter), pageQueryParams, null);
    assertThat(connection).isNotNull();
    assertThat(connection.getNodes()).isEmpty();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testFetchConnectionWithEmptyFilters() {
    List<QLPipelineFilter> pipelineFilters = Collections.emptyList();
    QLPageQueryParameterImpl pageQueryParams =
        QLPageQueryParameterImpl.builder().limit(100).selectionSet(mockSelectionSet).build();

    QLPipelineConnection connection =
        pipelineConnectionDataFetcher.fetchConnection(pipelineFilters, pageQueryParams, null);
    assertThat(connection).isNotNull();
    assertThat(connection.getNodes()).hasSize(2);
    assertThat(connection.getNodes().stream().map(QLPipeline::getName))
        .containsExactlyInAnyOrder(PIPELINE, "K8s Prod Pipeline");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testGenerateFilter() {
    doReturn("fieldValue").when(dataFetcherUtils).getFieldValue(any(), any());
    doReturn("source").when(dataFetchingEnvironment).getSource();
    doReturn("accountid").when(dataFetcherUtils).getAccountId(dataFetchingEnvironment);

    QLPipelineFilter pipelineFilter =
        pipelineConnectionDataFetcher.generateFilter(dataFetchingEnvironment, "Pipeline", "value");
    assertThat(pipelineFilter.getPipeline()).isNotNull();
    assertThat(pipelineFilter.getPipeline().getOperator()).isEqualTo(QLIdOperator.EQUALS);
    assertThat(pipelineFilter.getPipeline().getValues()).containsExactly("fieldValue");
    pipelineFilter = pipelineConnectionDataFetcher.generateFilter(dataFetchingEnvironment, "Application", "value");
    assertThat(pipelineFilter.getApplication()).isNotNull();
  }
}
