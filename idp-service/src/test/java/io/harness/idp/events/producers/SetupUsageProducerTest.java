/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.producers;

import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.*;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.BackstageEnvSecretVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;

import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class SetupUsageProducerTest extends CategoryTest {
  public static final String ACCOUNT_IDENTIFIER = "testAccount";
  public static final String IDP_CONNECTOR_IDENTIFIER = "testIdpConnector";
  public static final String MESSAGE_ID = "message_id";
  public static final String CONFIG_ENV = "configEnv";
  public static final String SECRET_ENV = "secretEnv";
  public static final String HARNESS_SECRET_IDENTIFIER = "secret_id";
  public static final String HARNESS_CONNECTOR_IDENTIFIER = "connector_id";
  @Captor private ArgumentCaptor<Message> messageCaptor;
  @Mock private Producer eventProducer;
  private SetupUsageProducer setupUsageProducer;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    setupUsageProducer = new SetupUsageProducer(eventProducer);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testPublishEnvVariableSetupUsage() {
    List<BackstageEnvVariable> envVariables = new ArrayList<>();

    BackstageEnvVariable configVariable = new BackstageEnvVariable();
    configVariable.setType(BackstageEnvVariable.TypeEnum.CONFIG);
    configVariable.setEnvName(CONFIG_ENV);

    BackstageEnvSecretVariable secretVariable = new BackstageEnvSecretVariable();
    secretVariable.setType(BackstageEnvVariable.TypeEnum.SECRET);
    secretVariable.setEnvName(SECRET_ENV);
    secretVariable.setHarnessSecretIdentifier(HARNESS_SECRET_IDENTIFIER);

    envVariables.add(configVariable);
    envVariables.add(secretVariable);

    when(eventProducer.send(any())).thenReturn(MESSAGE_ID);

    setupUsageProducer.publishEnvVariableSetupUsage(envVariables, ACCOUNT_IDENTIFIER);

    verify(eventProducer, times(1)).send(messageCaptor.capture());
    Message capturedMessage = messageCaptor.getValue();
    assertEquals(ACCOUNT_IDENTIFIER, capturedMessage.getMetadata().get("accountId"));
    assertEquals("SECRETS", capturedMessage.getMetadata().get("referredEntityType"));
    assertEquals("flushCreate", capturedMessage.getMetadata().get("action"));
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testPublishEnvVariableSetupUsageEventsFrameworkDown() {
    List<BackstageEnvVariable> envVariables = new ArrayList<>();

    BackstageEnvVariable configVariable = new BackstageEnvVariable();
    configVariable.setType(BackstageEnvVariable.TypeEnum.CONFIG);
    configVariable.setEnvName(CONFIG_ENV);

    BackstageEnvSecretVariable secretVariable = new BackstageEnvSecretVariable();
    secretVariable.setType(BackstageEnvVariable.TypeEnum.SECRET);
    secretVariable.setEnvName(SECRET_ENV);
    secretVariable.setHarnessSecretIdentifier(HARNESS_SECRET_IDENTIFIER);

    envVariables.add(configVariable);
    envVariables.add(secretVariable);

    when(eventProducer.send(any())).thenThrow(EventsFrameworkDownException.class);

    setupUsageProducer.publishEnvVariableSetupUsage(envVariables, ACCOUNT_IDENTIFIER);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testDeleteEnvVariableSetupUsage() {
    List<BackstageEnvVariable> envVariables = new ArrayList<>();

    BackstageEnvVariable configVariable = new BackstageEnvVariable();
    configVariable.setType(BackstageEnvVariable.TypeEnum.CONFIG);
    configVariable.setEnvName(CONFIG_ENV);

    BackstageEnvVariable envVariable = new BackstageEnvVariable();
    envVariable.setType(BackstageEnvVariable.TypeEnum.SECRET);
    envVariable.setEnvName(SECRET_ENV);

    envVariables.add(configVariable);
    envVariables.add(envVariable);

    when(eventProducer.send(any())).thenReturn(MESSAGE_ID);

    setupUsageProducer.deleteEnvVariableSetupUsage(envVariables, ACCOUNT_IDENTIFIER);

    verify(eventProducer, times(1)).send(any());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testDeleteEnvVariableSetupUsageEventsFrameworkDown() {
    List<BackstageEnvVariable> envVariables = new ArrayList<>();

    BackstageEnvVariable envVariable = new BackstageEnvVariable();
    envVariable.setType(BackstageEnvVariable.TypeEnum.SECRET);
    envVariable.setEnvName(SECRET_ENV);

    envVariables.add(envVariable);

    when(eventProducer.send(any())).thenThrow(EventsFrameworkDownException.class);

    setupUsageProducer.deleteEnvVariableSetupUsage(envVariables, ACCOUNT_IDENTIFIER);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testPublishConnectorSetupUsage() {
    when(eventProducer.send(any())).thenReturn(MESSAGE_ID);

    setupUsageProducer.publishConnectorSetupUsage(
        ACCOUNT_IDENTIFIER, HARNESS_CONNECTOR_IDENTIFIER, IDP_CONNECTOR_IDENTIFIER);

    verify(eventProducer, times(1)).send(any());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testPublishConnectorSetupUsageEventsFrameworkDown() {
    when(eventProducer.send(any())).thenThrow(EventsFrameworkDownException.class);

    setupUsageProducer.publishConnectorSetupUsage(
        ACCOUNT_IDENTIFIER, HARNESS_CONNECTOR_IDENTIFIER, IDP_CONNECTOR_IDENTIFIER);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testDeleteConnectorSetupUsage() {
    when(eventProducer.send(any())).thenReturn(MESSAGE_ID);

    setupUsageProducer.deleteConnectorSetupUsage(ACCOUNT_IDENTIFIER, IDP_CONNECTOR_IDENTIFIER);

    verify(eventProducer, times(1)).send(any());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testDeleteConnectorSetupUsageEventsFrameworkDown() {
    when(eventProducer.send(any())).thenThrow(EventsFrameworkDownException.class);

    setupUsageProducer.deleteConnectorSetupUsage(ACCOUNT_IDENTIFIER, IDP_CONNECTOR_IDENTIFIER);
  }
}
