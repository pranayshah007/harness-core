/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.handler;

import static io.harness.rule.OwnerRule.JENNY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorConnectivityDetails;
import io.harness.connector.entities.Connector;
import io.harness.connector.entities.embedded.vaultconnector.VaultConnector;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.ng.core.handler.connector.ConnectorDisconnectHandler;
import io.harness.notification.NotificationTriggerRequest;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.repositories.ConnectorRepository;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;

public class ConnectorDisconnectHandlerTest extends CategoryTest {
  @Mock MongoTemplate mongoTemplate;
  private PersistenceIteratorFactory persistenceIteratorFactory;
  @Mock private NotificationClient notificationClient;
  private ConnectorRepository connectorRepository;
  private ConnectorDisconnectHandler connectorDisconnectHandler;

  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String ORG_ID = "ORG_ID";
  private static final String PROJECT_ID = "PROJECT_ID";
  private static final String IDENTIFIER = "IDENTIFIER";
  private static final long TIMESTAMP = 1698209786;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    connectorDisconnectHandler = new ConnectorDisconnectHandler(
        persistenceIteratorFactory, mongoTemplate, notificationClient, connectorRepository);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testSendNotificationWhenConnectorDisconnect() {
    ArgumentCaptor<NotificationTriggerRequest> argumentCaptor =
        ArgumentCaptor.forClass(NotificationTriggerRequest.class);
    connectorDisconnectHandler.handle(getConnector());
    verify(notificationClient).sendNotificationTrigger(argumentCaptor.capture());
    NotificationTriggerRequest notificationTriggerRequest = argumentCaptor.getValue();
    assertThat(notificationTriggerRequest).isNotNull();
    assertThat(notificationTriggerRequest.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(notificationTriggerRequest.getOrgId()).isEqualTo(ORG_ID);
    assertThat(notificationTriggerRequest.getProjectId()).isEqualTo(PROJECT_ID);
    verify(notificationClient, times(1)).sendNotificationTrigger(any(NotificationTriggerRequest.class));
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testSendNotificationWhenConnectorDisconnectAccountLevel() {
    ArgumentCaptor<NotificationTriggerRequest> argumentCaptor =
        ArgumentCaptor.forClass(NotificationTriggerRequest.class);
    connectorDisconnectHandler.handle(getConnectorAccountLevel());
    verify(notificationClient).sendNotificationTrigger(argumentCaptor.capture());
    NotificationTriggerRequest notificationTriggerRequest = argumentCaptor.getValue();
    assertThat(notificationTriggerRequest).isNotNull();
    assertThat(notificationTriggerRequest.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(notificationTriggerRequest.getOrgId()).isEqualTo("");
    assertThat(notificationTriggerRequest.getProjectId()).isEqualTo("");
    verify(notificationClient, times(1)).sendNotificationTrigger(any(NotificationTriggerRequest.class));
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testSendNotificationWhenConnectorDisconnectOrgLevel() {
    ArgumentCaptor<NotificationTriggerRequest> argumentCaptor =
        ArgumentCaptor.forClass(NotificationTriggerRequest.class);
    connectorDisconnectHandler.handle(getConnectorOrgLevel());
    verify(notificationClient).sendNotificationTrigger(argumentCaptor.capture());
    NotificationTriggerRequest notificationTriggerRequest = argumentCaptor.getValue();
    assertThat(notificationTriggerRequest).isNotNull();
    assertThat(notificationTriggerRequest.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(notificationTriggerRequest.getOrgId()).isEqualTo(ORG_ID);
    assertThat(notificationTriggerRequest.getProjectId()).isEqualTo("");
    verify(notificationClient, times(1)).sendNotificationTrigger(any(NotificationTriggerRequest.class));
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testSendNotificationOnlyOnceWhenConnectorDisconnect() {
    ArgumentCaptor<NotificationTriggerRequest> argumentCaptor =
        ArgumentCaptor.forClass(NotificationTriggerRequest.class);
    Connector connector = getConnector();
    connector.setConnectivityDetails(ConnectorConnectivityDetails.builder()
                                         .status(ConnectivityStatus.UNKNOWN)
                                         .lastConnectedAt(TIMESTAMP)
                                         .lastAlertSent(TIMESTAMP)
                                         .build());
    connectorDisconnectHandler.handle(connector);
    verify(notificationClient, times(0)).sendNotificationTrigger(any(NotificationTriggerRequest.class));
  }

  private Connector getConnector() {
    Connector connector = VaultConnector.builder().build();
    connector.setType(ConnectorType.VAULT);
    connector.setAccountIdentifier(ACCOUNT_ID);
    connector.setIdentifier(IDENTIFIER);
    connector.setOrgIdentifier(ORG_ID);
    connector.setProjectIdentifier(PROJECT_ID);
    connector.setConnectivityDetails(
        ConnectorConnectivityDetails.builder().status(ConnectivityStatus.UNKNOWN).lastConnectedAt(TIMESTAMP).build());
    return connector;
  }

  private Connector getConnectorAccountLevel() {
    Connector connector = VaultConnector.builder().build();
    connector.setType(ConnectorType.VAULT);
    connector.setAccountIdentifier(ACCOUNT_ID);
    connector.setIdentifier(IDENTIFIER);
    connector.setConnectivityDetails(
        ConnectorConnectivityDetails.builder().status(ConnectivityStatus.UNKNOWN).lastConnectedAt(TIMESTAMP).build());
    return connector;
  }

  private Connector getConnectorOrgLevel() {
    Connector connector = VaultConnector.builder().build();
    connector.setType(ConnectorType.VAULT);
    connector.setAccountIdentifier(ACCOUNT_ID);
    connector.setIdentifier(IDENTIFIER);
    connector.setOrgIdentifier(ORG_ID);
    connector.setConnectivityDetails(
        ConnectorConnectivityDetails.builder().status(ConnectivityStatus.UNKNOWN).lastConnectedAt(TIMESTAMP).build());
    return connector;
  }
}
