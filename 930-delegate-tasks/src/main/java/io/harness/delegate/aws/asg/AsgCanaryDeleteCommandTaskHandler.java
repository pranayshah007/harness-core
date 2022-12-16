/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.aws.asg;

import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClient;
import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult;
import com.amazonaws.services.autoscaling.model.Instance;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.asg.AsgCommandUnitConstants;
import io.harness.delegate.beans.connector.spotconnector.SpotPermanentTokenConfigSpecDTO;
import io.harness.delegate.beans.elastigroup.ElastigroupPreFetchResult;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.exception.AsgNGException;
import io.harness.delegate.task.aws.asg.AsgCanaryDeleteRequest;
import io.harness.delegate.task.aws.asg.AsgCanaryDeleteResponse;
import io.harness.delegate.task.aws.asg.AsgCanaryDeleteResult;
import io.harness.delegate.task.aws.asg.AsgCommandRequest;
import io.harness.delegate.task.aws.asg.AsgCommandResponse;
import io.harness.delegate.task.aws.asg.AsgCommandTaskNGHelper;
import io.harness.delegate.task.aws.asg.AsgInfraConfig;
import io.harness.delegate.task.aws.asg.AsgInfraConfigHelper;
import io.harness.delegate.task.aws.asg.AsgTaskHelperBase;
import io.harness.delegate.task.elastigroup.response.ElastigroupPreFetchResponse;
import io.harness.delegate.task.elastigroup.response.SpotInstConfig;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import com.google.inject.Inject;
import io.harness.spotinst.model.ElastiGroup;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import software.wings.service.impl.AwsUtils;
import software.wings.service.impl.aws.client.CloseableAmazonWebServiceClient;
import software.wings.service.intfc.aws.delegate.AwsAsgHelperServiceDelegate;

import java.util.List;

@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class AsgCanaryDeleteCommandTaskHandler extends AsgCommandTaskNGHandler {
  @Inject private AsgTaskHelperBase asgTaskHelperBase;
  @Inject private AsgInfraConfigHelper asgInfraConfigHelper;
  @Inject private AsgCommandTaskNGHelper asgCommandTaskHelper;
  @Inject private AwsAsgHelperServiceDelegate awsAsgHelperServiceDelegate;
  @Inject private AwsUtils awsUtils;

  private AsgInfraConfig asgInfraConfig;
  private long timeoutInMillis;

  @Override
  protected AsgCommandResponse executeTaskInternal(AsgCommandRequest asgCommandRequest,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress)
      throws AsgNGException {
    if (!(asgCommandRequest instanceof AsgCanaryDeleteRequest)) {
      throw new InvalidArgumentsException(Pair.of("asgCommandRequest", "Must be instance of AsgCanaryDeleteRequest"));
    }

    AsgCanaryDeleteRequest asgCanaryDeleteRequest = (AsgCanaryDeleteRequest) asgCommandRequest;
    timeoutInMillis = asgCanaryDeleteRequest.getTimeoutIntervalInMin() * 60000;
    asgInfraConfig = asgCanaryDeleteRequest.getAsgInfraConfig();

    LogCallback canaryDeleteLogCallback = asgTaskHelperBase.getLogCallback(
        iLogStreamingTaskClient, AsgCommandUnitConstants.deleteService.toString(), true, commandUnitsProgress);

    String canaryAsgName = asgCanaryDeleteRequest.getCanaryAsgName();

    AsgCanaryDeleteResult asgCanaryDeleteResult = null;

    AsgInfraConfig asgInfraConfigValues = asgCanaryDeleteRequest.getAsgInfraConfig();

    // TODO - delete asg part
        try (CloseableAmazonWebServiceClient<AmazonAutoScalingClient> closeableAmazonAutoScalingClient =
                     new CloseableAmazonWebServiceClient(
                             awsUtils.getAmazonAutoScalingClient(asgInfraConfigValues.getRegion(), awsInternalConfig))) {
          DescribeAutoScalingGroupsRequest describeAutoScalingGroupsRequest =
                  new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(canaryAsgName);
          DescribeAutoScalingGroupsResult describeAutoScalingGroupsResult =
                  closeableAmazonAutoScalingClient.getClient().describeAutoScalingGroups(describeAutoScalingGroupsRequest);

          if (CollectionUtils.isNotEmpty(describeAutoScalingGroupsResult.getAutoScalingGroups())) {
            AutoScalingGroup autoScalingGroup = describeAutoScalingGroupsResult.getAutoScalingGroups().get(0);
            awsAsgHelperServiceDelegate.deleteAutoScalingGroups(awsConfig, encryptionDetails, region,
                    autoScalingGroup,
                    logCallback);

            result = autoScalingGroup.getInstances().stream().map(Instance::getInstanceId).collect(toList());
          }

        SpotInstConfig spotInstConfig = elastigroupPreFetchRequest.getSpotInstConfig();
                 elastigroupCommandTaskNGHelper.decryptSpotInstConfig(spotInstConfig);
                 SpotPermanentTokenConfigSpecDTO spotPermanentTokenConfigSpecDTO =
                (SpotPermanentTokenConfigSpecDTO) spotInstConfig.getSpotConnectorDTO().getCredential().getConfig();
                 String spotInstAccountId = spotPermanentTokenConfigSpecDTO.getSpotAccountIdRef().getDecryptedValue() != null
                ? String.valueOf(spotPermanentTokenConfigSpecDTO.getSpotAccountIdRef().getDecryptedValue())
                : spotPermanentTokenConfigSpecDTO.getSpotAccountId();
                 String spotInstApiToken = String.valueOf(spotPermanentTokenConfigSpecDTO.getApiTokenRef().getDecryptedValue());

                 List<ElastiGroup> elastigroups = elastigroupCommandTaskNGHelper.listElastigroups(
                blueGreen, elastigroupPreFetchRequest.getElastigroupNamePrefix(), spotInstAccountId, spotInstApiToken);

                 ElastigroupPreFetchResult result = ElastigroupPreFetchResult.builder().elastigroups(elastigroups).build();

        return ElastigroupPreFetchResponse.builder()
                .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                .elastigroupPreFetchResult(result)
                .build();

      } catch (Exception ex) {
        Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(ex);
        return ElastigroupPreFetchResponse.builder()
                .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                .errorMessage(sanitizedException.getMessage())
                .build();
      }
      awsAsgHelperServiceDelegate.deleteAutoScalingGroups(awsConfig, encryptionDetails, region,
              singletonList(
                      awsAsgHelperServiceDelegate.getAutoScalingGroup(awsConfig, encryptionDetails, region, newAsgName)),
              logCallback);
      asgCanaryDeleteResult = AsgCanaryDeleteResult.builder().canaryDeleted(true).canaryAsgName(canaryAsgName).build();

      canaryDeleteLogCallback.saveExecutionLog(
          format("Canary asg %s deleted", canaryAsgName), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    } else {
      canaryDeleteLogCallback.saveExecutionLog(
          format("Canary asg %s doesn't exist", canaryAsgName), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
      asgCanaryDeleteResult = AsgCanaryDeleteResult.builder().canaryDeleted(false).canaryAsgName(canaryAsgName).build();
    }
    return AsgCanaryDeleteResponse.builder()
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .asgCanaryDeleteResult(asgCanaryDeleteResult)
        .build();
  }
}
