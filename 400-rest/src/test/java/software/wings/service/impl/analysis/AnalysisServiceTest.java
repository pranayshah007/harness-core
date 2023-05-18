/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.analysis;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.KAMAL;
import static io.harness.rule.OwnerRule.PRAVEEN;

import static software.wings.beans.ElementExecutionSummary.ElementExecutionSummaryBuilder.anElementExecutionSummary;
import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.deployment.InstanceDetails;
import io.harness.exception.WingsException;
import io.harness.jira.JiraAction;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.api.DeploymentType;
import software.wings.api.jira.JiraExecutionData;
import software.wings.beans.Service;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.jira.JiraTaskParameters;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.JiraHelperService;
import software.wings.service.impl.analysis.AnalysisServiceImpl.CLUSTER_TYPE;
import software.wings.service.impl.analysis.CVFeedbackRecord.CVFeedbackRecordKeys;
import software.wings.service.intfc.analysis.AnalysisService;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.StateType;
import software.wings.verification.CVConfiguration;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class AnalysisServiceTest extends WingsBaseTest {
  @Inject WingsPersistence wingsPersistence;
  @Inject AnalysisService analysisService;
  @Mock JiraHelperService jiraHelperService;
  String accountId;
  String appId;
  String serviceId;
  String envId;
  CVConfiguration cvConfiguration;
  String jiraConfigId;

  @Before
  public void setupTests() throws Exception {
    accountId = generateUuid();
    serviceId = generateUuid();
    appId = generateUuid();
    envId = generateUuid();
    jiraConfigId = generateUuid();
    cvConfiguration = new CVConfiguration();
    cvConfiguration.setStateType(StateType.ELK);
    cvConfiguration.setServiceId(serviceId);
    cvConfiguration.setEnvId(envId);
    cvConfiguration.setAccountId(accountId);

    cvConfiguration.setUuid(generateUuid());
    wingsPersistence.save(cvConfiguration);
    FieldUtils.writeField(analysisService, "jiraHelperService", jiraHelperService, true);
    JiraExecutionData executionData =
        JiraExecutionData.builder().executionStatus(ExecutionStatus.SUCCESS).issueUrl("tempJiraUrl").build();
    when(jiraHelperService.createJira(anyString(), anyString(), anyString(), anyObject())).thenReturn(executionData);
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testCreateJira() {
    CVCollaborationProviderParameters cvJiraParameters = CVCollaborationProviderParameters.builder().build();
    cvJiraParameters.setCollaborationProviderConfigId(jiraConfigId);
    JiraTaskParameters taskParameters = JiraTaskParameters.builder()
                                            .jiraAction(JiraAction.CREATE_TICKET)
                                            .project("LE")
                                            .priority("P0")
                                            .description("This is a log message")
                                            .appId(appId)
                                            .build();

    CVFeedbackRecord feedbackRecord = CVFeedbackRecord.builder()
                                          .envId(envId)
                                          .cvConfigId(cvConfiguration.getUuid())
                                          .logMessage("This is a log message")
                                          .clusterType(CLUSTER_TYPE.UNKNOWN)
                                          .priority(FeedbackPriority.P4)
                                          .envId(envId)
                                          .serviceId(serviceId)
                                          .uuid("feedbackRecord1")
                                          .actionTaken(FeedbackAction.UPDATE_PRIORITY)
                                          .clusterLabel(2)
                                          .build();
    wingsPersistence.save(feedbackRecord);
    cvJiraParameters.setCvFeedbackRecord(feedbackRecord);
    cvJiraParameters.setJiraTaskParameters(taskParameters);

    String jiraLink = analysisService.createCollaborationFeedbackTicket(
        accountId, appId, cvConfiguration.getUuid(), null, cvJiraParameters);

    CVFeedbackRecord record = wingsPersistence.createQuery(CVFeedbackRecord.class)
                                  .filter(CVFeedbackRecordKeys.envId, envId)
                                  .filter(CVFeedbackRecordKeys.serviceId, serviceId)
                                  .get();

    assertThat(record.getActionTaken()).isEqualTo(FeedbackAction.UPDATE_PRIORITY);
    assertThat(record.getJiraLink()).isEqualTo(jiraLink);
    assertThat(record.getLogMessage()).isEqualTo("This is a log message");
  }

  @Test(expected = WingsException.class)
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testCreateJiraMissingTaskParams() {
    CVCollaborationProviderParameters cvJiraParameters = CVCollaborationProviderParameters.builder().build();
    cvJiraParameters.setCollaborationProviderConfigId(null);

    CVFeedbackRecord feedbackRecord = CVFeedbackRecord.builder()
                                          .envId(envId)
                                          .cvConfigId(cvConfiguration.getUuid())
                                          .logMessage("This is a log message")
                                          .clusterType(CLUSTER_TYPE.UNKNOWN)
                                          .clusterLabel(2)
                                          .build();
    JiraTaskParameters taskParameters = JiraTaskParameters.builder()
                                            .jiraAction(JiraAction.CREATE_TICKET)
                                            .project("LE")
                                            .priority("P0")
                                            .description("This is a log message")
                                            .appId(appId)
                                            .build();
    cvJiraParameters.setCvFeedbackRecord(feedbackRecord);
    cvJiraParameters.setJiraTaskParameters(taskParameters);

    String jiraLink = analysisService.createCollaborationFeedbackTicket(
        accountId, appId, cvConfiguration.getUuid(), null, cvJiraParameters);
  }

  @Test(expected = WingsException.class)
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testCreateJiraMissingJiraConfigId() {
    CVCollaborationProviderParameters cvJiraParameters = CVCollaborationProviderParameters.builder().build();

    CVFeedbackRecord feedbackRecord = CVFeedbackRecord.builder()
                                          .envId(envId)
                                          .cvConfigId(cvConfiguration.getUuid())
                                          .logMessage("This is a log message")
                                          .clusterType(CLUSTER_TYPE.UNKNOWN)
                                          .clusterLabel(2)
                                          .build();
    cvJiraParameters.setCvFeedbackRecord(feedbackRecord);
    cvJiraParameters.setJiraTaskParameters(null);

    String jiraLink = analysisService.createCollaborationFeedbackTicket(
        accountId, appId, cvConfiguration.getUuid(), null, cvJiraParameters);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void changePriorityAfterJira() {
    testCreateJira();
    CVFeedbackRecord record = wingsPersistence.createQuery(CVFeedbackRecord.class)
                                  .filter(CVFeedbackRecordKeys.envId, envId)
                                  .filter(CVFeedbackRecordKeys.serviceId, serviceId)
                                  .get();
    String jira = record.getJiraLink();
    record.setJiraLink(null);
    analysisService.addToBaseline(accountId, cvConfiguration.getUuid(), null, record);

    CVFeedbackRecord updatedRecord = wingsPersistence.createQuery(CVFeedbackRecord.class)
                                         .filter(CVFeedbackRecordKeys.envId, envId)
                                         .filter(CVFeedbackRecordKeys.serviceId, serviceId)
                                         .get();
    assertThat(updatedRecord.getUuid()).isEqualTo(record.getUuid());
    assertThat(updatedRecord.getActionTaken()).isEqualTo(FeedbackAction.ADD_TO_BASELINE);
    assertThat(updatedRecord.getJiraLink()).isEqualTo(jira);
  }

  @Test(expected = IllegalStateException.class)
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testFeedbackWithEmptyLogMessage() {
    CVFeedbackRecord feedbackRecord = CVFeedbackRecord.builder()
                                          .envId(envId)
                                          .cvConfigId(cvConfiguration.getUuid())
                                          .clusterType(CLUSTER_TYPE.UNKNOWN)
                                          .clusterLabel(2)
                                          .build();

    analysisService.addToBaseline(accountId, cvConfiguration.getUuid(), null, feedbackRecord);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetLastExecutionNodes_forPcfWithInstanceDetails() throws IllegalAccessException {
    final String workflowId =
        wingsPersistence.save(aWorkflow().appId(appId).accountId(accountId).name(generateUuid()).build());
    final String serviceId = wingsPersistence.save(
        Service.builder().appId(appId).deploymentType(DeploymentType.PCF).name(generateUuid()).build());
    final String workflowExecutionId =
        wingsPersistence.save(WorkflowExecution.builder()
                                  .appId(appId)
                                  .accountId(accountId)
                                  .workflowId(workflowId)
                                  .status(ExecutionStatus.SUCCESS)
                                  .serviceExecutionSummaries(Lists.newArrayList(anElementExecutionSummary().build()))
                                  .serviceIds(Lists.newArrayList(serviceId))
                                  .build());

    List<InstanceDetails> instanceDetails = Lists.newArrayList(
        InstanceDetails.builder().hostName("host1").build(), InstanceDetails.builder().hostName("host2").build());
    SweepingOutputService sweepingOutputService = mock(SweepingOutputService.class);
    FieldUtils.writeField(analysisService, "sweepingOutputService", sweepingOutputService, true);
    when(sweepingOutputService.findInstanceDetailsForWorkflowExecution(appId, workflowExecutionId))
        .thenReturn(instanceDetails);
    final Map<String, Map<String, InstanceDetails>> lastExecutionNodes =
        analysisService.getLastExecutionNodes(appId, workflowId);
    Map<String, Map<String, InstanceDetails>> expected = new HashMap<>();
    instanceDetails.forEach(instanceDetail -> {
      Map<String, InstanceDetails> instanceDetailMap = new HashMap<>();
      instanceDetailMap.put("instanceDetails", instanceDetail);
      expected.put(instanceDetail.getHostName(), instanceDetailMap);
    });

    assertThat(lastExecutionNodes).isEqualTo(expected);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testUpdateFeedbackPriority_withFeedbackNote() {
    CVFeedbackRecord feedbackRecord = CVFeedbackRecord.builder()
                                          .envId(envId)
                                          .cvConfigId(cvConfiguration.getUuid())
                                          .logMessage("This is a log message")
                                          .clusterType(CLUSTER_TYPE.UNKNOWN)
                                          .priority(FeedbackPriority.P4)
                                          .envId(envId)
                                          .serviceId(serviceId)
                                          .feedbackNote("testNote1")
                                          .actionTaken(FeedbackAction.UPDATE_PRIORITY)
                                          .clusterLabel(2)
                                          .build();

    boolean savedFeedback =
        analysisService.updateFeedbackPriority(accountId, cvConfiguration.getUuid(), null, feedbackRecord);
    assertThat(savedFeedback).isTrue();

    List<CVFeedbackRecord> feedbackRecords = analysisService.getFeedbacks(cvConfiguration.getUuid(), null, false);
    assertThat(feedbackRecords.size()).isEqualTo(1);
    assertThat(feedbackRecords.get(0).getFeedbackNote()).isEqualTo("testNote1");
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testUpdateFeedbackPriority_withPreExistingFeedbackNote() {
    CVFeedbackRecord feedbackRecord = CVFeedbackRecord.builder()
                                          .envId(envId)
                                          .cvConfigId(cvConfiguration.getUuid())
                                          .logMessage("This is a log message")
                                          .clusterType(CLUSTER_TYPE.UNKNOWN)
                                          .priority(FeedbackPriority.P4)
                                          .envId(envId)
                                          .serviceId(serviceId)
                                          .feedbackNote("testNote1")
                                          .actionTaken(FeedbackAction.UPDATE_PRIORITY)
                                          .clusterLabel(2)
                                          .build();

    boolean savedFeedback =
        analysisService.updateFeedbackPriority(accountId, cvConfiguration.getUuid(), null, feedbackRecord);
    assertThat(savedFeedback).isTrue();

    List<CVFeedbackRecord> feedbackRecords = analysisService.getFeedbacks(cvConfiguration.getUuid(), null, false);
    assertThat(feedbackRecords.size()).isEqualTo(1);

    feedbackRecord.setPriority(FeedbackPriority.P0);
    feedbackRecord.setFeedbackNote("updated feedback note");
    feedbackRecord.setUuid(feedbackRecords.get(0).getUuid());

    savedFeedback = analysisService.updateFeedbackPriority(accountId, cvConfiguration.getUuid(), null, feedbackRecord);
    assertThat(savedFeedback).isTrue();

    feedbackRecords = analysisService.getFeedbacks(cvConfiguration.getUuid(), null, false);
    assertThat(feedbackRecords.size()).isEqualTo(1);
    assertThat(feedbackRecords.get(0).getPriority().name()).isEqualTo(FeedbackPriority.P0.name());
    assertThat(feedbackRecords.get(0).getFeedbackNote()).isEqualTo("updated feedback note");
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testRemoveFromBaseline_withFeedbackNote() {
    CVFeedbackRecord feedbackRecord = CVFeedbackRecord.builder()
                                          .envId(envId)
                                          .cvConfigId(cvConfiguration.getUuid())
                                          .logMessage("This is a log message")
                                          .clusterType(CLUSTER_TYPE.UNKNOWN)
                                          .priority(FeedbackPriority.P4)
                                          .envId(envId)
                                          .serviceId(serviceId)
                                          .feedbackNote("removing from baseline")
                                          .actionTaken(FeedbackAction.REMOVE_FROM_BASELINE)
                                          .clusterLabel(2)
                                          .build();

    boolean savedFeedback =
        analysisService.removeFromBaseline(accountId, cvConfiguration.getUuid(), null, feedbackRecord);
    assertThat(savedFeedback).isTrue();

    List<CVFeedbackRecord> feedbackRecords = analysisService.getFeedbacks(cvConfiguration.getUuid(), null, false);
    assertThat(feedbackRecords.size()).isEqualTo(1);
    assertThat(feedbackRecords.get(0).getFeedbackNote()).isEqualTo("removing from baseline");
    assertThat(feedbackRecords.get(0).getPriority().name()).isEqualTo(FeedbackPriority.P4.name());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testAddToBaseline_withFeedbackNote() {
    CVFeedbackRecord feedbackRecord = CVFeedbackRecord.builder()
                                          .envId(envId)
                                          .cvConfigId(cvConfiguration.getUuid())
                                          .logMessage("This is a log message")
                                          .clusterType(CLUSTER_TYPE.UNKNOWN)
                                          .priority(FeedbackPriority.P4)
                                          .envId(envId)
                                          .serviceId(serviceId)
                                          .feedbackNote("testNote2")
                                          .actionTaken(FeedbackAction.ADD_TO_BASELINE)
                                          .clusterLabel(2)
                                          .build();

    boolean savedFeedback = analysisService.addToBaseline(accountId, cvConfiguration.getUuid(), null, feedbackRecord);
    assertThat(savedFeedback).isTrue();

    List<CVFeedbackRecord> feedbackRecords = analysisService.getFeedbacks(cvConfiguration.getUuid(), null, false);
    assertThat(feedbackRecords.size()).isEqualTo(1);
    assertThat(feedbackRecords.get(0).getFeedbackNote()).isEqualTo("testNote2");
  }
}
