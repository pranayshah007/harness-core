/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.search.framework;

import static io.harness.rule.OwnerRule.UTKARSH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.app.MainConfiguration;
import software.wings.search.entities.application.ApplicationSearchEntity;

import com.google.inject.Inject;
import java.io.IOException;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest.AliasActions;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest.AliasActions.Type;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.CreateIndexResponse;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class ElasticsearchIndexManagerTest extends WingsBaseTest {
  @Mock ElasticsearchClient elasticsearchClient;
  @Mock MainConfiguration mainConfiguration;
  @Inject @InjectMocks ElasticsearchIndexManager elasticsearchIndexManager;
  @Inject private HPersistence persistence;

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void createIndexTest() throws IOException {
    String indexName = "indexName";
    String entityConfiguration = "{\"a\": \"b\"}";

    CreateIndexResponse createIndexResponse = new CreateIndexResponse(true, true, indexName);
    when(elasticsearchClient.createIndex(any())).thenReturn(createIndexResponse);
    boolean isIndexCreated = elasticsearchIndexManager.createIndex(indexName, entityConfiguration);
    assertThat(isIndexCreated).isEqualTo(true);
    ArgumentCaptor<CreateIndexRequest> captor = ArgumentCaptor.forClass(CreateIndexRequest.class);
    verify(elasticsearchClient, times(1)).createIndex(captor.capture());
    CreateIndexRequest createIndexRequest = captor.getValue();
    assertThat(createIndexRequest.index()).isEqualTo(indexName);

    CreateIndexResponse createIndexResponse1 = new CreateIndexResponse(false, false, indexName);
    when(elasticsearchClient.createIndex(any())).thenReturn(createIndexResponse1);
    isIndexCreated = elasticsearchIndexManager.createIndex(indexName, entityConfiguration);
    assertThat(isIndexCreated).isEqualTo(false);
    verify(elasticsearchClient, times(2)).createIndex(any());
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void deleteIndexTest() throws IOException {
    String indexName = "indexName";

    when(elasticsearchClient.indexExists(any())).thenReturn(true);
    AcknowledgedResponse acknowledgedResponse = new AcknowledgedResponse(true);
    when(elasticsearchClient.deleteIndex(any())).thenReturn(acknowledgedResponse);

    boolean isIndexDeleted = elasticsearchIndexManager.deleteIndex(indexName);
    assertThat(isIndexDeleted).isEqualTo(true);

    ArgumentCaptor<GetIndexRequest> getRequestCaptor = ArgumentCaptor.forClass(GetIndexRequest.class);
    verify(elasticsearchClient, times(1)).indexExists(getRequestCaptor.capture());
    GetIndexRequest getIndexRequest = getRequestCaptor.getValue();
    assertThat(getIndexRequest.indices().length).isEqualTo(1);
    assertThat(getIndexRequest.indices()[0]).isEqualTo(indexName);

    ArgumentCaptor<DeleteIndexRequest> deleteRequestCaptor = ArgumentCaptor.forClass(DeleteIndexRequest.class);
    verify(elasticsearchClient, times(1)).deleteIndex(deleteRequestCaptor.capture());
    DeleteIndexRequest deleteIndexRequest = deleteRequestCaptor.getValue();
    assertThat(deleteIndexRequest.indices().length).isEqualTo(1);
    assertThat(deleteIndexRequest.indices()[0]).isEqualTo(indexName);

    when(elasticsearchClient.indexExists(any())).thenReturn(false);
    isIndexDeleted = elasticsearchIndexManager.deleteIndex(indexName);
    assertThat(isIndexDeleted).isEqualTo(true);

    getRequestCaptor = ArgumentCaptor.forClass(GetIndexRequest.class);
    verify(elasticsearchClient, times(2)).indexExists(getRequestCaptor.capture());
    getIndexRequest = getRequestCaptor.getValue();
    assertThat(getIndexRequest.indices().length).isEqualTo(1);
    assertThat(getIndexRequest.indices()[0]).isEqualTo(indexName);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void removeIndexFromAlias() throws IOException {
    String indexName = "indexName";

    when(elasticsearchClient.indexExists(any())).thenReturn(true);
    AcknowledgedResponse acknowledgedResponse = new AcknowledgedResponse(true);
    when(elasticsearchClient.updateAliases(any())).thenReturn(acknowledgedResponse);

    boolean isRemoved = elasticsearchIndexManager.removeIndexFromAlias(indexName);
    assertThat(isRemoved).isEqualTo(true);

    ArgumentCaptor<GetIndexRequest> getRequestCaptor = ArgumentCaptor.forClass(GetIndexRequest.class);
    verify(elasticsearchClient, times(1)).indexExists(getRequestCaptor.capture());
    GetIndexRequest getIndexRequest = getRequestCaptor.getValue();
    assertThat(getIndexRequest.indices().length).isEqualTo(1);
    assertThat(getIndexRequest.indices()[0]).isEqualTo(indexName);

    ArgumentCaptor<IndicesAliasesRequest> aliasRequestCaptor = ArgumentCaptor.forClass(IndicesAliasesRequest.class);
    verify(elasticsearchClient, times(1)).updateAliases(aliasRequestCaptor.capture());
    IndicesAliasesRequest indicesAliasesRequest = aliasRequestCaptor.getValue();
    assertThat(indicesAliasesRequest.getAliasActions().size()).isEqualTo(1);
    AliasActions aliasActions = indicesAliasesRequest.getAliasActions().get(0);
    assertThat(aliasActions.actionType()).isEqualTo(Type.REMOVE_INDEX);
    assertThat(aliasActions.indices().length).isEqualTo(1);
    assertThat(aliasActions.indices()[0]).isEqualTo(indexName);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void attachIndexToAlias() throws IOException {
    String indexName = "indexName";
    String aliasName = "aliasName";

    AcknowledgedResponse acknowledgedResponse = new AcknowledgedResponse(true);
    when(elasticsearchClient.updateAliases(any())).thenReturn(acknowledgedResponse);

    boolean isAttached = elasticsearchIndexManager.attachIndexToAlias(aliasName, indexName);
    assertThat(isAttached).isEqualTo(true);

    ArgumentCaptor<IndicesAliasesRequest> aliasRequestCaptor = ArgumentCaptor.forClass(IndicesAliasesRequest.class);
    verify(elasticsearchClient, times(1)).updateAliases(aliasRequestCaptor.capture());
    IndicesAliasesRequest indicesAliasesRequest = aliasRequestCaptor.getValue();
    assertThat(indicesAliasesRequest.getAliasActions().size()).isEqualTo(1);
    AliasActions aliasActions = indicesAliasesRequest.getAliasActions().get(0);
    assertThat(aliasActions.actionType()).isEqualTo(Type.ADD);
    assertThat(aliasActions.indices().length).isEqualTo(1);
    assertThat(aliasActions.indices()[0]).isEqualTo(indexName);
    assertThat(aliasActions.aliases().length).isEqualTo(1);
    assertThat(aliasActions.aliases()[0]).isEqualTo(aliasName);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void getIndexName() {
    String indexName = "indexName";

    String receivedIndexName = elasticsearchIndexManager.getIndexName(ApplicationSearchEntity.TYPE);
    assertThat(receivedIndexName).isNull();

    SearchEntityIndexState searchEntityIndexState =
        new SearchEntityIndexState(ApplicationSearchEntity.class.getCanonicalName(), "0.1", indexName, false);
    persistence.save(searchEntityIndexState);
    receivedIndexName = elasticsearchIndexManager.getIndexName(ApplicationSearchEntity.TYPE);
    assertThat(receivedIndexName).isEqualTo(indexName);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void getAliasName() {
    String type = "type";
    String suffix = "suffix";
    ElasticsearchConfig elasticsearchConfig = ElasticsearchConfig.builder().uri("url").indexSuffix(suffix).build();
    when(mainConfiguration.getElasticsearchConfig()).thenReturn(elasticsearchConfig);

    String aliasName = elasticsearchIndexManager.getAliasName(type);
    assertThat(aliasName).isEqualTo(type.concat(suffix));
  }
}
