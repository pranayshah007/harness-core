/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.aws.delegate;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.exception.WingsException.USER;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eraro.ErrorCode;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AwsConfig;
import software.wings.service.impl.aws.client.CloseableAmazonWebServiceClient;
import software.wings.service.intfc.aws.delegate.AwsAppAutoScalingHelperServiceDelegate;

import com.amazonaws.services.applicationautoscaling.AWSApplicationAutoScaling;
import com.amazonaws.services.applicationautoscaling.AWSApplicationAutoScalingClientBuilder;
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
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.DescribeAlarmsRequest;
import com.amazonaws.services.cloudwatch.model.DescribeAlarmsResult;
import com.amazonaws.services.cloudwatch.model.MetricAlarm;
import com.amazonaws.services.cloudwatch.model.PutMetricAlarmRequest;
import com.amazonaws.services.cloudwatch.model.PutMetricAlarmResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class AwsAppAutoScalingHelperServiceDelegateImpl
    extends AwsHelperServiceDelegateBase implements AwsAppAutoScalingHelperServiceDelegate {
  private AmazonCloudWatchClient getAmazonCloudWatchClient(String region, AwsConfig awsConfig) {
    AmazonCloudWatchClientBuilder builder = AmazonCloudWatchClient.builder().withRegion(region);
    attachCredentialsAndBackoffPolicy(builder, awsConfig);
    return (AmazonCloudWatchClient) builder.build();
  }

  private AWSApplicationAutoScaling getAWSApplicationAutoScalingClient(String region, AwsConfig awsConfig) {
    AWSApplicationAutoScalingClientBuilder builder =
        AWSApplicationAutoScalingClientBuilder.standard().withRegion(region);
    attachCredentialsAndBackoffPolicy(builder, awsConfig);
    return builder.build();
  }

  @Override
  public RegisterScalableTargetResult registerScalableTarget(String region, AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, RegisterScalableTargetRequest scalableTargetRequest) {
    tracker.trackAPPASGCall("Register Scalable Target");
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    return getAWSApplicationAutoScalingClient(region, awsConfig).registerScalableTarget(scalableTargetRequest);
  }

  @Override
  public DeregisterScalableTargetResult deregisterScalableTarget(String region, AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, DeregisterScalableTargetRequest deregisterTargetRequest) {
    tracker.trackAPPASGCall("Deregister Scalable Target");
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    return getAWSApplicationAutoScalingClient(region, awsConfig).deregisterScalableTarget(deregisterTargetRequest);
  }

  @Override
  public DescribeScalableTargetsResult listScalableTargets(String region, AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, DescribeScalableTargetsRequest request) {
    tracker.trackAPPASGCall("List Scalable Targets");
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    return getAWSApplicationAutoScalingClient(region, awsConfig).describeScalableTargets(request);
  }

  @Override
  public DescribeScalingPoliciesResult listScalingPolicies(String region, AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, DescribeScalingPoliciesRequest request) {
    tracker.trackAPPASGCall("List Scaling Policies");
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    return getAWSApplicationAutoScalingClient(region, awsConfig).describeScalingPolicies(request);
  }

  @Override
  public PutScalingPolicyResult upsertScalingPolicy(String region, AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, PutScalingPolicyRequest putScalingPolicyRequest) {
    tracker.trackAPPASGCall("Put Scaling Policy");
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    return getAWSApplicationAutoScalingClient(region, awsConfig).putScalingPolicy(putScalingPolicyRequest);
  }

  @Override
  public List<MetricAlarm> fetchAlarmsByName(
      String region, AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, List<Alarm> alarms) {
    if (EmptyPredicate.isEmpty(alarms)) {
      return Collections.EMPTY_LIST;
    }
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    try (CloseableAmazonWebServiceClient<AmazonCloudWatchClient> closeableAmazonCloudWatchClient =
             new CloseableAmazonWebServiceClient(getAmazonCloudWatchClient(region, awsConfig))) {
      tracker.trackCloudWatchCall("Fetch Alarm by Name");

      DescribeAlarmsResult describeAlarmsResult = closeableAmazonCloudWatchClient.getClient().describeAlarms(
          new DescribeAlarmsRequest().withAlarmNames(alarms.stream().map(Alarm::getAlarmName).collect(toList())));

      return describeAlarmsResult.getMetricAlarms();
    } catch (Exception e) {
      log.error("Exception fetchAlarmsByName", e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
  }

  @Override
  public PutMetricAlarmResult putMetricAlarm(
      String region, AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, MetricAlarm alarm) {
    if (alarm == null) {
      return null;
    }
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    try (CloseableAmazonWebServiceClient<AmazonCloudWatchClient> closeableAmazonCloudWatchClient =
             new CloseableAmazonWebServiceClient(getAmazonCloudWatchClient(region, awsConfig))) {
      tracker.trackCloudWatchCall("Put Metric Alarm");

      return closeableAmazonCloudWatchClient.getClient().putMetricAlarm(
          new PutMetricAlarmRequest()
              .withActionsEnabled(alarm.getActionsEnabled())
              .withAlarmActions(alarm.getAlarmActions())
              .withAlarmDescription(alarm.getAlarmDescription())
              .withAlarmName(alarm.getAlarmName())
              .withComparisonOperator(alarm.getComparisonOperator())
              .withDatapointsToAlarm(alarm.getDatapointsToAlarm())
              .withDimensions(alarm.getDimensions())
              .withEvaluateLowSampleCountPercentile(alarm.getEvaluateLowSampleCountPercentile())
              .withEvaluationPeriods(alarm.getEvaluationPeriods())
              .withExtendedStatistic(alarm.getExtendedStatistic())
              .withInsufficientDataActions(alarm.getInsufficientDataActions())
              .withMetricName(alarm.getMetricName())
              .withMetrics(alarm.getMetrics())
              .withNamespace(alarm.getNamespace())
              .withOKActions(alarm.getOKActions())
              .withPeriod(alarm.getPeriod())
              .withStatistic(alarm.getStatistic())
              .withThreshold(alarm.getThreshold())
              .withTreatMissingData(alarm.getTreatMissingData())
              .withUnit(alarm.getUnit()));
    } catch (Exception e) {
      log.error("Exception putMetricAlarm", e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
  }

  @Override
  public DeleteScalingPolicyResult deleteScalingPolicy(String region, AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, DeleteScalingPolicyRequest deleteScalingPolicyRequest) {
    tracker.trackAPPASGCall("Delete Scaling Policy");
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    return getAWSApplicationAutoScalingClient(region, awsConfig).deleteScalingPolicy(deleteScalingPolicyRequest);
  }

  @Override
  public List<ScalingPolicy> getScalingPolicyFromJson(String json) {
    ObjectMapper mapper = new ObjectMapper();
    mapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
    try {
      if (json.trim().charAt(0) == '[') {
        return mapper.readValue(json, new TypeReference<List<ScalingPolicy>>() {});
      } else {
        return Arrays.asList(mapper.readValue(json, ScalingPolicy.class));
      }
    } catch (IOException e) {
      String errorMsg = "Failed to Deserialize json into AWS Service object" + e;
      throw new WingsException(ErrorCode.GENERAL_ERROR, errorMsg, USER).addParam("message", errorMsg);
    }
  }

  @Override
  public ScalableTarget getScalableTargetFromJson(String json) {
    ObjectMapper mapper = new ObjectMapper();
    mapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
    try {
      return mapper.readValue(json, ScalableTarget.class);
    } catch (IOException e) {
      String errorMsg = "Failed to Deserialize json into AWS Service object" + e;
      throw new WingsException(ErrorCode.GENERAL_ERROR, errorMsg, USER).addParam("message", errorMsg);
    }
  }

  @Override
  public String getJsonForAwsScalableTarget(ScalableTarget scalableTarget) {
    ObjectMapper mapper = new ObjectMapper();
    try {
      return mapper.writeValueAsString(scalableTarget);
    } catch (JsonProcessingException e) {
      String errorMsg = "Failed to Serialize AWS ScalableTarget object into json";
      throw new WingsException(ErrorCode.GENERAL_ERROR, errorMsg, USER).addParam("message", errorMsg);
    }
  }

  @Override
  public String getJsonForAwsScalablePolicy(ScalingPolicy scalingPolicy) {
    ObjectMapper mapper = new ObjectMapper();
    try {
      return mapper.writeValueAsString(scalingPolicy);
    } catch (JsonProcessingException e) {
      String errorMsg = "Failed to Serialize AWS ScalableTarget object into json";
      throw new WingsException(ErrorCode.GENERAL_ERROR, errorMsg, USER).addParam("message", errorMsg);
    }
  }
}
