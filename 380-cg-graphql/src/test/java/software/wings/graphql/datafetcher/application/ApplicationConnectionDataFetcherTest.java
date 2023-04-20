/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.application;

import static io.harness.rule.OwnerRule.MILOS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.dl.WingsPersistence;
import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.graphql.datafetcher.tag.TagHelper;
import software.wings.graphql.schema.query.QLPageQueryParameterImpl;
import software.wings.graphql.schema.type.QLApplicationConnection;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;
import software.wings.graphql.schema.type.aggregation.application.QLApplicationFilter;
import software.wings.graphql.schema.type.aggregation.application.QLApplicationTagFilter;
import software.wings.graphql.schema.type.aggregation.application.QLApplicationTagType;
import software.wings.graphql.schema.type.aggregation.tag.QLTagInput;

import com.google.common.collect.ImmutableMap;
import dev.morphia.query.FieldEnd;
import dev.morphia.query.MorphiaIterator;
import dev.morphia.query.Query;
import graphql.execution.MergedSelectionSet;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingFieldSelectionSet;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.SelectedField;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;

public class ApplicationConnectionDataFetcherTest extends CategoryTest {
  @Mock FieldEnd<Query> field;
  @Mock MorphiaIterator morphiaIterator;
  @Mock Query query;
  @Mock TagHelper tagHelper;
  @Mock WingsPersistence wingsPersistence;
  @Mock DataFetchingEnvironment dataFetchingEnvironment;
  @Mock FeatureFlagService featureFlagService;

  @InjectMocks @Spy ApplicationConnectionDataFetcher applicationConnectionDataFetcher;
  @InjectMocks @Spy ApplicationQueryHelper applicationQueryHelper;
  @InjectMocks @Spy DataFetcherUtils dataFetcherUtils;

  private String accountId = "ACCOUNT_ID";
  private String appId = "APP_ID";
  private String entityId = "ENTITY_ID";
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
  public void setUp() throws Exception {
    initMocks(this);

    array[0] = appId;
    Set<String> entityIds = new HashSet<>();
    entityIds.add(entityId);

    when(wingsPersistence.createAuthorizedQuery(any())).thenReturn(query);
    when(tagHelper.getEntityIdsFromTags(any(), any(), any())).thenReturn(entityIds);
    when(query.field(anyString())).thenReturn(field);
    when(query.fetch(any())).thenReturn(morphiaIterator);
    Mockito.doReturn(accountId).when(applicationConnectionDataFetcher).getAccountId();
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testFetchConnection() {
    QLIdFilter idFilter = QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(array).build();
    QLTagInput tagInput = QLTagInput.builder().name("tagName").value("tagValue").build();
    List<QLTagInput> tagInputList = Arrays.asList(tagInput);
    QLApplicationTagFilter tagFilter =
        QLApplicationTagFilter.builder().entityType(QLApplicationTagType.APPLICATION).tags(tagInputList).build();
    QLApplicationFilter appFilter = QLApplicationFilter.builder().application(idFilter).tag(tagFilter).build();
    List<QLApplicationFilter> appFilterList = Arrays.asList(appFilter);
    QLPageQueryParameterImpl pageQueryParams =
        QLPageQueryParameterImpl.builder().limit(100).selectionSet(mockSelectionSet).build();

    QLApplicationConnection connection =
        applicationConnectionDataFetcher.fetchConnection(appFilterList, pageQueryParams, null);
    assertThat(connection).isNotNull();
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testFetchConnectionWithEmptyFilters() {
    List<QLApplicationFilter> appFilterList = Collections.emptyList();
    QLPageQueryParameterImpl pageQueryParams =
        QLPageQueryParameterImpl.builder().limit(100).selectionSet(mockSelectionSet).build();

    QLApplicationConnection connection =
        applicationConnectionDataFetcher.fetchConnection(appFilterList, pageQueryParams, null);
    assertThat(connection).isNotNull();
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testGenerateFilter() throws IllegalAccessException, NoSuchMethodException, InvocationTargetException {
    doReturn("fieldValue").when(dataFetcherUtils).getFieldValue(any(), any());
    doReturn(
        ImmutableMap.of("clientMutationId", "req1", "applicationId", "appid", "description", "new app description"))
        .when(dataFetchingEnvironment)
        .getArguments();
    doReturn("source").when(dataFetchingEnvironment).getSource();
    doReturn("accountid").when(dataFetcherUtils).getAccountId(dataFetchingEnvironment);

    QLApplicationFilter applicationFilter =
        applicationConnectionDataFetcher.generateFilter(dataFetchingEnvironment, "Application", "value");
    assertThat(applicationFilter).isNotNull();
  }
}
