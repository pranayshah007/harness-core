/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.impl.scm;

import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.doReturn;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.category.element.UnitTests;
import io.harness.encryption.SecretRefData;
import io.harness.gitsync.common.dtos.gitAccess.AzureRepoAccessDTO;
import io.harness.gitsync.common.dtos.gitAccess.GithubAccessTokenDTO;
import io.harness.gitsync.common.dtos.gitAccess.GithubAppAccessDTO;
import io.harness.gitsync.common.dtos.gitAccess.GitlabAccessDTO;
import io.harness.product.ci.scm.proto.AzureProvider;
import io.harness.product.ci.scm.proto.GithubProvider;
import io.harness.product.ci.scm.proto.GitlabProvider;
import io.harness.product.ci.scm.proto.Provider;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class SCMGitAccessToProviderMapperTest {
  @Mock ScmGitProviderHelper scmGitProviderHelper;
  @InjectMocks SCMGitAccessToProviderMapper scmGitAccessToProviderMapper;
  SecretRefData tokenRef;
  String applicationId = "applicationId";
  String installationId = "installationId";
  SecretRefData applicationIdRef;
  SecretRefData installationIdRef;
  SecretRefData privateKeyRef;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    tokenRef = buildSecretRefData("tokenRef");
    doReturn("tokenRef").when(scmGitProviderHelper).getToken(tokenRef);
    applicationIdRef = buildSecretRefData("applicationIdRef");
    privateKeyRef = buildSecretRefData("privateKeyRef");
    installationIdRef = buildSecretRefData("installationIdRef");
    doReturn(applicationId)
        .when(scmGitProviderHelper)
        .getAccessTokenFromGithubApp(applicationId, applicationIdRef, installationId, installationIdRef, privateKeyRef,
            "https://api.github.com/");
  }

  private SecretRefData buildSecretRefData(String identifier) {
    return SecretRefData.builder().scope(io.harness.encryption.Scope.ACCOUNT).identifier(identifier).build();
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testMapToSCMGithubProvider() {
    Provider provider = scmGitAccessToProviderMapper.mapToSCMGitProvider(
        GithubAccessTokenDTO.builder()
            .tokenScope(Scope.builder().accountIdentifier("accountId").build())
            .tokenRef(tokenRef)
            .build());
    GithubProvider githubProvider = GithubProvider.newBuilder().setAccessToken("tokenRef").build();
    assertEquals(provider.getGithub(), githubProvider);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testMapToSCMGithubAppProvider() {
    Provider provider = scmGitAccessToProviderMapper.mapToSCMGitProvider(
        GithubAppAccessDTO.builder()
            .tokenScope(Scope.builder().accountIdentifier("accountId").build())
            .installationId(installationId)
            .applicationId(applicationId)
            .applicationIdRef(applicationIdRef)
            .privateKeyRef(privateKeyRef)
            .installationIdRef(installationIdRef)
            .isGithubApp(true)
            .build());
    GithubProvider githubProvider =
        GithubProvider.newBuilder().setAccessToken(applicationId).setIsGithubApp(true).build();
    assertEquals(provider.getGithub(), githubProvider);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testMapToSCMGitlabProvider() {
    Provider provider = scmGitAccessToProviderMapper.mapToSCMGitProvider(
        GitlabAccessDTO.builder()
            .tokenScope(Scope.builder().accountIdentifier("accountId").build())
            .tokenRef(tokenRef)
            .build());
    GitlabProvider gitlabProvider = GitlabProvider.newBuilder().setAccessToken("tokenRef").build();
    assertEquals(provider.getGitlab(), gitlabProvider);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testMapToSCMAzureRepoProvider() {
    Provider provider = scmGitAccessToProviderMapper.mapToSCMGitProvider(
        AzureRepoAccessDTO.builder()
            .tokenScope(Scope.builder().accountIdentifier("accountId").build())
            .tokenRef(tokenRef)
            .build());
    AzureProvider azureProvider = AzureProvider.newBuilder().setPersonalAccessToken("tokenRef").build();
    assertEquals(provider.getAzure(), azureProvider);
  }
}
