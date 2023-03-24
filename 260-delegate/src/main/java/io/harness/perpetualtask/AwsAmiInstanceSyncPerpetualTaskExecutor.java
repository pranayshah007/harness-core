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
import io.harness.perpetualtask.instancesync.AwsAmiInstanceSyncPerpetualTaskParams;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.KryoSerializer;

import software.wings.beans.AwsConfig;
import software.wings.service.impl.aws.model.AwsAsgListInstancesResponse;
import software.wings.service.intfc.aws.delegate.AwsAsgHelperServiceDelegate;

import com.amazonaws.services.ec2.model.Instance;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.Response;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class AwsAmiInstanceSyncPerpetualTaskExecutor implements PerpetualTaskExecutor {
  @Inject private AwsAsgHelperServiceDelegate awsAsgHelperServiceDelegate;
  @Inject private DelegateAgentManagerClient delegateAgentManagerClient;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;

  @Override
  public PerpetualTaskResponse runOnce(
      PerpetualTaskId taskId, PerpetualTaskExecutionParams params, Instant heartbeatTime) {
    if (log.isDebugEnabled()) {
      log.debug("Running the InstanceSync perpetual task executor for task id: {}", taskId);
    }

    final AwsAmiInstanceSyncPerpetualTaskParams taskParams =
        AnyUtils.unpack(params.getCustomizedParams(), AwsAmiInstanceSyncPerpetualTaskParams.class);

    final AwsConfig awsConfig =
        (AwsConfig) referenceFalseKryoSerializer.asObject(taskParams.getAwsConfig().toByteArray());

    @SuppressWarnings("unchecked")
    final List<EncryptedDataDetail> encryptedDataDetails =
        (List<EncryptedDataDetail>) referenceFalseKryoSerializer.asObject(taskParams.getEncryptedData().toByteArray());

    final AwsAsgListInstancesResponse awsResponse = getAwsResponse(taskParams, awsConfig, encryptedDataDetails);

    try {
      execute(delegateAgentManagerClient.publishInstanceSyncResultV2(
          taskId.getId(), awsConfig.getAccountId(), awsResponse));
    } catch (Exception e) {
      log.error(
          String.format("Failed to publish instance sync result for aws ami. asgName [%s] and PerpetualTaskId [%s]",
              taskParams.getAsgName(), taskId.getId()),
          e);
    }
    return getPerpetualTaskResponse(awsResponse);
  }

  private AwsAsgListInstancesResponse getAwsResponse(AwsAmiInstanceSyncPerpetualTaskParams taskParams,
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptedDataDetails) {
    try {
      final List<Instance> instances = awsAsgHelperServiceDelegate.listAutoScalingGroupInstances(
          awsConfig, encryptedDataDetails, taskParams.getRegion(), taskParams.getAsgName(), true);
      return AwsAsgListInstancesResponse.builder()
          .executionStatus(ExecutionStatus.SUCCESS)
          .instances(instances)
          .asgName(taskParams.getAsgName())
          .build();
    } catch (Exception ex) {
      log.error(String.format("Failed to fetch aws instances for asg: [%s]", taskParams.getAsgName()), ex);
      return AwsAsgListInstancesResponse.builder()
          .executionStatus(ExecutionStatus.FAILED)
          .asgName(taskParams.getAsgName())
          .errorMessage(ex.getMessage())
          .build();
    }
  }

  private PerpetualTaskResponse getPerpetualTaskResponse(AwsAsgListInstancesResponse response) {
    String message = "success";
    if (ExecutionStatus.FAILED == response.getExecutionStatus()) {
      message = response.getErrorMessage();
    }

    return PerpetualTaskResponse.builder().responseCode(Response.SC_OK).responseMessage(message).build();
  }

  @Override
  public boolean cleanup(PerpetualTaskId taskId, PerpetualTaskExecutionParams params) {
    return false;
  }
}
