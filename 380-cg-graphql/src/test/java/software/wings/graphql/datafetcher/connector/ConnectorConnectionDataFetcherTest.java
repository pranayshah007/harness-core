/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.connector;

import static io.harness.rule.OwnerRule.PRABU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.CustomArtifactServerConfig;
import software.wings.beans.User;
import software.wings.dl.WingsPersistence;
import software.wings.graphql.datafetcher.AbstractDataFetcherTestBase;
import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.graphql.schema.query.QLPageQueryParameterImpl;
import software.wings.graphql.schema.type.QLConnectorType;
import software.wings.graphql.schema.type.aggregation.QLEnumOperator;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;
import software.wings.graphql.schema.type.aggregation.QLTimeFilter;
import software.wings.graphql.schema.type.aggregation.QLTimeOperator;
import software.wings.graphql.schema.type.aggregation.connector.QLConnectorFilter;
import software.wings.graphql.schema.type.aggregation.connector.QLConnectorTypeFilter;
import software.wings.graphql.schema.type.connector.QLConnectorsConnection;
import software.wings.graphql.schema.type.connector.QLCustomConnector;
import software.wings.graphql.schema.type.connector.QLDockerConnector;
import software.wings.security.UserThreadLocal;
import software.wings.settings.SettingValue;

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

public class ConnectorConnectionDataFetcherTest extends AbstractDataFetcherTestBase {
  @Mock DataFetchingEnvironment dataFetchingEnvironment;
  @Inject WingsPersistence wingsPersistence;

  @Inject @InjectMocks ConnectorConnectionDataFetcher connectorConnectionDataFetcher;
  @InjectMocks @Spy DataFetcherUtils dataFetcherUtils;

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
    SettingValue settingValue = CustomArtifactServerConfig.builder().accountId(ACCOUNT1_ID).build();
    createConnector(ACCOUNT1_ID, APP1_ID_ACCOUNT1, CONNECTOR_ID1_ACCOUNT1, "CONNECTOR_NAME", settingValue);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testFetchConnection() {
    array[0] = CONNECTOR_ID1_ACCOUNT1;
    QLIdFilter idFilter = QLIdFilter.builder().operator(QLIdOperator.NOT_IN).values(array).build();
    QLConnectorFilter connectorFilter =
        QLConnectorFilter.builder()
            .connector(idFilter)
            .connectorType(QLConnectorTypeFilter.builder()
                               .operator(QLEnumOperator.IN)
                               .values(new QLConnectorType[] {QLConnectorType.DOCKER})
                               .build())
            .createdAt(QLTimeFilter.builder().operator(QLTimeOperator.BEFORE).value(System.currentTimeMillis()).build())
            .build();
    List<QLConnectorFilter> connectorFilters = Arrays.asList(connectorFilter);
    QLPageQueryParameterImpl pageQueryParams =
        QLPageQueryParameterImpl.builder().limit(100).selectionSet(mockSelectionSet).build();
    QLConnectorsConnection connection =
        connectorConnectionDataFetcher.fetchConnection(connectorFilters, pageQueryParams, null);
    assertThat(connection).isNotNull();
    assertThat(connection.getNodes()).hasSize(1);
    assertThat(connection.getNodes().get(0)).isInstanceOf(QLDockerConnector.class);
    assertThat(((QLDockerConnector) connection.getNodes().get(0)).getName()).isEqualTo("Harness Docker Hub");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testFetchConnectionWithEmptyFilters() {
    List<QLConnectorFilter> connectorFilters = Collections.emptyList();
    QLPageQueryParameterImpl pageQueryParams =
        QLPageQueryParameterImpl.builder().limit(100).selectionSet(mockSelectionSet).build();

    QLConnectorsConnection connection =
        connectorConnectionDataFetcher.fetchConnection(connectorFilters, pageQueryParams, null);
    assertThat(connection).isNotNull();
    assertThat(connection.getNodes()).hasSize(2);
    assertThat(connection.getNodes().get(1)).isInstanceOf(QLCustomConnector.class);
    assertThat(((QLCustomConnector) connection.getNodes().get(1)).getId()).isEqualTo(CONNECTOR_ID1_ACCOUNT1);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testGenerateFilter() {
    doReturn("fieldValue").when(dataFetcherUtils).getFieldValue(any(), any());
    doReturn("source").when(dataFetchingEnvironment).getSource();
    doReturn("accountid").when(dataFetcherUtils).getAccountId(dataFetchingEnvironment);

    QLConnectorFilter connectorFilter =
        connectorConnectionDataFetcher.generateFilter(dataFetchingEnvironment, "Connector", "value");
    assertThat(connectorFilter.getConnector()).isNotNull();
    assertThat(connectorFilter.getConnector().getOperator()).isEqualTo(QLIdOperator.EQUALS);
    assertThat(connectorFilter.getConnector().getValues()).containsExactly("fieldValue");
  }
}
