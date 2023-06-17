/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.governance;

import static io.harness.ccm.views.entities.ViewFieldIdentifier.AWS;
import static io.harness.ccm.views.entities.ViewFieldIdentifier.COMMON;

import io.harness.batch.processing.cloudevents.aws.ecs.service.support.intfc.AwsEC2HelperService;
import io.harness.batch.processing.cloudevents.aws.ecs.service.tasklet.support.ng.NGConnectorHelper;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.shard.AccountShardService;
import io.harness.ccm.views.dao.RuleDAO;
import io.harness.ccm.views.dto.GovernanceJobEnqueueDTO;
import io.harness.ccm.views.entities.RecommendationAdhocDTO;
import io.harness.ccm.views.entities.Rule;
import io.harness.ccm.views.entities.ViewFieldIdentifier;
import io.harness.ccm.views.graphql.QLCESortOrder;
import io.harness.ccm.views.graphql.QLCEViewAggregateOperation;
import io.harness.ccm.views.graphql.QLCEViewAggregation;
import io.harness.ccm.views.graphql.QLCEViewEntityStatsDataPoint;
import io.harness.ccm.views.graphql.QLCEViewFieldInput;
import io.harness.ccm.views.graphql.QLCEViewFilter;
import io.harness.ccm.views.graphql.QLCEViewFilterOperator;
import io.harness.ccm.views.graphql.QLCEViewFilterWrapper;
import io.harness.ccm.views.graphql.QLCEViewGroupBy;
import io.harness.ccm.views.graphql.QLCEViewSortCriteria;
import io.harness.ccm.views.graphql.QLCEViewSortType;
import io.harness.ccm.views.graphql.QLCEViewTimeFilter;
import io.harness.ccm.views.graphql.QLCEViewTimeFilterOperator;
import io.harness.ccm.views.graphql.ViewsQueryHelper;
import io.harness.ccm.views.helper.RuleCloudProviderType;
import io.harness.ccm.views.helper.RuleExecutionType;
import io.harness.ccm.views.service.GovernanceRuleService;
import io.harness.ccm.views.service.ViewsBillingService;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.delegate.beans.connector.CEFeatures;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.ceawsconnector.CEAwsConnectorDTO;

import software.wings.beans.AwsCrossAccountAttributes;

import com.google.inject.Singleton;
import io.fabric8.utils.Lists;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Singleton
@Slf4j
public class GovernanceRecommendationService {
  @Autowired private GovernanceRuleService governanceRuleService;
  @Autowired private ConnectorResourceClient connectorResourceClient;
  @Autowired private AccountShardService accountShardService;
  @Autowired private ViewsBillingService viewsBillingService;
  @Autowired private ViewsQueryHelper viewsQueryHelper;
  @Autowired private AwsEC2HelperService awsEC2HelperService;
  @Autowired private RuleDAO ruleDAO;
  @Autowired BatchMainConfig configuration;
  @Autowired private NGConnectorHelper ngConnectorHelper;
  private static final String DEFAULT_TIMEZONE = "GMT";

  public void generateRecommendation(List<ConnectorType> ceEnabledConnectorType) {
    // get all ce enabled accounts
    List<String> getAccounts = accountShardService.getCeEnabledAccountIds();
    for (String account : getAccounts) {
      log.info("generateRecommendationForAccount: {}", account);
      generateRecommendationForAccount(account, ceEnabledConnectorType);
    }
  }

  public void generateRecommendationForAccount(String accountId, List<ConnectorType> ceEnabledConnectorType) {
    // fetch connector information for all the account with governance enabled
    List<ConnectorResponseDTO> nextGenConnectorResponses = ngConnectorHelper.getNextGenConnectors(
        accountId, ceEnabledConnectorType, Arrays.asList(CEFeatures.GOVERNANCE), null);
    if (!nextGenConnectorResponses.isEmpty()) {
      if (ceEnabledConnectorType.get(0).equals(ConnectorType.CE_AWS)) {
        generateAwsRecommendationForAccount(accountId, nextGenConnectorResponses);
      }
      if (ceEnabledConnectorType.get(0).equals(ConnectorType.CE_AZURE)) {
        generateAzureRecommendationForAccount(accountId, nextGenConnectorResponses);
      }

      try {
        TimeUnit.SECONDS.sleep(configuration.getGovernanceConfig().getSleepTime());
      } catch (InterruptedException e) {
        log.error("error which generating recommendation for {}", accountId);
      }
    } else {
      log.info("No connector found for {}", accountId);
    }
  }

  public void generateAwsRecommendationForAccount(
      String accountId, List<ConnectorResponseDTO> nextGenConnectorResponses) {
    Set<Rule> ruleList = new HashSet<>();
    ruleList.addAll(ruleDAO.forRecommendation());

    // getting the needed fields for recommendation
    List<RecommendationAdhocDTO> recommendationAdhocDTOList = new ArrayList<>();
    // List to get identifiers out; later used in fetching top accounts
    List<String> awsIdentifiers = new ArrayList<>();
    for (ConnectorResponseDTO connector : nextGenConnectorResponses) {
      ConnectorInfoDTO connectorInfoDTO = connector.getConnector();
      CEAwsConnectorDTO ceAwsConnectorDTO = (CEAwsConnectorDTO) connectorInfoDTO.getConnectorConfig();
      awsIdentifiers.add(ceAwsConnectorDTO.getAwsAccountId());
      recommendationAdhocDTOList.add(RecommendationAdhocDTO.builder()
                                         .targetAccountId(ceAwsConnectorDTO.getAwsAccountId())
                                         .roleArn(ceAwsConnectorDTO.getCrossAccountAccess().getCrossAccountRoleArn())
                                         .externalId(ceAwsConnectorDTO.getCrossAccountAccess().getExternalId())
                                         .build());
    }

    // get top regions
    List<String> regions = new ArrayList<>();
    List<QLCEViewEntityStatsDataPoint> accountNames = getAccountNames(accountId, awsIdentifiers);
    // filter out final list of rolearn,externalId etc based on top accounts
    List<RecommendationAdhocDTO> recommendationAdhocDTOListFinal = new ArrayList<>();
    if (!accountNames.isEmpty()) {
      List<String> awsIdentifiersFinal = new ArrayList<>();
      for (QLCEViewEntityStatsDataPoint accountData : accountNames) {
        recommendationAdhocDTOListFinal.add(recommendationAdhocDTOList.stream()
                                                .filter(e -> e.getTargetAccountId().matches(accountData.getId()))
                                                .findFirst()
                                                .get());
        awsIdentifiersFinal.add(accountData.getName());
      }
      log.info("Account {} connectors: {}\n\n\n awsIdentifiers{} ", accountId, recommendationAdhocDTOListFinal,
          awsIdentifiersFinal);
      List<QLCEViewEntityStatsDataPoint> regionsFromPerspective = getTopRegions(accountId, awsIdentifiersFinal);
      regions = regionsFromPerspective.stream().map(QLCEViewEntityStatsDataPoint::getId).collect(Collectors.toList());
    } else {
      log.info("Failed to get account and regions from perspective {} ", accountId);
      recommendationAdhocDTOListFinal.addAll(
          recommendationAdhocDTOList.subList(0, Math.min(recommendationAdhocDTOList.size(), 5)));
    }
    // enqueue call
    enqueueRecommendationForAccount(recommendationAdhocDTOListFinal, ruleList, regions, accountId);
  }

  public void generateAzureRecommendationForAccount(
      String accountId, List<ConnectorResponseDTO> nextGenConnectorResponses) {}

  void enqueueRecommendationForAccount(List<RecommendationAdhocDTO> recommendationAdhocDTOListFinal, Set<Rule> ruleList,
      List<String> regions, String accountId) {
    for (RecommendationAdhocDTO recommendationAdhoc : recommendationAdhocDTOListFinal) {
      if (Lists.isNullOrEmpty(regions)) {
        regions = awsEC2HelperService.listRegions(AwsCrossAccountAttributes.builder()
                                                      .crossAccountRoleArn(recommendationAdhoc.getRoleArn())
                                                      .externalId(recommendationAdhoc.getExternalId())
                                                      .build());
      }
      for (Rule rule : ruleList) {
        for (String region : regions) {
          GovernanceJobEnqueueDTO governanceJobEnqueueDTO =
              GovernanceJobEnqueueDTO.builder()
                  .executionType(RuleExecutionType.INTERNAL)
                  .ruleId(rule.getUuid())
                  .isDryRun(true)
                  .policy(rule.getRulesYaml())
                  .ruleCloudProviderType(RuleCloudProviderType.AWS)
                  .targetAccountId(recommendationAdhoc.getTargetAccountId())
                  .externalId(recommendationAdhoc.getExternalId())
                  .roleArn(recommendationAdhoc.getRoleArn())
                  .targetRegion(region)
                  .build();
          log.info("enqueued: {}", governanceJobEnqueueDTO);
          try {
            governanceRuleService.enqueueAdhoc(accountId, governanceJobEnqueueDTO);
          } catch (Exception e) {
            log.error("Exception while enqueuing ", e);
          }
        }
      }
    }
  }

  // Get high impact region per account (Top 5 regions) using  aggreated names of different accounts
  private List<QLCEViewEntityStatsDataPoint> getTopRegions(String accountId, List<String> awsID) {
    List<QLCEViewAggregation> aggregateFunction = Collections.singletonList(
        QLCEViewAggregation.builder().columnName("cost").operationType(QLCEViewAggregateOperation.SUM).build());

    List<QLCEViewGroupBy> groupBy = new ArrayList<>();
    groupBy.add(QLCEViewGroupBy.builder()
                    .entityGroupBy(QLCEViewFieldInput.builder()
                                       .fieldId("region")
                                       .fieldName("Region")
                                       .identifierName("Common")
                                       .identifier(COMMON)
                                       .build())
                    .build());

    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(getTimeFilter(getStartOfMonth(true), QLCEViewTimeFilterOperator.AFTER));
    filters.add(getTimeFilter(getStartOfMonth(false) - 1000, QLCEViewTimeFilterOperator.BEFORE));
    filters.add(QLCEViewFilterWrapper.builder()
                    .idFilter(QLCEViewFilter.builder()
                                  .field(QLCEViewFieldInput.builder()
                                             .fieldId("awsUsageaccountid")
                                             .fieldName("Account")
                                             .identifier(AWS)
                                             .build())
                                  .operator(QLCEViewFilterOperator.IN)
                                  .values(awsID.toArray(new String[0]))
                                  .build())
                    .build());
    List<QLCEViewSortCriteria> sort = Collections.singletonList(
        QLCEViewSortCriteria.builder().sortOrder(QLCESortOrder.DESCENDING).sortType(QLCEViewSortType.COST).build());
    return viewsBillingService
        .getEntityStatsDataPointsNg(filters, groupBy, aggregateFunction, sort,
            configuration.getRecommendationConfig().getRegionsLimit(), 0,
            viewsQueryHelper.buildQueryParams(accountId, false, false))
        .getData();
  }

  // Get aggreated names of different accounts
  private List<QLCEViewEntityStatsDataPoint> getAccountNames(String accountId, List<String> awsID) {
    List<QLCEViewAggregation> aggregateFunction = Collections.singletonList(
        QLCEViewAggregation.builder().columnName("cost").operationType(QLCEViewAggregateOperation.SUM).build());

    List<QLCEViewGroupBy> groupBy = new ArrayList<>();
    groupBy.add(QLCEViewGroupBy.builder()
                    .entityGroupBy(QLCEViewFieldInput.builder()
                                       .fieldId("awsUsageaccountid")
                                       .fieldName("Account")
                                       .identifierName("AWS")
                                       .identifier(AWS)
                                       .build())
                    .build());
    List<QLCEViewSortCriteria> sort = Collections.singletonList(
        QLCEViewSortCriteria.builder().sortOrder(QLCESortOrder.DESCENDING).sortType(QLCEViewSortType.COST).build());

    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(getTimeFilter(getStartOfMonth(true), QLCEViewTimeFilterOperator.AFTER));
    filters.add(getTimeFilter(getStartOfMonth(false) - 1000, QLCEViewTimeFilterOperator.BEFORE));
    filters.add(QLCEViewFilterWrapper.builder()
                    .idFilter(QLCEViewFilter.builder()
                                  .field(QLCEViewFieldInput.builder()
                                             .fieldId("awsUsageaccountid")
                                             .fieldName("Account")
                                             .identifier(AWS)
                                             .build())
                                  .operator(QLCEViewFilterOperator.IN)
                                  .values(awsID.toArray(new String[0]))
                                  .build())
                    .build());
    return viewsBillingService
        .getEntityStatsDataPointsNg(filters, groupBy, aggregateFunction, sort,
            configuration.getRecommendationConfig().getAccountLimit(), 0,
            viewsQueryHelper.buildQueryParams(accountId, false, false))
        .getData();
  }

  private long getStartOfMonth(boolean prevMonth) {
    Calendar c = Calendar.getInstance();
    c.setTimeZone(TimeZone.getTimeZone(DEFAULT_TIMEZONE));
    c.set(Calendar.DAY_OF_MONTH, 1);
    c.set(Calendar.HOUR_OF_DAY, 0);
    c.set(Calendar.MINUTE, 0);
    c.set(Calendar.SECOND, 0);
    c.set(Calendar.MILLISECOND, 0);
    if (prevMonth) {
      c.add(Calendar.MONTH, -1);
    }
    return c.getTimeInMillis();
  }

  private QLCEViewFilterWrapper getTimeFilter(long timestamp, QLCEViewTimeFilterOperator operator) {
    log.info("getting time filter");
    return QLCEViewFilterWrapper.builder()
        .timeFilter(QLCEViewTimeFilter.builder()
                        .field(QLCEViewFieldInput.builder()
                                   .fieldId("startTime")
                                   .fieldName("startTime")
                                   .identifier(ViewFieldIdentifier.COMMON)
                                   .identifierName(ViewFieldIdentifier.COMMON.getDisplayName())
                                   .build())
                        .operator(operator)
                        .value(timestamp)
                        .build())
        .build();
  }
}
