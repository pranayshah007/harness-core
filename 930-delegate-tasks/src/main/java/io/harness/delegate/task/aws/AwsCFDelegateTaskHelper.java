/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.AWSCloudformationClient;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.connector.service.git.NGGitService;
import io.harness.connector.task.git.GitDecryptionHelper;
import io.harness.connector.task.git.GitHubAppAuthenticationHelper;
import io.harness.connector.task.git.ScmConnectorMapperDelegate;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.connector.awsconnector.AwsCFTaskParamsRequest;
import io.harness.delegate.beans.connector.awsconnector.AwsCFTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsTaskParams;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.git.model.FetchFilesResult;
import io.harness.logging.CommandExecutionStatus;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.shell.SshSessionConfig;

import software.wings.service.impl.aws.model.AwsCFTemplateParamsData;

import com.amazonaws.services.cloudformation.model.ParameterDeclaration;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class AwsCFDelegateTaskHelper {
  @Inject private GitDecryptionHelper gitDecryptionHelper;
  @Inject private NGGitService ngGitService;
  @Inject private AWSCloudformationClient awsApiHelperService;
  @Inject private AwsNgConfigMapper awsNgConfigMapper;
  @Inject private SecretDecryptionService secretDecryptionService;
  @Inject private GitHubAppAuthenticationHelper gitHubAppAuthenticationHelper;
  @Inject private ScmConnectorMapperDelegate scmConnectorMapperDelegate;

  /*
   * Retrieve the parameters from a cloudformation template. The template can be stored in a git repository,
   * s3 bucket or be passed as a raw string.
   */
  public DelegateResponseData getCFParamsList(AwsCFTaskParamsRequest awsTaskParams) {
    // If the template is stored in a git repository, retrieve the template
    try {
      String templateValue = "";
      if (awsTaskParams.getGitStoreDelegateConfig() != null) {
        GitStoreDelegateConfig gitStoreDelegateConfig = awsTaskParams.getGitStoreDelegateConfig();
        GitConfigDTO gitConfigDTO = scmConnectorMapperDelegate.toGitConfigDTO(
            gitStoreDelegateConfig.getGitConfigDTO(), gitStoreDelegateConfig.getEncryptedDataDetails());
        gitDecryptionHelper.decryptGitConfig(gitConfigDTO, gitStoreDelegateConfig.getEncryptedDataDetails());
        SshSessionConfig sshSessionConfig = gitDecryptionHelper.getSSHSessionConfig(
            gitStoreDelegateConfig.getSshKeySpecDTO(), gitStoreDelegateConfig.getEncryptedDataDetails());
        FetchFilesResult gitFetchFilesResult = ngGitService.fetchFilesByPath(
            gitStoreDelegateConfig, awsTaskParams.getAccountId(), sshSessionConfig, gitConfigDTO);
        if (gitFetchFilesResult.getFiles().size() > 1) {
          log.error("more than 1 file found in git repository");
          return AwsCFTaskResponse.builder().commandExecutionStatus(CommandExecutionStatus.FAILURE).build();
        }
        templateValue = gitFetchFilesResult.getFiles().get(0).getFileContent();
      } else {
        templateValue = awsTaskParams.getData();
      }
      // Retrieve the AWS credentials
      decryptRequestDTOs(awsTaskParams);
      AwsInternalConfig awsInternalConfig = getAwsInternalConfig(awsTaskParams);

      // Retrieve the parameters from the cloudformation template
      List<ParameterDeclaration> parameterDeclarationList = awsApiHelperService.getParamsData(
          awsInternalConfig, awsTaskParams.getRegion(), templateValue, awsTaskParams.getFileStoreType());
      List<AwsCFTemplateParamsData> listOfParameters = parameterDeclarationList.stream()
                                                           .map(parameter
                                                               -> AwsCFTemplateParamsData.builder()
                                                                      .paramKey(parameter.getParameterKey())
                                                                      .paramType(parameter.getParameterType())
                                                                      .defaultValue(parameter.getDefaultValue())
                                                                      .build())
                                                           .collect(toList());

      return AwsCFTaskResponse.builder()
          .listOfParams(listOfParameters)
          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
          .build();
    } catch (Exception e) {
      log.error("error while retrieving parameters from cloudformation template ", e.getMessage());
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
  }

  private AwsInternalConfig getAwsInternalConfig(AwsTaskParams awsTaskParams) {
    AwsConnectorDTO awsConnectorDTO = awsTaskParams.getAwsConnector();
    AwsInternalConfig awsInternalConfig = awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO);
    awsInternalConfig.setDefaultRegion(awsTaskParams.getRegion());
    return awsInternalConfig;
  }

  private void decryptRequestDTOs(AwsTaskParams awsTaskParams) {
    AwsConnectorDTO awsConnectorDTO = awsTaskParams.getAwsConnector();
    if (awsConnectorDTO.getCredential() != null && awsConnectorDTO.getCredential().getConfig() != null) {
      secretDecryptionService.decrypt(
          (AwsManualConfigSpecDTO) awsConnectorDTO.getCredential().getConfig(), awsTaskParams.getEncryptionDetails());
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
          (AwsManualConfigSpecDTO) awsConnectorDTO.getCredential().getConfig(), awsTaskParams.getEncryptionDetails());
    }
  }
}
