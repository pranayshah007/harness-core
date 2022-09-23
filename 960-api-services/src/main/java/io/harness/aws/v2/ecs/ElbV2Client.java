package io.harness.aws.v2.ecs;

import io.harness.aws.beans.AwsInternalConfig;

import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeListenersRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeListenersResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeLoadBalancersRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeLoadBalancersResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeRulesRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeRulesResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.ModifyListenerRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.ModifyListenerResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.ModifyRuleRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.ModifyRuleResponse;

public interface ElbV2Client {
  DescribeListenersResponse describeListener(
      AwsInternalConfig awsConfig, DescribeListenersRequest describeListenersRequest, String region);

  DescribeRulesResponse describeRules(
      AwsInternalConfig awsConfig, DescribeRulesRequest describeRulesRequest, String region);

  ModifyRuleResponse modifyRule(AwsInternalConfig awsConfig, ModifyRuleRequest modifyRuleRequest, String region);

  ModifyListenerResponse modifyListener(
      AwsInternalConfig awsConfig, ModifyListenerRequest modifyListenerRequest, String region);

  DescribeLoadBalancersResponse describeLoadBalancer(
      AwsInternalConfig awsConfig, DescribeLoadBalancersRequest describeLoadBalancersRequest, String region);
}
