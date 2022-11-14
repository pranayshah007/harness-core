package io.harness.ccm.graphql.query.instanceutilization;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.beans.recommendation.EC2InstanceUtilizationData;
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
 * Query class to fetch the ec2 instance utilisation data from timescale.
 */
@Slf4j
@Singleton
@GraphQLApi
@OwnedBy(CE)
public class UtilizationQuery {
  @Inject GraphQLUtils graphQLUtils;
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
}
