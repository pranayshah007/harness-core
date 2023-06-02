/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aggregator.consumers;

import static io.harness.rule.OwnerRule.JIMIT_GANDHI;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.accesscontrol.AccessControlEntity;
import io.harness.aggregator.AccessControlAdminService;
import io.harness.aggregator.AggregatorTestBase;
import io.harness.aggregator.OpType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import io.debezium.embedded.EmbeddedEngineChangeEvent;
import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.connect.header.ConnectHeaders;
import org.apache.kafka.connect.header.Header;
import org.apache.kafka.connect.source.SourceRecord;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PL)
public class AccessControlDebeziumChangeConsumerTest extends AggregatorTestBase {
  private Deserializer<String> idDeserializer;
  private Map<String, Deserializer<? extends AccessControlEntity>> collectionToDeserializerMap;
  private Map<String, ChangeConsumer<? extends AccessControlEntity>> collectionToConsumerMap;
  private ChangeEventFailureHandler changeEventFailureHandler;
  private AccessControlAdminService accessControlAdminService;
  private AccessControlDebeziumChangeConsumer accessControlDebeziumChangeConsumer;
  private static final String OP_FIELD = "__op";
  DebeziumEngine.RecordCommitter<ChangeEvent<String, String>> recordCommitter;
  private String accountId = "dummyAccountId";
  private ChangeConsumer<MockAccessControlEntity> changeConsumer;
  private Deserializer<? extends AccessControlEntity> accessControlEntityDeserializer;

  @Before
  public void setup() {
    changeEventFailureHandler = mock(ChangeEventFailureHandler.class);
    accessControlAdminService = mock(AccessControlAdminService.class);
    idDeserializer = mock(Deserializer.class);
    collectionToDeserializerMap = new HashMap<>();
    accessControlEntityDeserializer = mock(Deserializer.class);
    collectionToDeserializerMap.put("mockAccessControlEntity", accessControlEntityDeserializer);
    collectionToConsumerMap = new HashMap<>();
    changeConsumer = mock(ChangeConsumer.class);
    collectionToConsumerMap.put("mockAccessControlEntity", changeConsumer);
    accessControlDebeziumChangeConsumer = new AccessControlDebeziumChangeConsumer(idDeserializer,
        collectionToDeserializerMap, collectionToConsumerMap, changeEventFailureHandler, accessControlAdminService);
    recordCommitter = mock(DebeziumEngine.RecordCommitter.class);
  }

  public class MockAccessControlEntity implements AccessControlEntity {
    public MockAccessControlEntity() {}
    @Override
    public Optional<String> getAccountId() {
      return Optional.of(accountId);
    }
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void handleBatch_WithDuplicateUpdateEvents_AvoidsProcessingDuplicates() throws InterruptedException {
    List<ChangeEvent<String, String>> changeEvents = new ArrayList<>();
    int batchSize = 100;
    for (int i = 0; i < batchSize; i++) {
      Header mockHeader = mock(Header.class);
      when(mockHeader.value()).thenReturn("u");
      ConnectHeaders mockConnectHeaders = mock(ConnectHeaders.class);
      when(mockConnectHeaders.lastWithName(OP_FIELD)).thenReturn(mockHeader);

      SourceRecord sourceRecord = new SourceRecord(null, null,
          "access_control_db.accesscontrol.mockAccessControlEntity", 0, null, "", null, null, 0L, mockConnectHeaders);
      EmbeddedEngineChangeEvent<String, String> changeEvent = new EmbeddedEngineChangeEvent<>("", "", sourceRecord);
      changeEvents.add(changeEvent);
      MockAccessControlEntity accessControlEntity = new MockAccessControlEntity();
      when(idDeserializer.deserialize(any(), any())).thenReturn("xyz");
      if (i == 0) {
        doReturn(accessControlEntity).when(accessControlEntityDeserializer).deserialize(any(), any());
        when(accessControlAdminService.isBlocked(any())).thenReturn(false);
        doReturn(true).when(changeConsumer).consumeEvent(any(), anyString(), any());
      }
    }

    accessControlDebeziumChangeConsumer.handleBatch(changeEvents, recordCommitter);
    verify(recordCommitter, times(1)).markBatchFinished();
    for (int i = 0; i < batchSize; i++) {
      verify(recordCommitter, times(1)).markProcessed(changeEvents.get(i));
    }
    verify(changeConsumer, times(1)).consumeEvent(any(), anyString(), any());
  }

  private class MockKey {
    String value;
    MockKey(String value) {
      this.value = value;
    }
    public byte[] getBytes() {
      return value.getBytes();
    }
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void handleBatch_WithCRUDeventIncludingDuplicateUpdateEvents_AvoidsProcessingDuplicateUpdates()
      throws InterruptedException {
    int batchSize = 100;
    List<String> ids = new ArrayList<>();
    Set<String> updateEventsSeen = new HashSet<>();
    Set<String> trackUpdateEventsSeen = new HashSet<>();
    for (int i = 0; i < 30; i++) {
      String id = RandomStringUtils.randomAlphanumeric(3);
      ids.add(id);
    }

    for (int i = 0; i < batchSize; i++) {
      int index = i % 30;
      String id = ids.get(index);
      int randomNum = ThreadLocalRandom.current().nextInt(0, 2 + 1);
      List<String> operationTypes = Arrays.asList("c", "u", "d");
      String operationType = operationTypes.get(randomNum);
      Header mockHeader = mock(Header.class);
      when(mockHeader.value()).thenReturn(operationType);
      ConnectHeaders mockConnectHeaders = mock(ConnectHeaders.class);
      when(mockConnectHeaders.lastWithName(OP_FIELD)).thenReturn(mockHeader);
      MockKey key = new MockKey(id);
      SourceRecord sourceRecord = new SourceRecord(null, null,
          "access_control_db.accesscontrol.mockAccessControlEntity", 0, null, key, null, null, 0L, mockConnectHeaders);
      EmbeddedEngineChangeEvent<String, String> changeEvent = new EmbeddedEngineChangeEvent<>("", "", sourceRecord);
      when(idDeserializer.deserialize(any(), any())).thenReturn(id);
      MockAccessControlEntity accessControlEntity = new MockAccessControlEntity();

      if (!operationType.equals("u")) {
        doReturn(accessControlEntity).when(accessControlEntityDeserializer).deserialize(any(), any());
        when(accessControlAdminService.isBlocked(any())).thenReturn(false);
      } else if (!updateEventsSeen.contains(id)) {
        doReturn(accessControlEntity).when(accessControlEntityDeserializer).deserialize(any(), any());
        when(accessControlAdminService.isBlocked(any())).thenReturn(false);
      }
      doReturn(true).when(changeConsumer).consumeEvent(any(), anyString(), any());

      accessControlDebeziumChangeConsumer.handleEvent(changeEvent, updateEventsSeen);
      if (operationType.equals("u")) {
        if (trackUpdateEventsSeen.contains(id)) {
          verify(changeConsumer, never()).consumeEvent(OpType.fromString(operationType).get(), id, accessControlEntity);
        } else {
          trackUpdateEventsSeen.add(id);
          verify(changeConsumer, times(1))
              .consumeEvent(OpType.fromString(operationType).get(), id, accessControlEntity);
        }
      } else {
        verify(changeConsumer, times(1)).consumeEvent(OpType.fromString(operationType).get(), id, accessControlEntity);
      }
    }
  }
}
