/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.command;

import static io.harness.annotations.dev.HarnessModule._930_DELEGATE_TASKS;
import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.ExceptionUtils;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.Misc;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.api.DeploymentType;
import software.wings.beans.AwsConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.command.ContainerSetupCommandUnitExecutionData.ContainerSetupCommandUnitExecutionDataBuilder;
import software.wings.delegatetasks.aws.ecs.ecstaskhandler.EcsBlueGreenRoute53SetupCommandHandler;
import software.wings.delegatetasks.aws.ecs.ecstaskhandler.EcsBlueGreenSetupCommandHandler;
import software.wings.delegatetasks.aws.ecs.ecstaskhandler.EcsSetupCommandHandler;
import software.wings.helpers.ext.ecs.request.EcsBGRoute53ServiceSetupRequest;
import software.wings.helpers.ext.ecs.request.EcsBGServiceSetupRequest;
import software.wings.helpers.ext.ecs.request.EcsServiceSetupRequest;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.mongodb.morphia.annotations.Transient;

/**
 * Created by brett on 11/18/17
 */
@OwnedBy(CDP)
@Slf4j
@JsonTypeName("ECS_SETUP")
@TargetModule(_930_DELEGATE_TASKS)
public class EcsSetupCommandUnit extends ContainerSetupCommandUnit {
  @Inject @Transient private transient EcsSetupCommandHandler ecsSetupCommandHandler;
  @Inject @Transient private transient EcsBlueGreenSetupCommandHandler ecsBlueGreenSetupCommandHandler;
  @Inject @Transient private transient EcsBlueGreenRoute53SetupCommandHandler ecsBlueGreenRoute53SetupCommandHandler;
  public static final String ERROR = "Error: ";

  public EcsSetupCommandUnit() {
    super(CommandUnitType.ECS_SETUP);
    setDeploymentType(DeploymentType.ECS.name());
  }

  @Override
  protected CommandExecutionStatus executeInternal(CommandExecutionContext context,
      SettingAttribute cloudProviderSetting, List<EncryptedDataDetail> encryptedDataDetails,
      ContainerSetupParams containerSetupParams, Map<String, String> serviceVariables,
      Map<String, String> safeDisplayServiceVariables, ExecutionLogCallback executionLogCallback) {
    ContainerSetupCommandUnitExecutionDataBuilder commandExecutionDataBuilder =
        ContainerSetupCommandUnitExecutionData.builder();
    try {
      EcsSetupParams ecsSetupParams = (EcsSetupParams) containerSetupParams;
      if (ecsSetupParams.isBlueGreen()) {
        if (ecsSetupParams.isUseRoute53DNSSwap()) {
          return handleEcsBlueGreenServiceSetupRoute53(cloudProviderSetting, encryptedDataDetails,
              (EcsSetupParams) containerSetupParams, serviceVariables, safeDisplayServiceVariables,
              commandExecutionDataBuilder, executionLogCallback);
        } else {
          return handleEcsBlueGreenServiceSetup(cloudProviderSetting, encryptedDataDetails,
              (EcsSetupParams) containerSetupParams, serviceVariables, safeDisplayServiceVariables,
              commandExecutionDataBuilder, executionLogCallback);
        }
      } else {
        return handleEcsNonBlueGreenServiceSetup(cloudProviderSetting, encryptedDataDetails,
            (EcsSetupParams) containerSetupParams, serviceVariables, safeDisplayServiceVariables,
            commandExecutionDataBuilder, executionLogCallback);
      }
    } catch (Exception ex) {
      log.error(ExceptionUtils.getMessage(ex), ex);
      Misc.logAllMessages(ex, executionLogCallback);
      return CommandExecutionStatus.FAILURE;
    } finally {
      context.setCommandExecutionData(commandExecutionDataBuilder.build());
    }
  }

  @NotNull
  private CommandExecutionStatus handleEcsNonBlueGreenServiceSetup(SettingAttribute cloudProviderSetting,
      List<EncryptedDataDetail> encryptedDataDetails, EcsSetupParams containerSetupParams,
      Map<String, String> serviceVariables, Map<String, String> safeDisplayServiceVariables,
      ContainerSetupCommandUnitExecutionDataBuilder commandExecutionDataBuilder,
      ExecutionLogCallback executionLogCallback) {
    ecsSetupCommandHandler.executeTask(EcsServiceSetupRequest.builder()
                                           .ecsSetupParams(containerSetupParams)
                                           .awsConfig((AwsConfig) cloudProviderSetting.getValue())
                                           .clusterName(containerSetupParams.getClusterName())
                                           .region(containerSetupParams.getRegion())
                                           .safeDisplayServiceVariables(safeDisplayServiceVariables)
                                           .serviceVariables(serviceVariables)
                                           .build(),
        encryptedDataDetails);

    return CommandExecutionStatus.SUCCESS;
  }

  @NotNull
  private CommandExecutionStatus handleEcsBlueGreenServiceSetupRoute53(SettingAttribute cloudProviderSetting,
      List<EncryptedDataDetail> encryptedDataDetails, EcsSetupParams containerSetupParams,
      Map<String, String> serviceVariables, Map<String, String> safeDisplayServiceVariables,
      ContainerSetupCommandUnitExecutionDataBuilder commandExecutionDataBuilder,
      ExecutionLogCallback executionLogCallback) {
    ecsBlueGreenRoute53SetupCommandHandler.executeTask(EcsBGRoute53ServiceSetupRequest.builder()
                                                           .ecsSetupParams(containerSetupParams)
                                                           .awsConfig((AwsConfig) cloudProviderSetting.getValue())
                                                           .clusterName(containerSetupParams.getClusterName())
                                                           .region(containerSetupParams.getRegion())
                                                           .safeDisplayServiceVariables(safeDisplayServiceVariables)
                                                           .serviceVariables(serviceVariables)
                                                           .build(),
        encryptedDataDetails);
    return CommandExecutionStatus.SUCCESS;
  }

  @NotNull
  private CommandExecutionStatus handleEcsBlueGreenServiceSetup(SettingAttribute cloudProviderSetting,
      List<EncryptedDataDetail> encryptedDataDetails, EcsSetupParams containerSetupParams,
      Map<String, String> serviceVariables, Map<String, String> safeDisplayServiceVariables,
      ContainerSetupCommandUnitExecutionDataBuilder commandExecutionDataBuilder,
      ExecutionLogCallback executionLogCallback) {
    ecsBlueGreenSetupCommandHandler.executeTask(EcsBGServiceSetupRequest.builder()
                                                    .ecsSetupParams(containerSetupParams)
                                                    .awsConfig((AwsConfig) cloudProviderSetting.getValue())
                                                    .clusterName(containerSetupParams.getClusterName())
                                                    .region(containerSetupParams.getRegion())
                                                    .safeDisplayServiceVariables(safeDisplayServiceVariables)
                                                    .serviceVariables(serviceVariables)
                                                    .build(),
        encryptedDataDetails);
    return CommandExecutionStatus.SUCCESS;
  }

  @Data
  @EqualsAndHashCode(callSuper = true)
  @JsonTypeName("ECS_SETUP")
  public static class Yaml extends ContainerSetupCommandUnit.Yaml {
    public Yaml() {
      super(CommandUnitType.ECS_SETUP.name());
    }

    @Builder
    public Yaml(String name, String deploymentType) {
      super(name, CommandUnitType.ECS_SETUP.name(), deploymentType);
    }
  }
}
