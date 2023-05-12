/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.search;

import static io.harness.rule.OwnerRule.UTKARSH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.AccountType;
import software.wings.search.entities.application.ApplicationSearchEntity;
import software.wings.search.framework.ElasticsearchClient;
import software.wings.search.framework.ElasticsearchIndexManager;
import software.wings.search.framework.ElasticsearchRequestHandler;
import software.wings.search.framework.SearchEntity;
import software.wings.search.framework.SearchResult;
import software.wings.search.framework.SearchResults;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

public class ElasticsearchServiceImplTest extends WingsBaseTest {
  @Spy private Set<SearchEntity<?>> searchEntities = new HashSet<>();
  @Mock private ElasticsearchIndexManager elasticsearchIndexManager;
  @Mock private ElasticsearchClient elasticsearchClient;
  @Inject @InjectMocks private ElasticsearchServiceImpl elasticsearchService;
  @Inject private HPersistence persistence;

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testGetSearchResults() throws IOException {
    String searchString = "value";
    Account account = getAccount(AccountType.PAID);
    String accountId = persistence.save(account);

    SearchResponse searchResponse = SearchRequestHandlerTestUtils.getSearchResponse(ApplicationSearchEntity.TYPE);

    when(elasticsearchIndexManager.getAliasName(ApplicationSearchEntity.TYPE)).thenReturn(ApplicationSearchEntity.TYPE);
    when(elasticsearchClient.search(any())).thenReturn(searchResponse);

    BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
    List<SearchResult> searchResultList = new ArrayList<>();
    SearchResult searchResult = mock(SearchResult.class);
    searchResultList.add(searchResult);
    ElasticsearchRequestHandler elasticsearchRequestHandler = mock(ElasticsearchRequestHandler.class);
    when(elasticsearchRequestHandler.createQuery(searchString, accountId)).thenReturn(boolQueryBuilder);
    when(elasticsearchRequestHandler.translateHitsToSearchResults(searchResponse.getHits(), accountId))
        .thenReturn(searchResultList);
    when(elasticsearchRequestHandler.processSearchResults(searchResultList)).thenReturn(searchResultList);
    when(elasticsearchRequestHandler.filterSearchResults(searchResultList)).thenReturn(searchResultList);

    SearchEntity searchEntity = mock(SearchEntity.class);
    searchEntities.add(searchEntity);
    when(searchEntity.getElasticsearchRequestHandler()).thenReturn(elasticsearchRequestHandler);
    when(searchEntity.getType()).thenReturn(ApplicationSearchEntity.TYPE);
    SearchResults searchResults = elasticsearchService.getSearchResults(searchString, accountId);
    assertThat(searchResults).isNotNull();
    assertThat(searchResults.getSearchResults().get(ApplicationSearchEntity.TYPE).size()).isEqualTo(1);
  }
}
