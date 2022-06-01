/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.impl.scm;

import static io.harness.delegate.beans.connector.scm.GitAuthType.HTTP;
import static io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoApiAccessType.TOKEN;
import static io.harness.rule.OwnerRule.MANKRIT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoApiAccessDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoSshCredentialsDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoUsernameTokenDTO;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefHelper;
import io.harness.git.GitClientHelper;
import io.harness.product.ci.scm.proto.Provider;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PrepareForTest;

@OwnedBy(HarnessTeam.PL)
@PrepareForTest({Provider.class})
public class AzureRepoGitProviderMapperTest extends CategoryTest {
  @InjectMocks ScmGitProviderMapper scmGitProviderMapper;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testMapToAzureRepoProviderHTTPRepo() {
    final String url = "https://mankritsingh@dev.azure.com/org/project/_git/repo";
    final String tokenRef = "tokenRef";
    final String username = "username";
    final AzureRepoAuthenticationDTO azureRepoAuthenticationDTO =
        AzureRepoAuthenticationDTO.builder()
            .authType(HTTP)
            .credentials(AzureRepoHttpCredentialsDTO.builder()
                             .type(AzureRepoHttpAuthenticationType.USERNAME_AND_TOKEN)
                             .httpCredentialsSpec(AzureRepoUsernameTokenDTO.builder()
                                                      .username(username)
                                                      .tokenRef(SecretRefHelper.createSecretRef(tokenRef))
                                                      .build())
                             .build())
            .build();

    SecretRefData x = SecretRefHelper.createSecretRef(tokenRef);
    x.setDecryptedValue("tokenRef".toCharArray());
    final AzureRepoApiAccessDTO azureRepoApiAccessDTO =
        AzureRepoApiAccessDTO.builder().type(TOKEN).spec(AzureRepoTokenSpecDTO.builder().tokenRef(x).build()).build();

    final AzureRepoConnectorDTO azureRepoConnectorDTO = AzureRepoConnectorDTO.builder()
                                                            .connectionType(GitConnectionType.REPO)
                                                            .url(url)
                                                            .authentication(azureRepoAuthenticationDTO)
                                                            .apiAccess(azureRepoApiAccessDTO)
                                                            .build();

    final Provider provider = scmGitProviderMapper.mapToSCMGitProvider(azureRepoConnectorDTO);
    assertThat(provider).isNotNull();
    assertThat(provider.getDebug()).isEqualTo(false);
    assertThat(provider.getEndpoint()).isEqualTo("https://dev.azure.com/");
    assertThat(provider.getAzure().getOrganization()).isEqualTo("org");
    assertThat(provider.getAzure().getProject()).isEqualTo("project");
    assertThat(provider.getAzure().getPersonalAccessToken()).isEqualTo(tokenRef);
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testMapToAzureRepoProviderHTTPAcc() {
    final String url = "https://mankritsingh@dev.azure.com/org/";
    final String tokenRef = "tokenRef";
    final String username = "username";
    final String validationProject = "project";
    final String validationRepo = "repo";
    final AzureRepoAuthenticationDTO azureRepoAuthenticationDTO =
        AzureRepoAuthenticationDTO.builder()
            .authType(HTTP)
            .credentials(AzureRepoHttpCredentialsDTO.builder()
                             .type(AzureRepoHttpAuthenticationType.USERNAME_AND_TOKEN)
                             .httpCredentialsSpec(AzureRepoUsernameTokenDTO.builder()
                                                      .username(username)
                                                      .tokenRef(SecretRefHelper.createSecretRef(tokenRef))
                                                      .build())
                             .build())
            .build();
    SecretRefData x = SecretRefHelper.createSecretRef(tokenRef);
    x.setDecryptedValue("tokenRef".toCharArray());
    final AzureRepoApiAccessDTO azureRepoApiAccessDTO =
        AzureRepoApiAccessDTO.builder().type(TOKEN).spec(AzureRepoTokenSpecDTO.builder().tokenRef(x).build()).build();

    final AzureRepoConnectorDTO azureRepoConnectorDTO = AzureRepoConnectorDTO.builder()
                                                            .connectionType(GitConnectionType.ACCOUNT)
                                                            .url(url)
                                                            .validationProject(validationProject)
                                                            .validationRepo(validationRepo)
                                                            .authentication(azureRepoAuthenticationDTO)
                                                            .apiAccess(azureRepoApiAccessDTO)
                                                            .build();

    final Provider provider = scmGitProviderMapper.mapToSCMGitProvider(azureRepoConnectorDTO);
    assertThat(provider).isNotNull();
    assertThat(provider.getDebug()).isEqualTo(false);
    assertThat(provider.getEndpoint()).isEqualTo("https://dev.azure.com/");
    assertThat(provider.getAzure().getOrganization()).isEqualTo("org");
    assertThat(provider.getAzure().getPersonalAccessToken()).isEqualTo(tokenRef);
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testMapToAzureRepoProviderSSHRepo() {
    final String url = "git@ssh.dev.azure.com:v3/org/project/repo";
    String sshKeyRef = "sshKeyRef";
    String tokenRef = "tokenRef";
    final AzureRepoAuthenticationDTO azureRepoAuthenticationDTO =
        AzureRepoAuthenticationDTO.builder()
            .authType(GitAuthType.SSH)
            .credentials(
                AzureRepoSshCredentialsDTO.builder().sshKeyRef(SecretRefHelper.createSecretRef(sshKeyRef)).build())
            .build();

    SecretRefData x = SecretRefHelper.createSecretRef(tokenRef);
    x.setDecryptedValue("tokenRef".toCharArray());
    final AzureRepoApiAccessDTO azureRepoApiAccessDTO =
        AzureRepoApiAccessDTO.builder().type(TOKEN).spec(AzureRepoTokenSpecDTO.builder().tokenRef(x).build()).build();

    final AzureRepoConnectorDTO azureRepoConnectorDTO = AzureRepoConnectorDTO.builder()
                                                            .connectionType(GitConnectionType.REPO)
                                                            .url(url)
                                                            .authentication(azureRepoAuthenticationDTO)
                                                            .apiAccess(azureRepoApiAccessDTO)
                                                            .build();

    final Provider provider = scmGitProviderMapper.mapToSCMGitProvider(azureRepoConnectorDTO);
    assertThat(provider).isNotNull();
    assertThat(provider.getDebug()).isEqualTo(false);
    assertThat(provider.getEndpoint()).isEqualTo("https://ssh.dev.azure.com/");
    assertThat(provider.getAzure().getOrganization()).isEqualTo("org");
    assertThat(provider.getAzure().getProject()).isEqualTo("project");
    assertThat(provider.getAzure().getPersonalAccessToken()).isEqualTo(tokenRef);
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testMapToAzureRepoProviderSSHAcc() {
    final String url = "git@ssh.dev.azure.com:v3/org/";
    String sshKeyRef = "sshKeyRef";
    String validationProject = "project";
    String validationRepo = "repo";
    String tokenRef = "tokenRef";
    final AzureRepoAuthenticationDTO azureRepoAuthenticationDTO =
        AzureRepoAuthenticationDTO.builder()
            .authType(GitAuthType.SSH)
            .credentials(
                AzureRepoSshCredentialsDTO.builder().sshKeyRef(SecretRefHelper.createSecretRef(sshKeyRef)).build())
            .build();

    SecretRefData x = SecretRefHelper.createSecretRef(tokenRef);
    x.setDecryptedValue("tokenRef".toCharArray());
    final AzureRepoApiAccessDTO azureRepoApiAccessDTO =
        AzureRepoApiAccessDTO.builder().type(TOKEN).spec(AzureRepoTokenSpecDTO.builder().tokenRef(x).build()).build();

    final AzureRepoConnectorDTO azureRepoConnectorDTO = AzureRepoConnectorDTO.builder()
                                                            .connectionType(GitConnectionType.ACCOUNT)
                                                            .url(url)
                                                            .validationProject(validationProject)
                                                            .validationRepo(validationRepo)
                                                            .authentication(azureRepoAuthenticationDTO)
                                                            .apiAccess(azureRepoApiAccessDTO)
                                                            .build();

    final Provider provider = scmGitProviderMapper.mapToSCMGitProvider(azureRepoConnectorDTO);
    assertThat(provider).isNotNull();
    assertThat(provider.getDebug()).isEqualTo(false);
    assertThat(provider.getEndpoint()).isEqualTo("https://ssh.dev.azure.com/");
    assertThat(provider.getAzure().getOrganization()).isEqualTo("org");
    assertThat(provider.getAzure().getPersonalAccessToken()).isEqualTo(tokenRef);
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testAzureRepoApiURL() {
    // URL for SaaS
    String url = "https://mankritsingh@dev.azure.com/org/";
    assertThat(GitClientHelper.getAzureRepoApiURL(url)).isEqualTo("https://dev.azure.com/");

    // URL for on-prem
    String url2 = "git@ssh.dev.azure.com:v3/org/";
    assertThat(GitClientHelper.getAzureRepoApiURL(url2)).isEqualTo("https://ssh.dev.azure.com/");
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testOrgAndProject() {
    // for HTTP
    String url = "https://mankritsingh@dev.azure.com/org/project/_git/repo";
    assertThat(GitClientHelper.getAzureRepoOrgAndProjectHTTP(url)).isEqualTo("org/project");

    // for SSH
    String url2 = "git@ssh.dev.azure.com:v3/org/project/repo";
    assertThat(GitClientHelper.getAzureRepoOrgAndProjectSSH(url2)).isEqualTo("org/project");

    // Org
    String orgAndProject = "org/project";
    assertThat(GitClientHelper.getAzureRepoOrg(orgAndProject)).isEqualTo("org");

    // Project
    assertThat(GitClientHelper.getAzureRepoProject(orgAndProject)).isEqualTo("project");
  }
}
