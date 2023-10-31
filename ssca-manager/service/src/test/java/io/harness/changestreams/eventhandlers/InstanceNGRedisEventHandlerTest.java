/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.changestreams.eventhandlers;

import static io.harness.rule.OwnerRule.ARPITJ;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.BuilderFactory;
import io.harness.SSCAManagerTestBase;
import io.harness.category.element.UnitTests;
import io.harness.debezium.DebeziumChangeEvent;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;

public class InstanceNGRedisEventHandlerTest extends SSCAManagerTestBase {
  @Inject InstanceNGRedisEventHandler handler;
  @Mock MongoTemplate mongoTemplate;
  @Mock MongoConverter mongoConverter;
  private BuilderFactory builderFactory;
  private DebeziumChangeEvent event;

  @Before
  public void setup() throws IllegalAccessException, InvalidProtocolBufferException {
    MockitoAnnotations.initMocks(this);
    FieldUtils.writeField(handler, "mongoTemplate", mongoTemplate, true);
    String messageString = readFile("debezium-create-event-message.txt");
    event = DebeziumChangeEvent.parseFrom(Base64.getDecoder().decode(messageString));
    builderFactory = BuilderFactory.getDefault();
    Mockito.when(mongoTemplate.getConverter()).thenReturn(mongoConverter);

    Mockito.when(mongoConverter.read(Mockito.any(), Mockito.any()))
        .thenReturn(builderFactory.getInstanceNGEntityBuilder().build());
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testHandleCreateEvent() {
    Boolean response = handler.handleCreateEvent("instanceId", event.getValue());
    assertThat(response).isEqualTo(true);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testHandleDeleteEvent() {
    Boolean response = handler.handleDeleteEvent("instanceId");
    assertThat(response).isEqualTo(true);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testHandleUpdateEvent() {
    Boolean response = handler.handleUpdateEvent("instanceId", event.getValue());
    assertThat(response).isEqualTo(true);
  }

  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }
}
