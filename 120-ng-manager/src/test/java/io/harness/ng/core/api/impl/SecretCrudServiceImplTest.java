/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.MEENAKSHI;
import static io.harness.rule.OwnerRule.NISHANT;
import static io.harness.rule.OwnerRule.PHOENIKX;
import static io.harness.rule.OwnerRule.VIKAS_M;

import static io.github.benas.randombeans.api.EnhancedRandom.random;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.services.NGConnectorSecretManagerService;
import io.harness.delegate.beans.FileUploadLimit;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.accountsetting.dto.AccountSettingType;
import io.harness.ng.core.accountsetting.services.NGAccountSettingService;
import io.harness.ng.core.api.NGEncryptedDataService;
import io.harness.ng.core.api.NGSecretServiceV2;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretFileSpecDTO;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.ng.core.dto.secrets.SecretTextSpecDTO;
import io.harness.ng.core.entities.NGEncryptedData;
import io.harness.ng.core.models.Secret;
import io.harness.ng.core.models.SecretTextSpec;
import io.harness.ng.core.remote.SSHKeyValidationMetadata;
import io.harness.ng.core.remote.SecretValidationResultDTO;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.SecretType;
import io.harness.secretmanagerclient.ValueType;
import io.harness.secretmanagerclient.remote.SecretManagerClient;

import software.wings.settings.SettingVariableTypes;

import com.amazonaws.util.StringInputStream;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

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
  @Mock private NGAccountSettingService accountSettingService;
  @Mock private NGConnectorSecretManagerService connectorService;

  @Before
  public void setup() {
    initMocks(this);
    secretCrudServiceSpy = new SecretCrudServiceImpl(secretEntityReferenceHelper, fileUploadLimit, ngSecretServiceV2,
        eventProducer, encryptedDataService, accountSettingService, connectorService);
    secretCrudService = spy(secretCrudServiceSpy);
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testCreateSecret() {
    NGEncryptedData encryptedDataDTO = NGEncryptedData.builder().type(SettingVariableTypes.SECRET_TEXT).build();
    Secret secret = Secret.builder().build();
    when(encryptedDataService.createSecretText(any(), any())).thenReturn(encryptedDataDTO);
    when(ngSecretServiceV2.create(any(), any(), eq(false))).thenReturn(secret);

    SecretDTOV2 secretDTOV2 = SecretDTOV2.builder()
                                  .type(SecretType.SecretText)
                                  .spec(SecretTextSpecDTO.builder().valueType(ValueType.Inline).value("value").build())
                                  .build();
    SecretResponseWrapper responseWrapper = secretCrudService.create("account", secretDTOV2);
    assertThat(responseWrapper).isNotNull();

    verify(encryptedDataService).createSecretText(any(), any());
    verify(ngSecretServiceV2).create(any(), any(), eq(false));
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testCreateSecretWhenDefaultSMIsDisabled() {
    SecretDTOV2 secretDTOV2 = SecretDTOV2.builder()
                                  .type(SecretType.SecretText)
                                  .spec(SecretTextSpecDTO.builder()
                                            .secretManagerIdentifier("harnessSecretManager")
                                            .valueType(ValueType.Inline)
                                            .value("value")
                                            .build())
                                  .build();
    doReturn(true)
        .when(accountSettingService)
        .getIsBuiltInSMDisabled("accountId", null, null, AccountSettingType.CONNECTOR);
    doReturn(true).when(secretCrudService).checkIfSecretManagerUsedIsHarnessManaged("accountId", secretDTOV2);
    assertThatThrownBy(() -> secretCrudService.create("accountId", secretDTOV2))
        .isInstanceOf(InvalidRequestException.class);
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
    doReturn(true)
        .when(accountSettingService)
        .getIsBuiltInSMDisabled("accountId", null, null, AccountSettingType.CONNECTOR);
    doReturn(false).when(secretCrudService).checkIfSecretManagerUsedIsHarnessManaged("accountId", secretDTOV2);
    SecretResponseWrapper responseWrapper = secretCrudService.create("accountId", secretDTOV2);
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
    doReturn(false)
        .when(accountSettingService)
        .getIsBuiltInSMDisabled("accountId", null, null, AccountSettingType.CONNECTOR);
    doReturn(true).when(secretCrudService).checkIfSecretManagerUsedIsHarnessManaged("accountId", secretDTOV2);
    SecretResponseWrapper responseWrapper = secretCrudService.create("accountId", secretDTOV2);
    assertThat(responseWrapper).isNotNull();

    verify(encryptedDataService).createSecretText(any(), any());
    verify(ngSecretServiceV2).create(any(), any(), eq(false));
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
    SecretResponseWrapper responseWrapper = secretCrudService.createViaYaml("account", secretDTOV2);
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
      secretCrudService.createViaYaml("account", secretDTOV2);
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
        .thenReturn(
            Secret.builder().identifier("secret").accountIdentifier("account").identifier("identifier").build());
    doReturn(Optional.ofNullable(SecretResponseWrapper.builder().secret(secretDTOV2).build()))
        .when(secretCrudService)
        .get(any(), any(), any(), any());

    SecretResponseWrapper updatedSecret = secretCrudService.update("account", null, null, "identifier", secretDTOV2);

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
    SecretDTOV2 secretDTOV2 = SecretDTOV2.builder().spec(SecretFileSpecDTO.builder().build()).build();
    Secret secret = Secret.builder().build();
    NGEncryptedData encryptedDataDTO = NGEncryptedData.builder().type(SettingVariableTypes.CONFIG_FILE).build();
    when(encryptedDataService.createSecretFile(any(), any(), any())).thenReturn(encryptedDataDTO);
    when(ngSecretServiceV2.create(any(), any(), eq(false))).thenReturn(secret);
    doNothing()
        .when(secretEntityReferenceHelper)
        .createSetupUsageForSecretManager(any(), any(), any(), any(), any(), any());

    SecretResponseWrapper created =
        secretCrudService.createFile("account", secretDTOV2, new StringInputStream("string"));
    assertThat(created).isNotNull();

    verify(encryptedDataService, atLeastOnce()).createSecretFile(any(), any(), any());
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
          "account", null, null, "identifier", newSecretDTOV2, new StringInputStream("string"));
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
        .thenReturn(Secret.builder().identifier("secret").accountIdentifier("account").build());
    doReturn(Optional.ofNullable(SecretResponseWrapper.builder().secret(secretDTOV2).build()))
        .when(secretCrudService)
        .get(any(), any(), any(), any());

    SecretResponseWrapper updatedFile =
        secretCrudService.updateFile("account", null, null, "identifier", secretDTOV2, new StringInputStream("string"));

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
        .thenReturn(Secret.builder().identifier("secret").accountIdentifier("account").name("updatedFileName").build());
    doReturn(Optional.ofNullable(SecretResponseWrapper.builder().secret(secretDTOV2).build()))
        .when(secretCrudService)
        .get(any(), any(), any(), any());

    SecretResponseWrapper updatedFile =
        secretCrudService.updateFile("account", null, null, "identifier", secretDTOV2, null);
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
        .thenReturn(Secret.builder().identifier("secret").accountIdentifier("account").name("updatedFileName").build());
    doReturn(Optional.ofNullable(SecretResponseWrapper.builder().secret(secretDTOV2).build()))
        .when(secretCrudService)
        .get(any(), any(), any(), any());

    SecretResponseWrapper updatedFile = secretCrudService.updateFile(
        "account", null, null, "identifier", secretDTOV2, new StringInputStream("input Stream is present"));
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
        "account", "org", "project", "identifier", SSHKeyValidationMetadata.builder().host("host").build());
    assertThat(resultDTO).isNotNull();
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testGet() {
    when(ngSecretServiceV2.get(any(), any(), any(), any())).thenReturn(Optional.ofNullable(Secret.builder().build()));
    Optional<SecretResponseWrapper> secretResponseWrapper = secretCrudService.get("account", null, null, "identifier");
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
                                            .accountIdentifier("account")
                                            .identifier("identifier")
                                            .type(SecretType.SecretText)
                                            .secretSpec(SecretTextSpec.builder().valueType(ValueType.Reference).build())
                                            .build()));
    when(encryptedDataService.get("account", null, null, "identifier"))
        .thenReturn(NGEncryptedData.builder().path(secretRef).build());
    Optional<SecretResponseWrapper> secretResponseWrapper = secretCrudService.get("account", null, null, "identifier");
    assertThat(secretResponseWrapper).isPresent();
    assertThat(secretResponseWrapper.get().getSecret().getSpec()).isInstanceOf(SecretTextSpecDTO.class);
    SecretTextSpecDTO secretSpec = (SecretTextSpecDTO) secretResponseWrapper.get().getSecret().getSpec();
    assertThat(secretSpec.getValue()).isEqualTo(secretRef);
    verify(ngSecretServiceV2).get(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testList() {
    when(ngSecretServiceV2.list(any(), anyInt(), anyInt()))
        .thenReturn(new PageImpl<>(Lists.newArrayList(Secret.builder().build()), PageRequest.of(0, 10), 1));
    Page<SecretResponseWrapper> secretPage = secretCrudService.list("account", "org", "proj", Collections.emptyList(),
        singletonList(SecretType.SSHKey), false, "abc", 0, 100, null);
    assertThat(secretPage.getContent()).isNotEmpty();
    assertThat(secretPage.getContent().size()).isEqualTo(1);
    verify(ngSecretServiceV2).list(any(), anyInt(), anyInt());
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testDelete() {
    NGEncryptedData encryptedDataDTO = random(NGEncryptedData.class);
    when(encryptedDataService.get(any(), any(), any(), any())).thenReturn(encryptedDataDTO);
    when(encryptedDataService.delete(any(), any(), any(), any())).thenReturn(true);
    when(ngSecretServiceV2.delete(any(), any(), any(), any())).thenReturn(true);
    doNothing()
        .when(secretEntityReferenceHelper)
        .deleteSecretEntityReferenceWhenSecretGetsDeleted(any(), any(), any(), any(), any());
    doNothing().when(secretEntityReferenceHelper).validateSecretIsNotUsedByOthers(any(), any(), any(), any());
    when(ngSecretServiceV2.get(any(), any(), any(), any()))
        .thenReturn(Optional.of(
            Secret.builder().type(SecretType.SecretText).secretSpec(SecretTextSpec.builder().build()).build()));
    boolean success = secretCrudService.delete("account", null, null, "identifier");

    assertThat(success).isTrue();
    verify(encryptedDataService, atLeastOnce()).get(any(), any(), any(), any());
    verify(encryptedDataService, atLeastOnce()).delete(any(), any(), any(), any());
    verify(ngSecretServiceV2, atLeastOnce()).delete(any(), any(), any(), any());
    verify(secretEntityReferenceHelper, atLeastOnce())
        .deleteSecretEntityReferenceWhenSecretGetsDeleted(any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testDeleteBatch() {
    List<String> secretIdentifiers = new ArrayList<>();
    secretIdentifiers.add("identifier1");
    secretIdentifiers.add("identifier2");
    when(ngSecretServiceV2.delete(any(), any(), any(), any())).thenReturn(true);
    doNothing()
        .when(secretEntityReferenceHelper)
        .deleteSecretEntityReferenceWhenSecretGetsDeleted(any(), any(), any(), any(), any());
    when(ngSecretServiceV2.get(any(), any(), any(), any()))
        .thenReturn(Optional.of(
            Secret.builder().type(SecretType.SecretText).secretSpec(SecretTextSpec.builder().build()).build()));
    secretCrudService.deleteBatch("accountId", "orgId", "projectId", secretIdentifiers);
    verify(ngSecretServiceV2, times(2)).get(any(), any(), any(), any());
    verify(secretEntityReferenceHelper, times(2))
        .deleteSecretEntityReferenceWhenSecretGetsDeleted(any(), any(), any(), any(), any());
  }
}
