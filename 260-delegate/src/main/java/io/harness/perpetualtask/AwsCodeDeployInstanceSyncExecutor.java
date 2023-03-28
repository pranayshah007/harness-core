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
import io.harness.perpetualtask.instancesync.AwsCodeDeployInstanceSyncPerpetualTaskParams;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AwsConfig;
import software.wings.service.impl.aws.model.AwsCodeDeployListDeploymentInstancesResponse;
import software.wings.service.impl.aws.model.AwsResponse;
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
public class AwsCodeDeployInstanceSyncExecutor extends PerpetualTaskExecutorBase implements PerpetualTaskExecutor {
  @Inject private AwsEc2HelperServiceDelegate ec2ServiceDelegate;
  @Inject private DelegateAgentManagerClient delegateAgentManagerClient;

  @Override
  public PerpetualTaskResponse runOnce(
      PerpetualTaskId taskId, PerpetualTaskExecutionParams params, Instant heartbeatTime) {
    final AwsCodeDeployInstanceSyncPerpetualTaskParams taskParams =
        AnyUtils.unpack(params.getCustomizedParams(), AwsCodeDeployInstanceSyncPerpetualTaskParams.class);
    final List<Filter> filters = (List<Filter>) getKryoSerializer(params.getReferenceFalseKryoSerializer())
                                     .asObject(taskParams.getFilter().toByteArray());
    final AwsConfig awsConfig = (AwsConfig) getKryoSerializer(params.getReferenceFalseKryoSerializer())
                                    .asObject(taskParams.getAwsConfig().toByteArray());
    final List<EncryptedDataDetail> encryptedDataDetails =
        (List<EncryptedDataDetail>) getKryoSerializer(params.getReferenceFalseKryoSerializer())
            .asObject(taskParams.getEncryptedData().toByteArray());

    AwsCodeDeployListDeploymentInstancesResponse instancesListResponse =
        getCodeDeployResponse(taskParams.getRegion(), filters, awsConfig, encryptedDataDetails);

    try {
      execute(delegateAgentManagerClient.publishInstanceSyncResultV2(
          taskId.getId(), awsConfig.getAccountId(), instancesListResponse));
    } catch (Exception ex) {
      log.error("Failed to publish instance sync result for task {}", taskId.getId(), ex);
    }

    return getPerpetualTaskResponse(instancesListResponse);
  }

  @Override
  public boolean cleanup(PerpetualTaskId taskId, PerpetualTaskExecutionParams params) {
    return false;
  }

  private AwsCodeDeployListDeploymentInstancesResponse getCodeDeployResponse(
      String region, List<Filter> filters, AwsConfig awsConfig, List<EncryptedDataDetail> encryptedDataDetails) {
    try {
      List<Instance> ec2InstancesList =
          ec2ServiceDelegate.listEc2Instances(awsConfig, encryptedDataDetails, region, filters, true);

      return AwsCodeDeployListDeploymentInstancesResponse.builder()
          .executionStatus(ExecutionStatus.SUCCESS)
          .instances(ec2InstancesList)
          .build();
    } catch (Exception ex) {
      log.error("Error occurred while fetching instances list", ex);
      return AwsCodeDeployListDeploymentInstancesResponse.builder()
          .executionStatus(ExecutionStatus.FAILED)
          .errorMessage(ex.getMessage())
          .build();
    }
  }

  private PerpetualTaskResponse getPerpetualTaskResponse(AwsResponse response) {
    String message = "success";
    if (ExecutionStatus.FAILED == response.getExecutionStatus()) {
      message = response.getErrorMessage();
    }

    return PerpetualTaskResponse.builder().responseCode(Response.SC_OK).responseMessage(message).build();
  }
}
