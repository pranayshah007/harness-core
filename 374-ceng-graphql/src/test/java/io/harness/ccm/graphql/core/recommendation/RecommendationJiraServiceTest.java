/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.core.recommendation;

import static io.harness.ccm.commons.beans.recommendation.ResourceType.AZURE_INSTANCE;
import static io.harness.ccm.commons.beans.recommendation.ResourceType.EC2_INSTANCE;
import static io.harness.ccm.commons.beans.recommendation.ResourceType.ECS_SERVICE;
import static io.harness.ccm.commons.beans.recommendation.ResourceType.GOVERNANCE;
import static io.harness.ccm.commons.beans.recommendation.ResourceType.NODE_POOL;
import static io.harness.ccm.commons.beans.recommendation.ResourceType.WORKLOAD;
import static io.harness.rule.OwnerRule.ANMOL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.beans.recommendation.CCMJiraDetails;
import io.harness.ccm.commons.beans.recommendation.ResourceType;
import io.harness.ccm.commons.dao.recommendation.AzureRecommendationDAO;
import io.harness.ccm.commons.dao.recommendation.EC2RecommendationDAO;
import io.harness.ccm.commons.dao.recommendation.ECSRecommendationDAO;
import io.harness.ccm.commons.dao.recommendation.K8sRecommendationDAO;
import io.harness.ccm.graphql.dto.recommendation.CCMJiraCreateDTO;
import io.harness.ccm.jira.CCMJiraHelper;
import io.harness.ccm.views.dao.RuleExecutionDAO;
import io.harness.jira.JiraConstantsNG;
import io.harness.jira.JiraIssueNG;
import io.harness.rule.Owner;

import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RecommendationJiraServiceTest extends CategoryTest {
  @Mock private K8sRecommendationDAO mockK8sRecommendationDAO;
  @Mock private ECSRecommendationDAO mockEcsRecommendationDAO;
  @Mock private EC2RecommendationDAO mockEc2RecommendationDAO;
  @Mock private RuleExecutionDAO mockRuleExecutionDAO;
  @Mock private AzureRecommendationDAO mockAzureRecommendationDAO;
  @Mock private CCMJiraHelper mockJiraHelper;

  @InjectMocks private RecommendationJiraService recommendationJiraServiceUnderTest;

  private JiraIssueNG jiraIssueNG;
  private CCMJiraCreateDTO jiraCreateDTO;
  private CCMJiraDetails expectedResult;

  private final String ACCOUNT_ID = "accountId";
  private final String CONNECTOR_REF = "connectorRef";
  private final String JIRA_KEY_ISSUE = "jiraissuekey";
  private final String RECOMMENDATION_ID = "recommendationId";

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testCreateJiraForRecommendation_WorkloadRecommendation() {
    jiraIssueNG = getJiraIssueNG();
    jiraCreateDTO = getJiraCreateDTO(WORKLOAD);
    expectedResult = CCMJiraDetails.builder().connectorRef(CONNECTOR_REF).jiraIssue(jiraIssueNG).build();
    when(mockJiraHelper.createIssue(
             ACCOUNT_ID, CONNECTOR_REF, "projectKey", "issueType", Map.ofEntries(Map.entry("value", "value"))))
        .thenReturn(jiraIssueNG);

    // Run the test
    final CCMJiraDetails result =
        recommendationJiraServiceUnderTest.createJiraForRecommendation(ACCOUNT_ID, jiraCreateDTO);

    // Verify the results
    assertThat(result).isEqualTo(expectedResult);
    verify(mockK8sRecommendationDAO)
        .updateJiraInWorkloadRecommendation(ACCOUNT_ID, RECOMMENDATION_ID,
            CCMJiraDetails.builder().connectorRef(CONNECTOR_REF).jiraIssue(jiraIssueNG).build());
    verify(mockK8sRecommendationDAO).updateJiraInTimescale(RECOMMENDATION_ID, CONNECTOR_REF, JIRA_KEY_ISSUE, "done");
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testCreateJiraForRecommendation_NodeRecommendation() {
    jiraIssueNG = getJiraIssueNG();
    jiraCreateDTO = getJiraCreateDTO(NODE_POOL);
    expectedResult = CCMJiraDetails.builder().connectorRef(CONNECTOR_REF).jiraIssue(jiraIssueNG).build();
    when(mockJiraHelper.createIssue(
             ACCOUNT_ID, CONNECTOR_REF, "projectKey", "issueType", Map.ofEntries(Map.entry("value", "value"))))
        .thenReturn(jiraIssueNG);

    // Run the test
    final CCMJiraDetails result =
        recommendationJiraServiceUnderTest.createJiraForRecommendation(ACCOUNT_ID, jiraCreateDTO);

    // Verify the results
    assertThat(result).isEqualTo(expectedResult);
    verify(mockK8sRecommendationDAO)
        .updateJiraInNodeRecommendation(ACCOUNT_ID, RECOMMENDATION_ID,
            CCMJiraDetails.builder().connectorRef(CONNECTOR_REF).jiraIssue(jiraIssueNG).build());
    verify(mockK8sRecommendationDAO).updateJiraInTimescale(RECOMMENDATION_ID, CONNECTOR_REF, JIRA_KEY_ISSUE, "done");
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testCreateJiraForRecommendation_ECSRecommendation() {
    jiraIssueNG = getJiraIssueNG();
    jiraCreateDTO = getJiraCreateDTO(ECS_SERVICE);
    expectedResult = CCMJiraDetails.builder().connectorRef(CONNECTOR_REF).jiraIssue(jiraIssueNG).build();
    when(mockJiraHelper.createIssue(
             ACCOUNT_ID, CONNECTOR_REF, "projectKey", "issueType", Map.ofEntries(Map.entry("value", "value"))))
        .thenReturn(jiraIssueNG);

    // Run the test
    final CCMJiraDetails result =
        recommendationJiraServiceUnderTest.createJiraForRecommendation(ACCOUNT_ID, jiraCreateDTO);

    // Verify the results
    assertThat(result).isEqualTo(expectedResult);
    verify(mockEcsRecommendationDAO)
        .updateJiraInECSRecommendation(ACCOUNT_ID, RECOMMENDATION_ID,
            CCMJiraDetails.builder().connectorRef(CONNECTOR_REF).jiraIssue(jiraIssueNG).build());
    verify(mockK8sRecommendationDAO).updateJiraInTimescale(RECOMMENDATION_ID, CONNECTOR_REF, JIRA_KEY_ISSUE, "done");
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testCreateJiraForRecommendation_EC2Recommendation() {
    jiraIssueNG = getJiraIssueNG();
    jiraCreateDTO = getJiraCreateDTO(EC2_INSTANCE);
    expectedResult = CCMJiraDetails.builder().connectorRef(CONNECTOR_REF).jiraIssue(jiraIssueNG).build();
    when(mockJiraHelper.createIssue(
             ACCOUNT_ID, CONNECTOR_REF, "projectKey", "issueType", Map.ofEntries(Map.entry("value", "value"))))
        .thenReturn(jiraIssueNG);

    // Run the test
    final CCMJiraDetails result =
        recommendationJiraServiceUnderTest.createJiraForRecommendation(ACCOUNT_ID, jiraCreateDTO);

    // Verify the results
    assertThat(result).isEqualTo(expectedResult);
    verify(mockEc2RecommendationDAO)
        .updateJiraInEC2Recommendation(ACCOUNT_ID, RECOMMENDATION_ID,
            CCMJiraDetails.builder().connectorRef(CONNECTOR_REF).jiraIssue(jiraIssueNG).build());
    verify(mockK8sRecommendationDAO).updateJiraInTimescale(RECOMMENDATION_ID, CONNECTOR_REF, JIRA_KEY_ISSUE, "done");
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testCreateJiraForRecommendation_GovernanceRecommendation() {
    jiraIssueNG = getJiraIssueNG();
    jiraCreateDTO = getJiraCreateDTO(GOVERNANCE);
    expectedResult = CCMJiraDetails.builder().connectorRef(CONNECTOR_REF).jiraIssue(jiraIssueNG).build();
    when(mockJiraHelper.createIssue(
             ACCOUNT_ID, CONNECTOR_REF, "projectKey", "issueType", Map.ofEntries(Map.entry("value", "value"))))
        .thenReturn(jiraIssueNG);

    // Run the test
    final CCMJiraDetails result =
        recommendationJiraServiceUnderTest.createJiraForRecommendation(ACCOUNT_ID, jiraCreateDTO);

    // Verify the results
    assertThat(result).isEqualTo(expectedResult);
    verify(mockRuleExecutionDAO)
        .updateJiraInGovernanceRecommendation(ACCOUNT_ID, RECOMMENDATION_ID,
            CCMJiraDetails.builder().connectorRef(CONNECTOR_REF).jiraIssue(jiraIssueNG).build());
    verify(mockK8sRecommendationDAO).updateJiraInTimescale(RECOMMENDATION_ID, CONNECTOR_REF, JIRA_KEY_ISSUE, "done");
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testCreateJiraForRecommendation_AzureRecommendation() {
    jiraIssueNG = getJiraIssueNG();
    jiraCreateDTO = getJiraCreateDTO(AZURE_INSTANCE);
    expectedResult = CCMJiraDetails.builder().connectorRef(CONNECTOR_REF).jiraIssue(jiraIssueNG).build();
    when(mockJiraHelper.createIssue(
             ACCOUNT_ID, CONNECTOR_REF, "projectKey", "issueType", Map.ofEntries(Map.entry("value", "value"))))
        .thenReturn(jiraIssueNG);

    // Run the test
    final CCMJiraDetails result =
        recommendationJiraServiceUnderTest.createJiraForRecommendation(ACCOUNT_ID, jiraCreateDTO);

    // Verify the results
    assertThat(result).isEqualTo(expectedResult);
    verify(mockAzureRecommendationDAO)
        .updateJiraInAzureRecommendation(ACCOUNT_ID, RECOMMENDATION_ID,
            CCMJiraDetails.builder().connectorRef(CONNECTOR_REF).jiraIssue(jiraIssueNG).build());
    verify(mockK8sRecommendationDAO).updateJiraInTimescale(RECOMMENDATION_ID, CONNECTOR_REF, JIRA_KEY_ISSUE, "done");
  }

  private JiraIssueNG getJiraIssueNG() {
    return new JiraIssueNG("url", "restUrl", "id", JIRA_KEY_ISSUE,
        Map.ofEntries(Map.entry(JiraConstantsNG.STATUS_INTERNAL_NAME,
            Map.ofEntries(
                Map.entry("name", "done"), Map.entry("statusCategory", Map.ofEntries(Map.entry("key", "done")))))));
  }

  private CCMJiraCreateDTO getJiraCreateDTO(ResourceType resourceType) {
    return CCMJiraCreateDTO.builder()
        .recommendationId(RECOMMENDATION_ID)
        .resourceType(resourceType)
        .connectorRef(CONNECTOR_REF)
        .projectKey("projectKey")
        .issueType("issueType")
        .fields(Map.ofEntries(Map.entry("value", "value")))
        .build();
  }
}
