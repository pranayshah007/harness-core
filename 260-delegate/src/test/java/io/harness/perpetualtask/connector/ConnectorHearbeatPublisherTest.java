/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.connector;

import static io.harness.ng.core.activityhistory.NGActivityStatus.SUCCESS;
import static io.harness.ng.core.activityhistory.NGActivityType.CONNECTIVITY_CHECK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.delegate.beans.connector.ConnectorHeartbeatDelegateResponse;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.protohelper.IdentifierRefProtoDTOHelper;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.eventsframework.schemas.entityactivity.EntityActivityCreateDTO;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@Slf4j
public class ConnectorHearbeatPublisherTest extends CategoryTest {
  @InjectMocks ConnectorHearbeatPublisher connectorHearbeatPublisher;
  @Mock Producer eventProducer;
  private static final String accountId = "accountId";

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    MockedStatic<IdentifierRefProtoDTOHelper> mockedStatic = Mockito.mockStatic(IdentifierRefProtoDTOHelper.class);
    mockedStatic
        .when(()
                  -> IdentifierRefProtoDTOHelper.createIdentifierRefProtoDTO(
                      anyString(), anyString(), anyString(), anyString()))
        .thenCallRealMethod();
    mockedStatic
        .when(()
                  -> IdentifierRefProtoDTOHelper.createIdentifierRefProtoDTO(
                      anyString(), anyString(), anyString(), anyString(), any()))
        .thenCallRealMethod();
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testPushConnectivityCheckActivity() {
    final String orgIdentifier = "orgIdentifier";
    final String projectIdentifier = "projectIdentifier";
    final String connectorIdentifier = "connectorIdentifier";
    final String connectorName = "connectorName";
    final String errorMessage = "errorMessage";
    final long testedAtTime = System.currentTimeMillis();
    ConnectorValidationResult connectorValidationResult = ConnectorValidationResult.builder()
                                                              .status(ConnectivityStatus.SUCCESS)
                                                              .errorSummary(errorMessage)
                                                              .testedAt(testedAtTime)
                                                              .build();
    ConnectorHeartbeatDelegateResponse connectorHeartbeatDelegateResponse =
        ConnectorHeartbeatDelegateResponse.builder()
            .accountIdentifier(accountId)
            .orgIdentifier(orgIdentifier)
            .projectIdentifier(projectIdentifier)
            .identifier(connectorIdentifier)
            .name(connectorName)
            .connectorValidationResult(connectorValidationResult)
            .build();
    connectorHearbeatPublisher.pushConnectivityCheckActivity(accountId, connectorHeartbeatDelegateResponse);
    ArgumentCaptor<Message> argumentCaptor = ArgumentCaptor.forClass(Message.class);
    try {
      verify(eventProducer, times(1)).send(argumentCaptor.capture());
    } catch (Exception ex) {
      log.error("Encountered error while sending tast {}", ex);
    }
    EntityActivityCreateDTO ngActivityDTO = null;
    try {
      ngActivityDTO = EntityActivityCreateDTO.parseFrom(argumentCaptor.getValue().getData());
    } catch (Exception ex) {
      log.info("UnExpected Exception", ex);
    }
    assertThat(ngActivityDTO).isNotNull();
    assertThat(ngActivityDTO.getType()).isEqualTo(CONNECTIVITY_CHECK.toString());
    assertThat(ngActivityDTO.getAccountIdentifier()).isEqualTo(accountId);
    assertThat(ngActivityDTO.getStatus()).isEqualTo(SUCCESS.toString());
    assertThat(ngActivityDTO.getErrorMessage()).isEqualTo(errorMessage);
    assertThat(ngActivityDTO.getActivityTime()).isEqualTo(testedAtTime);
    assertThat(ngActivityDTO.getReferredEntity().getType().toString()).isEqualTo("CONNECTORS");
    assertThat(ngActivityDTO.getReferredEntity().getName()).isEqualTo("connectorName");
    IdentifierRefProtoDTO entityReference = ngActivityDTO.getReferredEntity().getIdentifierRef();
    assertThat(entityReference.getAccountIdentifier().getValue()).isEqualTo(accountId);
    assertThat(entityReference.getIdentifier().getValue()).isEqualTo(connectorIdentifier);
    assertThat(entityReference.getOrgIdentifier().getValue()).isEqualTo(orgIdentifier);
    assertThat(entityReference.getProjectIdentifier().getValue()).isEqualTo(projectIdentifier);
  }
}
