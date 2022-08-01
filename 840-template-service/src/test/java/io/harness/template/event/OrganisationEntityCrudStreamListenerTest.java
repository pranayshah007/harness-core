/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.event;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ORGANIZATION_ENTITY;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.consumer.Message;
import io.harness.pms.contracts.interrupts.InterruptEvent;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.rule.Owner;
import io.harness.template.services.NGTemplateService;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDC)
public class OrganisationEntityCrudStreamListenerTest {
  @Mock NGTemplateService templateService;
  @InjectMocks OrgEntityCrudStreamListener orgEntityCrudStreamListener;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testHandleMessage() {
    Message message = Message.newBuilder().build();
    assertTrue(orgEntityCrudStreamListener.handleMessage(message));
    message =
        Message.newBuilder()
            .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                            .putMetadata(ENTITY_TYPE, ORGANIZATION_ENTITY)
                            .putMetadata(ACTION, DELETE_ACTION)
                            .setData(InterruptEvent.newBuilder().setType(InterruptType.ABORT).build().toByteString())
                            .build())
            .build();
    orgEntityCrudStreamListener.handleMessage(message);
    verify(templateService, times(1)).deleteAllOrgLevelTemplates(any(), any());
  }
}
