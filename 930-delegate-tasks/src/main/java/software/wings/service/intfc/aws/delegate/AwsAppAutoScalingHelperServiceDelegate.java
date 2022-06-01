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
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AwsConfig;

import com.amazonaws.services.applicationautoscaling.model.Alarm;
import com.amazonaws.services.applicationautoscaling.model.DeleteScalingPolicyRequest;
import com.amazonaws.services.applicationautoscaling.model.DeleteScalingPolicyResult;
import com.amazonaws.services.applicationautoscaling.model.DeregisterScalableTargetRequest;
import com.amazonaws.services.applicationautoscaling.model.DeregisterScalableTargetResult;
import com.amazonaws.services.applicationautoscaling.model.DescribeScalableTargetsRequest;
import com.amazonaws.services.applicationautoscaling.model.DescribeScalableTargetsResult;
import com.amazonaws.services.applicationautoscaling.model.DescribeScalingPoliciesRequest;
import com.amazonaws.services.applicationautoscaling.model.DescribeScalingPoliciesResult;
import com.amazonaws.services.applicationautoscaling.model.PutScalingPolicyRequest;
import com.amazonaws.services.applicationautoscaling.model.PutScalingPolicyResult;
import com.amazonaws.services.applicationautoscaling.model.RegisterScalableTargetRequest;
import com.amazonaws.services.applicationautoscaling.model.RegisterScalableTargetResult;
import com.amazonaws.services.applicationautoscaling.model.ScalableTarget;
import com.amazonaws.services.applicationautoscaling.model.ScalingPolicy;
import com.amazonaws.services.cloudwatch.model.MetricAlarm;
import com.amazonaws.services.cloudwatch.model.PutMetricAlarmResult;
import java.util.List;

@TargetModule(HarnessModule._960_API_SERVICES)
@OwnedBy(CDP)
public interface AwsAppAutoScalingHelperServiceDelegate {
  RegisterScalableTargetResult registerScalableTarget(String region, AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, RegisterScalableTargetRequest scalableTargetRequest);

  DeregisterScalableTargetResult deregisterScalableTarget(String region, AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, DeregisterScalableTargetRequest deregisterTargetRequest);

  DescribeScalableTargetsResult listScalableTargets(String region, AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, DescribeScalableTargetsRequest request);

  DescribeScalingPoliciesResult listScalingPolicies(String region, AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, DescribeScalingPoliciesRequest request);

  PutScalingPolicyResult upsertScalingPolicy(String region, AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, PutScalingPolicyRequest putScalingPolicyRequest);

  DeleteScalingPolicyResult deleteScalingPolicy(String region, AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, DeleteScalingPolicyRequest deleteScalingPolicyRequest);

  List<ScalingPolicy> getScalingPolicyFromJson(String json);

  ScalableTarget getScalableTargetFromJson(String json);

  String getJsonForAwsScalableTarget(ScalableTarget scalableTarget);

  String getJsonForAwsScalablePolicy(ScalingPolicy scalingPolicy);

  List<MetricAlarm> fetchAlarmsByName(
      String region, AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, List<Alarm> alarms);

  PutMetricAlarmResult putMetricAlarm(
      String region, AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, MetricAlarm alarm);
}
