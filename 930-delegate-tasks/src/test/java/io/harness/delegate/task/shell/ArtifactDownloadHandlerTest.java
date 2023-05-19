/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.shell;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.BOJAN;
import static io.harness.rule.OwnerRule.VITALIE;
import static io.harness.rule.OwnerRule.VLAD;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.artifactory.ArtifactoryNgService;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthType;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthenticationDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryUsernamePasswordAuthDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsIRSASpecDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsInheritFromDelegateSpecDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.awsconnector.CrossAccountAccessDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsAuthenticationDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsAuthenticationType;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsConnectorDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsCredentialsDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsTokenDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsAuthType;
import io.harness.delegate.beans.connector.jenkins.JenkinsAuthenticationDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsConnectorDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsUserNamePasswordDTO;
import io.harness.delegate.task.artifactory.ArtifactoryRequestMapper;
import io.harness.delegate.task.azure.artifact.AzureArtifactsHelper;
import io.harness.delegate.task.ssh.artifact.ArtifactoryArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.AwsS3ArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.AzureArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.JenkinsArtifactDelegateConfig;
import io.harness.delegate.task.winrm.ArtifactoryArtifactDownloadHandler;
import io.harness.delegate.task.winrm.AwsS3ArtifactDownloadHandler;
import io.harness.delegate.task.winrm.AzureArtifactDownloadHandler;
import io.harness.delegate.task.winrm.JenkinsArtifactDownloadHandler;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.shell.ScriptType;

import software.wings.beans.AWSTemporaryCredentials;
import software.wings.beans.AwsConfig;
import software.wings.service.impl.AwsHelperService;
import software.wings.service.intfc.security.EncryptionService;

import java.util.Collections;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(CDP)
@FieldDefaults(level = AccessLevel.PRIVATE)
@RunWith(MockitoJUnitRunner.Silent.class)
public class ArtifactDownloadHandlerTest extends CategoryTest {
  private static final String SECRET_IDENT = "secret_ident";
  private static final char[] DECRYPTED_PASSWORD_VALUE = new char[] {'t', 'e', 's', 't'};
  @InjectMocks private ArtifactoryArtifactDownloadHandler artifactoryArtifactDownloadHandler;
  @InjectMocks private JenkinsArtifactDownloadHandler jenkinsArtifactDownloadHandler;
  @InjectMocks private AwsS3ArtifactDownloadHandler s3ArtifactDownloadHandler;
  @InjectMocks private AzureArtifactDownloadHandler azureArtifactDownloadHandler;
  @Mock private ArtifactoryRequestMapper artifactoryRequestMapper;
  @Mock private SecretDecryptionService secretDecryptionService;
  @Mock private ArtifactoryNgService artifactoryNgService;
  @Mock private EncryptionService encryptionService;
  @Mock private AwsHelperService awsHelperService;
  @Mock private AzureArtifactsHelper azureArtifactsHelper;
  private SecretRefData passwordRef;

  @Before
  public void setup() {
    passwordRef = SecretRefData.builder().identifier(SECRET_IDENT).decryptedValue(DECRYPTED_PASSWORD_VALUE).build();
    JenkinsUserNamePasswordDTO credentials =
        JenkinsUserNamePasswordDTO.builder().username("username").passwordRef(passwordRef).build();
    when(secretDecryptionService.decrypt(any(JenkinsUserNamePasswordDTO.class), any())).thenReturn(credentials);

    ArtifactoryUsernamePasswordAuthDTO artifactoryUsernamePasswordAuthDTO =
        ArtifactoryUsernamePasswordAuthDTO.builder().username("username").passwordRef(passwordRef).build();
    when(secretDecryptionService.decrypt(any(ArtifactoryUsernamePasswordAuthDTO.class), any()))
        .thenReturn(artifactoryUsernamePasswordAuthDTO);
    AwsManualConfigSpecDTO awsManualConfigSpecDTO =
        AwsManualConfigSpecDTO.builder()
            .accessKey("key")
            .secretKeyRef(SecretRefData.builder().identifier("secret").build())
            .build();
    when(secretDecryptionService.decrypt(any(AwsManualConfigSpecDTO.class), any())).thenReturn(awsManualConfigSpecDTO);

    AzureArtifactsTokenDTO azureArtifactsTokenDTO = AzureArtifactsTokenDTO.builder()
                                                        .tokenRef(SecretRefData.builder()
                                                                      .decryptedValue(DECRYPTED_PASSWORD_VALUE)
                                                                      .identifier("azure_artifacts_secret")
                                                                      .build())
                                                        .build();
    when(secretDecryptionService.decrypt(any(AzureArtifactsTokenDTO.class), any())).thenReturn(azureArtifactsTokenDTO);
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testArtifactoryGetCommandString_BASH() {
    when(artifactoryRequestMapper.toArtifactoryRequest(any())).thenReturn(getDefaultArtifactoryConfigRequest());
    ConnectorInfoDTO connectorDTO = getArtifactoryConnectorInfoDTO();
    ArtifactoryArtifactDelegateConfig artifactDelegateConfig = ArtifactoryArtifactDelegateConfig.builder()
                                                                   .repositoryName("repo_name")
                                                                   .artifactPath("artifact_path")
                                                                   .identifier("identifier")
                                                                   .artifactDirectory("testdir")
                                                                   .connectorDTO(connectorDTO)
                                                                   .build();
    String commandString =
        artifactoryArtifactDownloadHandler.getCommandString(artifactDelegateConfig, "testdestination", ScriptType.BASH);

    assertThat(commandString)
        .isEqualTo(
            "curl -L --fail -X GET \"http://hostname/repo_name/artifact_path\" -o \"testdestination/artifact_path\"");
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testArtifactoryGetCommandStringWithAuth_BASH() {
    when(artifactoryRequestMapper.toArtifactoryRequest(any())).thenReturn(getArtifactoryConfigRequestWithPass());
    ConnectorInfoDTO connectorDTO = getArtifactoryConnectorInfoDTOWithSecret();
    ArtifactoryArtifactDelegateConfig artifactDelegateConfig = ArtifactoryArtifactDelegateConfig.builder()
                                                                   .repositoryName("repo_name")
                                                                   .artifactPath("artifact_path")
                                                                   .identifier("identifier")
                                                                   .artifactDirectory("testdir")
                                                                   .connectorDTO(connectorDTO)
                                                                   .build();
    String commandString =
        artifactoryArtifactDownloadHandler.getCommandString(artifactDelegateConfig, "testdestination", ScriptType.BASH);

    assertThat(commandString)
        .isEqualTo("curl -L --fail -H \"Authorization: Basic dXNlcm5hbWU6dGVzdA==\""
            + " -X GET \"http://hostname/repo_name/artifact_path\""
            + " -o \"testdestination/artifact_path\"");
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testArtifactoryGetCommandString_POWERSHELL() {
    when(artifactoryRequestMapper.toArtifactoryRequest(any())).thenReturn(getDefaultArtifactoryConfigRequest());
    ArtifactoryArtifactDelegateConfig artifactDelegateConfig = ArtifactoryArtifactDelegateConfig.builder()
                                                                   .repositoryName("repo_name")
                                                                   .artifactPath("artifact_path")
                                                                   .identifier("identifier")
                                                                   .artifactDirectory("testdir")
                                                                   .connectorDTO(getArtifactoryConnectorInfoDTO())
                                                                   .build();
    String commandString = artifactoryArtifactDownloadHandler.getCommandString(
        artifactDelegateConfig, "testdestination", ScriptType.POWERSHELL);

    assertThat(commandString)
        .isEqualTo("[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12\n"
            + "$ProgressPreference = 'SilentlyContinue'\n"
            + "$var1 = $env:HARNESS_ENV_PROXY\n"
            + "$var2 = $env:HTTP_PROXY\n"
            + "if ( ([string]::IsNullOrEmpty($var2)) -and  (\"true\" -eq $var1) ) {\n"
            + " Write-Host \"HTTP_PROXY environment variable not found or empty\"\n"
            + "}\n"
            + "if ( (-not [string]::IsNullOrEmpty($var2)) -and  (\"true\" -eq $var1) ) {\n"
            + " Write-Host \"Using HTTP_PROXY environment variable\"\n"
            + " Invoke-WebRequest -Uri \"http://hostname/repo_name/artifact_path\" -OutFile \"testdestination\\artifact_path\" -Proxy \"$env:HTTP_PROXY\"\n"
            + "} else {\n"
            + " Invoke-WebRequest -Uri \"http://hostname/repo_name/artifact_path\" -OutFile \"testdestination\\artifact_path\"\n"
            + "}");
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testArtifactoryGetCommandStringWithAuth_POWERSHELL() {
    when(artifactoryRequestMapper.toArtifactoryRequest(any())).thenReturn(getArtifactoryConfigRequestWithPass());

    ConnectorInfoDTO connectorDTO = getArtifactoryConnectorInfoDTOWithSecret();

    ArtifactoryArtifactDelegateConfig artifactDelegateConfig = ArtifactoryArtifactDelegateConfig.builder()
                                                                   .repositoryName("repo_name")
                                                                   .artifactPath("artifact_path")
                                                                   .identifier("identifier")
                                                                   .artifactDirectory("testdir")
                                                                   .connectorDTO(connectorDTO)
                                                                   .build();
    String commandString = artifactoryArtifactDownloadHandler.getCommandString(
        artifactDelegateConfig, "testdestination", ScriptType.POWERSHELL);

    assertThat(commandString)
        .isEqualTo("$Headers = @{\n"
            + "    Authorization = \"Basic dXNlcm5hbWU6dGVzdA==\"\n"
            + "}\n"
            + "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12\n"
            + "$ProgressPreference = 'SilentlyContinue'\n"
            + "$var1 = $env:HARNESS_ENV_PROXY\n"
            + "$var2 = $env:HTTP_PROXY\n"
            + "if ( ([string]::IsNullOrEmpty($var2)) -and  (\"true\" -eq $var1) ) {\n"
            + " Write-Host \"HTTP_PROXY environment variable not found or empty\"\n"
            + "}\n"
            + "if ( (-not [string]::IsNullOrEmpty($var2)) -and  (\"true\" -eq $var1) ) {\n"
            + " Write-Host \"Using HTTP_PROXY environment variable\"\n"
            + " Invoke-WebRequest -Uri \"http://hostname/repo_name/artifact_path\" -Headers $Headers -OutFile \"testdestination\\artifact_path\" -Proxy \"$env:HTTP_PROXY\"\n"
            + "} else {\n"
            + " Invoke-WebRequest -Uri \"http://hostname/repo_name/artifact_path\" -Headers $Headers -OutFile \"testdestination\\artifact_path\"\n"
            + "}");
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testJenkinsGetCommandString_BASH() {
    ConnectorInfoDTO connectorDTO = getJenkinsConnectorInfoDTO();
    JenkinsArtifactDelegateConfig artifactDelegateConfig = JenkinsArtifactDelegateConfig.builder()
                                                               .artifactPath("artifact_path")
                                                               .identifier("identifier")
                                                               .connectorDTO(connectorDTO)
                                                               .jobName("job_name")
                                                               .build("build_number54")
                                                               .build();
    String commandString =
        jenkinsArtifactDownloadHandler.getCommandString(artifactDelegateConfig, "testdestination", ScriptType.BASH);

    assertThat(commandString)
        .isEqualTo(
            "curl --fail -H \"Authorization: null\" -X GET \"http://hostname/job/job_name/build_number54/artifact/artifact_path\" -o \"testdestination/artifact_path\"");
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testJenkinsGetCommandStringWithAuth_BASH() {
    ConnectorInfoDTO connectorDTO = getJenkinsConnectorInfoDTOWithSecret();
    JenkinsArtifactDelegateConfig artifactDelegateConfig = JenkinsArtifactDelegateConfig.builder()
                                                               .artifactPath("artifact_path")
                                                               .identifier("identifier")
                                                               .connectorDTO(connectorDTO)
                                                               .jobName("job_name")
                                                               .build("build_number54")
                                                               .build();
    String commandString =
        jenkinsArtifactDownloadHandler.getCommandString(artifactDelegateConfig, "testdestination", ScriptType.BASH);

    assertThat(commandString)
        .isEqualTo("curl --fail -H \"Authorization: Basic dXNlcm5hbWU6dGVzdA==\" "
            + "-X GET \"http://hostname/job/job_name/build_number54/artifact/artifact_path\""
            + " -o \"testdestination/artifact_path\"");
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testJenkinsGetCommandString_POWERSHELL() {
    ConnectorInfoDTO connectorDTO = getJenkinsConnectorInfoDTO();

    JenkinsArtifactDelegateConfig artifactDelegateConfig = JenkinsArtifactDelegateConfig.builder()
                                                               .artifactPath("artifact_path")
                                                               .identifier("identifier")
                                                               .jobName("job_name")
                                                               .build("build_number54")
                                                               .connectorDTO(connectorDTO)
                                                               .build();
    String commandString = jenkinsArtifactDownloadHandler.getCommandString(
        artifactDelegateConfig, "testdestination", ScriptType.POWERSHELL);

    assertThat(commandString)
        .isEqualTo("$var1 = $env:HARNESS_ENV_PROXY\n"
            + "$var2 = $env:HTTP_PROXY\n"
            + "$webClient = New-Object System.Net.WebClient\n"
            + "if ( ([string]::IsNullOrEmpty($var2)) -and  (\"true\" -eq $var1) ) {\n"
            + " Write-Host \"HTTP_PROXY environment variable not found or empty\"\n"
            + "}\n"
            + "if ( (-not [string]::IsNullOrEmpty($var2)) -and  (\"true\" -eq $var1) ) {\n"
            + " Write-Host \"Using HTTP_PROXY environment variable\"\n"
            + " $webProxy = New-Object System.Net.WebProxy(\"$env:HTTP_PROXY\",$true)\n"
            + " $webClient.Proxy = $webProxy\n"
            + "}\n"
            + "$url = \"http://hostname/job/job_name/build_number54/artifact/artifact_path\"\n"
            + "$localfilename = \"testdestination\\artifact_path\"\n"
            + "$webClient.DownloadFile($url, $localfilename)\n");
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testJenkinsGetCommandStringWithAuth_POWERSHELL() {
    ConnectorInfoDTO connectorDTO = getJenkinsConnectorInfoDTOWithSecret();
    JenkinsArtifactDelegateConfig artifactDelegateConfig = JenkinsArtifactDelegateConfig.builder()
                                                               .artifactPath("artifact_path")
                                                               .identifier("identifier")
                                                               .connectorDTO(connectorDTO)
                                                               .jobName("job_name")
                                                               .build("build_number54")
                                                               .build();
    String commandString = jenkinsArtifactDownloadHandler.getCommandString(
        artifactDelegateConfig, "testdestination", ScriptType.POWERSHELL);

    assertThat(commandString)
        .isEqualTo("$var1 = $env:HARNESS_ENV_PROXY\n"
            + "$var2 = $env:HTTP_PROXY\n"
            + "$webClient = New-Object System.Net.WebClient\n"
            + "if ( ([string]::IsNullOrEmpty($var2)) -and  (\"true\" -eq $var1) ) {\n"
            + " Write-Host \"HTTP_PROXY environment variable not found or empty\"\n"
            + "}\n"
            + "if ( (-not [string]::IsNullOrEmpty($var2)) -and  (\"true\" -eq $var1) ) {\n"
            + " Write-Host \"Using HTTP_PROXY environment variable\"\n"
            + " $webProxy = New-Object System.Net.WebProxy(\"$env:HTTP_PROXY\",$true)\n"
            + " $webClient.Proxy = $webProxy\n"
            + "}\n"
            + "$url = \"http://hostname/job/job_name/build_number54/artifact/artifact_path\"\n"
            + "$localfilename = \"testdestination\\artifact_path\"\n"
            + "$webClient.Headers[[System.Net.HttpRequestHeader]::Authorization] = \"Basic dXNlcm5hbWU6dGVzdA==\"\n"
            + "$webClient.DownloadFile($url, $localfilename)\n");
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void testS3GetCommandString_POWERSHELL() {
    when(encryptionService.decrypt(any(AwsConfig.class), any(), anyBoolean()))
        .thenReturn(AwsConfig.builder().accessKey("accessKey".toCharArray()).secretKey("secret".toCharArray()).build());

    when(awsHelperService.getBucketRegion(any(), any(), any())).thenReturn("us-east-2");
    AwsConnectorDTO connectorDTO = getS3ConnectorInfoDTO();
    AwsS3ArtifactDelegateConfig s3DelegateConfig = AwsS3ArtifactDelegateConfig.builder()
                                                       .bucketName("bucket")
                                                       .artifactPath("artifact/name")
                                                       .identifier("S3delegateConfig")
                                                       .accountId("accountId")
                                                       .region("us-east-2")
                                                       .certValidationRequired(false)
                                                       .encryptionDetails(Collections.emptyList())
                                                       .awsConnector(connectorDTO)
                                                       .build();
    String commandString =
        s3ArtifactDownloadHandler.getCommandString(s3DelegateConfig, "testdestination", ScriptType.POWERSHELL);

    assertThat(commandString)
        .startsWith("$Headers = @{\n"
            + "    Authorization = \"AWS4-HMAC-SHA256 Credential");

    assertThat(commandString)
        .contains("$ProgressPreference = 'SilentlyContinue'\n"
            + "$var1 = $env:HARNESS_ENV_PROXY\n"
            + "$var2 = $env:HTTP_PROXY\n"
            + "if ( ([string]::IsNullOrEmpty($var2)) -and  (\"true\" -eq $var1) ) {\n"
            + " Write-Host \"HTTP_PROXY environment variable not found or empty\"\n"
            + "}\n"
            + "if ( (-not [string]::IsNullOrEmpty($var2)) -and  (\"true\" -eq $var1) ) {\n"
            + " Write-Host \"Using HTTP_PROXY environment variable\"\n"
            + " Invoke-WebRequest -Uri \"https://bucket.s3-us-east-2.amazonaws.com/artifact/name\" -Headers $Headers -OutFile (New-Item -Path \"testdestination\\name\" -Force) -Proxy \"$env:HTTP_PROXY\"\n"
            + "} else {\n"
            + " Invoke-WebRequest -Uri \"https://bucket.s3-us-east-2.amazonaws.com/artifact/name\" -Headers $Headers -OutFile (New-Item -Path \"testdestination\\name\" -Force)\n"
            + "}");
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void testS3GetCommandString_BASH() {
    when(encryptionService.decrypt(any(AwsConfig.class), any(), anyBoolean()))
        .thenReturn(AwsConfig.builder().accessKey("accessKey".toCharArray()).secretKey("secret".toCharArray()).build());

    when(awsHelperService.getBucketRegion(any(), any(), any())).thenReturn("us-east-2");
    AwsConnectorDTO connectorDTO = getS3ConnectorInfoDTO();
    AwsS3ArtifactDelegateConfig s3DelegateConfig = AwsS3ArtifactDelegateConfig.builder()
                                                       .bucketName("bucket")
                                                       .artifactPath("artifact/name")
                                                       .identifier("S3delegateConfig")
                                                       .accountId("accountId")
                                                       .region("us-east-2")
                                                       .certValidationRequired(false)
                                                       .encryptionDetails(Collections.emptyList())
                                                       .awsConnector(connectorDTO)
                                                       .build();
    String commandString =
        s3ArtifactDownloadHandler.getCommandString(s3DelegateConfig, "testdestination", ScriptType.BASH);

    assertThat(commandString).contains("curl --fail \"https://bucket.s3-us-east-2.amazonaws.com/artifact/name\" \\\n");
    assertThat(commandString).contains("-H \"Authorization: AWS4-HMAC-SHA256 Credential=accessKey/");
    assertThat(commandString).contains(" -o \"testdestination/name\"");
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testS3GetCommandStringNullScriptType() {
    AwsS3ArtifactDelegateConfig s3DelegateConfig = AwsS3ArtifactDelegateConfig.builder().build();
    assertThatThrownBy(() -> s3ArtifactDownloadHandler.getCommandString(s3DelegateConfig, "", null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Unknown script type.");
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testS3GetCommandString_BASH_IamCredentials() {
    when(encryptionService.decrypt(any(AwsConfig.class), any(), anyBoolean()))
        .thenReturn(AwsConfig.builder().useEc2IamCredentials(true).build());
    when(awsHelperService.getCredentialsForIAMROleOnDelegate(anyString(), any()))
        .thenReturn(
            AWSTemporaryCredentials.builder().accessKeyId("accessKey").secretKey("secretKey").token("qwerty").build());

    when(awsHelperService.getBucketRegion(any(), any(), any())).thenReturn("us-east-2");
    AwsConnectorDTO connectorDTO = getS3ConnectorInfoDTO();
    AwsS3ArtifactDelegateConfig s3DelegateConfig = AwsS3ArtifactDelegateConfig.builder()
                                                       .bucketName("bucket")
                                                       .artifactPath("artifact/name")
                                                       .identifier("S3delegateConfig")
                                                       .accountId("accountId")
                                                       .region("us-east-2")
                                                       .certValidationRequired(false)
                                                       .encryptionDetails(Collections.emptyList())
                                                       .awsConnector(connectorDTO)
                                                       .build();
    String commandString =
        s3ArtifactDownloadHandler.getCommandString(s3DelegateConfig, "testdestination", ScriptType.BASH);

    assertThat(commandString).contains("curl --fail \"https://bucket.s3-us-east-2.amazonaws.com/artifact/name\" \\\n");
    assertThat(commandString).contains("-H \"Authorization: AWS4-HMAC-SHA256 Credential=accessKey/");
    assertThat(commandString).contains(" -o \"testdestination/name\"");
    assertThat(commandString).contains("token: qwerty");
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testS3GetCommandString_BucketNameNull() {
    when(encryptionService.decrypt(any(AwsConfig.class), any(), anyBoolean()))
        .thenReturn(AwsConfig.builder().accessKey("accessKey".toCharArray()).secretKey("secret".toCharArray()).build());

    AwsConnectorDTO connectorDTO = getS3ConnectorInfoDTO();
    AwsS3ArtifactDelegateConfig s3DelegateConfig = AwsS3ArtifactDelegateConfig.builder()
                                                       .artifactPath("artifact/name")
                                                       .identifier("S3delegateConfig")
                                                       .accountId("accountId")
                                                       .region("us-east-2")
                                                       .certValidationRequired(false)
                                                       .encryptionDetails(Collections.emptyList())
                                                       .awsConnector(connectorDTO)
                                                       .build();

    assertThatThrownBy(() -> s3ArtifactDownloadHandler.getCommandString(s3DelegateConfig, "", ScriptType.BASH))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Bucket name needs to be defined");
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testS3GetCommandString_ArtifactPathNull() {
    when(encryptionService.decrypt(any(AwsConfig.class), any(), anyBoolean()))
        .thenReturn(AwsConfig.builder().accessKey("accessKey".toCharArray()).secretKey("secret".toCharArray()).build());
    when(awsHelperService.getBucketRegion(any(), any(), any())).thenReturn("us-east-2");

    AwsConnectorDTO connectorDTO = getS3ConnectorInfoDTO();
    AwsS3ArtifactDelegateConfig s3DelegateConfig = AwsS3ArtifactDelegateConfig.builder()
                                                       .bucketName("bucketName")
                                                       .identifier("S3delegateConfig")
                                                       .accountId("accountId")
                                                       .region("us-east-2")
                                                       .certValidationRequired(false)
                                                       .encryptionDetails(Collections.emptyList())
                                                       .awsConnector(connectorDTO)
                                                       .build();

    assertThatThrownBy(() -> s3ArtifactDownloadHandler.getCommandString(s3DelegateConfig, "", ScriptType.BASH))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Artifact path needs to be defined");
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testS3GetCommandString_INHERIT_FROM_DELEGATE() {
    when(encryptionService.decrypt(any(AwsConfig.class), any(), anyBoolean()))
        .thenReturn(AwsConfig.builder().accessKey("accessKey".toCharArray()).secretKey("secret".toCharArray()).build());

    when(awsHelperService.getBucketRegion(any(), any(), any())).thenReturn("us-east-2");
    AwsConnectorDTO connectorDTO = getS3ConnectorInfoDTO();
    connectorDTO.setCredential(
        AwsCredentialDTO.builder()
            .awsCredentialType(AwsCredentialType.INHERIT_FROM_DELEGATE)
            .config(AwsInheritFromDelegateSpecDTO.builder().delegateSelectors(Collections.EMPTY_SET).build())
            .build());

    AwsS3ArtifactDelegateConfig s3DelegateConfig = AwsS3ArtifactDelegateConfig.builder()
                                                       .bucketName("bucket")
                                                       .artifactPath("artifact/name")
                                                       .identifier("S3delegateConfig")
                                                       .accountId("accountId")
                                                       .region("us-east-2")
                                                       .certValidationRequired(false)
                                                       .encryptionDetails(Collections.emptyList())
                                                       .awsConnector(connectorDTO)
                                                       .build();
    String commandString =
        s3ArtifactDownloadHandler.getCommandString(s3DelegateConfig, "testdestination", ScriptType.BASH);

    assertThat(commandString).contains("curl --fail \"https://bucket.s3-us-east-2.amazonaws.com/artifact/name\" \\\n");
    assertThat(commandString).contains("-H \"Authorization: AWS4-HMAC-SHA256 Credential=accessKey/");
    assertThat(commandString).contains(" -o \"testdestination/name\"");
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testS3GetCommandString_IRSA() {
    when(encryptionService.decrypt(any(AwsConfig.class), any(), anyBoolean()))
        .thenReturn(AwsConfig.builder().accessKey("accessKey".toCharArray()).secretKey("secret".toCharArray()).build());

    when(awsHelperService.getBucketRegion(any(), any(), any())).thenReturn("us-east-2");
    AwsConnectorDTO connectorDTO = getS3ConnectorInfoDTO();
    connectorDTO.setCredential(AwsCredentialDTO.builder()
                                   .awsCredentialType(AwsCredentialType.IRSA)
                                   .config(AwsIRSASpecDTO.builder().build())
                                   .build());

    AwsS3ArtifactDelegateConfig s3DelegateConfig = AwsS3ArtifactDelegateConfig.builder()
                                                       .bucketName("bucket")
                                                       .artifactPath("artifact/name")
                                                       .identifier("S3delegateConfig")
                                                       .accountId("accountId")
                                                       .region("us-east-2")
                                                       .certValidationRequired(false)
                                                       .encryptionDetails(Collections.emptyList())
                                                       .awsConnector(connectorDTO)
                                                       .build();
    String commandString =
        s3ArtifactDownloadHandler.getCommandString(s3DelegateConfig, "testdestination", ScriptType.BASH);

    assertThat(commandString).contains("curl --fail \"https://bucket.s3-us-east-2.amazonaws.com/artifact/name\" \\\n");
    assertThat(commandString).contains("-H \"Authorization: AWS4-HMAC-SHA256 Credential=accessKey/");
    assertThat(commandString).contains(" -o \"testdestination/name\"");
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testS3GetCommandString_CrossAccountAccess() {
    when(encryptionService.decrypt(any(AwsConfig.class), any(), anyBoolean()))
        .thenReturn(AwsConfig.builder().accessKey("accessKey".toCharArray()).secretKey("secret".toCharArray()).build());

    when(awsHelperService.getBucketRegion(any(), any(), any())).thenReturn("us-east-2");
    AwsConnectorDTO connectorDTO = getS3ConnectorInfoDTO();
    connectorDTO.setCredential(
        AwsCredentialDTO.builder()
            .awsCredentialType(AwsCredentialType.IRSA)
            .config(AwsIRSASpecDTO.builder().build())
            .crossAccountAccess(CrossAccountAccessDTO.builder().crossAccountRoleArn("role").externalId("id").build())
            .build());

    AwsS3ArtifactDelegateConfig s3DelegateConfig = AwsS3ArtifactDelegateConfig.builder()
                                                       .bucketName("bucket")
                                                       .artifactPath("artifact/name")
                                                       .identifier("S3delegateConfig")
                                                       .accountId("accountId")
                                                       .region("us-east-2")
                                                       .certValidationRequired(false)
                                                       .encryptionDetails(Collections.emptyList())
                                                       .awsConnector(connectorDTO)
                                                       .build();
    String commandString =
        s3ArtifactDownloadHandler.getCommandString(s3DelegateConfig, "testdestination", ScriptType.BASH);

    assertThat(commandString).contains("curl --fail \"https://bucket.s3-us-east-2.amazonaws.com/artifact/name\" \\\n");
    assertThat(commandString).contains("-H \"Authorization: AWS4-HMAC-SHA256 Credential=accessKey/");
    assertThat(commandString).contains(" -o \"testdestination/name\"");
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testAzureGetCommandString_MavenArtifact_Bash() {
    when(azureArtifactsHelper.getArtifactFileName(any())).thenReturn("artifact_filename");
    AzureArtifactsConnectorDTO connectorDTO = getAzureArtifactConnectorInfoDTO();

    ConnectorInfoDTO connectorInfoDTO =
        ConnectorInfoDTO.builder().connectorType(ConnectorType.AZURE_ARTIFACTS).connectorConfig(connectorDTO).build();
    AzureArtifactDelegateConfig delegateConfig = AzureArtifactDelegateConfig.builder()
                                                     .identifier("azureArtifactsDelegateConfig")
                                                     .connectorDTO(connectorInfoDTO)
                                                     .feed("feed")
                                                     .packageId("packageId1")
                                                     .packageName("groupId:artifactId")
                                                     .packageType("maven")
                                                     .version("1.0.0")
                                                     .scope("project")
                                                     .project("test-project")
                                                     .build();
    String commandString =
        azureArtifactDownloadHandler.getCommandString(delegateConfig, "testdestination", ScriptType.BASH);

    assertThat(commandString)
        .isEqualTo("curl -L --fail -H \"Authorization: Basic OnRlc3Q=\" "
            + "-X GET \"azure.test.url/test-project/_apis/packaging/feeds/feed/maven/groupId/artifactId/1.0.0/artifact_filename/content?api-version=5.1-preview.1\""
            + " -o \"testdestination/artifact_filename\"");
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testAzureGetCommandString_MavenArtifact_Powershell() {
    when(azureArtifactsHelper.getArtifactFileName(any())).thenReturn("artifact_filename");
    AzureArtifactsConnectorDTO connectorDTO = getAzureArtifactConnectorInfoDTO();

    ConnectorInfoDTO connectorInfoDTO =
        ConnectorInfoDTO.builder().connectorType(ConnectorType.AZURE_ARTIFACTS).connectorConfig(connectorDTO).build();
    AzureArtifactDelegateConfig delegateConfig = AzureArtifactDelegateConfig.builder()
                                                     .identifier("azureArtifactsDelegateConfig")
                                                     .connectorDTO(connectorInfoDTO)
                                                     .feed("feed")
                                                     .packageId("packageId1")
                                                     .packageName("groupId:artifactId")
                                                     .packageType("maven")
                                                     .version("1.0.0")
                                                     .scope("project")
                                                     .project("test-project")
                                                     .build();
    String commandString =
        azureArtifactDownloadHandler.getCommandString(delegateConfig, "testdestination", ScriptType.POWERSHELL);

    assertThat(commandString)
        .isEqualTo("$Headers = @{\n"
            + "    Authorization = \"Basic OnRlc3Q=\"\n"
            + "}\n"
            + "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12\n"
            + "$ProgressPreference = 'SilentlyContinue'\n"
            + "$var1 = $env:HARNESS_ENV_PROXY\n"
            + "$var2 = $env:HTTP_PROXY\n"
            + "if ( ([string]::IsNullOrEmpty($var2)) -and  (\"true\" -eq $var1) ) {\n"
            + " Write-Host \"HTTP_PROXY environment variable not found or empty\"\n"
            + "}\n"
            + "if ( (-not [string]::IsNullOrEmpty($var2)) -and  (\"true\" -eq $var1) ) {\n"
            + " Write-Host \"Using HTTP_PROXY environment variable\"\n"
            + " Invoke-WebRequest -Uri \"azure.test.url/test-project/_apis/packaging/feeds/feed/maven/groupId/artifactId/1.0.0/artifact_filename/content?api-version=5.1-preview.1\" -Headers $Headers -OutFile \"testdestination\\artifact_filename\" -Proxy \"$env:HTTP_PROXY\"\n"
            + "} else {\n"
            + " Invoke-WebRequest -Uri \"azure.test.url/test-project/_apis/packaging/feeds/feed/maven/groupId/artifactId/1.0.0/artifact_filename/content?api-version=5.1-preview.1\" -Headers $Headers -OutFile \"testdestination\\artifact_filename\"\n"
            + "}");
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testAzureGetCommandString_NugetArtifact_Bash() {
    when(azureArtifactsHelper.getArtifactFileName(any())).thenReturn("artifact_filename");

    AzureArtifactsConnectorDTO connectorDTO = getAzureArtifactConnectorInfoDTO();

    ConnectorInfoDTO connectorInfoDTO =
        ConnectorInfoDTO.builder().connectorType(ConnectorType.AZURE_ARTIFACTS).connectorConfig(connectorDTO).build();
    AzureArtifactDelegateConfig delegateConfig = AzureArtifactDelegateConfig.builder()
                                                     .identifier("azureArtifactsDelegateConfig")
                                                     .connectorDTO(connectorInfoDTO)
                                                     .feed("feed")
                                                     .packageId("packageId1")
                                                     .packageName("groupId:artifactId")
                                                     .packageType("nuget")
                                                     .version("1.0.0")
                                                     .scope("project")
                                                     .project("test-project")
                                                     .build();
    String commandString =
        azureArtifactDownloadHandler.getCommandString(delegateConfig, "testdestination", ScriptType.BASH);

    assertThat(commandString)
        .isEqualTo("curl -L --fail -H \"Authorization: Basic OnRlc3Q=\" "
            + "-X GET \"azure.test.url/test-project/_apis/packaging/feeds/feed/nuget/packages/groupId:artifactId/versions/1.0.0/content?api-version=5.1-preview.1\" "
            + "-o \"testdestination/artifact_filename\"");
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testAzureGetCommandString_NugetArtifact_Powershell() {
    when(azureArtifactsHelper.getArtifactFileName(any())).thenReturn("artifact_filename");

    AzureArtifactsConnectorDTO connectorDTO = getAzureArtifactConnectorInfoDTO();

    ConnectorInfoDTO connectorInfoDTO =
        ConnectorInfoDTO.builder().connectorType(ConnectorType.AZURE_ARTIFACTS).connectorConfig(connectorDTO).build();
    AzureArtifactDelegateConfig delegateConfig = AzureArtifactDelegateConfig.builder()
                                                     .identifier("azureArtifactsDelegateConfig")
                                                     .connectorDTO(connectorInfoDTO)
                                                     .feed("feed")
                                                     .packageId("packageId1")
                                                     .packageName("groupId:artifactId")
                                                     .packageType("nuget")
                                                     .version("1.0.0")
                                                     .scope("project")
                                                     .project("test-project")
                                                     .build();
    String commandString =
        azureArtifactDownloadHandler.getCommandString(delegateConfig, "testdestination", ScriptType.POWERSHELL);

    assertThat(commandString)
        .isEqualTo("$Headers = @{\n"
            + "    Authorization = \"Basic OnRlc3Q=\"\n"
            + "}\n"
            + "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12\n"
            + "$ProgressPreference = 'SilentlyContinue'\n"
            + "$var1 = $env:HARNESS_ENV_PROXY\n"
            + "$var2 = $env:HTTP_PROXY\n"
            + "if ( ([string]::IsNullOrEmpty($var2)) -and  (\"true\" -eq $var1) ) {\n"
            + " Write-Host \"HTTP_PROXY environment variable not found or empty\"\n"
            + "}\n"
            + "if ( (-not [string]::IsNullOrEmpty($var2)) -and  (\"true\" -eq $var1) ) {\n"
            + " Write-Host \"Using HTTP_PROXY environment variable\"\n"
            + " Invoke-WebRequest -Uri \"azure.test.url/test-project/_apis/packaging/feeds/feed/nuget/packages/groupId:artifactId/versions/1.0.0/content?api-version=5.1-preview.1\" -Headers $Headers -OutFile \"testdestination\\artifact_filename\" -Proxy \"$env:HTTP_PROXY\"\n"
            + "} else {\n"
            + " Invoke-WebRequest -Uri \"azure.test.url/test-project/_apis/packaging/feeds/feed/nuget/packages/groupId:artifactId/versions/1.0.0/content?api-version=5.1-preview.1\" -Headers $Headers -OutFile \"testdestination\\artifact_filename\"\n"
            + "}");
  }

  private ConnectorInfoDTO getArtifactoryConnectorInfoDTOWithSecret() {
    SecretRefData passwordRef = SecretRefData.builder().identifier("secret_ident").build();
    ArtifactoryUsernamePasswordAuthDTO credentials =
        ArtifactoryUsernamePasswordAuthDTO.builder().username("username").passwordRef(passwordRef).build();
    ArtifactoryAuthenticationDTO artifactoryAuthenticationDTO = ArtifactoryAuthenticationDTO.builder()
                                                                    .authType(ArtifactoryAuthType.USER_PASSWORD)
                                                                    .credentials(credentials)
                                                                    .build();
    ArtifactoryConnectorDTO connectorConfig = ArtifactoryConnectorDTO.builder()
                                                  .auth(artifactoryAuthenticationDTO)
                                                  .artifactoryServerUrl("http://hostname")
                                                  .build();
    return ConnectorInfoDTO.builder().connectorConfig(connectorConfig).build();
  }

  private ConnectorInfoDTO getJenkinsConnectorInfoDTOWithSecret() {
    SecretRefData passwordRef = SecretRefData.builder().identifier("secret_ident").build();
    JenkinsUserNamePasswordDTO credentials =
        JenkinsUserNamePasswordDTO.builder().username("username").passwordRef(passwordRef).build();
    JenkinsAuthenticationDTO jenkinsAuthenticationDTO =
        JenkinsAuthenticationDTO.builder().authType(JenkinsAuthType.USER_PASSWORD).credentials(credentials).build();
    JenkinsConnectorDTO connectorConfig =
        JenkinsConnectorDTO.builder().jenkinsUrl("http://hostname").auth(jenkinsAuthenticationDTO).build();
    return ConnectorInfoDTO.builder().connectorConfig(connectorConfig).build();
  }

  private ConnectorInfoDTO getJenkinsConnectorInfoDTO() {
    JenkinsAuthenticationDTO jenkinsAuthenticationDTO =
        JenkinsAuthenticationDTO.builder().authType(JenkinsAuthType.ANONYMOUS).build();
    JenkinsConnectorDTO connectorConfig =
        JenkinsConnectorDTO.builder().jenkinsUrl("http://hostname").auth(jenkinsAuthenticationDTO).build();
    return ConnectorInfoDTO.builder().connectorConfig(connectorConfig).build();
  }

  private ConnectorInfoDTO getArtifactoryConnectorInfoDTO() {
    ArtifactoryAuthenticationDTO artifactoryAuthenticationDTO =
        ArtifactoryAuthenticationDTO.builder().authType(ArtifactoryAuthType.ANONYMOUS).build();
    ArtifactoryConnectorDTO connectorConfig = ArtifactoryConnectorDTO.builder()
                                                  .auth(artifactoryAuthenticationDTO)
                                                  .artifactoryServerUrl("http://hostname")
                                                  .build();
    return ConnectorInfoDTO.builder().connectorConfig(connectorConfig).build();
  }

  private ArtifactoryConfigRequest getArtifactoryConfigRequestWithPass() {
    return ArtifactoryConfigRequest.builder()
        .artifactoryUrl("http://hostname")
        .artifactRepositoryUrl("")
        .hasCredentials(true)
        .username("username")
        .password(DECRYPTED_PASSWORD_VALUE)
        .build();
  }

  private ArtifactoryConfigRequest getDefaultArtifactoryConfigRequest() {
    return ArtifactoryConfigRequest.builder()
        .artifactoryUrl("http://hostname")
        .hasCredentials(false)
        .username("username")
        .build();
  }

  private AwsConnectorDTO getS3ConnectorInfoDTO() {
    AwsManualConfigSpecDTO awsCredentialSpec = AwsManualConfigSpecDTO.builder()
                                                   .accessKey("accessKey")
                                                   .secretKeyRef(SecretRefData.builder().identifier("secredt").build())
                                                   .build();
    AwsCredentialDTO awsCredentialDTO = AwsCredentialDTO.builder()
                                            .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
                                            .config(awsCredentialSpec)
                                            .build();
    return AwsConnectorDTO.builder().credential(awsCredentialDTO).executeOnDelegate(false).build();
  }

  private AzureArtifactsConnectorDTO getAzureArtifactConnectorInfoDTO() {
    AzureArtifactsTokenDTO tokenDTO = AzureArtifactsTokenDTO.builder()
                                          .tokenRef(SecretRefData.builder()
                                                        .identifier("azure_artifacts_secret")
                                                        .decryptedValue(DECRYPTED_PASSWORD_VALUE)
                                                        .build())
                                          .build();
    AzureArtifactsCredentialsDTO azureArtifactsCredentialsDTO =
        AzureArtifactsCredentialsDTO.builder()
            .type(AzureArtifactsAuthenticationType.PERSONAL_ACCESS_TOKEN)
            .credentialsSpec(tokenDTO)
            .build();
    AzureArtifactsAuthenticationDTO azureArtifactsAuthenticationDTO =
        AzureArtifactsAuthenticationDTO.builder().credentials(azureArtifactsCredentialsDTO).build();
    return AzureArtifactsConnectorDTO.builder()
        .azureArtifactsUrl("azure.test.url")
        .executeOnDelegate(false)
        .auth(azureArtifactsAuthenticationDTO)
        .build();
  }
}