/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.service.impl;

import static io.harness.NGCommonEntityConstants.MONGODB_ID;
import static io.harness.ccm.commons.entities.CCMField.RULE_NAME;
import static io.harness.ccm.commons.entities.CCMField.RULE_SET_NAME;
import static io.harness.ccm.views.helper.RuleCloudProviderType.AWS;
import static io.harness.ccm.views.helper.RuleCloudProviderType.AZURE;
import static io.harness.ccm.views.helper.RuleCostType.POTENTIAL;
import static io.harness.ccm.views.helper.RuleCostType.REALIZED;
import static io.harness.ccm.views.helper.RuleExecutionType.INTERNAL;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.group;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.project;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.sort;

import io.harness.ccm.commons.entities.CCMSort;
import io.harness.ccm.commons.entities.CCMSortOrder;
import io.harness.ccm.commons.entities.CCMTimeFilter;
import io.harness.ccm.governance.dto.OverviewExecutionCostDetails;
import io.harness.ccm.views.dao.RuleDAO;
import io.harness.ccm.views.dao.RuleExecutionDAO;
import io.harness.ccm.views.dao.RuleSetDAO;
import io.harness.ccm.views.entities.Rule;
import io.harness.ccm.views.entities.RuleExecution;
import io.harness.ccm.views.entities.RuleExecution.RuleExecutionKeys;
import io.harness.ccm.views.entities.RuleRecommendation;
import io.harness.ccm.views.entities.RuleSet;
import io.harness.ccm.views.helper.ExecutionSummary;
import io.harness.ccm.views.helper.FilterValues;
import io.harness.ccm.views.helper.GovernanceRuleFilter;
import io.harness.ccm.views.helper.OverviewExecutionDetails;
import io.harness.ccm.views.helper.ResourceTypeCount;
import io.harness.ccm.views.helper.ResourceTypeCount.ResourceTypeCountkey;
import io.harness.ccm.views.helper.ResourceTypePotentialCost;
import io.harness.ccm.views.helper.ResourceTypePotentialCost.ResourceTypeCostKey;
import io.harness.ccm.views.helper.RuleExecutionFilter;
import io.harness.ccm.views.helper.RuleExecutionList;
import io.harness.ccm.views.helper.RuleSetFilter;
import io.harness.ccm.views.service.RuleExecutionService;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.fabric8.utils.Lists;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationOptions;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.aggregation.ProjectionOperation;
import org.springframework.data.mongodb.core.aggregation.SortOperation;
import org.springframework.data.mongodb.core.query.Criteria;

@Slf4j
@Singleton
public class RuleExecutionServiceImpl implements RuleExecutionService {
  @Inject private RuleExecutionDAO rulesExecutionDAO;
  @Inject private RuleSetDAO ruleSetDAO;
  @Inject private RuleDAO rulesDao;
  @Inject private MongoTemplate mongoTemplate;
  private final String POTENTIALCOST = "potentialCost";

  @Override
  public String save(RuleExecution rulesExecution) {
    return rulesExecutionDAO.save(rulesExecution);
  }

  @Override
  public List<String> save(List<RuleExecution> rulesExecutions) {
    return rulesExecutionDAO.save(rulesExecutions);
  }

  @Override
  public RuleExecution get(String accountId, String uuid) {
    return rulesExecutionDAO.get(accountId, uuid);
  }
  @Override

  public List<RuleExecution> list(String accountId) {
    return rulesExecutionDAO.list(accountId);
  }

  @Override
  public RuleExecutionList filterExecution(RuleExecutionFilter rulesExecutionFilter) {
    return rulesExecutionDAO.filterExecution(rulesExecutionFilter);
  }

  @Override
  public FilterValues filterValue(String accountId) {
    FilterValues filterValues = FilterValues.builder().build();
    RuleSetFilter ruleSetFilter = RuleSetFilter.builder().build();
    ruleSetFilter.setOrderBy(
        Collections.singletonList(CCMSort.builder().field(RULE_SET_NAME).order(CCMSortOrder.ASCENDING).build()));
    List<RuleSet> ruleSet = ruleSetDAO.list(accountId, ruleSetFilter).getRuleSet();
    if (ruleSet != null) {
      LinkedHashMap<String, String> ruleSetsIds = new LinkedHashMap<>();
      for (RuleSet iterate : ruleSet) {
        ruleSetsIds.put(iterate.getUuid(), iterate.getName());
      }
      filterValues.setRuleSetIds(ruleSetsIds);
    }
    GovernanceRuleFilter governancePolicyFilter = GovernanceRuleFilter.builder().build();
    governancePolicyFilter.setAccountId(accountId);
    governancePolicyFilter.setOrderBy(
        Collections.singletonList(CCMSort.builder().field(RULE_NAME).order(CCMSortOrder.ASCENDING).build()));
    List<Rule> rules = rulesDao.list(governancePolicyFilter).getRules();
    if (rules != null) {
      LinkedHashMap<String, String> rulesIds = new LinkedHashMap<>();
      for (Rule iterate : rules) {
        rulesIds.put(iterate.getUuid(), iterate.getName());
      }
      filterValues.setRuleIds(rulesIds);
    }

    return filterValues;
  }

  public static Map<String, String> getDates() {
    LocalDate endDate = LocalDate.now();
    LocalDate startDate = endDate.minusDays(30);
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    Map<String, String> datesAsString = new HashMap<>();
    LocalDate currentDate = startDate;
    while (currentDate.isBefore(endDate)) {
      currentDate = currentDate.plusDays(1);
      datesAsString.put(currentDate.format(formatter), "0");
    }
    return datesAsString;
  }

  @Override
  public OverviewExecutionDetails getOverviewExecutionDetails(
      String accountId, RuleExecutionFilter ruleExecutionFilter) {
    OverviewExecutionDetails overviewExecutionDetails =
        rulesExecutionDAO.getOverviewExecutionDetails(accountId, ruleExecutionFilter);
    overviewExecutionDetails.setTopResourceTypeExecution(getResourceTypeCount(accountId, ruleExecutionFilter));
    Map<String, String> dates = getDates();
    List<Map> result = getResourceActualCost(accountId, ruleExecutionFilter);
    for (Map res : result) {
      dates.put(res.get("_id").toString(), res.get("cost").toString());
    }
    overviewExecutionDetails.setMonthlyRealizedSavings(dates);
    return overviewExecutionDetails;
  }

  public OverviewExecutionCostDetails getExecutionCostDetails(
      String accountId, RuleExecutionFilter ruleExecutionFilter, List<String> recommendationIds) {
    return getResourcePotentialCost(accountId, ruleExecutionFilter, recommendationIds);
  }

  public <T> AggregationResults<T> aggregate(Aggregation aggregation, Class<T> classToFillResultIn) {
    return mongoTemplate.aggregate(aggregation, RuleExecution.class, classToFillResultIn);
  }

  private OverviewExecutionCostDetails getResourcePotentialCost(
      String accountId, RuleExecutionFilter ruleExecutionFilter, List<String> recommendationIds) {
    // Getting the recommendations data from mongo DB(Only the executions data is projected)
    List<RuleRecommendation> ruleRecommendationList =
        rulesExecutionDAO.getGovernanceRecommendations(accountId, recommendationIds);
    if (Lists.isNullOrEmpty(ruleRecommendationList)) {
      return OverviewExecutionCostDetails.builder().build();
    }

    // Extracting the ruleExecutionIds from recommendations data
    Set<String> ruleExecutionIds = ruleRecommendationList.stream()
                                       .flatMap(ruleRecommendation -> ruleRecommendation.getExecutions().stream())
                                       .map(ExecutionSummary::getRuleExecutionID)
                                       .collect(Collectors.toSet());
    if (ruleExecutionIds.size() < 1) {
      return OverviewExecutionCostDetails.builder().build();
    }
    return OverviewExecutionCostDetails.builder()
        .awsExecutionCostDetails(
            getResourcePotentialCostPerCloudProvider(accountId, ruleExecutionFilter, AWS.name(), ruleExecutionIds))
        .azureExecutionCostDetails(
            getResourcePotentialCostPerCloudProvider(accountId, ruleExecutionFilter, AZURE.name(), ruleExecutionIds))
        .build();
  }

  private Map<String, Double> getResourcePotentialCostPerCloudProvider(String accountId,
      RuleExecutionFilter ruleExecutionFilter, String cloudProvider, Set<String> ruleExecutionIdList) {
    // Adding ruleExecutionIdList as extra criteria to filter correct executions
    Criteria criteria = Criteria.where(RuleExecutionKeys.accountId)
                            .is(accountId)
                            .and(MONGODB_ID)
                            .in(new ArrayList<>(ruleExecutionIdList))
                            .and(RuleExecutionKeys.cost)
                            .ne(null)
                            .and(RuleExecutionKeys.costType)
                            .is(POTENTIAL)
                            .and(RuleExecutionKeys.executionType)
                            .is(INTERNAL)
                            .and(RuleExecutionKeys.cloudProvider)
                            .is(cloudProvider);
    if (ruleExecutionFilter.getTime() != null) {
      for (CCMTimeFilter time : ruleExecutionFilter.getTime()) {
        switch (time.getOperator()) {
          case AFTER:
            criteria.and(RuleExecutionKeys.createdAt).gte(time.getTimestamp());
            break;
          default:
            throw new InvalidRequestException("Operator not supported not supported for time fields");
        }
      }
    }
    MatchOperation matchStage = Aggregation.match(criteria);
    GroupOperation group =
        group(RuleExecutionKeys.resourceType).sum(RuleExecutionKeys.cost).as(ResourceTypeCostKey.cost);
    ProjectionOperation projectionStage =
        project().and(MONGODB_ID).as(ResourceTypeCostKey.resourceName).andInclude(ResourceTypeCostKey.cost);
    SortOperation sortStage = sort(Sort.by(ResourceTypeCostKey.cost));
    Map<String, Double> result = new HashMap<>();
    aggregate(newAggregation(matchStage, sortStage, group, projectionStage), ResourceTypePotentialCost.class)
        .getMappedResults()
        .forEach(resource
            -> result.put(
                resource.getResourceName() != null ? resource.getResourceName() : "others", resource.getCost()));
    return result;
  }

  public List<Map> getResourceActualCost(String accountId, RuleExecutionFilter ruleExecutionFilter) {
    Criteria criteria = Criteria.where(RuleExecutionKeys.accountId)
                            .is(accountId)
                            .and(RuleExecutionKeys.cost)
                            .ne(null)
                            .and(RuleExecutionKeys.costType)
                            .is(REALIZED)
                            .and(RuleExecutionKeys.executionType)
                            .ne(INTERNAL);
    if (ruleExecutionFilter.getTime() != null) {
      for (CCMTimeFilter time : ruleExecutionFilter.getTime()) {
        switch (time.getOperator()) {
          case AFTER:
            criteria.and(RuleExecutionKeys.createdAt).gte(time.getTimestamp());
            break;
          default:
            throw new InvalidRequestException("Operator not supported not supported for time fields");
        }
      }
    }
    MatchOperation matchStage = Aggregation.match(criteria);
    ProjectionOperation projectionStage = Aggregation.project()
                                              .andExpression("dateToString('%Y-%m-%d', toDate("
                                                  + "$createdAt"
                                                  + "))")
                                              .as("formatted_day")
                                              .andInclude("cost");

    GroupOperation group = Aggregation.group("formatted_day").sum("cost").as("cost");
    AggregationOperation[] stages = {matchStage, projectionStage, group};
    Aggregation aggregation = Aggregation.newAggregation(stages);
    List<Map> result = mongoTemplate.aggregate(aggregation, "governanceRuleExecution", Map.class).getMappedResults();

    log.info("getResourceActualCost: {}", result);
    return result;
  }

  public Map<String, Integer> getResourceTypeCount(String accountId, RuleExecutionFilter ruleExecutionFilter) {
    Criteria criteria = Criteria.where(RuleExecutionKeys.accountId)
                            .is(accountId)
                            .and(RuleExecutionKeys.resourceType)
                            .exists(true)
                            .and(RuleExecutionKeys.executionType)
                            .ne(INTERNAL);
    if (ruleExecutionFilter.getTime() != null) {
      for (CCMTimeFilter time : ruleExecutionFilter.getTime()) {
        switch (time.getOperator()) {
          case AFTER:
            criteria.and(RuleExecutionKeys.lastUpdatedAt).gte(time.getTimestamp());
            break;
          default:
            throw new InvalidRequestException("Operator not supported not supported for time fields");
        }
      }
    }
    MatchOperation matchStage = Aggregation.match(criteria);
    GroupOperation group = group(RuleExecutionKeys.resourceType).count().as(ResourceTypeCountkey.count);
    SortOperation sortStage = sort(Sort.by(ResourceTypeCountkey.count));

    ProjectionOperation projectionStage =
        project().and(MONGODB_ID).as(ResourceTypeCountkey.resourceName).andInclude(ResourceTypeCountkey.count);
    Map<String, Integer> result = new HashMap<>();
    AggregationOptions options = Aggregation.newAggregationOptions().allowDiskUse(true).build();
    aggregate(
        newAggregation(matchStage, sortStage, group, projectionStage).withOptions(options), ResourceTypeCount.class)
        .getMappedResults()
        .forEach(resource -> result.put(resource.getResourceName(), resource.getCount()));
    log.info("result: {}", result);
    return result;
  }

  @Override
  public RuleExecutionList getRuleRecommendationDetails(String ruleRecommendationId, String accountId) {
    MatchOperation match = Aggregation.match(Criteria.where("_id").is(ruleRecommendationId));
    Aggregation aggregation = Aggregation.newAggregation(match);
    RuleRecommendation ruleRecommendation =
        mongoTemplate.aggregate(aggregation, "governanceRecommendation", RuleRecommendation.class)
            .getMappedResults()
            .get(0);
    List<String> executionIds = new ArrayList<>();
    for (ExecutionSummary executionSummary : ruleRecommendation.getExecutions()) {
      executionIds.add(executionSummary.getRuleExecutionID());
    }
    RuleExecutionFilter ruleExecutionFilter =
        RuleExecutionFilter.builder().executionIds(executionIds).accountId(accountId).build();
    return rulesExecutionDAO.filterExecutionInternal(ruleExecutionFilter);
  }

  @Override
  public RuleRecommendation getRuleRecommendation(String ruleRecommendationId, String accountId) {
    MatchOperation match = Aggregation.match(Criteria.where("_id").is(ruleRecommendationId));
    Aggregation aggregation = Aggregation.newAggregation(match);
    return mongoTemplate.aggregate(aggregation, "governanceRecommendation", RuleRecommendation.class)
        .getMappedResults()
        .get(0);
  }
}
