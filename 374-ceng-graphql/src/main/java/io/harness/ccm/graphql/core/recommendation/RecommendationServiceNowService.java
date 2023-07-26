/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.core.recommendation;

import io.harness.ccm.commons.beans.recommendation.CCMServiceNowDetails;
import io.harness.ccm.commons.beans.recommendation.ResourceType;
import io.harness.ccm.commons.dao.recommendation.AzureRecommendationDAO;
import io.harness.ccm.commons.dao.recommendation.EC2RecommendationDAO;
import io.harness.ccm.commons.dao.recommendation.ECSRecommendationDAO;
import io.harness.ccm.commons.dao.recommendation.K8sRecommendationDAO;
import io.harness.ccm.graphql.dto.recommendation.CCMServiceNowCreateDTO;
import io.harness.ccm.serviceNow.CCMServiceNowHelper;
import io.harness.ccm.serviceNow.CCMServiceNowUtils;
import io.harness.ccm.views.dao.RuleExecutionDAO;
import io.harness.servicenow.ServiceNowTicketNG;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class RecommendationServiceNowService {
  @Inject private K8sRecommendationDAO k8sRecommendationDAO;
  @Inject private ECSRecommendationDAO ecsRecommendationDAO;
  @Inject private EC2RecommendationDAO ec2RecommendationDAO;
  @Inject private RuleExecutionDAO ruleExecutionDAO;
  @Inject private AzureRecommendationDAO azureRecommendationDAO;
  @Inject private CCMServiceNowHelper serviceNowHelper;
  @Inject private CCMServiceNowUtils serviceNowUtils;

  public CCMServiceNowDetails createServiceNowTicketForRecommendation(
      String accountId, CCMServiceNowCreateDTO serviceNowCreateDTO) {
    String recommendationId = serviceNowCreateDTO.getRecommendationId();
    ResourceType resourceType = serviceNowCreateDTO.getResourceType();
    String serviceNowConnectorRef = serviceNowCreateDTO.getConnectorRef();
    ServiceNowTicketNG serviceNowTicket = serviceNowHelper.createIssue(
        accountId, serviceNowConnectorRef, serviceNowCreateDTO.getTicketType(), serviceNowCreateDTO.getFields());
    CCMServiceNowDetails serviceNowDetails =
        CCMServiceNowDetails.builder().connectorRef(serviceNowConnectorRef).serviceNowTicket(serviceNowTicket).build();
    if (resourceType.equals(ResourceType.NODE_POOL)) {
      k8sRecommendationDAO.updateServicenowDetailsInNodeRecommendation(accountId, recommendationId, serviceNowDetails);
    } else if (resourceType.equals(ResourceType.WORKLOAD)) {
      k8sRecommendationDAO.updateServicenowDetailsInWorkloadRecommendation(
          accountId, recommendationId, serviceNowDetails);
    } else if (resourceType.equals(ResourceType.ECS_SERVICE)) {
      ecsRecommendationDAO.updateServicenowDetailsInECSRecommendation(accountId, recommendationId, serviceNowDetails);
    } else if (resourceType.equals(ResourceType.EC2_INSTANCE)) {
      ec2RecommendationDAO.updateServicenowDetailsInEC2Recommendation(accountId, recommendationId, serviceNowDetails);
    } else if (resourceType.equals(ResourceType.GOVERNANCE)) {
      ruleExecutionDAO.updateServicenowDetailsInGovernanceRecommendation(
          accountId, recommendationId, serviceNowDetails);
    } else if (resourceType.equals(ResourceType.AZURE_INSTANCE)) {
      azureRecommendationDAO.updateServicenowDetailsInAzureRecommendation(
          accountId, recommendationId, serviceNowDetails);
    }
    k8sRecommendationDAO.updateServicenowDetailsInTimescale(recommendationId, serviceNowConnectorRef,
        serviceNowCreateDTO.getTicketType(), serviceNowTicket.getNumber(), serviceNowUtils.getStatus(serviceNowTicket));
    return serviceNowDetails;
  }
}
