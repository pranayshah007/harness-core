/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.serverless;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ALLU_VAMSI;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;

import com.google.inject.Inject;
import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.artifactory.ArtifactoryNgService;
import io.harness.beans.DecryptableEntity;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.service.git.NGGitService;
import io.harness.connector.task.git.GitDecryptionHelper;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthCredentialsDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthType;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthenticationDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryUsernamePasswordAuthDTO;
import io.harness.delegate.beans.connector.awsconnector.*;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaManifestSchema;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.task.artifactory.ArtifactoryRequestMapper;
import io.harness.delegate.task.git.ScmFetchFilesHelperNG;
import io.harness.encryption.SecretRefData;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.serverless.model.ServerlessAwsLambdaConfig;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import java.util.*;

import io.harness.serverless.model.ServerlessDelegateTaskParams;
import io.harness.shell.SshSessionConfig;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

//////new file
@OwnedBy(CDP)
public class ServerlessInfraConfigHelperTest {
    @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock SecretDecryptionService secretDecryptionService;
    @Mock DecryptableEntity decryptableEntity;

    @InjectMocks private ServerlessInfraConfigHelper serverlessInfraConfigHelper;

    @Test
    @Owner(developers = ALLU_VAMSI)
    @Category(UnitTests.class)
    public void decryptServerlessInfraConfigTest(){
        AwsManualConfigSpecDTO config = AwsManualConfigSpecDTO.builder().accessKey("accessKey").build();
        AwsCredentialDTO awsCredentialDTO =  AwsCredentialDTO.builder()
                .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
                .config(config).build();
        AwsConnectorDTO awsConnectorDTO = AwsConnectorDTO.builder().credential(awsCredentialDTO).build();
        ServerlessAwsLambdaInfraConfig serverlessInfraConfig = ServerlessAwsLambdaInfraConfig.builder()
                .awsConnectorDTO(awsConnectorDTO).build();

        doReturn(decryptableEntity).when(secretDecryptionService).decrypt(eq(config),any(List.class));
        serverlessInfraConfigHelper.decryptServerlessInfraConfig(serverlessInfraConfig);
    }

    @Test
    @Owner(developers = ALLU_VAMSI)
    @Category(UnitTests.class)
    public void createServerlessConfigTest(){
        SecretRefData secretKeyRef = SecretRefData.builder().decryptedValue(new char[]{'a','b','c'}).build();
        AwsManualConfigSpecDTO config = AwsManualConfigSpecDTO.builder().accessKey("accessKey").secretKeyRef(secretKeyRef).build();
        AwsCredentialDTO awsCredentialDTO =  AwsCredentialDTO.builder()
                .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
                .config(config).build();
        AwsConnectorDTO awsConnectorDTO = AwsConnectorDTO.builder().credential(awsCredentialDTO).build();
        ServerlessAwsLambdaInfraConfig serverlessInfraConfig = ServerlessAwsLambdaInfraConfig.builder()
                .awsConnectorDTO(awsConnectorDTO).build();
        ServerlessAwsLambdaConfig serverlessConfig = ServerlessAwsLambdaConfig.builder()
                .provider("aws").accessKey("accessKey").secretKey("abc").build();
        serverlessInfraConfigHelper.createServerlessConfig(serverlessInfraConfig);
    }

    @Test(expected = UnsupportedOperationException.class)
    @Owner(developers = ALLU_VAMSI)
    @Category(UnitTests.class)
    public void createServerlessAwsConfigNotMANUAL_CREDENTIALSTest() throws Exception{
        SecretRefData secretKeyRef = SecretRefData.builder().decryptedValue(new char[]{'a','b','c'}).build();
        AwsManualConfigSpecDTO config = AwsManualConfigSpecDTO.builder().accessKey("accessKey").secretKeyRef(secretKeyRef).build();
        AwsCredentialDTO awsCredentialDTO =  AwsCredentialDTO.builder()
                .awsCredentialType(AwsCredentialType.IRSA)
                .config(config).build();
        AwsConnectorDTO awsConnectorDTO = AwsConnectorDTO.builder().credential(awsCredentialDTO).build();
        ServerlessAwsLambdaInfraConfig serverlessInfraConfig = ServerlessAwsLambdaInfraConfig.builder()
                .awsConnectorDTO(awsConnectorDTO).build();
        serverlessInfraConfigHelper.createServerlessAwsConfig(serverlessInfraConfig);
    }
}
