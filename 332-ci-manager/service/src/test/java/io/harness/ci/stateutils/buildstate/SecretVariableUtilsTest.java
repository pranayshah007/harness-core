/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.stateutils.buildstate;

import static io.harness.rule.OwnerRule.ALEKSANDAR;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.ci.buildstate.SecretUtils;
import io.harness.ci.executionplan.CIExecutionTestBase;
import io.harness.delegate.beans.ci.pod.SecretVariableDTO;
import io.harness.delegate.beans.ci.pod.SecretVariableDetails;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.exception.ngexception.CIStageExecutionUserException;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.SecretType;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.secrets.remote.SecretNGManagerClient;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.yaml.core.variables.SecretNGVariable;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import retrofit2.Call;
import retrofit2.Response;

public class SecretVariableUtilsTest extends CIExecutionTestBase {
  @Mock private SecretNGManagerClient secretNGManagerClient;
  @Mock private SecretManagerClientService secretManagerClientService;
  @InjectMocks SecretUtils secretUtils;

  private NGAccess ngAccess;
  private static final String PROJ_ID = "projectId";
  private static final String ORG_ID = "orgId";
  private static final String ACCOUNT_ID = "accountId";
  private static final String TEXT_SECRET_ID = "textSecretId";
  private static final String FILE_SECRET_ID = "fileSecretId";

  private static final String TEXT_SECRET = "textSecretName";
  private static final String FILE_SECRET = "fileSecretName";

  private SecretNGVariable secretVariableText;
  private SecretNGVariable secretVariableFile;

  @Before
  public void setUp() {
    ngAccess =
        BaseNGAccess.builder().projectIdentifier(PROJ_ID).orgIdentifier(ORG_ID).accountIdentifier(ACCOUNT_ID).build();
    secretVariableText = SecretNGVariable.builder()
                             .name(TEXT_SECRET)
                             .value(ParameterField.createValueField(
                                 SecretRefData.builder().identifier(TEXT_SECRET_ID).scope(Scope.ACCOUNT).build()))
                             .build();
    secretVariableFile = SecretNGVariable.builder()
                             .name(FILE_SECRET)
                             .value(ParameterField.createValueField(
                                 SecretRefData.builder().identifier(FILE_SECRET_ID).scope(Scope.ORG).build()))
                             .build();
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldGetTextSecret() throws IOException {
    Call<ResponseDTO<SecretResponseWrapper>> getSecretCall = mock(Call.class);
    ResponseDTO<SecretResponseWrapper> responseDTO = ResponseDTO.newResponse(
        SecretResponseWrapper.builder().secret(SecretDTOV2.builder().type(SecretType.SecretText).build()).build());

    when(getSecretCall.execute()).thenReturn(Response.success(responseDTO));
    when(secretNGManagerClient.getSecret(eq(TEXT_SECRET_ID), eq(ACCOUNT_ID), eq(null), eq(null)))
        .thenReturn(getSecretCall);
    when(secretManagerClientService.getEncryptionDetails(any(), any()))
        .thenReturn(Collections.singletonList(EncryptedDataDetail.builder().build()));

    SecretVariableDetails secretVariableDetails = secretUtils.getSecretVariableDetails(ngAccess, secretVariableText);
    assertThat(secretVariableDetails)
        .isEqualTo(SecretVariableDetails.builder()
                       .secretVariableDTO(
                           SecretVariableDTO.builder()
                               .type(SecretVariableDTO.Type.TEXT)
                               .name(TEXT_SECRET)
                               .secret(SecretRefData.builder().identifier(TEXT_SECRET_ID).scope(Scope.ACCOUNT).build())
                               .build())
                       .encryptedDataDetailList(Collections.singletonList(EncryptedDataDetail.builder().build()))
                       .build());
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldGetFileSecret() throws IOException {
    Call<ResponseDTO<SecretResponseWrapper>> getSecretCall = mock(Call.class);
    ResponseDTO<SecretResponseWrapper> responseDTO = ResponseDTO.newResponse(
        SecretResponseWrapper.builder().secret(SecretDTOV2.builder().type(SecretType.SecretFile).build()).build());
    when(getSecretCall.execute()).thenReturn(Response.success(responseDTO));
    when(secretNGManagerClient.getSecret(eq(FILE_SECRET_ID), eq(ACCOUNT_ID), eq(ORG_ID), eq(null)))
        .thenReturn(getSecretCall);
    when(secretManagerClientService.getEncryptionDetails(any(), any()))
        .thenReturn(Collections.singletonList(EncryptedDataDetail.builder().build()));

    SecretVariableDetails secretVariableDetails = secretUtils.getSecretVariableDetails(ngAccess, secretVariableFile);

    assertThat(secretVariableDetails)
        .isEqualTo(SecretVariableDetails.builder()
                       .secretVariableDTO(
                           SecretVariableDTO.builder()
                               .type(SecretVariableDTO.Type.FILE)
                               .name(FILE_SECRET)
                               .secret(SecretRefData.builder().identifier(FILE_SECRET_ID).scope(Scope.ORG).build())
                               .build())
                       .encryptedDataDetailList(Collections.singletonList(EncryptedDataDetail.builder().build()))
                       .build());
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldThrowException() throws IOException {
    Call<ResponseDTO<SecretResponseWrapper>> getSecretCall = mock(Call.class);
    when(getSecretCall.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(null)))
        .thenThrow(new IOException());
    when(secretNGManagerClient.getSecret(eq(FILE_SECRET_ID), eq(ACCOUNT_ID), eq(ORG_ID), eq(null)))
        .thenReturn(getSecretCall);

    when(secretManagerClientService.getEncryptionDetails(any(), any()))
        .thenReturn(Collections.singletonList(EncryptedDataDetail.builder().build()));

    assertThatThrownBy(() -> secretUtils.getSecretVariableDetails(ngAccess, secretVariableFile))
        .isInstanceOf(CIStageExecutionUserException.class);
    assertThatThrownBy(() -> secretUtils.getSecretVariableDetails(ngAccess, secretVariableFile))
        .isInstanceOf(CIStageExecutionException.class);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void shouldCallGetSecret() throws IOException {
    Call<ResponseDTO<SecretResponseWrapper>> getSecretCall = mock(Call.class);
    ResponseDTO<SecretResponseWrapper> responseDTO = ResponseDTO.newResponse(
        SecretResponseWrapper.builder().secret(SecretDTOV2.builder().type(SecretType.SecretText).build()).build());

    when(getSecretCall.execute()).thenReturn(Response.success(responseDTO));
    when(secretNGManagerClient.getSecret(eq(TEXT_SECRET_ID), eq(ACCOUNT_ID), eq(null), eq(null)))
        .thenReturn(getSecretCall);
    when(secretManagerClientService.getEncryptionDetails(any(), any()))
        .thenReturn(Collections.singletonList(EncryptedDataDetail.builder().build()));

    SecretVariableDetails secretVariableDetails =
        secretUtils.getSecretVariableDetailsWithScope(ngAccess, secretVariableText);
    assertThat(secretVariableDetails)
        .isEqualTo(SecretVariableDetails.builder()
                       .secretVariableDTO(
                           SecretVariableDTO.builder()
                               .type(SecretVariableDTO.Type.TEXT)
                               .name("HARNESS_account_" + TEXT_SECRET)
                               .secret(SecretRefData.builder().identifier(TEXT_SECRET_ID).scope(Scope.ACCOUNT).build())
                               .build())
                       .encryptedDataDetailList(Collections.singletonList(EncryptedDataDetail.builder().build()))
                       .build());
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void shouldNotCallGetSecret() {
    when(secretManagerClientService.getEncryptionDetails(any(), any()))
        .thenReturn(Collections.singletonList(EncryptedDataDetail.builder().build()));

    List<String> secrets =
        Arrays.asList("hashicorpvault://LocalVault/foo/bar/mysecret#mykey", "awssecretsmanager://exampleAWS/example",
            "azurevault://exampleAzureKeyVault/example", "gcpsecretsmanager://exampleGCP/example");

    secrets.forEach(s -> {
      secretVariableText.setName(s);
      secretVariableText.setValue(
          ParameterField.createValueField(SecretRefData.builder().identifier(s).scope(Scope.ACCOUNT).build()));
      SecretVariableDetails secretVariableDetails =
          secretUtils.getSecretVariableDetailsWithScope(ngAccess, secretVariableText);
      assertThat(secretVariableDetails)
          .isEqualTo(
              SecretVariableDetails.builder()
                  .secretVariableDTO(SecretVariableDTO.builder()
                                         .type(SecretVariableDTO.Type.TEXT)
                                         .name("HARNESS_account_" + s)
                                         .secret(SecretRefData.builder().identifier(s).scope(Scope.ACCOUNT).build())
                                         .build())
                  .encryptedDataDetailList(Collections.singletonList(EncryptedDataDetail.builder().build()))
                  .build());
    });
  }
}
