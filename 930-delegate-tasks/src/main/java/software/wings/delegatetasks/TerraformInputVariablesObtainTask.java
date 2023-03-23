/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.delegate.task.terraform.TerraformBaseHelper;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.ExplanationException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;

import software.wings.api.TerraformExecutionData;
import software.wings.beans.GitConfig;
import software.wings.beans.GitFileConfig;
import software.wings.beans.GitOperationContext;
import software.wings.beans.NameValuePair;
import software.wings.beans.ServiceVariableType;
import software.wings.beans.TerraformInputVariablesTaskResponse;
import software.wings.beans.TerraformSourceType;
import software.wings.beans.delegation.TerraformProvisionParameters;
import software.wings.delegatetasks.terraform.TerraformConfigInspectClient.BLOCK_TYPE;
import software.wings.delegatetasks.validation.terraform.TerraformTaskUtils;
import software.wings.service.impl.aws.delegate.AwsS3HelperServiceDelegateImpl;
import software.wings.service.intfc.GitService;
import software.wings.service.intfc.TerraformConfigInspectService;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.utils.GitUtilsDelegate;
import software.wings.utils.S3Utils;

import com.amazonaws.services.s3.AmazonS3URI;
import com.google.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.WildcardFileFilter;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class TerraformInputVariablesObtainTask extends AbstractDelegateRunnableTask {
  private static final String TERRAFORM_FILE_EXTENSION = ".tf";
  @Inject private GitService gitService;
  @Inject private EncryptionService encryptionService;
  @Inject private GitUtilsDelegate gitUtilsDelegate;
  @Inject private TerraformBaseHelper terraformBaseHelper;
  @Inject private S3Utils s3UtilsDelegate;
  @Inject private TerraformConfigInspectService terraformConfigInspectService;
  @Inject AwsS3HelperServiceDelegateImpl awsS3HelperServiceDelegate;

  public TerraformInputVariablesObtainTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public TerraformInputVariablesTaskResponse run(TaskParameters parameters) {
    return run((TerraformProvisionParameters) parameters);
  }

  @Override
  public TerraformInputVariablesTaskResponse run(Object[] parameters) {
    return run((TerraformProvisionParameters) parameters[0]);
  }

  private TerraformInputVariablesTaskResponse run(TerraformProvisionParameters parameters) {
    String absoluteModulePath = null;
    List<NameValuePair> variablesList;
    try {
      GitOperationContext gitOperationContext;
      GitConfig gitConfig = parameters.getSourceRepo();

      if (parameters.getSourceType() != null && parameters.getSourceType().equals(TerraformSourceType.S3)) {
        try {
          AmazonS3URI s3URI = new AmazonS3URI(parameters.getConfigFilesS3URI());
          encryptionService.decrypt(
              parameters.getConfigFilesAwsSourceConfig(), parameters.getConfigFileAWSEncryptionDetails(), false);
          String downloadDir =
              s3UtilsDelegate.buildS3FilePath(parameters.getConfigFilesAwsSourceConfig().getAccountId(), s3URI);

          absoluteModulePath = s3UtilsDelegate.resolveS3BucketAbsoluteFilePath(downloadDir, s3URI);

          try {
            FileUtils.deleteQuietly(new File(absoluteModulePath));
          } catch (Exception e) {
            log.info(
                "Caught Exception while cleaning the local S3 directory before downloading for TERRAFORM_INPUT_VARIABLES_OBTAIN_TASK, continuing execution");
          }

          awsS3HelperServiceDelegate.downloadS3Directory(
              parameters.getConfigFilesAwsSourceConfig(), parameters.getConfigFilesS3URI(), new File(downloadDir));
        } catch (InterruptedException ex) {
          return TerraformInputVariablesTaskResponse.builder()
              .terraformExecutionData(
                  TerraformExecutionData.builder()
                      .executionStatus(ExecutionStatus.FAILED)
                      .errorMessage(ExceptionUtils.getMessage(ExceptionMessageSanitizer.sanitizeException(ex)))
                      .build())
              .build();
        }
      } else {
        if (isNotEmpty(parameters.getSourceRepoBranch())) {
          gitConfig.setBranch(parameters.getSourceRepoBranch());
        }
        if (isNotEmpty(parameters.getCommitId())) {
          gitConfig.setReference(parameters.getCommitId());
        }

        try {
          gitOperationContext = gitUtilsDelegate.cloneRepo(gitConfig,
              GitFileConfig.builder().connectorId(parameters.getSourceRepoSettingId()).build(),
              parameters.getSourceRepoEncryptionDetails());
          absoluteModulePath =
              gitUtilsDelegate.resolveAbsoluteFilePath(gitOperationContext, parameters.getScriptPath());
        } catch (Exception ex) {
          return TerraformInputVariablesTaskResponse.builder()
              .terraformExecutionData(TerraformExecutionData.builder()
                                          .executionStatus(ExecutionStatus.FAILED)
                                          .errorMessage(TerraformTaskUtils.getGitExceptionMessageIfExists(ex))
                                          .build())
              .build();
        }
      }

      variablesList = new ArrayList<>();

      if (noTfFiles(absoluteModulePath)) {
        throw new InvalidRequestException("No Terraform Files Found", WingsException.USER);
      }

      List<String> variables = terraformConfigInspectService.parseFieldsUnderCategory(absoluteModulePath,
          BLOCK_TYPE.VARIABLES.name().toLowerCase(), terraformBaseHelper.getTerraformConfigInspectVersion(parameters));

      if (variables != null) {
        variables.stream()
            .distinct()
            .map(variable -> NameValuePair.builder().name(variable).valueType(ServiceVariableType.TEXT.name()).build())
            .forEach(variablesList::add);
      }

      if (variablesList.isEmpty()) {
        throw new ExplanationException("No Terraform input variables found", null);
      }

    } catch (RuntimeException e) {
      log.error("Terraform Input Variables Task Exception " + parameters, e);
      return TerraformInputVariablesTaskResponse.builder()
          .terraformExecutionData(TerraformExecutionData.builder()
                                      .executionStatus(ExecutionStatus.FAILED)
                                      .errorMessage(ExceptionUtils.getMessage(e))
                                      .build())
          .build();
    } finally {
      if (TerraformSourceType.S3.equals(parameters.getSourceType()) && absoluteModulePath != null) {
        FileUtils.deleteQuietly(new File(absoluteModulePath));
      }
    }
    return TerraformInputVariablesTaskResponse.builder()
        .variablesList(variablesList)
        .terraformExecutionData(TerraformExecutionData.builder().executionStatus(ExecutionStatus.SUCCESS).build())
        .build();
  }

  private boolean noTfFiles(String directory) {
    File dir = new File(directory);
    try {
      return FileUtils.listFiles(dir, new WildcardFileFilter("*.tf"), null).isEmpty();
    } catch (IllegalArgumentException e) {
      throw new InvalidRequestException(format("Could not read the specified "
                                                + "directory  \"%s\" for terraform files",
                                            dir.getName()),
          WingsException.USER);
    }
  }
}
