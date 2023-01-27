/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.terraformcloud;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.task.terraformcloud.TerraformCloudConfigMapper;
import io.harness.connector.task.terraformcloud.TerraformCloudValidationHandler;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.connector.terraformcloud.TerraformCloudTaskParams;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudConnectorDTO;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.delegate.task.terraformcloud.response.TerraformCloudValidateTaskResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.terraformcloud.TerraformCloudConfig;

import com.google.inject.Inject;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Slf4j
public class TerraformCloudTaskNG extends AbstractDelegateRunnableTask {
  @Inject private TerraformCloudValidationHandler terraformCloudValidationHandler;
  @Inject private TerraformCloudConfigMapper terraformCloudConfigMapper;

  public TerraformCloudTaskNG(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new UnsupportedOperationException("Object Array parameters not supported");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    TerraformCloudTaskParams taskParameters = (TerraformCloudTaskParams) parameters;

    TerraformCloudConnectorDTO terraformCloudConnectorDTO = taskParameters.getTerraformCloudConnectorDTO();
    TerraformCloudConfig terraformCloudConfig = terraformCloudConfigMapper.mapTerraformCloudConfigWithDecryption(
        terraformCloudConnectorDTO, taskParameters.getEncryptionDetails());
    switch (taskParameters.getTerraformCloudTaskType()) {
      case VALIDATE:
        ConnectorValidationResult connectorValidationResult =
            terraformCloudValidationHandler.validate(terraformCloudConfig);
        connectorValidationResult.setDelegateId(getDelegateId());
        return TerraformCloudValidateTaskResponse.builder()
            .connectorValidationResult(connectorValidationResult)
            .build();
      default:
        throw new InvalidRequestException("Task type not identified");
    }
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }
}
