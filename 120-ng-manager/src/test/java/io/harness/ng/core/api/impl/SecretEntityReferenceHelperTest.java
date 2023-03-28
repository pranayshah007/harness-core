/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api.impl;

import static io.harness.EntityType.CONNECTORS;
import static io.harness.EntityType.SECRETS;
import static io.harness.annotations.dev.HarnessTeam.PL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.entitysetupusageclient.EntitySetupUsageHelper;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.protohelper.IdentifierRefProtoDTOHelper;
import io.harness.eventsframework.schemas.entitysetupusage.DeleteSetupUsageDTO;
import io.harness.eventsframework.schemas.entitysetupusage.EntitySetupUsageCreateDTO;
import io.harness.ng.core.entitysetupusage.service.EntitySetupUsageService;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.utils.FullyQualifiedIdentifierHelper;

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
@OwnedBy(PL)
public class SecretEntityReferenceHelperTest extends CategoryTest {
  @InjectMocks SecretEntityReferenceHelper secretEntityReferenceHelper;
  @Mock EntitySetupUsageService entitySetupUsageService;
  @Mock Producer eventProducer;
  @Mock EntitySetupUsageHelper entityReferenceHelper;

  @Before
  public void setup() {
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
  public void createEntityReferenceForSecret() {
    String account = "account";
    String org = "org";
    String project = "project";
    String secretName = "secretName";
    String identifier = "identifier";
    String secretManager = "secretManager";
    when(entityReferenceHelper.createEntityReference(anyString(), any(), any())).thenCallRealMethod();
    secretEntityReferenceHelper.createSetupUsageForSecretManager(
        account, org, project, identifier, secretName, secretManager);
    ArgumentCaptor<Message> argumentCaptor = ArgumentCaptor.forClass(Message.class);
    try {
      verify(eventProducer, times(1)).send(argumentCaptor.capture());
    } catch (EventsFrameworkDownException e) {
      e.printStackTrace();
    }
    EntitySetupUsageCreateDTO entityReferenceDTO = null;
    try {
      entityReferenceDTO = EntitySetupUsageCreateDTO.parseFrom(argumentCaptor.getValue().getData());
    } catch (Exception ex) {
      log.error("Unexpected error :", ex);
    }
    assertThat(entityReferenceDTO.getReferredEntity().getType().toString()).isEqualTo(CONNECTORS.name());
    assertThat(entityReferenceDTO.getAccountIdentifier()).isEqualTo(account);
    assertThat(entityReferenceDTO.getReferredByEntity().getName()).isEqualTo(secretName);
    assertThat(entityReferenceDTO.getReferredByEntity().getType().toString()).isEqualTo(SECRETS.name());
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void deleteSecretEntityReferenceWhenSecretGetsDeleted() {
    String account = "account";
    String org = "org";
    String project = "project";
    String secretName = "secretName";
    String identifier = "identifier";
    String secretManager = "secretManager";
    ArgumentCaptor<Message> argumentCaptor = ArgumentCaptor.forClass(Message.class);
    secretEntityReferenceHelper.deleteSecretEntityReferenceWhenSecretGetsDeleted(
        account, org, project, identifier, secretManager);
    try {
      verify(eventProducer, times(1)).send(argumentCaptor.capture());
    } catch (EventsFrameworkDownException e) {
      e.printStackTrace();
    }
    DeleteSetupUsageDTO deleteSetupUsageDTO = null;
    try {
      deleteSetupUsageDTO = DeleteSetupUsageDTO.parseFrom(argumentCaptor.getValue().getData());
    } catch (Exception ex) {
      log.error("Exception in the event framework");
    }
    assertThat(deleteSetupUsageDTO.getAccountIdentifier()).isEqualTo(account);
    assertThat(deleteSetupUsageDTO.getReferredByEntityFQN())
        .isEqualTo(FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(account, org, project, identifier));
    assertThat(deleteSetupUsageDTO.getReferredByEntityFQN())
        .isEqualTo(FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(account, org, project, identifier));
  }
}
