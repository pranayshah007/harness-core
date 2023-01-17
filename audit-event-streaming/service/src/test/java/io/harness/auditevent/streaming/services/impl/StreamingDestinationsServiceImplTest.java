/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.auditevent.streaming.services.impl;

import static io.harness.audit.entities.streaming.StreamingDestination.StreamingDestinationKeys.accountIdentifier;
import static io.harness.audit.entities.streaming.StreamingDestination.StreamingDestinationKeys.identifier;
import static io.harness.audit.entities.streaming.StreamingDestination.StreamingDestinationKeys.name;
import static io.harness.rule.OwnerRule.NISHANT;
import static io.harness.spec.server.audit.v1.model.StreamingDestinationDTO.StatusEnum.ACTIVE;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.audit.entities.streaming.AwsS3StreamingDestination;
import io.harness.audit.entities.streaming.StreamingDestination;
import io.harness.audit.entities.streaming.StreamingDestination.StreamingDestinationKeys;
import io.harness.audit.entities.streaming.StreamingDestinationFilterProperties;
import io.harness.auditevent.streaming.repositories.StreamingDestinationRepository;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.spec.server.audit.v1.model.StreamingDestinationDTO.StatusEnum;

import com.mongodb.BasicDBList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.query.Criteria;

public class StreamingDestinationsServiceImplTest extends CategoryTest {
  @Mock private StreamingDestinationRepository streamingDestinationRepository;
  private StreamingDestinationsServiceImpl streamingDestinationsService;

  ArgumentCaptor<Criteria> criteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);

  public static final int RANDOM_STRING_LENGTH = 10;
  private static final String ACCOUNT_IDENTIFIER = randomAlphabetic(RANDOM_STRING_LENGTH);

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    this.streamingDestinationsService = new StreamingDestinationsServiceImpl(streamingDestinationRepository);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testList() {
    String searchTerm = randomAlphabetic(RANDOM_STRING_LENGTH);
    List<StreamingDestination> streamingDestinations = List.of(AwsS3StreamingDestination.builder().build());
    when(streamingDestinationRepository.findAll(any(Criteria.class))).thenReturn(streamingDestinations);

    List<StreamingDestination> streamingDestinationsReturned = streamingDestinationsService.list(ACCOUNT_IDENTIFIER,
        StreamingDestinationFilterProperties.builder().status(ACTIVE).searchTerm(searchTerm).build());

    assertThat(streamingDestinationsReturned).isNotEmpty();
    assertThat(streamingDestinationsReturned).hasSize(streamingDestinations.size());
    verify(streamingDestinationRepository, times(1)).findAll(criteriaArgumentCaptor.capture());
    assertListCriteria(criteriaArgumentCaptor.getValue(), ACTIVE, searchTerm);
  }

  private void assertListCriteria(Criteria criteria, StatusEnum status, String searchTerm) {
    Document document = criteria.getCriteriaObject();
    assertThat(document).containsEntry(accountIdentifier, ACCOUNT_IDENTIFIER);
    assertThat(document).containsAllEntriesOf(Map.ofEntries(
        Map.entry(accountIdentifier, ACCOUNT_IDENTIFIER), Map.entry(StreamingDestinationKeys.status, status)));
    assertThat(document).containsKey("$or");
    BasicDBList orList = (BasicDBList) document.get("$or");
    int expectedSize = 2;
    assertThat(orList).hasSize(expectedSize);
    Document nameMatcher = (Document) orList.get(0);
    Document identifierMatcher = (Document) orList.get(1);
    assertPatternMatcher(searchTerm, nameMatcher, name);
    assertPatternMatcher(searchTerm, identifierMatcher, identifier);
  }

  private void assertPatternMatcher(String searchTerm, Document document, String key) {
    assertThat(document.get(key)).isInstanceOf(Pattern.class);
    Pattern namePattern = (Pattern) document.get(key);
    assertThat(namePattern.pattern()).isEqualTo(searchTerm);
    assertThat(namePattern.flags()).isEqualTo(Pattern.CASE_INSENSITIVE);
  }
}
