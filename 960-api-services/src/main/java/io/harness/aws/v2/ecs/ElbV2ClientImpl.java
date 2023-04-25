/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aws.v2.ecs;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.v2.AwsClientHelper;

import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.SdkClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeListenersRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeListenersResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeLoadBalancersRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeLoadBalancersResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeRulesRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeRulesResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeTargetGroupsRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeTargetGroupsResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeTargetHealthRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeTargetHealthResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.ModifyListenerRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.ModifyListenerResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.ModifyRuleRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.ModifyRuleResponse;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class ElbV2ClientImpl extends AwsClientHelper implements ElbV2Client {
  @Override
  public SdkClient getClient(AwsInternalConfig awsConfig, String region) {
    return ElasticLoadBalancingV2Client.builder()
        .credentialsProvider(getAwsCredentialsProvider(awsConfig))
        .region(Region.of(region))
        .overrideConfiguration(getClientOverrideFromBackoffOverride(awsConfig))
        .build();
  }

  @Override
  public String client() {
    return "ELB";
  }

  @Override
  public void handleClientServiceException(AwsServiceException awsServiceException) {}

  @Override
  public DescribeListenersResponse describeListener(
      AwsInternalConfig awsConfig, DescribeListenersRequest describeListenersRequest, String region) {
    try (ElasticLoadBalancingV2Client elbClient = (ElasticLoadBalancingV2Client) getClient(awsConfig, region)) {
      super.logCall(client(), Thread.currentThread().getStackTrace()[1].getMethodName());
      return elbClient.describeListeners(describeListenersRequest);
    } catch (Exception exception) {
      super.logError(client(), Thread.currentThread().getStackTrace()[1].getMethodName(), exception.getMessage());
      super.handleException(exception);
    }
    return DescribeListenersResponse.builder().build();
  }

  @Override
  public DescribeTargetGroupsResponse describeTargetGroups(
      AwsInternalConfig awsConfig, DescribeTargetGroupsRequest describeTargetGroupsRequest, String region) {
    try (ElasticLoadBalancingV2Client elbClient = (ElasticLoadBalancingV2Client) getClient(awsConfig, region)) {
      super.logCall(client(), Thread.currentThread().getStackTrace()[1].getMethodName());
      return elbClient.describeTargetGroups(describeTargetGroupsRequest);
    } catch (Exception exception) {
      super.logError(client(), Thread.currentThread().getStackTrace()[1].getMethodName(), exception.getMessage());
      super.handleException(exception);
    }
    return DescribeTargetGroupsResponse.builder().build();
  }

  @Override
  public DescribeRulesResponse describeRules(
      AwsInternalConfig awsConfig, DescribeRulesRequest describeRulesRequest, String region) {
    try (ElasticLoadBalancingV2Client elbClient = (ElasticLoadBalancingV2Client) getClient(awsConfig, region)) {
      super.logCall(client(), Thread.currentThread().getStackTrace()[1].getMethodName());
      return elbClient.describeRules(describeRulesRequest);
    } catch (Exception exception) {
      super.logError(client(), Thread.currentThread().getStackTrace()[1].getMethodName(), exception.getMessage());
      super.handleException(exception);
    }
    return DescribeRulesResponse.builder().build();
  }

  @Override
  public ModifyRuleResponse modifyRule(
      AwsInternalConfig awsConfig, ModifyRuleRequest modifyRuleRequest, String region) {
    try (ElasticLoadBalancingV2Client elbClient = (ElasticLoadBalancingV2Client) getClient(awsConfig, region)) {
      super.logCall(client(), Thread.currentThread().getStackTrace()[1].getMethodName());
      return elbClient.modifyRule(modifyRuleRequest);
    } catch (Exception exception) {
      super.logError(client(), Thread.currentThread().getStackTrace()[1].getMethodName(), exception.getMessage());
      super.handleException(exception);
    }
    return ModifyRuleResponse.builder().build();
  }

  @Override
  public ModifyListenerResponse modifyListener(
      AwsInternalConfig awsConfig, ModifyListenerRequest modifyListenerRequest, String region) {
    try (ElasticLoadBalancingV2Client elbClient = (ElasticLoadBalancingV2Client) getClient(awsConfig, region)) {
      super.logCall(client(), Thread.currentThread().getStackTrace()[1].getMethodName());
      return elbClient.modifyListener(modifyListenerRequest);
    } catch (Exception exception) {
      super.logError(client(), Thread.currentThread().getStackTrace()[1].getMethodName(), exception.getMessage());
      super.handleException(exception);
    }
    return ModifyListenerResponse.builder().build();
  }

  @Override
  public DescribeLoadBalancersResponse describeLoadBalancer(
      AwsInternalConfig awsConfig, DescribeLoadBalancersRequest describeLoadBalancersRequest, String region) {
    try (ElasticLoadBalancingV2Client elbClient = (ElasticLoadBalancingV2Client) getClient(awsConfig, region)) {
      super.logCall(client(), Thread.currentThread().getStackTrace()[1].getMethodName());
      return elbClient.describeLoadBalancers(describeLoadBalancersRequest);
    } catch (Exception exception) {
      super.logError(client(), Thread.currentThread().getStackTrace()[1].getMethodName(), exception.getMessage());
      super.handleException(exception);
    }
    return DescribeLoadBalancersResponse.builder().build();
  }

  @Override
  public DescribeTargetHealthResponse describeTargetHealth(
      AwsInternalConfig awsConfig, DescribeTargetHealthRequest request, String region) {
    try (ElasticLoadBalancingV2Client elbClient = (ElasticLoadBalancingV2Client) getClient(awsConfig, region)) {
      super.logCall(client(), Thread.currentThread().getStackTrace()[1].getMethodName());
      return elbClient.describeTargetHealth(request);
    } catch (Exception exception) {
      super.logError(client(), Thread.currentThread().getStackTrace()[1].getMethodName(), exception.getMessage());
      super.handleException(exception);
    }
    return DescribeTargetHealthResponse.builder().build();
  }
}
