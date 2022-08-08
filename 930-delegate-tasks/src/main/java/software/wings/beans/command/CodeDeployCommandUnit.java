/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.command;

import static software.wings.beans.command.CodeDeployCommandExecutionData.Builder.aCodeDeployCommandExecutionData;

import static java.lang.String.format;
import static java.lang.String.join;

import io.harness.eraro.ErrorCode;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.logging.Misc;

import software.wings.api.DeploymentType;
import software.wings.beans.SettingAttribute;
import software.wings.cloudprovider.CodeDeployDeploymentInfo;
import software.wings.cloudprovider.aws.AwsCodeDeployService;
import software.wings.delegatetasks.DelegateLogService;

import com.amazonaws.services.codedeploy.model.AutoRollbackConfiguration;
import com.amazonaws.services.codedeploy.model.CreateDeploymentRequest;
import com.amazonaws.services.codedeploy.model.RevisionLocation;
import com.amazonaws.services.codedeploy.model.S3Location;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.annotations.Transient;

/**
 * Created by anubhaw on 6/23/17.
 */
@Slf4j
@JsonTypeName("CODE_DEPLOY")
public class CodeDeployCommandUnit extends AbstractCommandUnit {
  @Inject @Transient private transient AwsCodeDeployService awsCodeDeployService;
  @Inject @Transient private transient DelegateLogService logService;

  public CodeDeployCommandUnit() {
    super(CommandUnitType.CODE_DEPLOY);
    setArtifactNeeded(true);
    setDeploymentType(DeploymentType.AWS_CODEDEPLOY.name());
  }

  @Override
  public CommandExecutionStatus execute(CommandExecutionContext context) {
    SettingAttribute cloudProviderSetting = context.getCloudProviderSetting();
    CodeDeployParams codeDeployParams = context.getCodeDeployParams();
    String deploymentGroupName = codeDeployParams.getDeploymentGroupName();
    String applicationName = codeDeployParams.getApplicationName();
    String deploymentConfigurationName = codeDeployParams.getDeploymentConfigurationName();
    boolean enableAutoRollback = codeDeployParams.isEnableAutoRollback();
    List<String> autoRollbackConfigurations = codeDeployParams.getAutoRollbackConfigurations() != null
        ? codeDeployParams.getAutoRollbackConfigurations()
        : new ArrayList<>();
    boolean ignoreApplicationStopFailures = codeDeployParams.isIgnoreApplicationStopFailures();

    String fileExistsBehavior = codeDeployParams.getFileExistsBehavior();

    RevisionLocation revision = new RevisionLocation().withRevisionType("S3").withS3Location(
        new S3Location()
            .withBucket(codeDeployParams.getBucket())
            .withBundleType(codeDeployParams.getBundleType())
            .withKey(codeDeployParams.getKey()));

    ExecutionLogCallback executionLogCallback = new ExecutionLogCallback(
        logService, context.getAccountId(), context.getAppId(), context.getActivityId(), getName());
    CommandExecutionStatus commandExecutionStatus;

    try {
      executionLogCallback.saveExecutionLog(
          format("Deploying application [%s] with following configuration.", applicationName), LogLevel.INFO);
      executionLogCallback.saveExecutionLog(format("Application Name: [%s]", applicationName), LogLevel.INFO);
      executionLogCallback.saveExecutionLog(format("Aws Region: [%s]", codeDeployParams.getRegion()), LogLevel.INFO);
      executionLogCallback.saveExecutionLog(format("Deployment Group: [%s]", deploymentGroupName), LogLevel.INFO);
      executionLogCallback.saveExecutionLog(
          format("Deployment Configuration: [%s]", Optional.of(deploymentConfigurationName).orElse("DEFAULT")),
          LogLevel.INFO);
      executionLogCallback.saveExecutionLog(format("Enable Auto Rollback: [%s]", enableAutoRollback), LogLevel.INFO);
      if (enableAutoRollback) {
        executionLogCallback.saveExecutionLog(
            format("Auto Rollback Configurations: [%s]", join(",", autoRollbackConfigurations)), LogLevel.INFO);
      }
      executionLogCallback.saveExecutionLog(
          format("Ignore ApplicationStop lifecycle event failure: [%s]", ignoreApplicationStopFailures), LogLevel.INFO);
      executionLogCallback.saveExecutionLog(format("Content options : [%s]", fileExistsBehavior), LogLevel.INFO);
      executionLogCallback.saveExecutionLog(
          format("Revision: [Type: %s, Bucket: %s, Bundle: %s, Key: %s]", revision.getRevisionType(),
              revision.getS3Location().getBucket(), revision.getS3Location().getBundleType(),
              revision.getS3Location().getKey()),
          LogLevel.INFO);

      CreateDeploymentRequest createDeploymentRequest =
          new CreateDeploymentRequest()
              .withApplicationName(applicationName)
              .withDeploymentGroupName(deploymentGroupName)
              .withDeploymentConfigName(deploymentConfigurationName)
              .withRevision(revision)
              .withIgnoreApplicationStopFailures(ignoreApplicationStopFailures)
              .withAutoRollbackConfiguration(new AutoRollbackConfiguration()
                                                 .withEnabled(enableAutoRollback)
                                                 .withEvents(autoRollbackConfigurations))
              .withFileExistsBehavior(fileExistsBehavior);

      CodeDeployDeploymentInfo codeDeployDeploymentInfo = awsCodeDeployService.deployApplication(
          codeDeployParams.getRegion(), cloudProviderSetting, context.getCloudProviderCredentials(),
          createDeploymentRequest, executionLogCallback, codeDeployParams.getTimeout());
      commandExecutionStatus = codeDeployDeploymentInfo.getStatus();
      // go over instance data in command execution data and prepare execution data
      context.setCommandExecutionData(aCodeDeployCommandExecutionData()
                                          .withInstances(codeDeployDeploymentInfo.getInstances())
                                          .withDeploymentId(codeDeployDeploymentInfo.getDeploymentId())
                                          .build());
    } catch (Exception ex) {
      Misc.logAllMessages(ex, executionLogCallback);
      if (ex instanceof WingsException) {
        throw ex;
      }
      throw new WingsException(ErrorCode.GENERAL_ERROR, ex).addParam("message", ExceptionUtils.getMessage(ex));
    }
    executionLogCallback.saveExecutionLog(
        format("Deployment finished with status [%s]", commandExecutionStatus), LogLevel.INFO);
    return commandExecutionStatus;
  }

  @Data
  @EqualsAndHashCode(callSuper = true)
  @JsonTypeName("CODE_DEPLOY")
  public static class Yaml extends AbstractCommandUnit.Yaml {
    public Yaml() {
      super(CommandUnitType.CODE_DEPLOY.name());
    }

    @Builder
    public Yaml(String name, String deploymentType) {
      super(name, CommandUnitType.CODE_DEPLOY.name(), deploymentType);
    }
  }
}
