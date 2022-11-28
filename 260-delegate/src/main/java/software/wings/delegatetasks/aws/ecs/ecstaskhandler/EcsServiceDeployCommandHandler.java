/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.aws.ecs.ecstaskhandler;

import static io.harness.exception.ExceptionUtils.getMessage;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.LogLevel.ERROR;

import static software.wings.beans.command.CommandExecutionContext.Builder.aCommandExecutionContext;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.TimeoutException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.api.DeploymentType;
import software.wings.beans.command.CommandExecutionContext;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.beans.command.ResizeCommandUnit;
import software.wings.beans.command.ResizeCommandUnitExecutionData;
import software.wings.beans.dto.SettingAttribute;
import software.wings.helpers.ext.ecs.request.EcsCommandRequest;
import software.wings.helpers.ext.ecs.request.EcsServiceDeployRequest;
import software.wings.helpers.ext.ecs.response.EcsCommandExecutionResponse;
import software.wings.helpers.ext.ecs.response.EcsServiceDeployResponse;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import java.util.List;

@Singleton
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@Deprecated
public class EcsServiceDeployCommandHandler extends EcsCommandTaskHandler {
  @Inject private Injector injector;

  @Override
  public EcsCommandExecutionResponse executeTaskInternal(EcsCommandRequest ecsCommandRequest,
      List<EncryptedDataDetail> encryptedDataDetails, ExecutionLogCallback executionLogCallback) {
    try {
      if (!(ecsCommandRequest instanceof EcsServiceDeployRequest)) {
        return EcsCommandExecutionResponse.builder()
            .commandExecutionStatus(FAILURE)
            .ecsCommandResponse(EcsServiceDeployResponse.builder()
                                    .output("Invalid Request Type: Expected was : [EcsServiceDeployRequest]")
                                    .commandExecutionStatus(FAILURE)
                                    .build())
            .build();
      }

      EcsServiceDeployRequest request = (EcsServiceDeployRequest) ecsCommandRequest;
      ResizeCommandUnit resizeCommandUnit = new ResizeCommandUnit();
      resizeCommandUnit.setName("ECS Service Deploy");
      injector.injectMembers(resizeCommandUnit);
      SettingAttribute settingAttribute = SettingAttribute.builder().value(request.getAwsConfig()).build();
      CommandExecutionContext commandExecutionContext = aCommandExecutionContext()
                                                            .accountId(request.getAccountId())
                                                            .appId(request.getAppId())
                                                            .activityId(request.getActivityId())
                                                            .cloudProviderSetting(settingAttribute)
                                                            .cloudProviderCredentials(encryptedDataDetails)
                                                            .containerResizeParams(request.getEcsResizeParams())
                                                            .deploymentType(DeploymentType.ECS.name())
                                                            .build();
      CommandExecutionStatus status = resizeCommandUnit.execute(commandExecutionContext);
      ResizeCommandUnitExecutionData commandExecutionData =
          (ResizeCommandUnitExecutionData) commandExecutionContext.getCommandExecutionData();
      EcsServiceDeployResponse response = EcsServiceDeployResponse.builder()
                                              .commandExecutionStatus(status)
                                              .containerInfos(commandExecutionData.getContainerInfos())
                                              .oldInstanceData(commandExecutionData.getOldInstanceData())
                                              .newInstanceData(commandExecutionData.getNewInstanceData())
                                              .build();
      return EcsCommandExecutionResponse.builder().commandExecutionStatus(status).ecsCommandResponse(response).build();
    } catch (TimeoutException ex) {
      String errorMessage = getMessage(ex);
      executionLogCallback.saveExecutionLog(errorMessage, ERROR);
      EcsServiceDeployResponse response = EcsServiceDeployResponse.builder().commandExecutionStatus(FAILURE).build();
      if (ecsCommandRequest.isTimeoutErrorSupported()) {
        response.setTimeoutFailure(true);
      }

      return EcsCommandExecutionResponse.builder()
          .commandExecutionStatus(FAILURE)
          .errorMessage(errorMessage)
          .ecsCommandResponse(response)
          .build();

    } catch (Exception ex) {
      String errorMessage = getMessage(ex);
      executionLogCallback.saveExecutionLog(errorMessage, ERROR);
      return EcsCommandExecutionResponse.builder()
          .commandExecutionStatus(FAILURE)
          .errorMessage(errorMessage)
          .ecsCommandResponse(EcsServiceDeployResponse.builder().commandExecutionStatus(FAILURE).build())
          .build();
    }
  }
}
