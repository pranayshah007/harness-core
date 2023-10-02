/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.NGONZALEZ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.AWSCloudformationClient;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.category.element.UnitTests;
import io.harness.connector.service.git.NGGitService;
import io.harness.connector.task.git.GitDecryptionHelper;
import io.harness.connector.task.git.ScmConnectorMapperDelegate;
import io.harness.delegate.beans.connector.awsconnector.AwsCFTaskParamsRequest;
import io.harness.delegate.beans.connector.awsconnector.AwsCFTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.github.GithubAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernamePasswordDTO;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.git.model.FetchFilesResult;
import io.harness.git.model.GitFile;
import io.harness.rule.Owner;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.shell.SshSessionConfig;

import com.amazonaws.services.cloudformation.model.ParameterDeclaration;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class AwsCFDelegateTaskHelperTest extends CategoryTest {
  @Mock GitDecryptionHelper gitDecryptionHelper;
  @Mock NGGitService gitService;
  @Mock AWSCloudformationClient awsCloudformationClient;
  @Mock AwsNgConfigMapper awsNgConfigMapper;
  @Mock SecretDecryptionService secretDecryptionService;
  @Mock ScmConnectorMapperDelegate scmConnectorMapperDelegate;

  @InjectMocks AwsCFDelegateTaskHelper awsCFDelegateTaskHelper;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void getCFParamsList() throws IOException {
    AwsCFTaskParamsRequest awsCFTaskParamsRequest = generateParameters();
    doNothing().when(gitDecryptionHelper).decryptGitConfig(any(), any());
    SshSessionConfig sshSessionConfig = SshSessionConfig.Builder.aSshSessionConfig().build();
    doReturn(sshSessionConfig).when(gitDecryptionHelper).getSSHSessionConfig(any(), any());
    List<GitFile> gitFiles = new ArrayList<>();
    gitFiles.add(GitFile.builder().fileContent("content").build());
    FetchFilesResult fetchFilesResult = FetchFilesResult.builder().files(gitFiles).build();
    doReturn(fetchFilesResult).when(gitService).fetchFilesByPath(any(), any(), any(), any());
    doReturn(null).when(secretDecryptionService).decrypt(any(), any());
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();
    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(any());
    List<ParameterDeclaration> parameterDeclarationList = new ArrayList<>();
    parameterDeclarationList.add(new ParameterDeclaration().withParameterType("key").withParameterKey("value"));
    doReturn(parameterDeclarationList).when(awsCloudformationClient).getParamsData(any(), any(), any(), any());
    doReturn(GitConfigDTO.builder().build()).when(scmConnectorMapperDelegate).toGitConfigDTO(any(), any());

    AwsCFTaskResponse response = (AwsCFTaskResponse) awsCFDelegateTaskHelper.getCFParamsList(awsCFTaskParamsRequest);
    assertThat(response.getListOfParams().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void getCFParamsListFailToRetrieveTemplateParamsExceptionIsThrown() throws IOException {
    AwsCFTaskParamsRequest awsCFTaskParamsRequest = generateParameters();
    doNothing().when(gitDecryptionHelper).decryptGitConfig(any(), any());
    SshSessionConfig sshSessionConfig = SshSessionConfig.Builder.aSshSessionConfig().build();
    doReturn(sshSessionConfig).when(gitDecryptionHelper).getSSHSessionConfig(any(), any());
    List<GitFile> gitFiles = new ArrayList<>();
    gitFiles.add(GitFile.builder().fileContent("content").build());
    FetchFilesResult fetchFilesResult = FetchFilesResult.builder().files(gitFiles).build();
    doReturn(fetchFilesResult).when(gitService).fetchFilesByPath(any(), any(), any(), any());
    doReturn(null).when(secretDecryptionService).decrypt(any(), any());
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();
    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(any());
    doAnswer(invocationOnMock -> { throw new Exception(); })
        .when(awsCloudformationClient)
        .getParamsData(any(), any(), any(), any());
    assertThatThrownBy(() -> awsCFDelegateTaskHelper.getCFParamsList(awsCFTaskParamsRequest))
        .isInstanceOf(InvalidRequestException.class);
  }

  private AwsCFTaskParamsRequest generateParameters() {
    return AwsCFTaskParamsRequest.builder()
        .gitStoreDelegateConfig(
            GitStoreDelegateConfig.builder()
                .gitConfigDTO(
                    GithubConnectorDTO.builder()
                        .authentication(
                            GithubAuthenticationDTO.builder()
                                .authType(GitAuthType.HTTP)
                                .credentials(GithubHttpCredentialsDTO.builder()
                                                 .type(GithubHttpAuthenticationType.USERNAME_AND_PASSWORD)
                                                 .httpCredentialsSpec(GithubUsernamePasswordDTO.builder()
                                                                          .username("foobar")
                                                                          .passwordRef(SecretRefData.builder().build())
                                                                          .usernameRef(SecretRefData.builder().build())
                                                                          .build())
                                                 .build())
                                .build())
                        .build())
                .build())
        .awsConnector(
            AwsConnectorDTO.builder()
                .credential(AwsCredentialDTO.builder().config(AwsManualConfigSpecDTO.builder().build()).build())
                .build())
        .build();
  }
}
