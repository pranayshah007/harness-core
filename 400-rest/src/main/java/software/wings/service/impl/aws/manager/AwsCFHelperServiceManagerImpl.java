/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.aws.manager;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.INVALID_ARGUMENT;
import static io.harness.exception.WingsException.USER;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;

import static java.util.Collections.singletonList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.TaskData;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AwsConfig;
import software.wings.beans.CloudFormationSourceType;
import software.wings.beans.GitConfig;
import software.wings.beans.GitFileConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.service.impl.GitConfigHelperService;
import software.wings.service.impl.aws.model.AwsCFGetTemplateParamsRequest;
import software.wings.service.impl.aws.model.AwsCFGetTemplateParamsResponse;
import software.wings.service.impl.aws.model.AwsCFRequest;
import software.wings.service.impl.aws.model.AwsCFTemplateParamsData;
import software.wings.service.impl.aws.model.AwsResponse;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.aws.manager.AwsCFHelperServiceManager;
import software.wings.service.intfc.security.SecretManager;
import software.wings.utils.GitUtilsManager;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Singleton
@OwnedBy(CDP)
public class AwsCFHelperServiceManagerImpl implements AwsCFHelperServiceManager {
  private static final long TIME_OUT_IN_MINUTES = 2;
  @Inject private DelegateService delegateService;
  @Inject private SettingsService settingService;
  @Inject private SecretManager secretManager;
  @Inject private GitUtilsManager gitUtilsManager;
  @Inject private AwsHelperServiceManager helper;
  @Inject private GitConfigHelperService gitConfigHelperService;

  private AwsConfig getAwsConfig(String awsConfigId) {
    SettingAttribute attribute = settingService.get(awsConfigId);
    if (attribute == null || !(attribute.getValue() instanceof AwsConfig)) {
      throw new WingsException(INVALID_ARGUMENT, USER).addParam("args", "InvalidConfiguration");
    }
    return (AwsConfig) attribute.getValue();
  }

  @Override
  public List<AwsCFTemplateParamsData> getParamsData(String type, String data, String awsConfigId, String region,
      String appId, String sourceRepoSettingId, String sourceRepoBranch, String templatePath, String commitId,
      Boolean useBranch, String repoName) {
    AwsConfig awsConfig = getAwsConfig(awsConfigId);
    List<EncryptedDataDetail> details =
        secretManager.getEncryptionDetails(awsConfig, isNotEmpty(appId) ? appId : GLOBAL_APP_ID, null);
    GitConfig gitConfig = GitConfig.builder().build();
    GitFileConfig gitFileConfig = GitFileConfig.builder().build();
    if (type.equalsIgnoreCase(CloudFormationSourceType.GIT.name())) {
      gitConfig = gitUtilsManager.getGitConfig(sourceRepoSettingId);
      gitFileConfig.setConnectorId(sourceRepoSettingId);
      gitFileConfig.setUseBranch(useBranch);
      gitFileConfig.setFilePath(templatePath);
      gitFileConfig.setRepoName(repoName);

      if (isNotEmpty(sourceRepoBranch)) {
        gitConfig.setBranch(sourceRepoBranch);
        gitFileConfig.setBranch(sourceRepoBranch);
      }
      if (isNotEmpty(commitId)) {
        gitConfig.setReference(commitId);
        gitFileConfig.setCommitId(commitId);
      }
      gitConfigHelperService.convertToRepoGitConfig(gitConfig, repoName);
    }
    AwsResponse response = executeTask(awsConfig.getAccountId(),
        AwsCFGetTemplateParamsRequest.builder()
            .awsConfig(awsConfig)
            .encryptionDetails(details)
            .region(region)
            .data(data)
            .type(type)
            .gitConfig(gitConfig)
            .gitFileConfig(gitFileConfig)
            .sourceRepoEncryptionDetails(
                gitConfig != null ? secretManager.getEncryptionDetails(gitConfig, appId, null) : null)
            .build(),
        appId);
    return ((AwsCFGetTemplateParamsResponse) response).getParameters();
  }

  private AwsResponse executeTask(String accountId, AwsCFRequest request, String appId) {
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(accountId)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, isNotEmpty(appId) ? appId : GLOBAL_APP_ID)
            .tags(isNotEmpty(request.getAwsConfig().getTag()) ? singletonList(request.getAwsConfig().getTag()) : null)
            .data(TaskData.builder()
                      .async(false)
                      .taskType(TaskType.AWS_CF_TASK.name())
                      .parameters(new Object[] {request})
                      .timeout(TimeUnit.MINUTES.toMillis(TIME_OUT_IN_MINUTES))
                      .build())
            .build();
    try {
      DelegateResponseData notifyResponseData = delegateService.executeTaskV2(delegateTask);
      helper.validateDelegateSuccessForSyncTask(notifyResponseData);
      return (AwsResponse) notifyResponseData;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new InvalidRequestException(ex.getMessage(), USER);
    }
  }
}
