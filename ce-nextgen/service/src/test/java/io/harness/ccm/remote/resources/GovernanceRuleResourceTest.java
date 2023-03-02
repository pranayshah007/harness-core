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
import io.harness.ccm.CENextGenConfiguration;
import io.harness.ccm.audittrails.events.RuleCreateEvent;
import io.harness.ccm.audittrails.events.RuleDeleteEvent;
import io.harness.ccm.audittrails.events.RuleEnforcementCreateEvent;
import io.harness.ccm.audittrails.events.RuleEnforcementDeleteEvent;
import io.harness.ccm.audittrails.events.RuleEnforcementUpdateEvent;
import io.harness.ccm.audittrails.events.RuleSetCreateEvent;
import io.harness.ccm.audittrails.events.RuleSetDeleteEvent;
import io.harness.ccm.audittrails.events.RuleSetUpdateEvent;
import io.harness.ccm.audittrails.events.RuleUpdateEvent;
import io.harness.ccm.rbac.CCMRbacHelper;
import io.harness.ccm.remote.resources.governance.GovernanceRuleEnforcementResource;
import io.harness.ccm.remote.resources.governance.GovernanceRuleResource;
import io.harness.ccm.remote.resources.governance.GovernanceRuleSetResource;
import io.harness.ccm.views.dto.CreateRuleDTO;
import io.harness.ccm.views.dto.CreateRuleEnforcementDTO;
import io.harness.ccm.views.dto.CreateRuleSetDTO;
import io.harness.ccm.views.entities.Rule;
import io.harness.ccm.views.entities.RuleEnforcement;
import io.harness.ccm.views.entities.RuleSet;
import io.harness.ccm.views.helper.GovernanceRuleFilter;
import io.harness.ccm.views.helper.RuleCloudProviderType;
import io.harness.ccm.views.helper.RuleList;
import io.harness.ccm.views.service.GovernanceRuleService;
import io.harness.ccm.views.service.RuleEnforcementService;
import io.harness.ccm.views.service.RuleExecutionService;
import io.harness.ccm.views.service.RuleSetService;
import io.harness.connector.ConnectorResourceClient;
import io.harness.outbox.api.OutboxService;
import io.harness.remote.GovernanceConfig;
import io.harness.rule.Owner;
import io.harness.telemetry.TelemetryReporter;
import io.harness.yaml.schema.YamlSchemaProvider;
import io.harness.yaml.validator.YamlSchemaValidator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j

@RunWith(MockitoJUnitRunner.class)
public class GovernanceRuleResourceTest extends CategoryTest {
  private GovernanceRuleService governanceRuleService = mock(GovernanceRuleService.class);
  private RuleSetService ruleSetService = mock(RuleSetService.class);
  private RuleEnforcementService ruleEnforcementService = mock(RuleEnforcementService.class);
  private CCMRbacHelper rbacHelper = mock(CCMRbacHelper.class);
  private ConnectorResourceClient connectorResourceClient = mock(ConnectorResourceClient.class);
  private RuleExecutionService rulesExecutionService = mock(RuleExecutionService.class);
  private TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
  private OutboxService outboxService = mock(OutboxService.class);
  @Mock CENextGenConfiguration configuration;
  @Mock private TelemetryReporter telemetryReporter;
  GovernanceConfig governanceConfig;
  @Mock private YamlSchemaProvider yamlSchemaProvider;
  @Mock private YamlSchemaValidator yamlSchemaValidator;

  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String UUID = "UUID";
  private static final String UUIDSET = "UUID2";
  private static final String UUIDENF = "UUID3";
  private static final String NAME = "Name";
  private static final String NAMESET = "NameSet";
  private static final String REGION = "REGION";
  private static final String POLICY =
      "policies:\n  - name: test\n    resource: elb\n    filters:\n      - Instances: []\n    actions:\n      - type: tag\n        tag: tag\n        value: tagged\n";
  private static final Boolean OOTB = false;
  private static final String CRON = "0 0 0 1 1 *";
  private static final int LIMITpolicyPerAccountLimit = 300;
  private static final int LIMITpoliciesInPack = 30;
  private static final int LIMITpoliciesInEnforcement = 30;
  private static final int LIMITpacksInEnforcement = 30;
  private static final int LIMITregionLimit = 5;
  private static final int LIMITaccountLimit = 100;
  private static final RuleCloudProviderType CLOUD = AWS;

  private Rule rule;
  private List<Rule> listOfRule = new ArrayList<>();
  private GovernanceRuleResource rulesManagement;
  private RuleSet ruleSet;
  private GovernanceRuleSetResource ruleSetManagement;
  private RuleEnforcement ruleEnforcement;
  private GovernanceRuleEnforcementResource ruleEnforcementManagement;

  @Captor private ArgumentCaptor<RuleCreateEvent> rulesCreateEventArgumentCaptor;
  @Captor private ArgumentCaptor<RuleUpdateEvent> rulesUpdateEventArgumentCaptor;
  @Captor private ArgumentCaptor<RuleDeleteEvent> rulesDeleteEventArgumentCaptor;

  @Captor private ArgumentCaptor<RuleSetCreateEvent> rulesSetCreateEventArgumentCaptor;
  @Captor private ArgumentCaptor<RuleSetUpdateEvent> rulesSetUpdateEventArgumentCaptor;
  @Captor private ArgumentCaptor<RuleSetDeleteEvent> rulesSetDeleteEventArgumentCaptor;

  @Captor private ArgumentCaptor<RuleEnforcementCreateEvent> rulesEnforcementCreateEventArgumentCaptor;
  @Captor private ArgumentCaptor<RuleEnforcementUpdateEvent> rulesEnforcementUpdateEventArgumentCaptor;
  @Captor private ArgumentCaptor<RuleEnforcementDeleteEvent> rulesEnforcementDeleteEventArgumentCaptor;

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
    GovernanceRuleFilter governancePolicyFilter = GovernanceRuleFilter.builder().build();
    listOfRule.add(rule);
    RuleList ruleList = RuleList.builder().rule(listOfRule).build();
    when(governanceRuleService.list(governancePolicyFilter)).thenReturn(ruleList);
    governanceConfig = GovernanceConfig.builder()
                           .policyPerAccountLimit(LIMITpolicyPerAccountLimit)
                           .policiesInPack(LIMITpoliciesInPack)
                           .policiesInEnforcement(LIMITpoliciesInEnforcement)
                           .packsInEnforcement(LIMITpacksInEnforcement)
                           .regionLimit(LIMITregionLimit)
                           .accountLimit(LIMITaccountLimit)
                           .build();
    when(configuration.getGovernanceConfig()).thenReturn(governanceConfig);
    rulesManagement = new GovernanceRuleResource(governanceRuleService, ruleEnforcementService, ruleSetService,
        connectorResourceClient, rulesExecutionService, telemetryReporter, transactionTemplate, outboxService,
        yamlSchemaProvider, yamlSchemaValidator, configuration, rbacHelper);
    when(governanceRuleService.fetchById(ACCOUNT_ID, UUID, true)).thenReturn(rule);

    ruleSet = RuleSet.builder()
                  .uuid(UUIDSET)
                  .accountId(ACCOUNT_ID)
                  .name(NAMESET)
                  .rulesIdentifier(Collections.singletonList(UUID))
                  .isOOTB(OOTB)
                  .cloudProvider(CLOUD)
                  .build();
    when(ruleSetService.fetchById(ACCOUNT_ID, UUIDSET, false)).thenReturn(ruleSet);
    ruleSetManagement = new GovernanceRuleSetResource(ruleSetService, governanceRuleService, telemetryReporter,
        outboxService, transactionTemplate, configuration, rbacHelper);
    when(ruleSetService.fetchById(ACCOUNT_ID, UUIDSET, true)).thenReturn(ruleSet);

    ruleEnforcement = RuleEnforcement.builder()
                          .uuid(UUIDENF)
                          .accountId(ACCOUNT_ID)
                          .name(NAME)
                          .ruleIds(Collections.singletonList(UUID))
                          .ruleSetIDs(Collections.singletonList(UUID))
                          .cloudProvider(CLOUD)
                          .executionSchedule(CRON)
                          .targetRegions(Collections.singletonList(REGION))
                          .targetAccounts(Collections.singletonList(ACCOUNT_ID))
                          .build();
    ruleEnforcementManagement = new GovernanceRuleEnforcementResource(
        ruleEnforcementService, telemetryReporter, transactionTemplate, outboxService, configuration, rbacHelper);
    when(ruleEnforcementService.listId(ACCOUNT_ID, UUIDENF, false)).thenReturn(ruleEnforcement);
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
  public void testCreatePolicySet() {
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    ruleSetManagement.create(ACCOUNT_ID, CreateRuleSetDTO.builder().ruleSet(ruleSet).build());
    verify(transactionTemplate, times(1)).execute(any());
    verify(outboxService, times(1)).save(rulesSetCreateEventArgumentCaptor.capture());
    RuleSetCreateEvent ruleSetCreateEvent = rulesSetCreateEventArgumentCaptor.getValue();
    assertThat(ruleSet).isEqualTo(ruleSetCreateEvent.getRuleSet());
    verify(ruleSetService).save(ruleSet);
  }

  @Test
  @Owner(developers = SAHIBA)
  @Category(UnitTests.class)
  public void testCreatePolicyEnforcement() {
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    ruleEnforcementManagement.create(
        ACCOUNT_ID, CreateRuleEnforcementDTO.builder().ruleEnforcement(ruleEnforcement).build());
    verify(transactionTemplate, times(1)).execute(any());
    verify(outboxService, times(1)).save(rulesEnforcementCreateEventArgumentCaptor.capture());
    RuleEnforcementCreateEvent ruleEnforcementCreateEvent = rulesEnforcementCreateEventArgumentCaptor.getValue();
    assertThat(ruleEnforcement).isEqualTo(ruleEnforcementCreateEvent.getRuleEnforcement());
    verify(ruleEnforcementService).save(ruleEnforcement);
  }

  @Test
  @Owner(developers = SAHIBA)
  @Category(UnitTests.class)
  public void testUpdatePolicy() {
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
  public void testUpdatePolicySet() {
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    ruleSetManagement.updateRuleSet(ACCOUNT_ID, CreateRuleSetDTO.builder().ruleSet(ruleSet).build());
    verify(transactionTemplate, times(1)).execute(any());
    verify(outboxService, times(1)).save(rulesSetUpdateEventArgumentCaptor.capture());
    RuleSetUpdateEvent rulesSetUpdateEvent = rulesSetUpdateEventArgumentCaptor.getValue();
    assertThat(ruleSet).isEqualTo(rulesSetUpdateEvent.getRuleSet());
    verify(ruleSetService).update(ACCOUNT_ID, ruleSet);
  }

  @Test
  @Owner(developers = SAHIBA)
  @Category(UnitTests.class)
  public void testUpdatePolicyEnforcement() {
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    ruleEnforcementManagement.update(
        ACCOUNT_ID, CreateRuleEnforcementDTO.builder().ruleEnforcement(ruleEnforcement).build());
    verify(transactionTemplate, times(1)).execute(any());
    verify(outboxService, times(1)).save(rulesEnforcementUpdateEventArgumentCaptor.capture());
    RuleEnforcementUpdateEvent ruleEnforcementUpdateEvent = rulesEnforcementUpdateEventArgumentCaptor.getValue();
    assertThat(ruleEnforcement).isEqualTo(ruleEnforcementUpdateEvent.getRuleEnforcement());
    verify(ruleEnforcementService).update(ruleEnforcement);
  }

  @Test
  @Owner(developers = SAHIBA)
  @Category(UnitTests.class)
  public void testDeletePolicyEnforcement() {
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    ruleEnforcementManagement.delete(ACCOUNT_ID, UUIDENF);
    verify(transactionTemplate, times(1)).execute(any());
    verify(outboxService, times(1)).save(rulesEnforcementDeleteEventArgumentCaptor.capture());
    RuleEnforcementDeleteEvent ruleEnforcementDeleteEvent = rulesEnforcementDeleteEventArgumentCaptor.getValue();
    assertThat(ruleEnforcement).isEqualTo(ruleEnforcementDeleteEvent.getRuleEnforcement());
    verify(ruleEnforcementService).delete(ACCOUNT_ID, UUIDENF);
  }

  @Test
  @Owner(developers = SAHIBA)
  @Category(UnitTests.class)
  public void testDeletePolicySet() {
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    ruleSetManagement.delete(ACCOUNT_ID, UUIDSET);
    verify(transactionTemplate, times(1)).execute(any());
    verify(outboxService, times(1)).save(rulesSetDeleteEventArgumentCaptor.capture());
    RuleSetDeleteEvent ruleSetDeleteEvent = rulesSetDeleteEventArgumentCaptor.getValue();
    assertThat(ruleSet).isEqualTo(ruleSetDeleteEvent.getRuleSet());
    verify(ruleSetService).delete(ACCOUNT_ID, UUIDSET);
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
