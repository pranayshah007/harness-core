/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.grpc;

import static io.harness.rule.OwnerRule.HANTANG;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.entities.events.CeExceptionRecord;
import io.harness.ccm.commons.entities.events.PublishedMessage;
import io.harness.ccm.health.CeExceptionRecordDao;
import io.harness.event.payloads.CeExceptionMessage;
import io.harness.rule.Owner;

import com.google.protobuf.Any;
import com.google.protobuf.Message;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class ExceptionMessageProcessorTest extends CategoryTest {
  private String accountId = "ACCOUNT_ID";
  private String clusterId = "CLUSTER_ID";

  @Mock CeExceptionRecordDao CeExceptionRecordDao;
  @InjectMocks ExceptionMessageProcessor processor;
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldProcess() {
    CeExceptionMessage message =
        CeExceptionMessage.newBuilder().setClusterId(clusterId).setMessage("Exception").build();
    io.harness.ccm.commons.entities.events.PublishedMessage publishedMessage = getPublishedMessage(message);
    CeExceptionRecord exception =
        CeExceptionRecord.builder().accountId(accountId).clusterId(clusterId).message("Exception").build();
    processor.process(publishedMessage);
    verify(CeExceptionRecordDao).save(eq(exception));
  }

  private io.harness.ccm.commons.entities.events.PublishedMessage getPublishedMessage(Message message) {
    Any payload = Any.pack(message);
    return PublishedMessage.builder()
        .accountId(accountId)
        .data(payload.toByteArray())
        .type(message.getClass().getName())
        .category("Exception")
        .build();
  }
}
