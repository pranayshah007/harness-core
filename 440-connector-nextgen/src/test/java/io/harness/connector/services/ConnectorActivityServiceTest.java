/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.services;

import static io.harness.EntityType.CONNECTORS;
import static io.harness.connector.impl.ConnectorActivityServiceImpl.CREATION_DESCRIPTION;
import static io.harness.connector.impl.ConnectorActivityServiceImpl.UPDATE_DESCRIPTION;
import static io.harness.ng.core.activityhistory.NGActivityStatus.SUCCESS;
import static io.harness.ng.core.activityhistory.NGActivityType.ENTITY_CREATION;
import static io.harness.ng.core.activityhistory.NGActivityType.ENTITY_UPDATE;
import static io.harness.rule.OwnerRule.DEEPAK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.impl.ConnectorActivityServiceImpl;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.protohelper.IdentifierRefProtoDTOHelper;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.eventsframework.schemas.entityactivity.EntityActivityCreateDTO;
import io.harness.ng.core.activityhistory.NGActivityType;
import io.harness.ng.core.activityhistory.service.NGActivityService;
import io.harness.rule.Owner;

import com.google.protobuf.InvalidProtocolBufferException;
import java.util.List;
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
public class ConnectorActivityServiceTest extends CategoryTest {
  @InjectMocks ConnectorActivityServiceImpl connectorActivityService;
  @Mock NGActivityService ngActivityService;
  @Mock Producer producer;
  String connectorName = "connector";
  String accountIdentifier = "accountIdentifier";
  String orgIdentifier = "orgIdentifier";
  String projIdentifier = "projIdentifier";
  String identifier = "identifier";

  @Before
  public void setUp() throws Exception {
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

  private ConnectorInfoDTO createConnector() {
    return ConnectorInfoDTO.builder()
        .name(connectorName)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projIdentifier)
        .identifier(identifier)
        .build();
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void createTestForConnectorCreated() {
    ConnectorInfoDTO connector = createConnector();
    connectorActivityService.create(accountIdentifier, connector, ENTITY_CREATION);
    ArgumentCaptor<Message> argumentCaptor = ArgumentCaptor.forClass(Message.class);
    try {
      verify(producer, times(1)).send(argumentCaptor.capture());
    } catch (Exception ex) {
    }
    EntityActivityCreateDTO ngActivity = getEntityActivityDTO(argumentCaptor.getValue());
    verityTheFieldsOFActivity(ngActivity, CREATION_DESCRIPTION, ENTITY_CREATION);
  }

  private EntityActivityCreateDTO getEntityActivityDTO(io.harness.eventsframework.producer.Message message) {
    EntityActivityCreateDTO activityDTO = null;
    try {
      activityDTO = EntityActivityCreateDTO.parseFrom(message.getData());
    } catch (InvalidProtocolBufferException e) {
      log.error("Exception in unpacking EntityActivityCreateDTO for", e);
    }
    return activityDTO;
  }

  private void verityTheFieldsOFActivity(
      EntityActivityCreateDTO ngActivity, String description, NGActivityType activityType) {
    assertThat(ngActivity).isNotNull();
    assertThat(ngActivity.getAccountIdentifier()).isEqualTo(accountIdentifier);
    assertThat(ngActivity.getActivityTime()).isGreaterThan(0L);
    assertThat(ngActivity.getDescription()).isEqualTo(description);
    assertThat(ngActivity.getStatus()).isEqualTo(SUCCESS.toString());
    assertThat(ngActivity.getType()).isEqualTo(activityType.toString());

    assertThat(ngActivity.getReferredEntity().getName()).isEqualTo(connectorName);
    assertThat(ngActivity.getReferredEntity().getType().toString()).isEqualTo(CONNECTORS.name());
    EntityDetailProtoDTO referredEntity = ngActivity.getReferredEntity();
    IdentifierRefProtoDTO identifierRef = referredEntity.getIdentifierRef();
    assertThat(identifierRef.getAccountIdentifier().getValue()).isEqualTo(accountIdentifier);
    assertThat(identifierRef.getOrgIdentifier().getValue()).isEqualTo(orgIdentifier);
    assertThat(identifierRef.getProjectIdentifier().getValue()).isEqualTo(projIdentifier);
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void createTestForConnectorUpdate() {
    ConnectorInfoDTO connector = createConnector();
    connectorActivityService.create(accountIdentifier, connector, ENTITY_UPDATE);
    ArgumentCaptor<Message> argumentCaptor = ArgumentCaptor.forClass(Message.class);
    try {
      verify(producer, times(1)).send(argumentCaptor.capture());
    } catch (Exception ex) {
    }
    EntityActivityCreateDTO ngActivity = getEntityActivityDTO(argumentCaptor.getValue());
    verityTheFieldsOFActivity(ngActivity, UPDATE_DESCRIPTION, ENTITY_UPDATE);
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void deleteAllActivities() {
    String connectorFQN = "connectorFQN";
    connectorActivityService.deleteAllActivities(accountIdentifier, connectorFQN);
    ArgumentCaptor<String> stringArgumentCaptor = ArgumentCaptor.forClass(String.class);
    verify(ngActivityService, times(1))
        .deleteAllActivitiesOfAnEntity(stringArgumentCaptor.capture(), stringArgumentCaptor.capture(), any());
    List<String> arguments = stringArgumentCaptor.getAllValues();
    assertThat(arguments.get(0)).isEqualTo(accountIdentifier);
    assertThat(arguments.get(1)).isEqualTo(connectorFQN);
  }
}
