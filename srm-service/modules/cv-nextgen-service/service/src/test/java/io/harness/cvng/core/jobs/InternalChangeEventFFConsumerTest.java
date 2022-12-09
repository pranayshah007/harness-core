/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.jobs;

import static io.harness.rule.OwnerRule.ARPITJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.change.ChangeCategory;
import io.harness.cvng.beans.change.ChangeEventDTO;
import io.harness.cvng.beans.change.ChangeSourceType;
import io.harness.cvng.core.services.api.ChangeEventService;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.schemas.cv.InternalChangeEventDTO;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.List;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class InternalChangeEventFFConsumerTest extends CvNextGenTestBase {
  @Inject InternalChangeEventFFConsumer internalChangeEventFFConsumer;

  @Mock ChangeEventService changeEventService;

  BuilderFactory builderFactory;

  @Before
  public void setup() throws IllegalAccessException {
    MockitoAnnotations.initMocks(this);
    builderFactory = BuilderFactory.getDefault();
    FieldUtils.writeField(internalChangeEventFFConsumer, "changeEventService", changeEventService, true);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testProcessMessage() throws InterruptedException {
    InternalChangeEventDTO internalChangeEventDTO = builderFactory.getInternalChangeEventBuilder().build();
    final ArgumentCaptor<ChangeEventDTO> captor = ArgumentCaptor.forClass(ChangeEventDTO.class);
    internalChangeEventFFConsumer.processMessage(
        Message.newBuilder()
            .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                            .setData(internalChangeEventDTO.toByteString())
                            .build())
            .build());

    Mockito.verify(changeEventService, times(1)).register(captor.capture());
    List<ChangeEventDTO> changeEventDTOs = captor.getAllValues();
    assertThat(changeEventDTOs.size()).isEqualTo(1);

    assertThat(changeEventDTOs.get(0).getAccountId()).isEqualTo(internalChangeEventDTO.getAccountId());
    assertThat(changeEventDTOs.get(0).getOrgIdentifier()).isEqualTo(internalChangeEventDTO.getOrgIdentifier());
    assertThat(changeEventDTOs.get(0).getProjectIdentifier()).isEqualTo(internalChangeEventDTO.getProjectIdentifier());
    assertThat(changeEventDTOs.get(0).getServiceIdentifier()).isEqualTo(internalChangeEventDTO.getServiceIdentifier(0));
    assertThat(changeEventDTOs.get(0).getEnvIdentifier()).isEqualTo(internalChangeEventDTO.getEnvironmentIdentifier(0));
    assertThat(changeEventDTOs.get(0).getCategory()).isEqualTo(ChangeCategory.FEATURE_FLAG);
    // assertThat(changeEventDTOs.get(0).getEventTime(internalChangeEventDTO.getExecutionTime()));
    assertThat(changeEventDTOs.get(0).getType()).isEqualTo(ChangeSourceType.INTERNAL_CHANGE_SOURCE_FF);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testProcessMessage_multipleServiceEnv() throws InterruptedException {
    InternalChangeEventDTO internalChangeEventDTO = builderFactory.getInternalChangeEventBuilder().build();
    final ArgumentCaptor<ChangeEventDTO> captor = ArgumentCaptor.forClass(ChangeEventDTO.class);
    internalChangeEventFFConsumer.processMessage(
        Message.newBuilder()
            .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                            .setData(internalChangeEventDTO.toByteString())
                            .build())
            .build());

    Mockito.verify(changeEventService, times(4)).register(captor.capture());
    List<ChangeEventDTO> changeEventDTOs = captor.getAllValues();
    assertThat(changeEventDTOs.size()).isEqualTo(4);

    assertThat(changeEventDTOs.get(3).getAccountId()).isEqualTo(internalChangeEventDTO.getAccountId());
    assertThat(changeEventDTOs.get(3).getOrgIdentifier()).isEqualTo(internalChangeEventDTO.getOrgIdentifier());
    assertThat(changeEventDTOs.get(3).getProjectIdentifier()).isEqualTo(internalChangeEventDTO.getProjectIdentifier());
    assertThat(changeEventDTOs.get(3).getServiceIdentifier()).isEqualTo(internalChangeEventDTO.getServiceIdentifier(0));
    assertThat(changeEventDTOs.get(3).getEnvIdentifier()).isEqualTo(internalChangeEventDTO.getEnvironmentIdentifier(1));
  }
}
