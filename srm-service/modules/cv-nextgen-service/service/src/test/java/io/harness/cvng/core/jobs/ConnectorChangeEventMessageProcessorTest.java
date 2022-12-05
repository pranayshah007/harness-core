/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.jobs;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.KAMAL;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.core.entities.MonitoringSourcePerpetualTask;
import io.harness.cvng.core.services.api.MonitoringSourcePerpetualTaskService;
import io.harness.encryption.Scope;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.rule.Owner;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.protobuf.StringValue;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class ConnectorChangeEventMessageProcessorTest extends CvNextGenTestBase {
  @Inject private ConnectorChangeEventMessageProcessor connectorChangeEventMessageProcessor;

  private String accountIdentifier;
  private String orgIdentifier;
  private String projectIdentifier;
  private String connectorIdentifier;

  @Before
  public void setup() throws IllegalAccessException {
    MockitoAnnotations.initMocks(this);
    accountIdentifier = generateUuid();
    orgIdentifier = generateUuid();
    projectIdentifier = generateUuid();
    connectorIdentifier = "connectorIdentifier";
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testProcessUpdateAction_resetLiveMonitoringPerpetualTask() throws IllegalAccessException {
    MonitoringSourcePerpetualTaskService monitoringSourcePerpetualTaskService =
        Mockito.mock(MonitoringSourcePerpetualTaskService.class);
    MonitoringSourcePerpetualTask monitoringSourcePerpetualTask = MonitoringSourcePerpetualTask.builder()
                                                                      .accountId(accountIdentifier)
                                                                      .orgIdentifier(orgIdentifier)
                                                                      .projectIdentifier(projectIdentifier)
                                                                      .connectorIdentifier(connectorIdentifier)
                                                                      .monitoringSourceIdentifier(generateUuid())
                                                                      .perpetualTaskId(generateUuid())
                                                                      .build();
    when(monitoringSourcePerpetualTaskService.listByConnectorIdentifier(
             accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier, Scope.PROJECT))
        .thenReturn(Lists.newArrayList(monitoringSourcePerpetualTask));

    FieldUtils.writeField(connectorChangeEventMessageProcessor, "monitoringSourcePerpetualTaskService",
        monitoringSourcePerpetualTaskService, true);
    connectorChangeEventMessageProcessor.processUpdateAction(
        EntityChangeDTO.newBuilder()
            .setAccountIdentifier(StringValue.newBuilder().setValue(accountIdentifier).build())
            .setOrgIdentifier(StringValue.newBuilder().setValue(orgIdentifier).build())
            .setProjectIdentifier(StringValue.newBuilder().setValue(projectIdentifier).build())
            .setIdentifier(StringValue.newBuilder().setValue(connectorIdentifier).build())
            .build());
    verify(monitoringSourcePerpetualTaskService, times(1))
        .resetLiveMonitoringPerpetualTask(monitoringSourcePerpetualTask);
  }
}
