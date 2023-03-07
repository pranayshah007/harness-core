/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.terraformcloud;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.terraformcloud.model.RunStatus.DISCARDED;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.task.terraformcloud.TerraformCloudConfigMapper;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudConnectorDTO;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.delegate.task.terraformcloud.cleanup.TerraformCloudCleanupTaskParams;
import io.harness.delegate.task.terraformcloud.cleanup.TerraformCloudCleanupTaskResponse;
import io.harness.logging.CommandExecutionStatus;
import io.harness.terraformcloud.TerraformCloudApiTokenCredentials;
import io.harness.terraformcloud.TerraformCloudConfig;
import io.harness.terraformcloud.model.RunData;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDP)
public class TerraformCloudCleanupTaskNG extends AbstractDelegateRunnableTask {
  static final String IS_DISCARDABLE = "is-discardable";
  static final String DISCARD_MESSAGE = "No Harness Apply step executed, discarding run...";
  private static final String TF_CLEANUP_FAILURE = "Failed to discard run: %s";
  @Inject private TerraformCloudConfigMapper terraformCloudConfigMapper;
  @Inject private TerraformCloudTaskHelper terraformCloudTaskHelper;

  public TerraformCloudCleanupTaskNG(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) throws IOException {
    if (!(parameters instanceof TerraformCloudCleanupTaskParams)) {
      throw new UnsupportedOperationException("Unsupported parameters type");
    }

    TerraformCloudCleanupTaskParams terraformCleanupParams = (TerraformCloudCleanupTaskParams) parameters;
    TerraformCloudConnectorDTO terraformCloudConnectorDTO = terraformCleanupParams.getTerraformCloudConnectorDTO();
    TerraformCloudConfig terraformCloudConfig = terraformCloudConfigMapper.mapTerraformCloudConfigWithDecryption(
        terraformCloudConnectorDTO, terraformCleanupParams.getEncryptionDetails());

    try {
      log.info(String.format("Discarding run: %s", terraformCleanupParams.getRunId()));
      TerraformCloudApiTokenCredentials credentials =
          (TerraformCloudApiTokenCredentials) terraformCloudConfig.getTerraformCloudCredentials();
      RunData initialRunData = terraformCloudTaskHelper.getRun(
          credentials.getUrl(), credentials.getToken(), terraformCleanupParams.getRunId());
      if (initialRunData.getAttributes().getActions().get(IS_DISCARDABLE)) {
        terraformCloudTaskHelper.discardRun(
            credentials.getUrl(), credentials.getToken(), terraformCleanupParams.getRunId(), DISCARD_MESSAGE);
        RunData runData = terraformCloudTaskHelper.getRun(
            credentials.getUrl(), credentials.getToken(), terraformCleanupParams.getRunId());
        if (DISCARDED.equals(runData.getAttributes().getStatus())) {
          return TerraformCloudCleanupTaskResponse.builder()
              .runId(terraformCleanupParams.getRunId())
              .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
              .build();
        } else {
          return getErrorResponse(terraformCleanupParams.getRunId(),
              String.format("Did not discard, status is: %s", runData.getAttributes().getStatus()));
        }
      } else {
        return getErrorResponse(terraformCleanupParams.getRunId(),
            String.format("Run is not discardable, status is: %s", initialRunData.getAttributes().getStatus()));
      }
    } catch (Exception ex) {
      log.error(String.format(TF_CLEANUP_FAILURE, terraformCleanupParams.getRunId()));
      return getErrorResponse(terraformCleanupParams.getRunId(), ex.getMessage());
    }
  }

  private TerraformCloudCleanupTaskResponse getErrorResponse(String runId, String message) {
    return TerraformCloudCleanupTaskResponse.builder()
        .commandExecutionStatus(CommandExecutionStatus.FAILURE)
        .runId(runId)
        .errorMessage(message)
        .build();
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }
}