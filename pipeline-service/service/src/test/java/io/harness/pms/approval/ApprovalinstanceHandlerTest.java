/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.approval;

import static io.harness.rule.OwnerRule.vivekveman;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.PipelineServiceIteratorsConfig;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator.MongoPersistenceIteratorBuilder;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.steps.approval.step.entities.ApprovalInstance;
import io.harness.steps.approval.step.jira.JiraApprovalHelperService;
import io.harness.steps.approval.step.jira.JiraApprovalSpecParameters;
import io.harness.steps.approval.step.jira.entities.JiraApprovalInstance;
import io.harness.steps.approval.step.servicenow.ServiceNowApprovalHelperService;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.yaml.core.timeout.Timeout;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.data.mongodb.core.MongoTemplate;

public class ApprovalinstanceHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private PipelineServiceIteratorsConfig iteratorsConfig;
  @Mock private JiraApprovalHelperService jiraApprovalHelperService;
  @Mock private MongoTemplate mongoTemplate;
  @Mock private PersistenceIteratorFactory persistenceIteratorFactory;
  @Mock Ambiance ambiance;
  @Mock private ServiceNowApprovalHelperService serviceNowApprovalHelperService;
  public static final String TICKET_NUMBER = "TICKET_NUMBER";
  public static final String CONNECTOR = "CONNECTOR";
  @Mock PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testregisterIterators() {
    ArgumentCaptor<MongoPersistenceIteratorBuilder> captor =
        ArgumentCaptor.forClass(MongoPersistenceIteratorBuilder.class);
    ApprovalInstanceHandler approvalInstanceHandler =
        new ApprovalInstanceHandler(jiraApprovalHelperService, mongoTemplate, persistenceIteratorFactory,
            iteratorsConfig, serviceNowApprovalHelperService, pmsFeatureFlagHelper);
    approvalInstanceHandler.registerIterators();
    verify(persistenceIteratorFactory, times(1))
        .createPumpIteratorWithDedicatedThreadPool(any(), eq(ApprovalInstanceHandler.class), captor.capture());
    MongoPersistenceIteratorBuilder mongoPersistenceIteratorBuilder = captor.getValue();
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testApprovalInstanceHandlerServiceWithFFON() {
    ApprovalInstanceHandler approvalInstanceHandler =
        new ApprovalInstanceHandler(jiraApprovalHelperService, mongoTemplate, persistenceIteratorFactory,
            iteratorsConfig, serviceNowApprovalHelperService, pmsFeatureFlagHelper);
    when(pmsFeatureFlagHelper.isEnabled(null, FeatureName.CDS_DISABLE_JIRA_SERVICENOW_RETRY_INTERVAL)).thenReturn(true);
    StepElementParameters stepElementParameters = getStepElementParametersWithRetryInterval();
    JiraApprovalInstance jiraApprovalInstance =
        JiraApprovalInstance.fromStepParameters(ambiance, stepElementParameters);
    ApprovalInstance entity = (ApprovalInstance) jiraApprovalInstance;
    approvalInstanceHandler.handle(entity);
    verify(jiraApprovalHelperService, times(1)).handlePollingEvent(null, jiraApprovalInstance);
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testApprovalInstanceHandlerServiceWithFFOff() {
    ApprovalInstanceHandler approvalInstanceHandler =
        new ApprovalInstanceHandler(jiraApprovalHelperService, mongoTemplate, persistenceIteratorFactory,
            iteratorsConfig, serviceNowApprovalHelperService, pmsFeatureFlagHelper);
    when(pmsFeatureFlagHelper.isEnabled(null, FeatureName.CDS_DISABLE_JIRA_SERVICENOW_RETRY_INTERVAL))
        .thenReturn(false);
    StepElementParameters stepElementParameters = getStepElementParametersWithoutRetryInterval();
    JiraApprovalInstance jiraApprovalInstance =
        JiraApprovalInstance.fromStepParameters(ambiance, stepElementParameters);
    ApprovalInstance entity = (ApprovalInstance) jiraApprovalInstance;
    approvalInstanceHandler.handle(entity);
    verify(jiraApprovalHelperService, times(0)).handlePollingEvent(null, jiraApprovalInstance);
  }
  private StepElementParameters getStepElementParametersWithRetryInterval() {
    return StepElementParameters.builder()
        .type("JiraApproval")
        .spec(JiraApprovalSpecParameters.builder()
                  .issueKey(ParameterField.<String>builder().value(TICKET_NUMBER).build())
                  .connectorRef(ParameterField.<String>builder().value(CONNECTOR).build())
                  .retryInterval(ParameterField.createValueField(Timeout.fromString("1m")))
                  .build())
        .build();
  }
  private StepElementParameters getStepElementParametersWithoutRetryInterval() {
    return StepElementParameters.builder()
        .type("JiraApproval")
        .spec(JiraApprovalSpecParameters.builder()
                  .issueKey(ParameterField.<String>builder().value(TICKET_NUMBER).build())
                  .connectorRef(ParameterField.<String>builder().value(CONNECTOR).build())
                  .build())
        .build();
  }
}
