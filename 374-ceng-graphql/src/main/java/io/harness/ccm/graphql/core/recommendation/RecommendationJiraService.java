/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.core.recommendation;

import static io.harness.ccm.jira.CCMJiraUtils.getStatus;

import io.harness.ccm.commons.beans.recommendation.CCMJiraDetails;
import io.harness.ccm.commons.beans.recommendation.ResourceType;
import io.harness.ccm.commons.dao.recommendation.EC2RecommendationDAO;
import io.harness.ccm.commons.dao.recommendation.ECSRecommendationDAO;
import io.harness.ccm.commons.dao.recommendation.K8sRecommendationDAO;
import io.harness.ccm.graphql.dto.recommendation.CCMJiraCreateDTO;
import io.harness.ccm.jira.CCMJiraHelper;
import io.harness.ccm.views.dao.RuleExecutionDAO;
import io.harness.jira.JiraIssueNG;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class RecommendationJiraService {
  @Inject private K8sRecommendationDAO k8sRecommendationDAO;
  @Inject private ECSRecommendationDAO ecsRecommendationDAO;
  @Inject private EC2RecommendationDAO ec2RecommendationDAO;
  @Inject private RuleExecutionDAO ruleExecutionDAO;
  @Inject private CCMJiraHelper jiraHelper;

  public CCMJiraDetails createJiraForRecommendation(String accountId, CCMJiraCreateDTO jiraCreateDTO) {
    String recommendationId = jiraCreateDTO.getRecommendationId();
    ResourceType resourceType = jiraCreateDTO.getResourceType();
    String jiraConnectorRef = jiraCreateDTO.getConnectorRef();
    JiraIssueNG jiraIssueNG = jiraHelper.createIssue(accountId, jiraConnectorRef, jiraCreateDTO.getProjectKey(),
        jiraCreateDTO.getIssueType(), jiraCreateDTO.getFields());
    CCMJiraDetails jiraDetails = CCMJiraDetails.builder().connectorRef(jiraConnectorRef).jiraIssue(jiraIssueNG).build();
    if (resourceType.equals(ResourceType.NODE_POOL)) {
      k8sRecommendationDAO.updateJiraInNodeRecommendation(accountId, recommendationId, jiraDetails);
    } else if (resourceType.equals(ResourceType.WORKLOAD)) {
      k8sRecommendationDAO.updateJiraInWorkloadRecommendation(accountId, recommendationId, jiraDetails);
    } else if (resourceType.equals(ResourceType.ECS_SERVICE)) {
      ecsRecommendationDAO.updateJiraInECSRecommendation(accountId, recommendationId, jiraDetails);
    } else if (resourceType.equals(ResourceType.EC2_INSTANCE)) {
      ec2RecommendationDAO.updateJiraInEC2Recommendation(accountId, recommendationId, jiraDetails);
    } else if (resourceType.equals(ResourceType.GOVERNANCE)) {
      ruleExecutionDAO.updateJiraInGovernanceRecommendation(accountId, recommendationId, jiraDetails);
    }
    k8sRecommendationDAO.updateJiraInTimescale(
        recommendationId, jiraConnectorRef, jiraIssueNG.getKey(), getStatus(jiraIssueNG));
    return jiraDetails;
  }
}
