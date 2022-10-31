/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.exception.UnexpectedException;
import io.harness.exception.WingsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;

import software.wings.api.TerraformExecutionData;
import software.wings.beans.GitConfig;
import software.wings.beans.GitFileConfig;
import software.wings.beans.GitOperationContext;
import software.wings.beans.delegation.TerraformProvisionParameters;
import software.wings.delegatetasks.terraform.TerraformConfigInspectClient.BLOCK_TYPE;
import software.wings.delegatetasks.validation.terraform.TerraformTaskUtils;
import software.wings.service.intfc.GitService;
import software.wings.service.intfc.TerraformConfigInspectService;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.utils.GitUtilsDelegate;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class TerraformFetchTargetsTask extends AbstractDelegateRunnableTask {
  @Inject private GitService gitService;
  @Inject private EncryptionService encryptionService;
  @Inject private GitUtilsDelegate gitUtilsDelegate;
  @Inject private TerraformConfigInspectService terraformConfigInspectService;

  public TerraformFetchTargetsTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public TerraformExecutionData run(Object[] parameters) {
    return run((TerraformProvisionParameters) parameters[0]);
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    return run((TerraformProvisionParameters) parameters);
  }

  private TerraformExecutionData run(TerraformProvisionParameters parameters) {
    try {
      GitConfig gitConfig = parameters.getSourceRepo();

      if (isNotEmpty(parameters.getSourceRepoBranch())) {
        gitConfig.setBranch(parameters.getSourceRepoBranch());
      }
      if (isNotEmpty(parameters.getCommitId())) {
        gitConfig.setReference(parameters.getCommitId());
      }
      GitOperationContext gitOperationContext = null;
      try {
        gitOperationContext = gitUtilsDelegate.cloneRepo(gitConfig,
            GitFileConfig.builder().connectorId(parameters.getSourceRepoSettingId()).build(),
            parameters.getSourceRepoEncryptionDetails());
      } catch (Exception e) {
        return TerraformExecutionData.builder()
            .executionStatus(ExecutionStatus.FAILED)
            .errorMessage(
                TerraformTaskUtils.getGitExceptionMessageIfExists(ExceptionMessageSanitizer.sanitizeException(e)))
            .build();
      }

      String absoluteModulePath =
          gitUtilsDelegate.resolveAbsoluteFilePath(gitOperationContext, parameters.getScriptPath());
      List<String> targets = terraformConfigInspectService.parseFieldsUnderCategory(absoluteModulePath,
          BLOCK_TYPE.MANAGED_RESOURCES.name().toLowerCase(), parameters.isUseTfConfigInspectLatestVersion());
      return TerraformExecutionData.builder().targets(targets).build();
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      throw new UnexpectedException("Unknown failure while fetching targets", e);
    }
  }

  @VisibleForTesting
  public List<String> getTargetResources(Map<String, Object> parsedContent) {
    List<String> targetResources = new ArrayList<>();
    Object object = parsedContent.get("resource");
    if (object != null) {
      Map<String, Map<String, Object>> resourceMap = (Map<String, Map<String, Object>>) object;
      for (Entry<String, Map<String, Object>> resource : resourceMap.entrySet()) {
        String resourceType = resource.getKey();
        if (resource.getValue() != null) {
          for (String resourceName : resource.getValue().keySet()) {
            targetResources.add(resourceType + "." + resourceName);
          }
        }
      }
    }
    return targetResources;
  }

  @VisibleForTesting
  public List<String> getTargetModules(Map<String, Object> parsedContent) {
    List<String> targetModules = new ArrayList<>();
    Object object = parsedContent.get("module");
    if (object != null) {
      Map<String, Object> moduleMap = (Map<String, Object>) object;
      for (String moduleName : moduleMap.keySet()) {
        targetModules.add("module." + moduleName);
      }
    }
    return targetModules;
  }
}
