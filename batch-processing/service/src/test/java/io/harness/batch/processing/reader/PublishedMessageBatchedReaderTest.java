/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.reader;

import static io.harness.rule.OwnerRule.UTSAV;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.batch.processing.dao.intfc.PublishedMessageDao;
import io.harness.batch.processing.writer.constants.EventTypeConstants;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.entities.events.PublishedMessage;
import io.harness.event.payloads.PodMetric;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableList;
import java.time.Instant;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class PublishedMessageBatchedReaderTest extends CategoryTest {
  @Mock private PublishedMessageDao publishedMessageDao;
  private PublishedMessageBatchedReader publishedMessageBatchedReader;

  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String MESSAGE_TYPE = EventTypeConstants.POD_UTILIZATION;
  private static final Long NOW = Instant.now().toEpochMilli();

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);

    publishedMessageBatchedReader =
        new PublishedMessageBatchedReader(ACCOUNT_ID, MESSAGE_TYPE, NOW, NOW, null, publishedMessageDao);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testReadWithNoItem() {
    when(publishedMessageDao.fetchPublishedMessage(any(), any(), any(), any(), anyInt()))
        .thenReturn(Collections.emptyList());

    assertThat(publishedMessageBatchedReader.read()).isEqualTo(null);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testReadWithAtLeastOneItem() {
    final PublishedMessage expectedPublishedMessage = createPM("0");

    when(publishedMessageDao.fetchPublishedMessage(eq(ACCOUNT_ID), eq(MESSAGE_TYPE), any(), any(), anyInt()))
        .thenReturn(ImmutableList.of(expectedPublishedMessage))
        .thenReturn(Collections.emptyList());

    assertThat(publishedMessageBatchedReader.read()).isNotNull().isEqualTo(expectedPublishedMessage);
    assertThat(publishedMessageBatchedReader.read()).isNull();
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testReadWith2BatchedRead() {
    when(publishedMessageDao.fetchPublishedMessage(eq(ACCOUNT_ID), eq(MESSAGE_TYPE), any(), any(), anyInt()))
        .thenReturn(ImmutableList.of(createPM("0"), createPM("1")))
        .thenReturn(ImmutableList.of(createPM("2")))
        .thenReturn(Collections.emptyList());

    for (int i = 0; i < 3; i++) {
      assertThat(publishedMessageBatchedReader.read()).isNotNull().isEqualTo(createPM(String.valueOf(i)));
    }
    assertThat(publishedMessageBatchedReader.read()).isNull();
  }

  private static PublishedMessage createPM(final String uuid) {
    return PublishedMessage.builder()
        .accountId(ACCOUNT_ID)
        .type(MESSAGE_TYPE)
        .uuid(uuid)
        .message(PodMetric.newBuilder().setName("name" + uuid).build())
        .build();
  }
}
