/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc.aws.delegate;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.task.aws.AwsElbListener;
import io.harness.delegate.task.aws.AwsLoadBalancerDetails;
import io.harness.delegate.task.aws.LbDetailsForAlbTrafficShift;
import io.harness.delegate.task.aws.LoadBalancerDetailsForBGDeployment;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AwsConfig;
import software.wings.beans.command.ExecutionLogCallback;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancingClient;
import com.amazonaws.services.elasticloadbalancingv2.model.Action;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeListenersResult;
import com.amazonaws.services.elasticloadbalancingv2.model.Listener;
import com.amazonaws.services.elasticloadbalancingv2.model.LoadBalancer;
import com.amazonaws.services.elasticloadbalancingv2.model.Rule;
import com.amazonaws.services.elasticloadbalancingv2.model.TargetGroup;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public interface AwsElbHelperServiceDelegate {
  List<AwsLoadBalancerDetails> listApplicationLoadBalancerDetails(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region);

  List<AwsLoadBalancerDetails> listNetworkLoadBalancerDetails(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region);

  List<AwsLoadBalancerDetails> listElasticLoadBalancerDetails(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region);

  AmazonElasticLoadBalancingClient getAmazonElasticLoadBalancingClientV2(Regions region, AwsConfig awsConfig);

  List<String> listClassicLoadBalancers(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region);
  Map<String, String> listTargetGroupsForAlb(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region, String loadBalancerName);
  void waitForAsgInstancesToRegisterWithTargetGroup(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      String region, String targetGroupArn, String asgName, int timeout, ExecutionLogCallback logCallback);
  void waitForAsgInstancesToDeRegisterWithTargetGroup(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      String region, String targetGroupArn, String asgName, int timeout, ExecutionLogCallback logCallback);
  void waitForAsgInstancesToRegisterWithClassicLB(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      String region, String classicLB, String asgName, int timeout, ExecutionLogCallback logCallback);
  void waitForAsgInstancesToDeRegisterWithClassicLB(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      String region, String classicLB, String asgName, int timeout, ExecutionLogCallback logCallback);
  TargetGroup cloneTargetGroup(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region,
      String targetGroupArn, String newTargetGroupName);
  Listener getElbListener(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region, String listenerArn);
  List<Rule> getListenerRulesFromListenerArn(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      String region, String listenerArn, ExecutionLogCallback logCallback);
  List<Rule> getListenerRuleFromListenerRuleArn(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      String region, String listenerRuleArn, ExecutionLogCallback logCallback);
  List<AwsElbListener> getElbListenersForLoadBalaner(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region, String loadBalancerName);
  Listener createStageListener(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region,
      String listenerArn, Integer port, String targetGroupArn);
  Optional<TargetGroup> getTargetGroup(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region, String targetGroupArn);
  Optional<TargetGroup> getTargetGroupByName(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region, String targetGroupName);
  Optional<LoadBalancer> getLoadBalancer(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region, String loadBalancerName);
  void updateDefaultListenersForSpotInstBG(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      String prodListenerArn, String stageListenerArn, String region);
  void updateListenersForEcsBG(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String prodListenerArn,
      String stageListenerArn, String region);
  List<Action> getMatchingTargetGroupForSpecificListenerRuleArn(AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, String region, String listenerArn, String prodListenerRuleArn,
      String targetGroupArn, ExecutionLogCallback executionLogCallback);
  void swapListenersForEcsBG(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      boolean isUseSpecificRules, String prodListenerArn, String stageListenerArn, String prodListenerRuleArn,
      String stageListenerRuleArn, String targetGroupArn1, String targetGroupArn2, String region,
      ExecutionLogCallback logCallback);
  DescribeListenersResult describeListenerResult(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String prodListenerArn, String region);
  String getTargetGroupForDefaultAction(Listener listener, ExecutionLogCallback executionLogCallback);
  void updateListenersForBGDeployment(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      List<LoadBalancerDetailsForBGDeployment> lbDetailsForBGDeployments, String region,
      ExecutionLogCallback logCallback);
  void updateListenersForSpotInstBGDeployment(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      List<LoadBalancerDetailsForBGDeployment> lbDetailsForBGDeployments, String region,
      ExecutionLogCallback logCallback);
  void modifyListenerRule(AmazonElasticLoadBalancing client, String listenerArn, String listenerRuleArn,
      String targetGroupArn, ExecutionLogCallback executionLogCallback);
  void modifySpecificRule(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region,
      String ruleArn, String targetGroupArn, ExecutionLogCallback logCallback);
  TargetGroup fetchTargetGroupForSpecificRules(AwsElbListener listener, String ruleArn,
      ExecutionLogCallback logCallback, AwsConfig awsConfig, String region,
      List<EncryptedDataDetail> encryptionDetails);
  LbDetailsForAlbTrafficShift loadTrafficShiftTargetGroupData(AwsConfig awsConfig, String region,
      List<EncryptedDataDetail> encryptionDetails, LbDetailsForAlbTrafficShift originalLbDetails,
      ExecutionLogCallback logCallback);
  void updateRulesForAlbTrafficShift(AwsConfig awsConfig, String region, List<EncryptedDataDetail> encryptionDetails,
      List<LbDetailsForAlbTrafficShift> details, ExecutionLogCallback logCallback, int newServiceTrafficWeight,
      String groupType);
}
