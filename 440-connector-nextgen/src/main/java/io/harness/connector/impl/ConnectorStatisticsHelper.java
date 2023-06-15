/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.impl;

import static io.harness.NGCommonEntityConstants.MONGODB_ID;

import static java.lang.Boolean.parseBoolean;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.facet;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.group;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.project;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.account.AccountClient;
import io.harness.connector.entities.Connector.ConnectorKeys;
import io.harness.connector.entities.embedded.gcpkmsconnector.GcpKmsConnector.GcpKmsConnectorKeys;
import io.harness.connector.stats.ConnectorStatistics;
import io.harness.connector.stats.ConnectorStatistics.ConnectorStatisticsKeys;
import io.harness.connector.stats.ConnectorStatusStats.ConnectorStatusStatsKeys;
import io.harness.connector.stats.ConnectorTypeStats.ConnectorTypeStatsKeys;
import io.harness.gitsync.helpers.GitContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.ConnectorRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Arrays;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.FacetOperation;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.aggregation.ProjectionOperation;
import org.springframework.data.mongodb.core.query.Criteria;

@Singleton
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
public class ConnectorStatisticsHelper {
  ConnectorRepository connectorRepository;
  NGSettingsClient settingsClient;
  AccountClient accountClient;

  public ConnectorStatistics getStats(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    Criteria criteria = createCriteriaObjectForConnectorScope(accountIdentifier, orgIdentifier, projectIdentifier);
    MatchOperation matchStage = Aggregation.match(criteria);
    GroupOperation groupByType = group(ConnectorKeys.type).count().as(ConnectorTypeStatsKeys.count);
    ProjectionOperation projectType =
        project().and(MONGODB_ID).as(ConnectorTypeStatsKeys.type).andInclude(ConnectorTypeStatsKeys.count);
    GroupOperation groupByStatus = group(ConnectorKeys.connectionStatus).count().as(ConnectorStatusStatsKeys.count);
    ProjectionOperation projectStatus =
        project().and(MONGODB_ID).as(ConnectorStatusStatsKeys.status).andInclude(ConnectorStatusStatsKeys.count);
    FacetOperation facetOperation = facet(groupByType, projectType)
                                        .as(ConnectorStatisticsKeys.typeStats)
                                        .and(groupByStatus, projectStatus)
                                        .as(ConnectorStatisticsKeys.statusStats);
    Aggregation aggregation = Aggregation.newAggregation(matchStage, facetOperation);
    return connectorRepository.aggregate(aggregation, ConnectorStatistics.class).getUniqueMappedResult();
  }

  private Criteria createCriteriaObjectForConnectorScope(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    Criteria criteria =
        where(ConnectorKeys.accountIdentifier)
            .in(accountIdentifier)
            .and(ConnectorKeys.orgIdentifier)
            .in(orgIdentifier)
            .and(ConnectorKeys.projectIdentifier)
            .in(projectIdentifier)
            .orOperator(where(ConnectorKeys.deleted).exists(false), where(ConnectorKeys.deleted).is(false));

    Boolean isBuiltInSMDisabled = isBuiltInSMDisabled(accountIdentifier);

    if (isBuiltInSMDisabled) {
      criteria.and(GcpKmsConnectorKeys.harnessManaged).ne(true);
    }
    GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
    if (gitEntityInfo != null) {
      criteria.and(ConnectorKeys.yamlGitConfigRef)
          .is(gitEntityInfo.getYamlGitConfigId())
          .and(ConnectorKeys.branch)
          .is(gitEntityInfo.getBranch());
    } else {
      final Criteria isDefaultConnectorCriteria = new Criteria().orOperator(
          where(ConnectorKeys.isFromDefaultBranch).is(true), where(ConnectorKeys.isFromDefaultBranch).exists(false));
      List<Criteria> criteriaList = Arrays.asList(criteria, isDefaultConnectorCriteria);
      return new Criteria().andOperator(criteriaList.toArray(new Criteria[criteriaList.size()]));
    }
    return criteria;
  }

  private Boolean isBuiltInSMDisabled(String accountIdentifier) {
    return parseBoolean(
        NGRestUtils
            .getResponse(settingsClient.getSetting(
                SettingIdentifiers.DISABLE_HARNESS_BUILT_IN_SECRET_MANAGER, accountIdentifier, null, null))
            .getValue());
  }
}
