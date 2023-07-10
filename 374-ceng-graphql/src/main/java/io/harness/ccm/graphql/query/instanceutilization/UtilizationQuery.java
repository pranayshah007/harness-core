/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.query.instanceutilization;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.beans.recommendation.AzureVmMetricType;
import io.harness.ccm.commons.beans.recommendation.AzureVmUtilisationDTO;
import io.harness.ccm.commons.beans.recommendation.EC2InstanceUtilizationData;
import io.harness.ccm.graphql.core.recommendation.AzureMetricsUtilisationService;
import io.harness.ccm.graphql.core.recommendation.EC2InstanceUtilizationService;
import io.harness.ccm.graphql.utils.GraphQLUtils;
import io.harness.ccm.graphql.utils.annotations.GraphQLApi;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.leangen.graphql.annotations.GraphQLArgument;
import io.leangen.graphql.annotations.GraphQLEnvironment;
import io.leangen.graphql.annotations.GraphQLQuery;
import io.leangen.graphql.execution.ResolutionEnvironment;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Query class to fetch the utilisation data for vm instance(s)
 * EC2 instance from the timescale DB
 * Azure VM from the Bigquery
 */
@Slf4j
@Singleton
@GraphQLApi
@OwnedBy(CE)
public class UtilizationQuery {
  @Inject GraphQLUtils graphQLUtils;
  @Inject AzureMetricsUtilisationService azureMetricsUtilisationService;
  @Inject EC2InstanceUtilizationService ec2InstanceUtilizationService;

  /**
   * GQL query to fetch the instance util data.
   * @param instanceId
   * @param env
   * @return
   */
  @GraphQLQuery(name = "utilData", description = "Fetches the cpu and memory utilization data for ec2 instance")
  public List<EC2InstanceUtilizationData> utilData(
      @GraphQLArgument(name = "instanceId") String instanceId, @GraphQLEnvironment final ResolutionEnvironment env) {
    final String accountId = graphQLUtils.getAccountIdentifier(env);
    return ec2InstanceUtilizationService.getEC2InstanceUtilizationData(accountId, instanceId);
  }

  /**
   * GQL query to fetch the instance util data.
   * @param azureVmId
   * @param duration
   * @param env
   * @return
   */
  @GraphQLQuery(name = "azureVmUtilData", description = "Fetches the utilization data for azure vm")
  public List<AzureVmUtilisationDTO> azureVmUtilData(@GraphQLArgument(name = "azureVmId") String azureVmId,
      @GraphQLArgument(name = "duration") int duration, @GraphQLEnvironment final ResolutionEnvironment env,
      @GraphQLArgument(name = "metricType") AzureVmMetricType azureVmMetricType) {
    final String accountId = graphQLUtils.getAccountIdentifier(env);
    return azureMetricsUtilisationService.getAzureVmMetricUtilisationData(
        azureVmId, accountId, duration, azureVmMetricType);
  }
}
