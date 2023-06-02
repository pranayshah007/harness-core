/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ng.core.models.Secret.SecretKeys;
import static io.harness.rule.OwnerRule.MEENAKSHI;
import static io.harness.rule.OwnerRule.NISHANT;
import static io.harness.rule.OwnerRule.PHOENIKX;
import static io.harness.rule.OwnerRule.TEJAS;
import static io.harness.rule.OwnerRule.UJJAWAL;
import static io.harness.rule.OwnerRule.VIKAS_M;
import static io.harness.rule.OwnerRule.VLAD;

import static io.github.benas.randombeans.api.EnhancedRandom.random;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.services.NGConnectorSecretManagerService;
import io.harness.delegate.beans.FileUploadLimit;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.SecretManagementException;
import io.harness.ng.core.api.NGEncryptedDataService;
import io.harness.ng.core.api.NGSecretServiceV2;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.secrets.NTLMConfigDTO;
import io.harness.ng.core.dto.secrets.SSHAuthDTO;
import io.harness.ng.core.dto.secrets.SSHConfigDTO;
import io.harness.ng.core.dto.secrets.SSHCredentialSpecDTO;
import io.harness.ng.core.dto.secrets.SSHCredentialType;
import io.harness.ng.core.dto.secrets.SSHKeyPathCredentialDTO;
import io.harness.ng.core.dto.secrets.SSHKeyReferenceCredentialDTO;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.ng.core.dto.secrets.SSHPasswordCredentialDTO;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretFileSpecDTO;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.ng.core.dto.secrets.SecretTextSpecDTO;
import io.harness.ng.core.dto.secrets.WinRmAuthDTO;
import io.harness.ng.core.dto.secrets.WinRmCommandParameter;
import io.harness.ng.core.dto.secrets.WinRmCredentialsSpecDTO;
import io.harness.ng.core.entities.NGEncryptedData;
import io.harness.ng.core.models.Secret;
import io.harness.ng.core.models.SecretTextSpec;
import io.harness.ng.core.remote.SSHKeyValidationMetadata;
import io.harness.ng.core.remote.SecretValidationResultDTO;
import io.harness.ng.opa.entities.secret.OpaSecretService;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.SettingValueType;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngsettings.dto.SettingValueResponseDTO;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.SSHAuthScheme;
import io.harness.secretmanagerclient.SecretType;
import io.harness.secretmanagerclient.ValueType;
import io.harness.secretmanagerclient.dto.LocalConfigDTO;
import io.harness.secretmanagerclient.remote.SecretManagerClient;
import io.harness.utils.featureflaghelper.NGFeatureFlagHelperService;

import software.wings.settings.SettingVariableTypes;

import com.amazonaws.util.StringInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(PL)
public class SecretCrudServiceImplTest extends CategoryTest {
  @Mock(answer = Answers.RETURNS_DEEP_STUBS) private SecretManagerClient secretManagerClient;
  @Mock private NGSecretServiceV2 ngSecretServiceV2;
  private final FileUploadLimit fileUploadLimit = new FileUploadLimit();
  @Mock private SecretEntityReferenceHelper secretEntityReferenceHelper;
  @Mock private SecretCrudServiceImpl secretCrudServiceSpy;
  @Mock private SecretCrudServiceImpl secretCrudService;
  @Mock private Producer eventProducer;
  @Mock private NGEncryptedDataService encryptedDataService;
  @Mock private NGConnectorSecretManagerService connectorService;
  @Mock private AccessControlClient accessControlClient;
  @Mock private OpaSecretService opaSecretService;
  @Mock private NGSettingsClient settingsClient;
  @Mock private Call<ResponseDTO<SettingValueResponseDTO>> request;
  @Mock private NGFeatureFlagHelperService featureFlagHelperService;

  private String ORG_ID = randomAlphabetic(10);
  private String PROJECT_ID = randomAlphabetic(10);
  private String EMPTY_ID = "";
  private String accountIdentifier = randomAlphabetic(10);

  private String ACC_ID_CONSTANT = "accountIdentifier";

  @Before
  public void setup() throws IOException {
    initMocks(this);
    secretCrudServiceSpy = new SecretCrudServiceImpl(secretEntityReferenceHelper, fileUploadLimit, ngSecretServiceV2,
        eventProducer, encryptedDataService, connectorService, accessControlClient, opaSecretService, settingsClient,
        featureFlagHelperService);
    secretCrudService = spy(secretCrudServiceSpy);
    when(connectorService.getUsingIdentifier(any(), any(), any(), any(), eq(false))).thenReturn(new LocalConfigDTO());
    when(opaSecretService.evaluatePoliciesWithEntity(any(), any(), any(), any(), any(), any())).thenReturn(null);
    when(settingsClient.getSetting(
             SettingIdentifiers.DISABLE_HARNESS_BUILT_IN_SECRET_MANAGER, accountIdentifier, null, null))
        .thenReturn(request);
    SettingValueResponseDTO settingValueResponseDTO =
        SettingValueResponseDTO.builder().value("false").valueType(SettingValueType.BOOLEAN).build();
    when(request.execute()).thenReturn(Response.success(ResponseDTO.newResponse(settingValueResponseDTO)));
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testCreateSecret() {
    NGEncryptedData encryptedDataDTO = NGEncryptedData.builder().type(SettingVariableTypes.SECRET_TEXT).build();
    Secret secret = Secret.builder().build();
    when(encryptedDataService.createSecretText(any(), any())).thenReturn(encryptedDataDTO);
    when(ngSecretServiceV2.create(any(), any(), eq(false))).thenReturn(secret);

    when(connectorService.getUsingIdentifier(any(), any(), any(), any(), eq(false))).thenReturn(new LocalConfigDTO());

    SecretDTOV2 secretDTOV2 = SecretDTOV2.builder()
                                  .type(SecretType.SecretText)
                                  .spec(SecretTextSpecDTO.builder().valueType(ValueType.Inline).value("value").build())
                                  .build();
    SecretResponseWrapper responseWrapper = secretCrudService.create(accountIdentifier, secretDTOV2);
    assertThat(responseWrapper).isNotNull();

    verify(encryptedDataService).createSecretText(any(), any());
    verify(ngSecretServiceV2).create(any(), any(), eq(false));
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testCheckIfSecretManagerUsedIsHarnessManagedForSSHSecret() {
    SecretDTOV2 secretDTO = SecretDTOV2.builder()
                                .name(randomAlphabetic(10))
                                .identifier(randomAlphabetic(10))
                                .type(SecretType.SSHKey)
                                .spec(SSHKeySpecDTO.builder().auth(SSHAuthDTO.builder().build()).build())
                                .build();
    boolean response = secretCrudServiceSpy.checkIfSecretManagerUsedIsHarnessManaged(accountIdentifier, secretDTO);
    assertThat(response).isFalse();
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testCheckIfSecretManagerUsedIsHarnessManagedForWinRmSecret() {
    SecretDTOV2 secretDTO = SecretDTOV2.builder()
                                .name(randomAlphabetic(10))
                                .identifier(randomAlphabetic(10))
                                .type(SecretType.WinRmCredentials)
                                .spec(WinRmCredentialsSpecDTO.builder().auth(WinRmAuthDTO.builder().build()).build())
                                .build();
    boolean response = secretCrudServiceSpy.checkIfSecretManagerUsedIsHarnessManaged(accountIdentifier, secretDTO);
    assertThat(response).isFalse();
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testCreateSecretWhenDefaultSMIsDisabled() throws IOException {
    SettingValueResponseDTO settingValueResponseDTO =
        SettingValueResponseDTO.builder().value("true").valueType(SettingValueType.BOOLEAN).build();
    when(request.execute()).thenReturn(Response.success(ResponseDTO.newResponse(settingValueResponseDTO)));
    SecretDTOV2 secretDTOV2 = SecretDTOV2.builder()
                                  .type(SecretType.SecretText)
                                  .spec(SecretTextSpecDTO.builder()
                                            .secretManagerIdentifier("harnessSecretManager")
                                            .valueType(ValueType.Inline)
                                            .value("value")
                                            .build())
                                  .build();
    doReturn(true).when(secretCrudService).checkIfSecretManagerUsedIsHarnessManaged(accountIdentifier, secretDTOV2);
    assertThatThrownBy(() -> secretCrudService.create(accountIdentifier, secretDTOV2))
        .isInstanceOf(SecretManagementException.class);
  }
  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testCreateSecretWithSMOtherThanHarnessManagedSMWhichIsDisabled() {
    NGEncryptedData encryptedDataDTO = NGEncryptedData.builder().type(SettingVariableTypes.SECRET_TEXT).build();
    Secret secret = Secret.builder().build();
    when(encryptedDataService.createSecretText(any(), any())).thenReturn(encryptedDataDTO);
    when(ngSecretServiceV2.create(any(), any(), eq(false))).thenReturn(secret);
    SecretDTOV2 secretDTOV2 = SecretDTOV2.builder()
                                  .type(SecretType.SecretText)
                                  .spec(SecretTextSpecDTO.builder()
                                            .secretManagerIdentifier("harnessSecretManager")
                                            .valueType(ValueType.Inline)
                                            .value("value")
                                            .build())
                                  .build();
    doReturn(false).when(secretCrudService).checkIfSecretManagerUsedIsHarnessManaged(accountIdentifier, secretDTOV2);
    SecretResponseWrapper responseWrapper = secretCrudService.create(accountIdentifier, secretDTOV2);
    assertThat(responseWrapper).isNotNull();

    verify(encryptedDataService).createSecretText(any(), any());
    verify(ngSecretServiceV2).create(any(), any(), eq(false));
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testCreateSecretWithHarnessManagedSMWhichIsEnabled() {
    NGEncryptedData encryptedDataDTO = NGEncryptedData.builder().type(SettingVariableTypes.SECRET_TEXT).build();
    Secret secret = Secret.builder().build();
    when(encryptedDataService.createSecretText(any(), any())).thenReturn(encryptedDataDTO);
    when(ngSecretServiceV2.create(any(), any(), eq(false))).thenReturn(secret);
    SecretDTOV2 secretDTOV2 = SecretDTOV2.builder()
                                  .type(SecretType.SecretText)
                                  .spec(SecretTextSpecDTO.builder()
                                            .secretManagerIdentifier("harnessSecretManager")
                                            .valueType(ValueType.Inline)
                                            .value("value")
                                            .build())
                                  .build();
    doReturn(true).when(secretCrudService).checkIfSecretManagerUsedIsHarnessManaged(accountIdentifier, secretDTOV2);
    SecretResponseWrapper responseWrapper = secretCrudService.create(accountIdentifier, secretDTOV2);
    assertThat(responseWrapper).isNotNull();

    verify(encryptedDataService).createSecretText(any(), any());
    verify(ngSecretServiceV2).create(any(), any(), eq(false));
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testCreateSshWithExistingIdentifierShouldNotCreateSetupUsage() {
    String exMessage = "Duplicate identifier, please try again with a new identifier";
    when(ngSecretServiceV2.create(any(), any(), eq(false))).thenThrow(new DuplicateFieldException(exMessage));
    SecretDTOV2 secretDTOV2 = SecretDTOV2.builder()
                                  .type(SecretType.SSHKey)
                                  .spec(SSHKeySpecDTO.builder().auth(SSHAuthDTO.builder().build()).build())
                                  .identifier(randomAlphabetic(10))
                                  .name(randomAlphabetic(10))
                                  .build();
    assertThatThrownBy(() -> secretCrudService.create(accountIdentifier, secretDTOV2))
        .isInstanceOf(DuplicateFieldException.class)
        .hasMessage(exMessage);

    verify(ngSecretServiceV2).create(any(), any(), eq(false));
    verify(secretEntityReferenceHelper, times(0))
        .createSetupUsageForSecretManager(anyString(), any(), any(), anyString(), anyString(), any());
    verify(secretEntityReferenceHelper, times(0)).createSetupUsageForSecret(anyString(), any());
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testCreateViaYaml() {
    NGEncryptedData encryptedDataDTO = NGEncryptedData.builder().type(SettingVariableTypes.SECRET_TEXT).build();
    Secret secret = Secret.builder().build();
    when(encryptedDataService.createSecretText(any(), any())).thenReturn(encryptedDataDTO);
    when(ngSecretServiceV2.create(any(), any(), eq(true))).thenReturn(secret);

    SecretDTOV2 secretDTOV2 = SecretDTOV2.builder()
                                  .type(SecretType.SecretText)
                                  .spec(SecretTextSpecDTO.builder().valueType(ValueType.Inline).build())
                                  .build();
    SecretResponseWrapper responseWrapper = secretCrudService.createViaYaml(accountIdentifier, secretDTOV2);
    assertThat(responseWrapper).isNotNull();

    verify(encryptedDataService).createSecretText(any(), any());
    verify(ngSecretServiceV2).create(any(), any(), eq(true));
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testCreateSecretViaYaml_failDueToValueProvided() {
    SecretDTOV2 secretDTOV2 = SecretDTOV2.builder()
                                  .type(SecretType.SecretText)
                                  .spec(SecretTextSpecDTO.builder().valueType(ValueType.Inline).value("value").build())
                                  .build();
    try {
      secretCrudService.createViaYaml(accountIdentifier, secretDTOV2);
      fail("Execution should not reach here");
    } catch (InvalidRequestException invalidRequestException) {
      // not required
    }
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testUpdate() {
    NGEncryptedData encryptedDataDTO = NGEncryptedData.builder().type(SettingVariableTypes.CONFIG_FILE).build();
    SecretDTOV2 secretDTOV2 =
        SecretDTOV2.builder().type(SecretType.SecretText).spec(SecretTextSpecDTO.builder().build()).build();
    when(encryptedDataService.updateSecretText(any(), any())).thenReturn(encryptedDataDTO);
    when(ngSecretServiceV2.update(any(), any(), eq(false)))
        .thenReturn(Secret.builder()
                        .identifier("secret")
                        .accountIdentifier(accountIdentifier)
                        .identifier("identifier")
                        .build());
    doReturn(Optional.ofNullable(SecretResponseWrapper.builder().secret(secretDTOV2).build()))
        .when(secretCrudService)
        .get(any(), any(), any(), any());

    SecretResponseWrapper updatedSecret =
        secretCrudService.update(accountIdentifier, null, null, "identifier", secretDTOV2);

    ArgumentCaptor<Message> producerMessage = ArgumentCaptor.forClass(Message.class);
    try {
      verify(eventProducer, times(1)).send(producerMessage.capture());
    } catch (EventsFrameworkDownException e) {
      e.printStackTrace();
    }

    assertThat(updatedSecret).isNotNull();
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testCreateFile() throws IOException {
    SecretDTOV2 secretDTOV2 =
        SecretDTOV2.builder().spec(SecretFileSpecDTO.builder().build()).type(SecretType.SecretFile).build();
    Secret secret = Secret.builder().build();
    NGEncryptedData encryptedDataDTO = NGEncryptedData.builder().type(SettingVariableTypes.CONFIG_FILE).build();
    when(encryptedDataService.createSecretFile(any(), any(), any())).thenReturn(encryptedDataDTO);
    when(ngSecretServiceV2.create(any(), any(), eq(false))).thenReturn(secret);
    doNothing()
        .when(secretEntityReferenceHelper)
        .createSetupUsageForSecretManager(any(), any(), any(), any(), any(), any());
    when(opaSecretService.evaluatePoliciesWithEntity(any(), any(), any(), any(), any(), any())).thenReturn(null);

    SecretResponseWrapper created =
        secretCrudService.createFile(accountIdentifier, secretDTOV2, new StringInputStream("string"));
    assertThat(created).isNotNull();

    verify(encryptedDataService, atLeastOnce()).createSecretFile(any(), any(), any());
    verify(ngSecretServiceV2).create(any(), any(), eq(false));
    verify(secretEntityReferenceHelper).createSetupUsageForSecretManager(any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testSecretFileMigration_willCreateSecretInNgSecretsDB() {
    SecretDTOV2 secretDTOV2 =
        SecretDTOV2.builder().spec(SecretFileSpecDTO.builder().build()).type(SecretType.SecretFile).build();
    Secret secret = Secret.builder().build();
    NGEncryptedData encryptedDataDTO = NGEncryptedData.builder().type(SettingVariableTypes.CONFIG_FILE).build();
    when(encryptedDataService.createSecretFile(any(), any(), any(), any())).thenReturn(encryptedDataDTO);
    when(ngSecretServiceV2.create(any(), any(), eq(false))).thenReturn(secret);
    doNothing()
        .when(secretEntityReferenceHelper)
        .createSetupUsageForSecretManager(any(), any(), any(), any(), any(), any());
    when(opaSecretService.evaluatePoliciesWithEntity(any(), any(), any(), any(), any(), any())).thenReturn(null);

    SecretResponseWrapper created =
        secretCrudService.createFile(accountIdentifier, secretDTOV2, "encryptionKey", "encryptedValue");
    assertThat(created).isNotNull();

    verify(encryptedDataService, atLeastOnce()).createSecretFile(any(), any(), any(), any());
    verify(ngSecretServiceV2).create(any(), any(), eq(false));
    verify(secretEntityReferenceHelper).createSetupUsageForSecretManager(any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testUpdateFile_failDueToSecretManagerChangeNotAllowed() throws IOException {
    NGEncryptedData encryptedDataDTO = NGEncryptedData.builder()
                                           .type(SettingVariableTypes.CONFIG_FILE)
                                           .secretManagerIdentifier("secretManager1")
                                           .build();
    when(encryptedDataService.get(any(), any(), any(), any())).thenReturn(encryptedDataDTO);
    SecretDTOV2 secretDTOV2 = SecretDTOV2.builder()
                                  .type(SecretType.SecretFile)
                                  .spec(SecretFileSpecDTO.builder().secretManagerIdentifier("secretManager1").build())
                                  .build();
    SecretDTOV2 newSecretDTOV2 =
        SecretDTOV2.builder()
            .type(SecretType.SecretFile)
            .spec(SecretFileSpecDTO.builder().secretManagerIdentifier("secretManager2").build())
            .build();
    doReturn(Optional.ofNullable(SecretResponseWrapper.builder().secret(secretDTOV2).build()))
        .when(secretCrudService)
        .get(any(), any(), any(), any());

    try {
      secretCrudService.updateFile(
          accountIdentifier, null, null, "identifier", newSecretDTOV2, new StringInputStream("string"));
      fail("Execution should not reach here");
    } catch (InvalidRequestException invalidRequestException) {
      // not required
    }
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testUpdateFile() throws IOException {
    NGEncryptedData encryptedDataDTO = NGEncryptedData.builder()
                                           .type(SettingVariableTypes.CONFIG_FILE)
                                           .secretManagerIdentifier("secretManager1")
                                           .build();
    SecretDTOV2 secretDTOV2 = SecretDTOV2.builder()
                                  .type(SecretType.SecretFile)
                                  .spec(SecretFileSpecDTO.builder().secretManagerIdentifier("secretManager1").build())
                                  .build();
    when(encryptedDataService.updateSecretFile(any(), any(), any())).thenReturn(encryptedDataDTO);
    when(ngSecretServiceV2.update(any(), any(), eq(false)))
        .thenReturn(Secret.builder().identifier("secret").accountIdentifier(accountIdentifier).build());
    doReturn(Optional.ofNullable(SecretResponseWrapper.builder().secret(secretDTOV2).build()))
        .when(secretCrudService)
        .get(any(), any(), any(), any());

    SecretResponseWrapper updatedFile = secretCrudService.updateFile(
        accountIdentifier, null, null, "identifier", secretDTOV2, new StringInputStream("string"));

    ArgumentCaptor<Message> producerMessage = ArgumentCaptor.forClass(Message.class);
    try {
      verify(eventProducer, times(1)).send(producerMessage.capture());
    } catch (EventsFrameworkDownException e) {
      e.printStackTrace();
    }

    assertThat(updatedFile).isNotNull();
    verify(encryptedDataService, atLeastOnce()).updateSecretFile(any(), any(), any());
    verify(ngSecretServiceV2).update(any(), any(), eq(false));
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testUpdateFile_WithoutInputFile() {
    NGEncryptedData encryptedDataDTO = NGEncryptedData.builder()
                                           .type(SettingVariableTypes.CONFIG_FILE)
                                           .name("fileName")
                                           .secretManagerIdentifier("secretManager1")
                                           .build();
    SecretDTOV2 secretDTOV2 = SecretDTOV2.builder()
                                  .type(SecretType.SecretFile)
                                  .name("updatedFileName")
                                  .spec(SecretFileSpecDTO.builder().secretManagerIdentifier("secretManager1").build())
                                  .build();
    when(encryptedDataService.updateSecretFile(any(), any(), any())).thenReturn(encryptedDataDTO);
    when(ngSecretServiceV2.update(any(), any(), eq(false)))
        .thenReturn(
            Secret.builder().identifier("secret").accountIdentifier(accountIdentifier).name("updatedFileName").build());
    doReturn(Optional.ofNullable(SecretResponseWrapper.builder().secret(secretDTOV2).build()))
        .when(secretCrudService)
        .get(any(), any(), any(), any());

    SecretResponseWrapper updatedFile =
        secretCrudService.updateFile(accountIdentifier, null, null, "identifier", secretDTOV2, null);
    ArgumentCaptor<InputStream> inputStreamArgumentCaptor = ArgumentCaptor.forClass(InputStream.class);
    assertThat(updatedFile).isNotNull();
    assertThat(updatedFile.getSecret().getName()).isEqualTo("updatedFileName");
    verify(encryptedDataService, times(1)).updateSecretFile(any(), any(), inputStreamArgumentCaptor.capture());
    assertThat(inputStreamArgumentCaptor.getValue()).isEqualTo(null);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testUpdateFile_WithInputFile() throws IOException {
    NGEncryptedData encryptedDataDTO = NGEncryptedData.builder()
                                           .type(SettingVariableTypes.CONFIG_FILE)
                                           .name("fileName")
                                           .secretManagerIdentifier("secretManager1")
                                           .build();
    SecretDTOV2 secretDTOV2 = SecretDTOV2.builder()
                                  .type(SecretType.SecretFile)
                                  .name("updatedFileName")
                                  .spec(SecretFileSpecDTO.builder().secretManagerIdentifier("secretManager1").build())
                                  .build();
    when(encryptedDataService.updateSecretFile(any(), any(), any())).thenReturn(encryptedDataDTO);
    when(ngSecretServiceV2.update(any(), any(), eq(false)))
        .thenReturn(
            Secret.builder().identifier("secret").accountIdentifier(accountIdentifier).name("updatedFileName").build());
    doReturn(Optional.ofNullable(SecretResponseWrapper.builder().secret(secretDTOV2).build()))
        .when(secretCrudService)
        .get(any(), any(), any(), any());

    SecretResponseWrapper updatedFile = secretCrudService.updateFile(
        accountIdentifier, null, null, "identifier", secretDTOV2, new StringInputStream("input Stream is present"));
    ArgumentCaptor<InputStream> inputStreamArgumentCaptor = ArgumentCaptor.forClass(InputStream.class);
    assertThat(updatedFile).isNotNull();
    assertThat(updatedFile.getSecret().getName()).isEqualTo("updatedFileName");
    verify(encryptedDataService, times(1)).updateSecretFile(any(), any(), inputStreamArgumentCaptor.capture());
    assertThat(inputStreamArgumentCaptor.getValue()).isNotNull();
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testValidateSecret() {
    SecretValidationResultDTO secretValidationResultDTO = SecretValidationResultDTO.builder().success(true).build();
    when(ngSecretServiceV2.validateSecret(any(), any(), any(), any(), any())).thenReturn(secretValidationResultDTO);
    SecretValidationResultDTO resultDTO = secretCrudService.validateSecret(
        accountIdentifier, "org", "project", "identifier", SSHKeyValidationMetadata.builder().host("host").build());
    assertThat(resultDTO).isNotNull();
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testGet() {
    when(ngSecretServiceV2.get(any(), any(), any(), any())).thenReturn(Optional.ofNullable(Secret.builder().build()));
    Optional<SecretResponseWrapper> secretResponseWrapper =
        secretCrudService.get(accountIdentifier, null, null, "identifier");
    assertThat(secretResponseWrapper).isPresent();
    verify(ngSecretServiceV2).get(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testGetForSecretRef() {
    String secretRef = "SOME/PATH#value";
    when(ngSecretServiceV2.get(any(), any(), any(), any()))
        .thenReturn(Optional.ofNullable(Secret.builder()
                                            .accountIdentifier(accountIdentifier)
                                            .identifier("identifier")
                                            .type(SecretType.SecretText)
                                            .secretSpec(SecretTextSpec.builder().valueType(ValueType.Reference).build())
                                            .build()));
    when(encryptedDataService.get(accountIdentifier, null, null, "identifier"))
        .thenReturn(NGEncryptedData.builder().path(secretRef).build());
    Optional<SecretResponseWrapper> secretResponseWrapper =
        secretCrudService.get(accountIdentifier, null, null, "identifier");
    assertThat(secretResponseWrapper).isPresent();
    assertThat(secretResponseWrapper.get().getSecret().getSpec()).isInstanceOf(SecretTextSpecDTO.class);
    SecretTextSpecDTO secretSpec = (SecretTextSpecDTO) secretResponseWrapper.get().getSecret().getSpec();
    assertThat(secretSpec.getValue()).isEqualTo(secretRef);
    verify(ngSecretServiceV2).get(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testDelete() {
    NGEncryptedData encryptedDataDTO = random(NGEncryptedData.class);
    when(encryptedDataService.get(any(), any(), any(), any())).thenReturn(encryptedDataDTO);
    when(encryptedDataService.delete(any(), any(), any(), any(), eq(false))).thenReturn(true);
    when(ngSecretServiceV2.delete(any(), any(), any(), any(), eq(false))).thenReturn(true);
    doNothing().when(secretEntityReferenceHelper).deleteExistingSetupUsage(any(), any(), any(), any());
    doNothing().when(secretEntityReferenceHelper).validateSecretIsNotUsedByOthers(any(), any(), any(), any());
    when(ngSecretServiceV2.get(any(), any(), any(), any()))
        .thenReturn(Optional.of(
            Secret.builder().type(SecretType.SecretText).secretSpec(SecretTextSpec.builder().build()).build()));
    boolean success = secretCrudService.delete(accountIdentifier, null, null, "identifier", false);

    assertThat(success).isTrue();
    verify(encryptedDataService, atLeastOnce()).get(any(), any(), any(), any());
    verify(encryptedDataService, atLeastOnce()).delete(any(), any(), any(), any(), eq(false));
    verify(ngSecretServiceV2, atLeastOnce()).delete(any(), any(), any(), any(), eq(false));
    verify(secretEntityReferenceHelper, atLeastOnce()).deleteExistingSetupUsage(any(), any(), any(), any());
  }
  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testDelete_withForceDeleteTrue_forceDeleteEnabled() {
    doReturn(true).when(secretCrudService).isForceDeleteFFEnabled(accountIdentifier);
    doReturn(true).when(secretCrudService).isNgSettingsFFEnabled(accountIdentifier);
    doReturn(true).when(secretCrudService).isForceDeleteFFEnabledViaSettings(accountIdentifier);
    NGEncryptedData encryptedDataDTO = random(NGEncryptedData.class);
    when(encryptedDataService.get(any(), any(), any(), any())).thenReturn(encryptedDataDTO);
    when(encryptedDataService.delete(any(), any(), any(), any(), eq(true))).thenReturn(true);
    when(ngSecretServiceV2.delete(any(), any(), any(), any(), eq(true))).thenReturn(true);

    doNothing()
        .when(secretEntityReferenceHelper)
        .deleteSecretEntityReferenceWhenSecretGetsDeleted(any(), any(), any(), any(), any());
    doNothing().when(secretEntityReferenceHelper).validateSecretIsNotUsedByOthers(any(), any(), any(), any());
    when(ngSecretServiceV2.get(any(), any(), any(), any()))
        .thenReturn(Optional.of(
            Secret.builder().type(SecretType.SecretText).secretSpec(SecretTextSpec.builder().build()).build()));
    boolean success = secretCrudService.delete(accountIdentifier, null, null, "identifier", true);
    assertThat(success).isTrue();
    verify(encryptedDataService, atLeastOnce()).get(any(), any(), any(), any());
    verify(encryptedDataService, atLeastOnce()).delete(any(), any(), any(), any(), eq(true));
    verify(ngSecretServiceV2, atLeastOnce()).delete(any(), any(), any(), any(), eq(true));
    verify(secretEntityReferenceHelper, times(0)).validateSecretIsNotUsedByOthers(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testDelete_withForceDeleteTrue_forceDeleteFFOFF_settingFFOFF() {
    doReturn(false).when(secretCrudService).isForceDeleteFFEnabled(ACC_ID_CONSTANT);
    doReturn(false).when(secretCrudService).isNgSettingsFFEnabled(ACC_ID_CONSTANT);
    try {
      secretCrudService.delete(ACC_ID_CONSTANT, null, null, "identifier", true);
    } catch (InvalidRequestException e) {
      assertThat(e.getMessage())
          .isEqualTo(
              "Parameter forcedDelete cannot be true. Force deletion of secret is not enabled for this account [accountIdentifier]");
    }
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testDelete_withForceDeleteTrue_forceDeleteFFON_settingFFOFF_settingsEnabled() {
    doReturn(true).when(secretCrudService).isForceDeleteFFEnabled(ACC_ID_CONSTANT);
    doReturn(false).when(secretCrudService).isNgSettingsFFEnabled(ACC_ID_CONSTANT);
    doReturn(true).when(secretCrudService).isForceDeleteFFEnabledViaSettings(ACC_ID_CONSTANT);
    try {
      secretCrudService.delete(ACC_ID_CONSTANT, null, null, "identifier", true);
    } catch (InvalidRequestException e) {
      assertThat(e.getMessage())
          .isEqualTo(
              "Parameter forcedDelete cannot be true. Force deletion of secret is not enabled for this account [accountIdentifier]");
    }
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testDelete_withForceDeleteTrue_forceDeleteFFON_settingFFON_settingDisabled() {
    doReturn(true).when(secretCrudService).isForceDeleteFFEnabled(ACC_ID_CONSTANT);
    doReturn(true).when(secretCrudService).isNgSettingsFFEnabled(ACC_ID_CONSTANT);
    doReturn(false).when(secretCrudService).isForceDeleteFFEnabledViaSettings(ACC_ID_CONSTANT);
    try {
      secretCrudService.delete(ACC_ID_CONSTANT, null, null, "identifier", true);
    } catch (InvalidRequestException e) {
      assertThat(e.getMessage())
          .isEqualTo(
              "Parameter forcedDelete cannot be true. Force deletion of secret is not enabled for this account [accountIdentifier]");
    }
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testDelete_withForceDeleteTrue_forceDeleteFFOFF_settingFFON_settingsDisabled() {
    doReturn(false).when(secretCrudService).isForceDeleteFFEnabled(ACC_ID_CONSTANT);
    doReturn(true).when(secretCrudService).isNgSettingsFFEnabled(ACC_ID_CONSTANT);
    doReturn(false).when(secretCrudService).isForceDeleteFFEnabledViaSettings(ACC_ID_CONSTANT);
    try {
      secretCrudService.delete(ACC_ID_CONSTANT, null, null, "identifier", true);
    } catch (InvalidRequestException e) {
      assertThat(e.getMessage())
          .isEqualTo(
              "Parameter forcedDelete cannot be true. Force deletion of secret is not enabled for this account [accountIdentifier]");
    }
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testDelete_withForceDeleteTrue_forceDeleteFFOFF_settingFFON_settingsEnabled() {
    doReturn(false).when(secretCrudService).isForceDeleteFFEnabled(ACC_ID_CONSTANT);
    doReturn(true).when(secretCrudService).isNgSettingsFFEnabled(ACC_ID_CONSTANT);
    doReturn(true).when(secretCrudService).isForceDeleteFFEnabledViaSettings(ACC_ID_CONSTANT);
    try {
      secretCrudService.delete(ACC_ID_CONSTANT, null, null, "identifier", true);
    } catch (InvalidRequestException e) {
      assertThat(e.getMessage())
          .isEqualTo(
              "Parameter forcedDelete cannot be true. Force deletion of secret is not enabled for this account [accountIdentifier]");
    }
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testDeleteBatch() {
    List<String> secretIdentifiers = new ArrayList<>();
    secretIdentifiers.add("identifier1");
    secretIdentifiers.add("identifier2");
    when(ngSecretServiceV2.delete(any(), any(), any(), any(), eq(false))).thenReturn(true);
    doNothing().when(secretEntityReferenceHelper).deleteExistingSetupUsage(any(), any(), any(), any());
    when(ngSecretServiceV2.get(any(), any(), any(), any()))
        .thenReturn(Optional.of(
            Secret.builder().type(SecretType.SecretText).secretSpec(SecretTextSpec.builder().build()).build()));
    secretCrudService.deleteBatch(accountIdentifier, "orgId", "projectId", secretIdentifiers);
    verify(encryptedDataService, times(2)).hardDelete(any(), any(), any(), any());
    verify(ngSecretServiceV2, times(2)).get(any(), any(), any(), any());
    verify(secretEntityReferenceHelper, times(2)).deleteExistingSetupUsage(any(), any(), any(), any());
  }

  @Test(expected = EntityNotFoundException.class)
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testDeleteInvalidIdentifier() {
    when(ngSecretServiceV2.get(any(), any(), any(), any())).thenReturn(Optional.empty());
    secretCrudService.delete(accountIdentifier, null, null, "identifier", false);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testSecretMasking() {
    SecretTextSpecDTO secretTextSpecDTO = SecretTextSpecDTO.builder().value("value").build();
    SecretDTOV2 secretDTOV2 =
        SecretDTOV2.builder().name("name").identifier("id").type(SecretType.SecretText).spec(secretTextSpecDTO).build();
    SecretDTOV2 response = secretCrudService.getMaskedDTOForOpa(secretDTOV2);
    assertThat(((SecretTextSpecDTO) response.getSpec()).getValue()).isNull();
    assertThat(((SecretTextSpecDTO) secretDTOV2.getSpec()).getValue()).isNotNull();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testSecretMasking2() {
    SSHCredentialSpecDTO sshCredentialSpecDTO =
        SSHKeyPathCredentialDTO.builder()
            .userName("userName")
            .keyPath("keyPath")
            .encryptedPassphrase(SecretRefData.builder().decryptedValue("val".toCharArray()).build())
            .build();
    SSHConfigDTO sshConfigDTO =
        SSHConfigDTO.builder().credentialType(SSHCredentialType.KeyPath).spec(sshCredentialSpecDTO).build();
    SSHAuthDTO sshAuthDTO = SSHAuthDTO.builder().type(SSHAuthScheme.SSH).spec(sshConfigDTO).build();
    SSHKeySpecDTO secretTextSpecDTO = SSHKeySpecDTO.builder().auth(sshAuthDTO).build();
    SecretDTOV2 secretDTOV2 =
        SecretDTOV2.builder().name("name").identifier("id").type(SecretType.SSHKey).spec(secretTextSpecDTO).build();

    SecretDTOV2 response = secretCrudService.getMaskedDTOForOpa(secretDTOV2);

    assertThat(response).isNotNull();
    SSHCredentialSpecDTO initialSshCredentialSpecDTO =
        ((SSHConfigDTO) ((SSHKeySpecDTO) secretDTOV2.getSpec()).getAuth().getSpec()).getSpec();
    assertThat(initialSshCredentialSpecDTO).isNotNull();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testSecretMasking3() {
    SSHKeyReferenceCredentialDTO sshKeyReferenceCredentialDTO =
        SSHKeyReferenceCredentialDTO.builder()
            .userName("userName")
            .key(SecretRefData.builder().decryptedValue("key".toCharArray()).build())
            .encryptedPassphrase(SecretRefData.builder().decryptedValue("val".toCharArray()).build())
            .build();
    SSHConfigDTO sshConfigDTO = SSHConfigDTO.builder()
                                    .credentialType(SSHCredentialType.KeyReference)
                                    .spec(sshKeyReferenceCredentialDTO)
                                    .build();
    SSHAuthDTO sshAuthDTO = SSHAuthDTO.builder().type(SSHAuthScheme.SSH).spec(sshConfigDTO).build();
    SSHKeySpecDTO secretTextSpecDTO = SSHKeySpecDTO.builder().auth(sshAuthDTO).build();
    SecretDTOV2 secretDTOV2 =
        SecretDTOV2.builder().name("name").identifier("id").type(SecretType.SSHKey).spec(secretTextSpecDTO).build();

    SecretDTOV2 response = secretCrudService.getMaskedDTOForOpa(secretDTOV2);

    assertThat(response).isNotNull();
    assertThat(
        ((SSHKeyReferenceCredentialDTO) ((SSHConfigDTO) ((SSHKeySpecDTO) secretDTOV2.getSpec()).getAuth().getSpec())
                .getSpec())
            .getEncryptedPassphrase())
        .isNotNull();

    assertThat(((SSHKeyReferenceCredentialDTO) ((SSHConfigDTO) ((SSHKeySpecDTO) response.getSpec()).getAuth().getSpec())
                       .getSpec())
                   .getEncryptedPassphrase())
        .isNull();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testSecretMasking4() {
    SSHPasswordCredentialDTO sshPasswordCredentialDTO =
        SSHPasswordCredentialDTO.builder()
            .userName("user-name")
            .password(SecretRefData.builder().decryptedValue("val".toCharArray()).build())
            .build();
    SSHConfigDTO sshConfigDTO =
        SSHConfigDTO.builder().credentialType(SSHCredentialType.Password).spec(sshPasswordCredentialDTO).build();
    SSHAuthDTO sshAuthDTO = SSHAuthDTO.builder().type(SSHAuthScheme.SSH).spec(sshConfigDTO).build();
    SSHKeySpecDTO secretTextSpecDTO = SSHKeySpecDTO.builder().auth(sshAuthDTO).build();
    SecretDTOV2 secretDTOV2 =
        SecretDTOV2.builder().name("name").identifier("id").type(SecretType.SSHKey).spec(secretTextSpecDTO).build();

    SecretDTOV2 response = secretCrudService.getMaskedDTOForOpa(secretDTOV2);

    assertThat(response).isNotNull();
    SSHPasswordCredentialDTO initialSSHPasswordCredentialDTO =
        (SSHPasswordCredentialDTO) ((SSHConfigDTO) ((SSHKeySpecDTO) secretDTOV2.getSpec()).getAuth().getSpec())
            .getSpec();
    assertThat(initialSSHPasswordCredentialDTO.getPassword()).isNotNull();

    SSHPasswordCredentialDTO finalSSHPasswordCredentialDTO =
        (SSHPasswordCredentialDTO) ((SSHConfigDTO) ((SSHKeySpecDTO) response.getSpec()).getAuth().getSpec()).getSpec();
    assertThat(finalSSHPasswordCredentialDTO.getPassword()).isNull();
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testAddCriteriaForRequestedScopes_currentScope_Account() {
    verifyCurrentScopeCriteria(EMPTY_ID, EMPTY_ID);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testAddCriteriaForRequestedScopes_currentScope_Org() {
    verifyCurrentScopeCriteria(ORG_ID, EMPTY_ID);
  }
  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testAddCriteriaForRequestedScopes_currentScope_Project() {
    verifyCurrentScopeCriteria(ORG_ID, PROJECT_ID);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testAddCriteriaForRequestedScopes_subScopesForAccount() {
    Criteria expectedCriteria = new Criteria();
    Criteria resultCriteria = new Criteria();
    expectedCriteria.andOperator(new Criteria());
    verifyForSuperSubScope(expectedCriteria, resultCriteria, EMPTY_ID, EMPTY_ID, false, true);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testAddCriteriaForRequestedScopes_subScopesForOrg() {
    Criteria expectedCriteria = new Criteria();
    Criteria resultCriteria = new Criteria();
    expectedCriteria.andOperator(Criteria.where(SecretKeys.orgIdentifier).is(ORG_ID));
    verifyForSuperSubScope(expectedCriteria, resultCriteria, ORG_ID, EMPTY_ID, false, true);
  }
  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testAddCriteriaForRequestedScopes_subScopesForProject() {
    Criteria expectedCriteria = new Criteria();
    Criteria resultCriteria = new Criteria();
    expectedCriteria.andOperator(
        Criteria.where(SecretKeys.orgIdentifier).is(ORG_ID).and(SecretKeys.projectIdentifier).is(PROJECT_ID));
    verifyForSuperSubScope(expectedCriteria, resultCriteria, ORG_ID, PROJECT_ID, false, true);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testAddCriteriaForRequestedScopes_superScopesForAccount() {
    Criteria expectedCriteria = new Criteria();
    Criteria resultCriteria = new Criteria();
    expectedCriteria.andOperator(new Criteria().orOperator(
        Criteria.where(SecretKeys.orgIdentifier).is(null).and(SecretKeys.projectIdentifier).is(null)));
    verifyForSuperSubScope(expectedCriteria, resultCriteria, EMPTY_ID, EMPTY_ID, true, false);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testAddCriteriaForRequestedScopes_superScopesForOrg() {
    Criteria expectedCriteria = new Criteria();
    Criteria resultCriteria = new Criteria();
    expectedCriteria.andOperator(new Criteria().orOperator(
        Criteria.where(SecretKeys.orgIdentifier).is(ORG_ID).and(SecretKeys.projectIdentifier).is(null),
        Criteria.where(SecretKeys.orgIdentifier).is(null).and(SecretKeys.projectIdentifier).is(null)));
    verifyForSuperSubScope(expectedCriteria, resultCriteria, ORG_ID, EMPTY_ID, true, false);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testAddCriteriaForRequestedScopes_superScopesForProject() {
    Criteria expectedCriteria = new Criteria();
    Criteria resultCriteria = new Criteria();
    expectedCriteria.andOperator(new Criteria().orOperator(
        Criteria.where(SecretKeys.orgIdentifier).is(ORG_ID).and(SecretKeys.projectIdentifier).is(PROJECT_ID),
        Criteria.where(SecretKeys.orgIdentifier).is(ORG_ID).and(SecretKeys.projectIdentifier).is(null),
        Criteria.where(SecretKeys.orgIdentifier).is(null).and(SecretKeys.projectIdentifier).is(null)));
    verifyForSuperSubScope(expectedCriteria, resultCriteria, ORG_ID, PROJECT_ID, true, false);
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testValidateSshWinRmSecretRef_WinRm_NTLM_AccountScope() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    SecretRefData password = SecretRefData.builder().identifier(identifier).scope(Scope.ACCOUNT).build();
    SecretDTOV2 secretDTO =
        SecretDTOV2.builder()
            .spec(WinRmCredentialsSpecDTO.builder()
                      .auth(WinRmAuthDTO.builder().spec(NTLMConfigDTO.builder().password(password).build()).build())
                      .build())
            .build();
    when(ngSecretServiceV2.get(any(), any(), any(), any())).thenReturn(Optional.of(Secret.builder().build()));
    secretCrudService.validateSshWinRmSecretRef(accountIdentifier, orgIdentifier, projectIdentifier, secretDTO);
    verify(ngSecretServiceV2, times(1)).get(accountIdentifier, null, null, identifier);
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testValidateSshWinRmSecretRef_WinRm_NTLM_OrgScope() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    SecretRefData password = SecretRefData.builder().identifier(identifier).scope(Scope.ORG).build();
    SecretDTOV2 secretDTO =
        SecretDTOV2.builder()
            .spec(WinRmCredentialsSpecDTO.builder()
                      .auth(WinRmAuthDTO.builder().spec(NTLMConfigDTO.builder().password(password).build()).build())
                      .build())
            .build();
    when(ngSecretServiceV2.get(any(), any(), any(), any())).thenReturn(Optional.of(Secret.builder().build()));
    secretCrudService.validateSshWinRmSecretRef(accountIdentifier, orgIdentifier, projectIdentifier, secretDTO);
    verify(ngSecretServiceV2, times(1)).get(accountIdentifier, orgIdentifier, null, identifier);
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testValidateSshWinRmSecretRef_WinRm_NTLM_ProjectScope() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    SecretRefData password = SecretRefData.builder().identifier(identifier).scope(Scope.PROJECT).build();
    SecretDTOV2 secretDTO =
        SecretDTOV2.builder()
            .spec(WinRmCredentialsSpecDTO.builder()
                      .auth(WinRmAuthDTO.builder().spec(NTLMConfigDTO.builder().password(password).build()).build())
                      .build())
            .build();
    when(ngSecretServiceV2.get(any(), any(), any(), any())).thenReturn(Optional.of(Secret.builder().build()));
    secretCrudService.validateSshWinRmSecretRef(accountIdentifier, orgIdentifier, projectIdentifier, secretDTO);
    verify(ngSecretServiceV2, times(1)).get(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldPassSecretDtoValidation() {
    List<WinRmCommandParameter> parameters =
        Arrays.asList(WinRmCommandParameter.builder().parameter("name").value("value").build());
    SecretDTOV2 secretDTOV2 = SecretDTOV2.builder()
                                  .type(SecretType.WinRmCredentials)
                                  .spec(WinRmCredentialsSpecDTO.builder().parameters(parameters).build())
                                  .build();
    assertThatCode(() -> secretCrudService.validateSecretDtoSpec(secretDTOV2)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldPassSecretDtoValidationEmptyParameters() {
    List<WinRmCommandParameter> parameters = Arrays.asList(WinRmCommandParameter.builder().build());
    SecretDTOV2 secretDTOV2 = SecretDTOV2.builder()
                                  .type(SecretType.WinRmCredentials)
                                  .spec(WinRmCredentialsSpecDTO.builder().parameters(parameters).build())
                                  .build();
    assertThatCode(() -> secretCrudService.validateSecretDtoSpec(secretDTOV2)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldFailSecretDtoValidation() {
    List<WinRmCommandParameter> parameters =
        Arrays.asList(WinRmCommandParameter.builder().parameter("name1").value("value1").build(),
            WinRmCommandParameter.builder().parameter("name1").value("value2").build(),
            WinRmCommandParameter.builder().parameter("name2").value("value1").build(),
            WinRmCommandParameter.builder().parameter("name2").value("value2").build(),
            WinRmCommandParameter.builder().parameter("name3").value("value1").build(),
            WinRmCommandParameter.builder().parameter("name3").value("value2").build());
    SecretDTOV2 secretDTOV2 = SecretDTOV2.builder()
                                  .type(SecretType.WinRmCredentials)
                                  .spec(WinRmCredentialsSpecDTO.builder().parameters(parameters).build())
                                  .build();

    assertThatThrownBy(() -> secretCrudService.validateSecretDtoSpec(secretDTOV2))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Command parameter names must be unique, however duplicate(s) found: name1, name2, name3");
  }

  private void verifyForSuperSubScope(Criteria expectedCriteria, Criteria resultCriteria, String orgId,
      String projectId, boolean includeAllSecretsAccessibleAtScope, boolean includeSecretsFromEverySubScope) {
    secretCrudService.addCriteriaForRequestedScopes(
        resultCriteria, orgId, projectId, includeAllSecretsAccessibleAtScope, includeSecretsFromEverySubScope);
    assertThat(resultCriteria).isNotNull();
    assertThat(criteriaToStringQuery(resultCriteria)).isEqualTo(criteriaToStringQuery(expectedCriteria));
  }

  private void verifyCurrentScopeCriteria(String orgId, String projectId) {
    Criteria expectedCriteria = new Criteria();
    Criteria resultCriteria = new Criteria();
    expectedCriteria.and(SecretKeys.orgIdentifier).is(orgId).and(SecretKeys.projectIdentifier).is(projectId);
    secretCrudService.addCriteriaForRequestedScopes(resultCriteria, orgId, projectId, false, false);
    assertThat(resultCriteria).isNotNull();
    assertThat(resultCriteria).isEqualTo(expectedCriteria);
  }
  private String criteriaToStringQuery(Criteria criteria) {
    return new Query(criteria).toString();
  }
}