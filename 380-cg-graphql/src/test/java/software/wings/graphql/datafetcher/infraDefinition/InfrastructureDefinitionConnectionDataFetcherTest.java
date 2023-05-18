/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.infraDefinition;

import static io.harness.rule.OwnerRule.PARDHA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.User;
import software.wings.dl.WingsPersistence;
import software.wings.graphql.datafetcher.AbstractDataFetcherTestBase;
import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.graphql.schema.query.QLPageQueryParameterImpl;
import software.wings.graphql.schema.type.QLInfrastructureDefinition;
import software.wings.graphql.schema.type.QLInfrastructureDefinitionConnection;
import software.wings.graphql.schema.type.QLInfrastructureDefinitionFilter;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;
import software.wings.graphql.schema.type.aggregation.tag.QLTagInput;
import software.wings.infra.InfrastructureDefinition;
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

public class InfrastructureDefinitionConnectionDataFetcherTest extends AbstractDataFetcherTestBase {
  private static final String INFRASTRUCTURE_DEFINITION = "INFRASTRUCTURE_DEFINITION";
  @Mock private DataFetchingEnvironment dataFetchingEnvironment;
  @Inject private WingsPersistence wingsPersistence;

  @Inject
  @InjectMocks
  private InfrastructureDefinitionConnectionDataFetcher infrastructureDefinitionConnectionDataFetcher;
  @InjectMocks @Spy private DataFetcherUtils dataFetcherUtils;

  private InfrastructureDefinition infrastructureDefinition;

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
    createEnv(ACCOUNT1_ID, APP1_ID_ACCOUNT1, ENV1_ID_APP1_ACCOUNT1, ENV1_ID_APP1_ACCOUNT1, TAG_TEAM, TAG_VALUE_TEAM1);
    infrastructureDefinition = createInfrastructureDefinition(ACCOUNT1_ID, ENV1_ID_APP1_ACCOUNT1, APP1_ID_ACCOUNT1,
        INFRA1_ID_ENV1_APP1_ACCOUNT1, INFRA1_ID_ENV1_APP1_ACCOUNT1);
  }

  @Test
  @Owner(developers = PARDHA)
  @Category(UnitTests.class)
  public void testFetchConnection() {
    array[0] = infrastructureDefinition.getUuid();
    QLIdFilter idFilter = QLIdFilter.builder().operator(QLIdOperator.IN).values(array).build();
    QLTagInput tagInput = QLTagInput.builder().name("tag").value("value").build();
    List<QLTagInput> tagInputList = Arrays.asList(tagInput);
    QLInfrastructureDefinitionFilter qlInfrastructureDefinitionFilter =
        QLInfrastructureDefinitionFilter.builder()
            .infrastructureDefinition(idFilter)
            .environment(
                QLIdFilter.builder().operator(QLIdOperator.IN).values(new String[] {ENV1_ID_APP1_ACCOUNT1}).build())
            .build();

    List<QLInfrastructureDefinitionFilter> infrastructureDefinitionFilters =
        Arrays.asList(qlInfrastructureDefinitionFilter);
    QLPageQueryParameterImpl pageQueryParams =
        QLPageQueryParameterImpl.builder().limit(100).selectionSet(mockSelectionSet).build();

    QLInfrastructureDefinitionConnection connection = infrastructureDefinitionConnectionDataFetcher.fetchConnection(
        infrastructureDefinitionFilters, pageQueryParams, null);
    assertThat(connection).isNotNull();
    assertThat(connection.getNodes()).hasSize(1);
    assertThat(connection.getNodes().get(0).getName()).isEqualTo(INFRA1_ID_ENV1_APP1_ACCOUNT1);
  }

  @Test
  @Owner(developers = PARDHA)
  @Category(UnitTests.class)
  public void testFetchConnectionWithEmptyFilters() {
    List<QLInfrastructureDefinitionFilter> infrastructureDefinitionFilters = Collections.emptyList();
    QLPageQueryParameterImpl pageQueryParams =
        QLPageQueryParameterImpl.builder().limit(100).selectionSet(mockSelectionSet).build();

    QLInfrastructureDefinitionConnection connection = infrastructureDefinitionConnectionDataFetcher.fetchConnection(
        infrastructureDefinitionFilters, pageQueryParams, null);
    assertThat(connection).isNotNull();
    assertThat(connection.getNodes()).hasSize(3);
    assertThat(connection.getNodes().stream().map(QLInfrastructureDefinition::getName))
        .containsExactlyInAnyOrder(INFRA1_ID_ENV1_APP1_ACCOUNT1, "K8s", "K8s");
  }

  @Test
  @Owner(developers = PARDHA)
  @Category(UnitTests.class)
  public void testGenerateFilter() {
    doReturn("fieldValue").when(dataFetcherUtils).getFieldValue(any(), any());
    doReturn("source").when(dataFetchingEnvironment).getSource();
    doReturn("accountid").when(dataFetcherUtils).getAccountId(dataFetchingEnvironment);

    QLInfrastructureDefinitionFilter infrastructureDefinitionFilter =
        infrastructureDefinitionConnectionDataFetcher.generateFilter(
            dataFetchingEnvironment, "InfrastructureDefinition", "value");
    assertThat(infrastructureDefinitionFilter.getInfrastructureDefinition()).isNotNull();
    assertThat(infrastructureDefinitionFilter.getInfrastructureDefinition().getOperator())
        .isEqualTo(QLIdOperator.EQUALS);
    assertThat(infrastructureDefinitionFilter.getInfrastructureDefinition().getValues()).containsExactly("fieldValue");
    infrastructureDefinitionFilter =
        infrastructureDefinitionConnectionDataFetcher.generateFilter(dataFetchingEnvironment, "Environment", "value");
    assertThat(infrastructureDefinitionFilter.getEnvironment()).isNotNull();
  }
}
