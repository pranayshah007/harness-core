/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.AwsCallTracker;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.data.structure.CollectionUtils;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.connector.awsconnector.AwsListLoadBalancersTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsTaskParams;
import io.harness.exception.AwsLoadBalancerException;
import io.harness.exception.ExceptionUtils;
import io.harness.logging.CommandExecutionStatus;

import software.wings.service.impl.AwsUtils;
import software.wings.service.impl.aws.client.CloseableAmazonWebServiceClient;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient;
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersRequest;
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersResult;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class AwsListLoadBalancersDelegateTaskHelper {
  @Inject private AwsCallTracker tracker;
  @Inject private AwsUtils awsUtils;

  public DelegateResponseData getLoadBalancerList(AwsTaskParams awsTaskParams) {
    awsUtils.decryptRequestDTOs(awsTaskParams.getAwsConnector(), awsTaskParams.getEncryptionDetails());
    AwsInternalConfig awsInternalConfig =
        awsUtils.getAwsInternalConfig(awsTaskParams.getAwsConnector(), awsTaskParams.getRegion());

    try (CloseableAmazonWebServiceClient<AmazonElasticLoadBalancingClient> closeableAmazonElbClient =
             new CloseableAmazonWebServiceClient(
                 awsUtils.getAmazonElbClient(Regions.fromName(awsTaskParams.getRegion()), awsInternalConfig))) {
      String nextMarker = null;
      List<String> result = new ArrayList<>();

      do {
        DescribeLoadBalancersRequest describeLoadBalancersRequest =
            new DescribeLoadBalancersRequest().withPageSize(400).withMarker(nextMarker);
        tracker.trackClassicELBCall("Get aws LB list");
        DescribeLoadBalancersResult describeLoadBalancersResult =
            closeableAmazonElbClient.getClient().describeLoadBalancers(describeLoadBalancersRequest);
        result.addAll(convertToList(describeLoadBalancersResult));
        nextMarker = describeLoadBalancersResult.getNextMarker();
      } while (nextMarker != null);

      return AwsListLoadBalancersTaskResponse.builder()
          .loadBalancers(result)
          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
          .build();

    } catch (Exception e) {
      log.error("Exception get aws load balancers", e);
      throw new AwsLoadBalancerException(ExceptionUtils.getMessage(e), e);
    }
  }

  private List<String> convertToList(DescribeLoadBalancersResult result) {
    return CollectionUtils.emptyIfNull(result.getLoadBalancerDescriptions())
        .stream()
        .map(LoadBalancerDescription::getLoadBalancerName)
        .collect(toList());
  }
}
