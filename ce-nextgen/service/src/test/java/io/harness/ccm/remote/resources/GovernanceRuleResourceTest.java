/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources;

import static io.harness.ccm.views.helper.RuleCloudProviderType.AWS;
import static io.harness.rule.OwnerRule.SAHIBA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.audittrails.events.RuleCreateEvent;
import io.harness.ccm.audittrails.events.RuleDeleteEvent;
import io.harness.ccm.audittrails.events.RuleUpdateEvent;
import io.harness.ccm.remote.resources.governance.GovernanceRuleResource;
import io.harness.ccm.views.dto.CreateRuleDTO;
import io.harness.ccm.views.entities.Rule;
import io.harness.ccm.views.helper.RuleCloudProviderType;
import io.harness.ccm.views.service.GovernanceRuleService;
import io.harness.ccm.views.service.RuleEnforcementService;
import io.harness.ccm.views.service.RuleExecutionService;
import io.harness.ccm.views.service.RuleSetService;
import io.harness.connector.ConnectorResourceClient;
import io.harness.outbox.api.OutboxService;
import io.harness.rule.Owner;
import io.harness.telemetry.TelemetryReporter;

import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@RunWith(MockitoJUnitRunner.class)
public class GovernanceRuleResourceTest extends CategoryTest {
  private GovernanceRuleService governanceRuleService = mock(GovernanceRuleService.class);
  private RuleSetService ruleSetService = mock(RuleSetService.class);
  private RuleEnforcementService ruleEnforcementService = mock(RuleEnforcementService.class);
  //  private CCMRbacHelper rbacHelper  mock(CCMRbacHelper.class)
  private ConnectorResourceClient connectorResourceClient = mock(ConnectorResourceClient.class);
  private RuleExecutionService rulesExecutionService = mock(RuleExecutionService.class);
  private TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
  private OutboxService outboxService = mock(OutboxService.class);
  @Mock private TelemetryReporter telemetryReporter;

  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String UUID = "UUID";
  private static final String NAME = "Name";
  private static final String POLICY = "POLICY";
  private static final Boolean OOTB = false;
  private static final RuleCloudProviderType CLOUD = AWS;

  private Rule rule;
  private GovernanceRuleResource rulesManagement;

  @Captor private ArgumentCaptor<RuleCreateEvent> rulesCreateEventArgumentCaptor;
  @Captor private ArgumentCaptor<RuleUpdateEvent> rulesUpdateEventArgumentCaptor;
  @Captor private ArgumentCaptor<RuleDeleteEvent> rulesDeleteEventArgumentCaptor;

  @Before
  public void setUp() throws IllegalAccessException, IOException {
    rule = Rule.builder()
               .uuid(UUID)
               .accountId(ACCOUNT_ID)
               .name(NAME)
               .rulesYaml(POLICY)
               .isOOTB(OOTB)
               .cloudProvider(CLOUD)
               .build();
    when(governanceRuleService.fetchById(ACCOUNT_ID, UUID, false)).thenReturn(rule);
    rulesManagement = new GovernanceRuleResource(governanceRuleService, ruleEnforcementService, ruleSetService,
        connectorResourceClient, rulesExecutionService, telemetryReporter, transactionTemplate, outboxService);
  }

  @Test
  @Owner(developers = SAHIBA)
  @Category(UnitTests.class)
  public void testCreatePolicy() {
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    rulesManagement.create(ACCOUNT_ID, CreateRuleDTO.builder().rule(rule).build());
    verify(transactionTemplate, times(1)).execute(any());
    verify(outboxService, times(1)).save(rulesCreateEventArgumentCaptor.capture());
    RuleCreateEvent ruleCreateEvent = rulesCreateEventArgumentCaptor.getValue();
    assertThat(rule).isEqualTo(ruleCreateEvent.getRule());
    verify(governanceRuleService).save(rule);
  }

  @Test
  @Owner(developers = SAHIBA)
  @Category(UnitTests.class)
  public void testUpadtePolicy() {
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    rulesManagement.updateRule(ACCOUNT_ID, CreateRuleDTO.builder().rule(rule).build());
    verify(transactionTemplate, times(1)).execute(any());
    verify(outboxService, times(1)).save(rulesUpdateEventArgumentCaptor.capture());
    RuleUpdateEvent rulesUpdateEvent = rulesUpdateEventArgumentCaptor.getValue();
    assertThat(rule).isEqualTo(rulesUpdateEvent.getRule());
    verify(governanceRuleService).update(rule, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = SAHIBA)
  @Category(UnitTests.class)
  public void deletePolicy() {
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    rulesManagement.delete(ACCOUNT_ID, UUID);
    verify(transactionTemplate, times(1)).execute(any());
    verify(outboxService, times(1)).save(rulesDeleteEventArgumentCaptor.capture());
    RuleDeleteEvent rulesDeleteEvent = rulesDeleteEventArgumentCaptor.getValue();
    assertThat(rule).isEqualTo(rulesDeleteEvent.getRule());
    verify(governanceRuleService).delete(ACCOUNT_ID, UUID);
  }
}
