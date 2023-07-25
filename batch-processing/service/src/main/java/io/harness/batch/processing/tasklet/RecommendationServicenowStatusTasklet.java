package io.harness.batch.processing.tasklet;

import static io.harness.ccm.commons.constants.ViewFieldConstants.THRESHOLD_DAYS_TO_SHOW_RECOMMENDATION;
import static io.harness.ccm.commons.utils.TimeUtils.offsetDateTimeNow;
import static io.harness.timescaledb.Tables.CE_RECOMMENDATIONS;

import io.harness.batch.processing.ccm.CCMJobConstants;
import io.harness.ccm.commons.beans.JobConstants;
import io.harness.ccm.commons.beans.recommendation.CCMServiceNowDetails;
import io.harness.ccm.commons.beans.recommendation.ResourceType;
import io.harness.ccm.commons.dao.recommendation.AzureRecommendationDAO;
import io.harness.ccm.commons.dao.recommendation.EC2RecommendationDAO;
import io.harness.ccm.commons.dao.recommendation.ECSRecommendationDAO;
import io.harness.ccm.commons.dao.recommendation.K8sRecommendationDAO;
import io.harness.ccm.serviceNow.CCMServiceNowHelper;
import io.harness.ccm.serviceNow.CCMServiceNowUtils;
import io.harness.ccm.views.dao.RuleExecutionDAO;
import io.harness.servicenow.ServiceNowTicketNG;
import io.harness.timescaledb.tables.pojos.CeRecommendations;

import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class RecommendationServicenowStatusTasklet implements Tasklet {
  @Autowired private CCMServiceNowHelper serviceNowHelper;
  @Autowired private CCMServiceNowUtils serviceNowUtils;
  @Autowired private K8sRecommendationDAO k8sRecommendationDAO;
  @Autowired private ECSRecommendationDAO ecsRecommendationDAO;
  @Autowired private EC2RecommendationDAO ec2RecommendationDAO;
  @Autowired private RuleExecutionDAO ruleExecutionDAO;
  @Autowired private AzureRecommendationDAO azureRecommendationDAO;
  private static final long BATCH_SIZE = 100;
  private static final HashSet<String> APPLIED_CATEGORIES = new HashSet<>(Arrays.asList("done", "complete"));
  @Override
  public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) throws Exception {
    final JobConstants jobConstants = CCMJobConstants.fromContext(chunkContext);
    String accountId = jobConstants.getAccountId();
    int recommendationsCount =
        k8sRecommendationDAO.fetchRecommendationsCount(accountId, getValidRecommendationFilter());
    for (long offset = 0; offset < recommendationsCount; offset += BATCH_SIZE) {
      List<CeRecommendations> ceRecommendationsList = k8sRecommendationDAO.fetchRecommendationsOverview(
          accountId, getValidRecommendationFilter(), offset, BATCH_SIZE);
      for (CeRecommendations recommendation : ceRecommendationsList) {
        ServiceNowTicketNG serviceNowTicketNG;
        CCMServiceNowDetails serviceNowDetails;
        try {
          serviceNowTicketNG = serviceNowHelper.getIssue(
              accountId, recommendation.getServicenowconnectorref(), "", recommendation.getServicenowissuekey());
          serviceNowDetails = CCMServiceNowDetails.builder()
                                  .connectorRef(recommendation.getServicenowconnectorref())
                                  .serviceNowTicket(serviceNowTicketNG)
                                  .build();
        } catch (Exception e) {
          log.warn("Couldn't fetch recommendation jira for recommendationId: {}, error: {}", recommendation.getId(), e);
          continue;
        }
        try {
          String status = serviceNowUtils.getStatus(serviceNowTicketNG);
          if (!Objects.equals(recommendation.getJirastatus(), status)) {
            // updates in timescale
            k8sRecommendationDAO.updateJiraInTimescale(
                recommendation.getId(), recommendation.getJiraconnectorref(), recommendation.getJiraissuekey(), status);

            // updates in mongo
            switch (ResourceType.valueOf(recommendation.getResourcetype())) {
              case NODE_POOL:
                k8sRecommendationDAO.updateServicenowDetailsInNodeRecommendation(
                    accountId, recommendation.getId(), serviceNowDetails);
                break;
              case WORKLOAD:
                k8sRecommendationDAO.updateServicenowDetailsInWorkloadRecommendation(
                    accountId, recommendation.getId(), serviceNowDetails);
                break;
              case ECS_SERVICE:
                ecsRecommendationDAO.updateServicenowDetailsInECSRecommendation(
                    accountId, recommendation.getId(), serviceNowDetails);
                break;
              case EC2_INSTANCE:
                ec2RecommendationDAO.updateServicenowDetailsInEC2Recommendation(
                    accountId, recommendation.getId(), serviceNowDetails);
                break;
              case GOVERNANCE:
                ruleExecutionDAO.updateServicenowDetailsInGovernanceRecommendation(
                    accountId, recommendation.getId(), serviceNowDetails);
                break;
              case AZURE_INSTANCE:
                azureRecommendationDAO.updateServicenowDetailsInAzureRecommendation(
                    accountId, recommendation.getId(), serviceNowDetails);
                break;
              default:
                log.warn("Unknown resource type {} for recommendation id {}", recommendation.getResourcetype(),
                    recommendation.getId());
            }
          }
        } catch (Exception e) {
          log.warn("Error getting status of jira: {}, recommendationId: {}, error: {}",
              recommendation.getJiraissuekey(), recommendation.getId(), e);
          log.info("Jira issue fetched: {}", serviceNowTicketNG);
        }
      }
    }
    return null;
  }

  private static Condition getValidRecommendationFilter() {
    return CE_RECOMMENDATIONS.ISVALID
        .eq(true)
        // based on current-gen workload recommendation dataFetcher
        .and(CE_RECOMMENDATIONS.LASTPROCESSEDAT.greaterOrEqual(
            offsetDateTimeNow().truncatedTo(ChronoUnit.DAYS).minusDays(THRESHOLD_DAYS_TO_SHOW_RECOMMENDATION)))
        .and(nonDelegate())
        .and(CE_RECOMMENDATIONS.RECOMMENDATIONSTATE.notEqual("APPLIED"))
        .and(CE_RECOMMENDATIONS.JIRACONNECTORREF.isNotNull())
        .and(CE_RECOMMENDATIONS.JIRACONNECTORREF.notIn("", " "))
        .and(CE_RECOMMENDATIONS.JIRAISSUEKEY.isNotNull())
        .and(CE_RECOMMENDATIONS.JIRAISSUEKEY.notIn("", " "));
  }

  private static Condition nonDelegate() {
    return CE_RECOMMENDATIONS.RESOURCETYPE.notEqual(ResourceType.WORKLOAD.name())
        .or(CE_RECOMMENDATIONS.RESOURCETYPE.eq(ResourceType.WORKLOAD.name())
                .and(CE_RECOMMENDATIONS.NAMESPACE.notIn("harness-delegate", "harness-delegate-ng")));
  }
}
