/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.aws.ecs.ecstaskhandler;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AwsConfig;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.service.intfc.aws.delegate.AwsAppAutoScalingHelperServiceDelegate;

import com.amazonaws.services.applicationautoscaling.model.Alarm;
import com.amazonaws.services.applicationautoscaling.model.PutScalingPolicyRequest;
import com.amazonaws.services.applicationautoscaling.model.PutScalingPolicyResult;
import com.amazonaws.services.applicationautoscaling.model.RegisterScalableTargetRequest;
import com.amazonaws.services.applicationautoscaling.model.ScalableTarget;
import com.amazonaws.services.applicationautoscaling.model.ScalingPolicy;
import com.amazonaws.services.cloudwatch.model.MetricAlarm;
import com.google.inject.Singleton;
import java.util.List;

@Singleton
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class EcsCommandTaskHelper {
  public void registerScalableTargetForEcsService(AwsAppAutoScalingHelperServiceDelegate appAutoScalingService,
      String region, AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      ExecutionLogCallback executionLogCallback, ScalableTarget scalableTarget) {
    if (scalableTarget == null) {
      return;
    }

    executionLogCallback.saveExecutionLog("Registering Scalable Target : " + scalableTarget.getResourceId());
    RegisterScalableTargetRequest request = new RegisterScalableTargetRequest()
                                                .withResourceId(scalableTarget.getResourceId())
                                                .withServiceNamespace(scalableTarget.getServiceNamespace())
                                                .withScalableDimension(scalableTarget.getScalableDimension())
                                                .withRoleARN(scalableTarget.getRoleARN())
                                                .withMinCapacity(scalableTarget.getMinCapacity())
                                                .withMaxCapacity(scalableTarget.getMaxCapacity());

    appAutoScalingService.registerScalableTarget(region, awsConfig, encryptionDetails, request);
    executionLogCallback.saveExecutionLog(new StringBuilder("Registered scalable target Successfully\n")
                                              .append(scalableTarget.toString())
                                              .append("\n")
                                              .toString());
  }

  public void upsertScalingPolicyIfRequired(String policyJson, String resourceId, String scalableDimention,
      String region, AwsConfig awsConfig, AwsAppAutoScalingHelperServiceDelegate appAutoScalingService,
      List<EncryptedDataDetail> encryptionDetails, ExecutionLogCallback executionLogCallback) {
    if (isBlank(policyJson) || isBlank(resourceId)) {
      return;
    }

    List<ScalingPolicy> scalingPolicies = appAutoScalingService.getScalingPolicyFromJson(policyJson);
    if (isNotEmpty(scalingPolicies)) {
      scalingPolicies.forEach(scalingPolicy -> {
        scalingPolicy.withResourceId(resourceId).withScalableDimension(scalableDimention);
        PutScalingPolicyResult result = upsertScalingPolicyIfRequired(
            region, awsConfig, appAutoScalingService, encryptionDetails, executionLogCallback, scalingPolicy);
        updateCloudWatchMetricWithPolicyCreated(region, awsConfig, result.getPolicyARN(),
            scalingPolicy.getAlarms() /*alarms mentioned in policyJson provided by user*/, appAutoScalingService,
            executionLogCallback, encryptionDetails);
      });
    }
  }

  public PutScalingPolicyResult upsertScalingPolicyIfRequired(String region, AwsConfig awsConfig,
      AwsAppAutoScalingHelperServiceDelegate appAutoScalingService, List<EncryptedDataDetail> encryptionDetails,
      ExecutionLogCallback executionLogCallback, ScalingPolicy scalingPolicy) {
    PutScalingPolicyRequest request =
        new PutScalingPolicyRequest()
            .withResourceId(scalingPolicy.getResourceId())
            .withScalableDimension(scalingPolicy.getScalableDimension())
            .withServiceNamespace(scalingPolicy.getServiceNamespace())
            .withPolicyName(scalingPolicy.getPolicyName())
            .withPolicyType(scalingPolicy.getPolicyType())
            .withTargetTrackingScalingPolicyConfiguration(scalingPolicy.getTargetTrackingScalingPolicyConfiguration())
            .withStepScalingPolicyConfiguration(scalingPolicy.getStepScalingPolicyConfiguration());

    executionLogCallback.saveExecutionLog(
        new StringBuilder("Creating Scaling Policy: ").append(scalingPolicy.getPolicyName()).toString());
    PutScalingPolicyResult result =
        appAutoScalingService.upsertScalingPolicy(region, awsConfig, encryptionDetails, request);
    executionLogCallback.saveExecutionLog(
        new StringBuilder("Created Scaling Policy Successfully.\n").append(result.toString()).append("\n").toString());

    return result;
  }

  public void updateCloudWatchMetricWithPolicyCreated(String region, AwsConfig awsConfig, String policyArn,
      List<Alarm> alarms, AwsAppAutoScalingHelperServiceDelegate appAutoScalingService,
      ExecutionLogCallback executionLogCallback, List<EncryptedDataDetail> encryptionDetails) {
    if (isEmpty(alarms)) {
      return;
    }

    // this is = aws cloudwatch describe-alarms --alarm-names
    List<MetricAlarm> metricAlarms =
        appAutoScalingService.fetchAlarmsByName(region, awsConfig, encryptionDetails, alarms);
    if (isEmpty(metricAlarms)) {
      return;
    }

    // Filter out alarm if it already contains policyArn in alarmActions.
    // e.g. For CpuUtilization or MemoryUtilization aws automatically create alarms and associate policy with it
    metricAlarms = metricAlarms.stream()
                       .filter(metricAlarm -> updateAlarmWithPolicyRequired(policyArn, metricAlarm))
                       .collect(toList());
    if (isEmpty(metricAlarms)) {
      return;
    }

    // Log alarm data
    executionLogCallback.saveExecutionLog("Following CloudWatch Alarms will be updated with new registered Policy");
    StringBuilder builder = new StringBuilder(128);
    metricAlarms.forEach(metricAlarm
        -> builder.append("AlarmName: ")
               .append(metricAlarm.getAlarmName())
               .append("\nNamespace: ")
               .append(metricAlarm.getNamespace())
               .append("\nMetricName: ")
               .append(metricAlarm.getMetricName())
               .append("\n "));
    executionLogCallback.saveExecutionLog(builder.toString());

    metricAlarms.forEach(metricAlarm -> {
      metricAlarm.getAlarmActions().add(policyArn);
      appAutoScalingService.putMetricAlarm(region, awsConfig, encryptionDetails, metricAlarm);
    });
  }

  private boolean updateAlarmWithPolicyRequired(String policyArn, MetricAlarm metricAlarm) {
    return isEmpty(metricAlarm.getAlarmActions()) || !metricAlarm.getAlarmActions().contains(policyArn);
  }

  public String getResourceIdForEcsService(String serviceName, String clusterName) {
    return new StringBuilder("service/").append(clusterName).append("/").append(serviceName).toString();
  }
}
