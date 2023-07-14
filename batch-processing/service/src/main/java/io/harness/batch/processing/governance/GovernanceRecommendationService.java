/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.governance;

import static io.harness.ccm.views.entities.ViewFieldIdentifier.AWS;
import static io.harness.ccm.views.entities.ViewFieldIdentifier.AZURE;
import static io.harness.ccm.views.entities.ViewFieldIdentifier.COMMON;

import io.harness.batch.processing.cloudevents.aws.ecs.service.support.intfc.AwsEC2HelperService;
import io.harness.batch.processing.cloudevents.aws.ecs.service.tasklet.support.ng.NGConnectorHelper;
import io.harness.batch.processing.cloudevents.azure.vm.service.AzureHelperService;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.shard.AccountShardService;
import io.harness.ccm.governance.entities.AwsRecommendationAdhocDTO;
import io.harness.ccm.governance.entities.AzureRecommendationAdhocDTO;
import io.harness.ccm.governance.entities.RecommendationAdhocDTO;
import io.harness.ccm.views.dao.RuleDAO;
import io.harness.ccm.views.dto.GovernanceJobEnqueueDTO;
import io.harness.ccm.views.entities.Rule;
import io.harness.ccm.views.entities.ViewFieldIdentifier;
import io.harness.ccm.views.graphql.QLCESortOrder;
import io.harness.ccm.views.graphql.QLCEViewAggregateOperation;
import io.harness.ccm.views.graphql.QLCEViewAggregation;
import io.harness.ccm.views.graphql.QLCEViewEntityStatsDataPoint;
import io.harness.ccm.views.graphql.QLCEViewFieldInput;
import io.harness.ccm.views.graphql.QLCEViewFieldInput.QLCEViewFieldInputBuilder;
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
import io.harness.delegate.beans.connector.ceazure.CEAzureConnectorDTO;

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
  @Autowired private AzureHelperService azureHelperService;
  private static final String DEFAULT_TIMEZONE = "GMT";
  private static final String AWS_USAGE_ACCOUNT_ID = "awsUsageaccountid";
  private static final String ACCOUNT = "Account";
  private static final String AZURE_SUBSCRIPTION_GUID = "azureSubscriptionGuid";
  private static final String SUBSCRIPTION_ID = "Subscription id";

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
        generateRecommendationForAccount(accountId, nextGenConnectorResponses, RuleCloudProviderType.AWS,
            AWS_USAGE_ACCOUNT_ID, ACCOUNT, AWS, AWS.getDisplayName());
      }
      if (ceEnabledConnectorType.get(0).equals(ConnectorType.CE_AZURE)) {
        generateRecommendationForAccount(accountId, nextGenConnectorResponses, RuleCloudProviderType.AZURE,
            AZURE_SUBSCRIPTION_GUID, SUBSCRIPTION_ID, AZURE, AZURE.getDisplayName());
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

  public void generateRecommendationForAccount(String accountId, List<ConnectorResponseDTO> nextGenConnectorResponses,
      RuleCloudProviderType ruleCloudProviderType, String fieldId, String fieldName, ViewFieldIdentifier identifier,
      String identifierName) {
    Set<Rule> ruleList = new HashSet<>();
    ruleList.addAll(ruleDAO.forRecommendation(ruleCloudProviderType));

    // getting the needed fields for recommendation
    List<RecommendationAdhocDTO> recommendationAdhocDTOList = new ArrayList<>();
    // List to get identifiers out; later used in fetching top accounts
    List<String> cloudProviderIdentifiers = new ArrayList<>();
    for (ConnectorResponseDTO connector : nextGenConnectorResponses) {
      ConnectorInfoDTO connectorInfoDTO = connector.getConnector();
      if (ruleCloudProviderType == RuleCloudProviderType.AWS) {
        CEAwsConnectorDTO ceAwsConnectorDTO = (CEAwsConnectorDTO) connectorInfoDTO.getConnectorConfig();
        cloudProviderIdentifiers.add(ceAwsConnectorDTO.getAwsAccountId());
        recommendationAdhocDTOList.add(AwsRecommendationAdhocDTO.builder()
                                           .targetAccountId(ceAwsConnectorDTO.getAwsAccountId())
                                           .roleArn(ceAwsConnectorDTO.getCrossAccountAccess().getCrossAccountRoleArn())
                                           .externalId(ceAwsConnectorDTO.getCrossAccountAccess().getExternalId())
                                           .build());
      } else if (ruleCloudProviderType == RuleCloudProviderType.AZURE) {
        CEAzureConnectorDTO ceAzureConnectorDTO = (CEAzureConnectorDTO) connectorInfoDTO.getConnectorConfig();
        cloudProviderIdentifiers.add(ceAzureConnectorDTO.getSubscriptionId());
        recommendationAdhocDTOList.add(AzureRecommendationAdhocDTO.builder()
                                           .tenantId(ceAzureConnectorDTO.getTenantId())
                                           .subscriptionId(ceAzureConnectorDTO.getSubscriptionId())
                                           .build());
      }
    }

    // get top regions
    List<String> regions = new ArrayList<>();
    List<QLCEViewEntityStatsDataPoint> accountNames =
        getAccountNames(accountId, cloudProviderIdentifiers, fieldId, fieldName, identifier, identifierName);
    // filter out final list of rolearn,externalId etc based on top accounts
    List<RecommendationAdhocDTO> recommendationAdhocDTOListFinal = new ArrayList<>();
    if (!accountNames.isEmpty()) {
      List<String> cloudProviderIdentifiersFinal = new ArrayList<>();
      for (QLCEViewEntityStatsDataPoint accountData : accountNames) {
        recommendationAdhocDTOListFinal.add(recommendationAdhocDTOList.stream()
                                                .filter(e -> e.getTargetInfo().matches(accountData.getId()))
                                                .findFirst()
                                                .get());
        cloudProviderIdentifiersFinal.add(accountData.getName());
      }
      log.info("Account {} connectors: {}\n\n\n {} Identifiers{} ", accountId, recommendationAdhocDTOListFinal,
          ruleCloudProviderType.name(), cloudProviderIdentifiersFinal);

      if (ruleCloudProviderType == RuleCloudProviderType.AWS) {
        List<QLCEViewEntityStatsDataPoint> regionsFromPerspective =
            getTopRegions(accountId, cloudProviderIdentifiersFinal, fieldId, fieldName, identifier);
        regions = regionsFromPerspective.stream().map(QLCEViewEntityStatsDataPoint::getId).collect(Collectors.toList());
      }
    } else {
      log.info("Failed to get account and regions from perspective {} ", accountId);
      recommendationAdhocDTOListFinal.addAll(
          recommendationAdhocDTOList.subList(0, Math.min(recommendationAdhocDTOList.size(), 5)));
    }
    // enqueue call
    enqueueRecommendationForAccount(
        recommendationAdhocDTOListFinal, ruleList, regions, accountId, ruleCloudProviderType);
  }

  void enqueueRecommendationForAccount(List<RecommendationAdhocDTO> recommendationAdhocDTOListFinal, Set<Rule> ruleList,
      List<String> regions, String accountId, RuleCloudProviderType ruleCloudProviderType) {
    for (RecommendationAdhocDTO recommendationAdhoc : recommendationAdhocDTOListFinal) {
      if (Lists.isNullOrEmpty(regions)) {
        if (ruleCloudProviderType == RuleCloudProviderType.AWS) {
          regions = awsEC2HelperService.listRegions(AwsCrossAccountAttributes.builder()
                                                        .crossAccountRoleArn(recommendationAdhoc.getRoleInfo())
                                                        .externalId(recommendationAdhoc.getRoleId())
                                                        .build());
        } else if (ruleCloudProviderType == RuleCloudProviderType.AZURE) {
          regions = azureHelperService.getValidRegions(accountId, recommendationAdhoc);
          log.info("Regions for running azure policy : {}", regions);
        }
      }
      for (Rule rule : ruleList) {
        for (String region : regions) {
          GovernanceJobEnqueueDTO governanceJobEnqueueDTO = GovernanceJobEnqueueDTO.builder()
                                                                .executionType(RuleExecutionType.INTERNAL)
                                                                .ruleId(rule.getUuid())
                                                                .isDryRun(true)
                                                                .policy(rule.getRulesYaml())
                                                                .ruleCloudProviderType(ruleCloudProviderType)
                                                                .targetAccountDetails(recommendationAdhoc)
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
  private List<QLCEViewEntityStatsDataPoint> getTopRegions(
      String accountId, List<String> awsID, String fieldId, String fieldName, ViewFieldIdentifier identifier) {
    List<QLCEViewAggregation> aggregateFunction = Collections.singletonList(
        QLCEViewAggregation.builder().columnName("cost").operationType(QLCEViewAggregateOperation.SUM).build());

    QLCEViewFieldInputBuilder regionQlceViewFieldInputBuilder =
        QLCEViewFieldInput.builder().fieldId("region").fieldName("Region").identifier(COMMON);

    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(getTimeFilter(getStartOfMonth(true), QLCEViewTimeFilterOperator.AFTER));
    filters.add(getTimeFilter(getStartOfMonth(false) - 1000, QLCEViewTimeFilterOperator.BEFORE));
    filters.add(QLCEViewFilterWrapper.builder()
                    .idFilter(QLCEViewFilter.builder()
                                  .field(regionQlceViewFieldInputBuilder.build())
                                  .operator(QLCEViewFilterOperator.NOT_IN)
                                  .values(new String[] {"global"})
                                  .build())
                    .build());
    filters.add(QLCEViewFilterWrapper.builder()
                    .idFilter(QLCEViewFilter.builder()
                                  .field(regionQlceViewFieldInputBuilder.build())
                                  .operator(QLCEViewFilterOperator.NOT_NULL)
                                  .values(new String[] {""})
                                  .build())
                    .build());
    filters.add(QLCEViewFilterWrapper.builder()
                    .idFilter(QLCEViewFilter.builder()
                                  .field(QLCEViewFieldInput.builder()
                                             .fieldId(fieldId)
                                             .fieldName(fieldName)
                                             .identifier(identifier)
                                             .build())
                                  .operator(QLCEViewFilterOperator.IN)
                                  .values(awsID.toArray(new String[0]))
                                  .build())
                    .build());

    regionQlceViewFieldInputBuilder = regionQlceViewFieldInputBuilder.identifierName(COMMON.getDisplayName());

    List<QLCEViewGroupBy> groupBy = new ArrayList<>();
    groupBy.add(QLCEViewGroupBy.builder().entityGroupBy(regionQlceViewFieldInputBuilder.build()).build());

    List<QLCEViewSortCriteria> sort = Collections.singletonList(
        QLCEViewSortCriteria.builder().sortOrder(QLCESortOrder.DESCENDING).sortType(QLCEViewSortType.COST).build());
    return viewsBillingService
        .getEntityStatsDataPointsNg(filters, groupBy, aggregateFunction, sort,
            configuration.getRecommendationConfig().getRegionsLimit(), 0,
            viewsQueryHelper.buildQueryParams(accountId, false, false))
        .getData();
  }

  // Get aggreated names of different accounts
  private List<QLCEViewEntityStatsDataPoint> getAccountNames(String accountId, List<String> awsID, String fieldId,
      String fieldName, ViewFieldIdentifier identifier, String identifierName) {
    List<QLCEViewAggregation> aggregateFunction = Collections.singletonList(
        QLCEViewAggregation.builder().columnName("cost").operationType(QLCEViewAggregateOperation.SUM).build());

    QLCEViewFieldInputBuilder qlceViewFieldInputBuilder =
        QLCEViewFieldInput.builder().fieldId(fieldId).fieldName(fieldName).identifier(identifier);

    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(getTimeFilter(getStartOfMonth(true), QLCEViewTimeFilterOperator.AFTER));
    filters.add(getTimeFilter(getStartOfMonth(false) - 1000, QLCEViewTimeFilterOperator.BEFORE));
    filters.add(QLCEViewFilterWrapper.builder()
                    .idFilter(QLCEViewFilter.builder()
                                  .field(qlceViewFieldInputBuilder.build())
                                  .operator(QLCEViewFilterOperator.IN)
                                  .values(awsID.toArray(new String[0]))
                                  .build())
                    .build());

    qlceViewFieldInputBuilder = qlceViewFieldInputBuilder.identifierName(identifierName);

    List<QLCEViewGroupBy> groupBy = new ArrayList<>();
    groupBy.add(QLCEViewGroupBy.builder().entityGroupBy(qlceViewFieldInputBuilder.build()).build());
    List<QLCEViewSortCriteria> sort = Collections.singletonList(
        QLCEViewSortCriteria.builder().sortOrder(QLCESortOrder.DESCENDING).sortType(QLCEViewSortType.COST).build());

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
