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
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.connector.awsconnector.AwsListElbListenerRulesTaskParamsRequest;
import io.harness.delegate.beans.connector.awsconnector.AwsListElbListenerRulesTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsListElbListenersTaskParamsRequest;
import io.harness.delegate.beans.connector.awsconnector.AwsListElbListenersTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsListElbTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsTaskParams;
import io.harness.exception.AwsLoadBalancerException;
import io.harness.exception.ExceptionUtils;
import io.harness.logging.CommandExecutionStatus;

import software.wings.service.impl.AwsApiHelperService;
import software.wings.service.impl.AwsUtils;
import software.wings.service.impl.aws.client.CloseableAmazonWebServiceClient;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancingClient;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancingClientBuilder;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeListenersRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeListenersResult;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeLoadBalancersRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeLoadBalancersResult;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeRulesRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeRulesResult;
import com.amazonaws.services.elasticloadbalancingv2.model.LoadBalancer;
import com.amazonaws.services.elasticloadbalancingv2.model.Rule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class AwsElasticLoadBalancersDelegateTaskHelper {
  @Inject private AwsCallTracker tracker;
  @Inject private AwsApiHelperService awsApiHelperService;
  @Inject private AwsUtils awsUtils;

  public DelegateResponseData getElbList(AwsTaskParams awsTaskParams) {
    awsUtils.decryptRequestDTOs(awsTaskParams.getAwsConnector(), awsTaskParams.getEncryptionDetails());
    AwsInternalConfig awsInternalConfig =
        awsUtils.getAwsInternalConfig(awsTaskParams.getAwsConnector(), awsTaskParams.getRegion());

    try (CloseableAmazonWebServiceClient<AmazonElasticLoadBalancingClient> closeableAmazonElbClient =
             new CloseableAmazonWebServiceClient(
                 getAmazonElbClientV2(Regions.fromName(awsTaskParams.getRegion()), awsInternalConfig))) {
      String nextMarker = null;
      List<String> result = new ArrayList<>();
      do {
        DescribeLoadBalancersRequest describeLoadBalancersRequest =
            new DescribeLoadBalancersRequest().withPageSize(100).withMarker(nextMarker);
        tracker.trackELBCall("Get aws ELB list");
        DescribeLoadBalancersResult describeLoadBalancersResult =
            closeableAmazonElbClient.getClient().describeLoadBalancers(describeLoadBalancersRequest);
        result.addAll(convertDescribeLoadBalancersResultToList(describeLoadBalancersResult));
        nextMarker = describeLoadBalancersResult.getNextMarker();
      } while (nextMarker != null);

      return AwsListElbTaskResponse.builder()
          .loadBalancerNames(result)
          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
          .build();

    } catch (Exception e) {
      log.error("Exception get aws elastic load balancers", e);
      throw new AwsLoadBalancerException(ExceptionUtils.getMessage(e), e);
    }
  }

  public DelegateResponseData getElbListenerList(AwsTaskParams awsTaskParams) {
    awsUtils.decryptRequestDTOs(awsTaskParams.getAwsConnector(), awsTaskParams.getEncryptionDetails());
    AwsInternalConfig awsInternalConfig =
        awsUtils.getAwsInternalConfig(awsTaskParams.getAwsConnector(), awsTaskParams.getRegion());

    AwsListElbListenersTaskParamsRequest awsListElbListenersTaskParamsRequest =
        (AwsListElbListenersTaskParamsRequest) awsTaskParams;

    try (CloseableAmazonWebServiceClient<AmazonElasticLoadBalancingClient> closeableAmazonElbClient =
             new CloseableAmazonWebServiceClient(getAmazonElbClientV2(
                 Regions.fromName(awsListElbListenersTaskParamsRequest.getRegion()), awsInternalConfig))) {
      String nextMarker = null;
      Map<String, String> result = new HashMap<>();
      DescribeLoadBalancersRequest describeLoadBalancersRequest =
          new DescribeLoadBalancersRequest().withNames(awsListElbListenersTaskParamsRequest.getElasticLoadBalancer());
      tracker.trackELBCall("Get aws ELB list");
      DescribeLoadBalancersResult describeLoadBalancersResult =
          closeableAmazonElbClient.getClient().describeLoadBalancers(describeLoadBalancersRequest);
      do {
        DescribeListenersRequest describeListenersRequest =
            new DescribeListenersRequest()
                .withPageSize(100)
                .withMarker(nextMarker)
                .withLoadBalancerArn(describeLoadBalancersResult.getLoadBalancers().get(0).getLoadBalancerArn());
        tracker.trackELBCall("Get aws ELB listener list");
        DescribeListenersResult describeListenersResult =
            closeableAmazonElbClient.getClient().describeListeners(describeListenersRequest);
        result.putAll(convertDescribeListenersResultToMap(describeListenersResult));
        nextMarker = describeListenersResult.getNextMarker();
      } while (nextMarker != null);

      return AwsListElbListenersTaskResponse.builder()
          .listenerArnMap(result)
          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
          .build();

    } catch (Exception e) {
      log.error("Exception get aws elastic load balancer listeners", e);
      throw new AwsLoadBalancerException(ExceptionUtils.getMessage(e), e);
    }
  }

  public DelegateResponseData getElbListenerRulesList(AwsTaskParams awsTaskParams) {
    awsUtils.decryptRequestDTOs(awsTaskParams.getAwsConnector(), awsTaskParams.getEncryptionDetails());
    AwsInternalConfig awsInternalConfig =
        awsUtils.getAwsInternalConfig(awsTaskParams.getAwsConnector(), awsTaskParams.getRegion());

    AwsListElbListenerRulesTaskParamsRequest awsListElbListenerRulesTaskParamsRequest =
        (AwsListElbListenerRulesTaskParamsRequest) awsTaskParams;

    try (CloseableAmazonWebServiceClient<AmazonElasticLoadBalancingClient> closeableAmazonElbClient =
             new CloseableAmazonWebServiceClient(getAmazonElbClientV2(
                 Regions.fromName(awsListElbListenerRulesTaskParamsRequest.getRegion()), awsInternalConfig))) {
      String nextMarker = null;
      List<String> result = new ArrayList<>();
      do {
        DescribeRulesRequest describeRulesRequest =
            new DescribeRulesRequest()
                .withPageSize(100)
                .withMarker(nextMarker)
                .withListenerArn(awsListElbListenerRulesTaskParamsRequest.getListenerArn());
        tracker.trackELBCall("Get aws ELB listener rules list");
        DescribeRulesResult describeRulesResult =
            closeableAmazonElbClient.getClient().describeRules(describeRulesRequest);
        result.addAll(convertDescribeRulesResultToList(describeRulesResult));
        nextMarker = describeRulesResult.getNextMarker();
      } while (nextMarker != null);

      return AwsListElbListenerRulesTaskResponse.builder()
          .listenerRulesArn(result)
          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
          .build();

    } catch (Exception e) {
      log.error("Exception get aws elastic load balancer listener rules", e);
      throw new AwsLoadBalancerException(ExceptionUtils.getMessage(e), e);
    }
  }

  private AmazonElasticLoadBalancingClient getAmazonElbClientV2(Regions region, AwsInternalConfig awsConfig) {
    AmazonElasticLoadBalancingClientBuilder builder =
        AmazonElasticLoadBalancingClientBuilder.standard().withRegion(region);
    awsApiHelperService.attachCredentialsAndBackoffPolicy(builder, awsConfig);
    return (AmazonElasticLoadBalancingClient) builder.build();
  }

  private List<String> convertDescribeLoadBalancersResultToList(DescribeLoadBalancersResult result) {
    return CollectionUtils.emptyIfNull(result.getLoadBalancers())
        .stream()
        .map(LoadBalancer::getLoadBalancerName)
        .collect(toList());
  }

  private Map<String, String> convertDescribeListenersResultToMap(DescribeListenersResult result) {
    if (EmptyPredicate.isEmpty(result.getListeners())) {
      return new HashMap<>();
    }
    Map<String, String> output = new HashMap<>();
    result.getListeners().forEach(listener
        -> output.put(String.format("%s: %s", listener.getProtocol(), listener.getPort()), listener.getListenerArn()));
    return output;
  }

  private List<String> convertDescribeRulesResultToList(DescribeRulesResult result) {
    return CollectionUtils.emptyIfNull(result.getRules()).stream().map(Rule::getRuleArn).collect(toList());
  }
}
