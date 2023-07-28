/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.validator.scmValidators;

import static io.harness.delegate.beans.connector.scm.GitAuthType.HTTP;
import static io.harness.rule.OwnerRule.SOURABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import io.harness.CategoryTest;
import io.harness.account.AccountClient;
import io.harness.category.element.UnitTests;
import io.harness.connector.helper.EncryptionHelper;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.github.GithubAppDTO;
import io.harness.delegate.beans.connector.scm.github.GithubAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.encryption.SecretRefData;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.api.SecretCrudService;
import io.harness.remote.client.CGRestUtils;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.SshKeySpecDTOHelper;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import retrofit2.Call;

public class GitConfigAuthenticationInfoHelperTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock EncryptionHelper encryptionHelper;
  @Mock SshKeySpecDTOHelper sshKeySpecDTOHelper;
  @Mock SecretCrudService secretCrudService;
  @Mock AccountClient accountClient;

  @InjectMocks private GitConfigAuthenticationInfoHelper gitConfigAuthenticationInfoHelper;

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testPrepareEntityDetailsForVarFiles() {
    GithubAuthenticationDTO githubAuthenticationDTO =
        GithubAuthenticationDTO.builder()
            .authType(HTTP)
            .credentials(
                GithubHttpCredentialsDTO.builder()
                    .type(GithubHttpAuthenticationType.USERNAME_AND_TOKEN)
                    .httpCredentialsSpec(
                        GithubAppDTO.builder()
                            .applicationId("app")
                            .installationId("install")
                            .privateKeyRef(
                                SecretRefData.builder().identifier("id").decryptedValue("key".toCharArray()).build())
                            .build())
                    .build())
            .build();
    GithubConnectorDTO connector = GithubConnectorDTO.builder()
                                       .url("url")
                                       .connectionType(GitConnectionType.REPO)
                                       .authentication(githubAuthenticationDTO)
                                       .build();
    doReturn(List.of(EncryptedDataDetail.builder().build()))
        .when(encryptionHelper)
        .getEncryptionDetail(any(), any(), any(), any());

    Call<RestResponse<Boolean>> requestCall = mock(Call.class);
    doReturn(requestCall).when(accountClient).isFeatureFlagEnabled(any(), any());
    try (MockedStatic<CGRestUtils> mockStatic = mockStatic(CGRestUtils.class)) {
      mockStatic.when(() -> CGRestUtils.getResponse(requestCall)).thenReturn(true);
      List<EncryptedDataDetail> encryptedDataDetails = gitConfigAuthenticationInfoHelper.getEncryptedDataDetails(
          connector, null, BaseNGAccess.builder().accountIdentifier("account").build());
      assertThat(encryptedDataDetails.size()).isEqualTo(1);
    }
  }
}
