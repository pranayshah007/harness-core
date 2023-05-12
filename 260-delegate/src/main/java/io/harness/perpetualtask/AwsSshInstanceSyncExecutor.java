/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.network.SafeHttpCall.execute;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.grpc.utils.AnyUtils;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.instancesync.AwsSshInstanceSyncPerpetualTaskParams;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AwsConfig;
import software.wings.service.impl.aws.model.AwsEc2ListInstancesResponse;
import software.wings.service.intfc.aws.delegate.AwsEc2HelperServiceDelegate;

import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Instance;
import com.google.inject.Inject;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.Response;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class AwsSshInstanceSyncExecutor extends PerpetualTaskExecutorBase implements PerpetualTaskExecutor {
  @Inject private AwsEc2HelperServiceDelegate ec2ServiceDelegate;
  @Inject private DelegateAgentManagerClient delegateAgentManagerClient;

  @Override
  public PerpetualTaskResponse runOnce(
      PerpetualTaskId taskId, PerpetualTaskExecutionParams params, Instant heartbeatTime) {
    final AwsSshInstanceSyncPerpetualTaskParams instanceSyncParams =
        AnyUtils.unpack(params.getCustomizedParams(), AwsSshInstanceSyncPerpetualTaskParams.class);

    final String region = instanceSyncParams.getRegion();

    final AwsConfig awsConfig = (AwsConfig) getKryoSerializer(params.getReferenceFalseKryoSerializer())
                                    .asObject(instanceSyncParams.getAwsConfig().toByteArray());
    final List<EncryptedDataDetail> encryptedDataDetails =
        cast(getKryoSerializer(params.getReferenceFalseKryoSerializer())
                 .asObject(instanceSyncParams.getEncryptedData().toByteArray()));
    final List<Filter> filters = cast(getKryoSerializer(params.getReferenceFalseKryoSerializer())
                                          .asObject(instanceSyncParams.getFilter().toByteArray()));

    final AwsEc2ListInstancesResponse awsResponse = getInstances(region, awsConfig, encryptedDataDetails, filters);
    try {
      if (params.getReferenceFalseKryoSerializer()) {
        execute(delegateAgentManagerClient.publishInstanceSyncResultV2(
            taskId.getId(), awsConfig.getAccountId(), awsResponse));
      } else {
        execute(delegateAgentManagerClient.publishInstanceSyncResult(
            taskId.getId(), awsConfig.getAccountId(), awsResponse));
      }
    } catch (Exception e) {
      log.error(String.format("Failed to publish the instance collection result to manager for aws ssh for taskId [%s]",
                    taskId.getId()),
          e);
    }

    return getPerpetualTaskResponse(awsResponse);
  }

  private AwsEc2ListInstancesResponse getInstances(
      String region, AwsConfig awsConfig, List<EncryptedDataDetail> encryptedDataDetails, List<Filter> filters) {
    try {
      final List<Instance> instances =
          ec2ServiceDelegate.listEc2Instances(awsConfig, encryptedDataDetails, region, filters, true);
      return AwsEc2ListInstancesResponse.builder()
          .instances(instances)
          .executionStatus(ExecutionStatus.SUCCESS)
          .build();
    } catch (Exception e) {
      log.error("Error occured while fetching instances from AWS", e);
      return AwsEc2ListInstancesResponse.builder()
          .executionStatus(ExecutionStatus.FAILED)
          .errorMessage(e.getMessage())
          .build();
    }
  }

  private PerpetualTaskResponse getPerpetualTaskResponse(AwsEc2ListInstancesResponse awsResponse) {
    String message = "success";
    if (ExecutionStatus.FAILED == awsResponse.getExecutionStatus()) {
      message = awsResponse.getErrorMessage();
    }

    return PerpetualTaskResponse.builder().responseCode(Response.SC_OK).responseMessage(message).build();
  }

  @SuppressWarnings("unchecked")
  private static <T extends List<?>> T cast(Object obj) {
    return (T) obj;
  }

  @Override
  public boolean cleanup(PerpetualTaskId taskId, PerpetualTaskExecutionParams params) {
    return false;
  }
}
