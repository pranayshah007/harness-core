/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api.impl;

import static io.harness.EntityType.CONNECTORS;
import static io.harness.EntityType.SECRETS;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.REFERRED_ENTITY_TYPE;
import static io.harness.ng.core.entitysetupusage.dto.SetupUsageDetailType.SECRET_REFERRED_BY_SECRET;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.impl.SecretRefInputValidationHelper;
import io.harness.encryption.SecretRefData;
import io.harness.entitysetupusageclient.EntitySetupUsageHelper;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.protohelper.IdentifierRefProtoDTOHelper;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entitysetupusage.DeleteSetupUsageDTO;
import io.harness.eventsframework.schemas.entitysetupusage.EntitySetupUsageCreateDTO;
import io.harness.eventsframework.schemas.entitysetupusage.EntitySetupUsageCreateV2DTO;
import io.harness.ng.core.dto.secrets.KerberosConfigDTO;
import io.harness.ng.core.dto.secrets.KerberosWinRmConfigDTO;
import io.harness.ng.core.dto.secrets.NTLMConfigDTO;
import io.harness.ng.core.dto.secrets.SSHAuthDTO;
import io.harness.ng.core.dto.secrets.SSHConfigDTO;
import io.harness.ng.core.dto.secrets.SSHCredentialType;
import io.harness.ng.core.dto.secrets.SSHKeyPathCredentialDTO;
import io.harness.ng.core.dto.secrets.SSHKeyReferenceCredentialDTO;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.ng.core.dto.secrets.SSHPasswordCredentialDTO;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretSpecDTO;
import io.harness.ng.core.dto.secrets.TGTGenerationMethod;
import io.harness.ng.core.dto.secrets.TGTKeyTabFilePathSpecDTO;
import io.harness.ng.core.dto.secrets.TGTPasswordSpecDTO;
import io.harness.ng.core.dto.secrets.WinRmAuthDTO;
import io.harness.ng.core.dto.secrets.WinRmCredentialsSpecDTO;
import io.harness.ng.core.entitysetupusage.service.EntitySetupUsageService;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.secretmanagerclient.SSHAuthScheme;
import io.harness.secretmanagerclient.WinRmAuthScheme;
import io.harness.utils.FullyQualifiedIdentifierHelper;

import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@Slf4j
@OwnedBy(PL)
public class SecretEntityReferenceHelperTest extends CategoryTest {
  public static final String ACCOUNT_IDENTIFIER = randomAlphabetic(10);
  public static final String ORG_IDENTIFIER = randomAlphabetic(10);
  public static final String PROJECT_IDENTIFIER = randomAlphabetic(10);
  @InjectMocks SecretEntityReferenceHelper secretEntityReferenceHelper;
  @Mock EntitySetupUsageService entitySetupUsageService;
  @Mock Producer eventProducer;
  @Mock EntitySetupUsageHelper entityReferenceHelper;
  @Mock IdentifierRefProtoDTOHelper identifierRefProtoDTOHelper;
  @Mock SecretRefInputValidationHelper secretRefInputValidationHelper;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    MockedStatic<IdentifierRefProtoDTOHelper> mockedStatic = Mockito.mockStatic(IdentifierRefProtoDTOHelper.class);
    mockedStatic
        .when(()
                  -> IdentifierRefProtoDTOHelper.createIdentifierRefProtoDTO(
                      anyString(), anyString(), anyString(), anyString()))
        .thenCallRealMethod();
    mockedStatic
        .when(()
                  -> IdentifierRefProtoDTOHelper.createIdentifierRefProtoDTO(
                      anyString(), anyString(), anyString(), anyString(), any()))
        .thenCallRealMethod();
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void createEntityReferenceForSecret() {
    String account = "account";
    String org = "org";
    String project = "project";
    String secretName = "secretName";
    String identifier = "identifier";
    String secretManager = "secretManager";
    when(entityReferenceHelper.createEntityReference(anyString(), any(), any())).thenCallRealMethod();
    secretEntityReferenceHelper.createSetupUsageForSecretManager(
        account, org, project, identifier, secretName, secretManager);
    ArgumentCaptor<Message> argumentCaptor = ArgumentCaptor.forClass(Message.class);
    try {
      verify(eventProducer, times(1)).send(argumentCaptor.capture());
    } catch (EventsFrameworkDownException e) {
      e.printStackTrace();
    }
    EntitySetupUsageCreateDTO entityReferenceDTO = null;
    try {
      entityReferenceDTO = EntitySetupUsageCreateDTO.parseFrom(argumentCaptor.getValue().getData());
    } catch (Exception ex) {
      log.error("Unexpected error :", ex);
    }
    assertThat(entityReferenceDTO.getReferredEntity().getType().toString()).isEqualTo(CONNECTORS.name());
    assertThat(entityReferenceDTO.getAccountIdentifier()).isEqualTo(account);
    assertThat(entityReferenceDTO.getReferredByEntity().getName()).isEqualTo(secretName);
    assertThat(entityReferenceDTO.getReferredByEntity().getType().toString()).isEqualTo(SECRETS.name());
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void deleteSecretEntityReferenceWhenSecretGetsDeleted() {
    String account = "account";
    String org = "org";
    String project = "project";
    String secretName = "secretName";
    String identifier = "identifier";
    String secretManager = "secretManager";
    ArgumentCaptor<Message> argumentCaptor = ArgumentCaptor.forClass(Message.class);
    secretEntityReferenceHelper.deleteSecretEntityReferenceWhenSecretGetsDeleted(
        account, org, project, identifier, secretManager);
    try {
      verify(eventProducer, times(1)).send(argumentCaptor.capture());
    } catch (EventsFrameworkDownException e) {
      e.printStackTrace();
    }
    DeleteSetupUsageDTO deleteSetupUsageDTO = null;
    try {
      deleteSetupUsageDTO = DeleteSetupUsageDTO.parseFrom(argumentCaptor.getValue().getData());
    } catch (Exception ex) {
      log.error("Exception in the event framework");
    }
    assertThat(deleteSetupUsageDTO.getAccountIdentifier()).isEqualTo(account);
    assertThat(deleteSetupUsageDTO.getReferredByEntityFQN())
        .isEqualTo(FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(account, org, project, identifier));
    assertThat(deleteSetupUsageDTO.getReferredByEntityFQN())
        .isEqualTo(FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(account, org, project, identifier));
  }

  @Test
  @Owner(developers = OwnerRule.NISHANT)
  @Category(UnitTests.class)
  public void testCreateSetupUsageForSecret_ssh_password() throws InvalidProtocolBufferException {
    SecretDTOV2 dto = getSSHKeySpecDTOWithCredTypePassword();
    callAndVerifySetupUsageForSecret(dto, 1);
  }

  @Test
  @Owner(developers = OwnerRule.NISHANT)
  @Category(UnitTests.class)
  public void testCreateSetupUsageForSecret_ssh_key_ref() throws InvalidProtocolBufferException {
    SecretDTOV2 dto = getSSHKeySpecDTOWithCredTypeKeyRef();
    callAndVerifySetupUsageForSecret(dto, 2);
  }

  @Test
  @Owner(developers = OwnerRule.NISHANT)
  @Category(UnitTests.class)
  public void testCreateSetupUsageForSecret_ssh_key_path() throws InvalidProtocolBufferException {
    SecretDTOV2 dto = getSSHKeySpecDTOWithCredTypeKeyPath();
    callAndVerifySetupUsageForSecret(dto, 1);
  }

  @Test
  @Owner(developers = OwnerRule.NISHANT)
  @Category(UnitTests.class)
  public void testCreateSetupUsageForSecret_ssh_kerberos_key_path() throws InvalidProtocolBufferException {
    SecretDTOV2 dto = getSSHKeySpecDTOWithKerberosTGTKeyTabFilePath();
    callAndVerifySetupUsageForSecret(dto, 0);
  }

  @Test
  @Owner(developers = OwnerRule.NISHANT)
  @Category(UnitTests.class)
  public void testCreateSetupUsageForSecret_ssh_kerberos_password() throws InvalidProtocolBufferException {
    SecretDTOV2 dto = getSSHKeySpecDTOWithKerberosPassword();
    callAndVerifySetupUsageForSecret(dto, 1);
  }

  @Test
  @Owner(developers = OwnerRule.NISHANT)
  @Category(UnitTests.class)
  public void testCreateSetupUsageForSecret_winrm_ntlm() throws InvalidProtocolBufferException {
    SecretDTOV2 dto = getWinRmCredentialsSpecDTONTLM();
    callAndVerifySetupUsageForSecret(dto, 1);
  }

  @Test
  @Owner(developers = OwnerRule.NISHANT)
  @Category(UnitTests.class)
  public void testCreateSetupUsageForSecret_winrm_kerberos_key_path() throws InvalidProtocolBufferException {
    SecretDTOV2 dto = getWinRmCredentialsSpecDTOKerberosTGTKeyTabFilePath();
    callAndVerifySetupUsageForSecret(dto, 0);
  }

  @Test
  @Owner(developers = OwnerRule.NISHANT)
  @Category(UnitTests.class)
  public void testCreateSetupUsageForSecret_winrm_kerberos_password() throws InvalidProtocolBufferException {
    SecretDTOV2 dto = getWinRmCredentialsSpecDTOKerberosPassword();
    callAndVerifySetupUsageForSecret(dto, 1);
  }

  private void callAndVerifySetupUsageForSecret(SecretDTOV2 dto, int setupUsageDetailCount)
      throws InvalidProtocolBufferException {
    ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
    when(secretRefInputValidationHelper.getDecryptableFieldsData(any())).thenCallRealMethod();
    secretEntityReferenceHelper.createSetupUsageForSecret(ACCOUNT_IDENTIFIER, dto);
    verify(eventProducer, times(1)).send(messageArgumentCaptor.capture());
    Message capturedMessage = messageArgumentCaptor.getValue();
    assertForSetupUsageForSecret(dto, capturedMessage, setupUsageDetailCount);
  }

  private SecretDTOV2 getSSHKeySpecDTOWithCredTypePassword() {
    SSHPasswordCredentialDTO sshPasswordCredentialDTO =
        SSHPasswordCredentialDTO.builder().userName(randomAlphabetic(10)).password(getSecretRefData()).build();
    SSHConfigDTO sshConfigDTO = new SSHConfigDTO(SSHCredentialType.Password, sshPasswordCredentialDTO);
    SSHAuthDTO sshAuthDTO = new SSHAuthDTO(SSHAuthScheme.SSH, sshConfigDTO);
    SSHKeySpecDTO sshKeySpecDTO = new SSHKeySpecDTO(9090, sshAuthDTO);
    return getSecretDTOV2(sshKeySpecDTO);
  }

  private SecretDTOV2 getSSHKeySpecDTOWithCredTypeKeyRef() {
    SSHKeyReferenceCredentialDTO sshKeyReferenceCredentialDTO =
        SSHKeyReferenceCredentialDTO.builder().key(getSecretRefData()).encryptedPassphrase(getSecretRefData()).build();
    SSHConfigDTO sshConfigDTO = new SSHConfigDTO(SSHCredentialType.KeyReference, sshKeyReferenceCredentialDTO);
    SSHAuthDTO sshAuthDTO = new SSHAuthDTO(SSHAuthScheme.SSH, sshConfigDTO);
    SSHKeySpecDTO sshKeySpecDTO = new SSHKeySpecDTO(9090, sshAuthDTO);
    return getSecretDTOV2(sshKeySpecDTO);
  }

  private SecretDTOV2 getSSHKeySpecDTOWithCredTypeKeyPath() {
    SSHKeyPathCredentialDTO sshKeyPathCredentialDTO =
        SSHKeyPathCredentialDTO.builder().encryptedPassphrase(getSecretRefData()).build();
    SSHConfigDTO sshConfigDTO = new SSHConfigDTO(SSHCredentialType.KeyPath, sshKeyPathCredentialDTO);
    SSHAuthDTO sshAuthDTO = new SSHAuthDTO(SSHAuthScheme.SSH, sshConfigDTO);
    SSHKeySpecDTO sshKeySpecDTO = new SSHKeySpecDTO(9090, sshAuthDTO);
    return getSecretDTOV2(sshKeySpecDTO);
  }

  private SecretDTOV2 getSSHKeySpecDTOWithKerberosTGTKeyTabFilePath() {
    TGTKeyTabFilePathSpecDTO tgtKeyTabFilePathSpecDTO =
        TGTKeyTabFilePathSpecDTO.builder().keyPath(randomAlphabetic(10)).build();
    KerberosConfigDTO kerberosConfigDTO = KerberosConfigDTO.builder()
                                              .spec(tgtKeyTabFilePathSpecDTO)
                                              .tgtGenerationMethod(TGTGenerationMethod.KeyTabFilePath)
                                              .build();
    SSHAuthDTO sshAuthDTO = new SSHAuthDTO(SSHAuthScheme.Kerberos, kerberosConfigDTO);
    SSHKeySpecDTO sshKeySpecDTO = new SSHKeySpecDTO(9090, sshAuthDTO);
    return getSecretDTOV2(sshKeySpecDTO);
  }

  private SecretDTOV2 getSSHKeySpecDTOWithKerberosPassword() {
    TGTPasswordSpecDTO tgtPasswordSpecDTO = TGTPasswordSpecDTO.builder().password(getSecretRefData()).build();
    KerberosConfigDTO kerberosConfigDTO =
        KerberosConfigDTO.builder().spec(tgtPasswordSpecDTO).tgtGenerationMethod(TGTGenerationMethod.Password).build();
    SSHAuthDTO sshAuthDTO = new SSHAuthDTO(SSHAuthScheme.Kerberos, kerberosConfigDTO);
    SSHKeySpecDTO sshKeySpecDTO = new SSHKeySpecDTO(9090, sshAuthDTO);
    return getSecretDTOV2(sshKeySpecDTO);
  }

  private SecretDTOV2 getWinRmCredentialsSpecDTONTLM() {
    NTLMConfigDTO ntlmConfigDTO =
        new NTLMConfigDTO(randomAlphabetic(10), randomAlphabetic(10), getSecretRefData(), true, false, false);
    WinRmCredentialsSpecDTO specDTO =
        WinRmCredentialsSpecDTO.builder()
            .auth(WinRmAuthDTO.builder().spec(ntlmConfigDTO).type(WinRmAuthScheme.NTLM).build())
            .build();
    return getSecretDTOV2(specDTO);
  }

  private SecretDTOV2 getWinRmCredentialsSpecDTOKerberosTGTKeyTabFilePath() {
    KerberosWinRmConfigDTO kerberosWinRmConfigDTO =
        KerberosWinRmConfigDTO.builder()
            .tgtGenerationMethod(TGTGenerationMethod.KeyTabFilePath)
            .spec(TGTKeyTabFilePathSpecDTO.builder().keyPath(randomAlphabetic(10)).build())
            .build();
    WinRmCredentialsSpecDTO specDTO =
        WinRmCredentialsSpecDTO.builder()
            .auth(WinRmAuthDTO.builder().spec(kerberosWinRmConfigDTO).type(WinRmAuthScheme.Kerberos).build())
            .build();
    return getSecretDTOV2(specDTO);
  }

  private SecretDTOV2 getWinRmCredentialsSpecDTOKerberosPassword() {
    KerberosWinRmConfigDTO kerberosWinRmConfigDTO =
        KerberosWinRmConfigDTO.builder()
            .tgtGenerationMethod(TGTGenerationMethod.Password)
            .spec(TGTPasswordSpecDTO.builder().password(getSecretRefData()).build())
            .build();
    WinRmCredentialsSpecDTO specDTO =
        WinRmCredentialsSpecDTO.builder()
            .auth(WinRmAuthDTO.builder().spec(kerberosWinRmConfigDTO).type(WinRmAuthScheme.Kerberos).build())
            .build();
    return getSecretDTOV2(specDTO);
  }

  private SecretRefData getSecretRefData() {
    return new SecretRefData(randomAlphabetic(10));
  }

  private SecretDTOV2 getSecretDTOV2(SecretSpecDTO sshKeySpecDTO) {
    return SecretDTOV2.builder()
        .identifier(randomAlphabetic(10))
        .name(randomAlphabetic(10))
        .orgIdentifier(ORG_IDENTIFIER)
        .projectIdentifier(PROJECT_IDENTIFIER)
        .spec(sshKeySpecDTO)
        .build();
  }

  private void assertForSetupUsageForSecret(SecretDTOV2 dto, Message capturedMessage, int setupUsageDetailCount)
      throws InvalidProtocolBufferException {
    assertThat(capturedMessage.getMetadataMap())
        .containsExactlyInAnyOrderEntriesOf(Map.ofEntries(Map.entry("accountId", ACCOUNT_IDENTIFIER),
            Map.entry(REFERRED_ENTITY_TYPE, EntityTypeProtoEnum.SECRETS.name()),
            Map.entry(ACTION, FLUSH_CREATE_ACTION)));
    EntitySetupUsageCreateV2DTO entitySetupUsageCreateV2DTO =
        EntitySetupUsageCreateV2DTO.parseFrom(capturedMessage.getData());
    assertThat(entitySetupUsageCreateV2DTO).isNotNull();
    assertThat(entitySetupUsageCreateV2DTO.getReferredByEntity().getType()).isEqualTo(EntityTypeProtoEnum.SECRETS);
    assertThat(entitySetupUsageCreateV2DTO.getReferredByEntity().getName()).isEqualTo(dto.getName());
    assertThat(entitySetupUsageCreateV2DTO.getReferredEntityWithSetupUsageDetailCount())
        .isEqualTo(setupUsageDetailCount);
    for (int i = 0; i < setupUsageDetailCount; i++) {
      assertThat(entitySetupUsageCreateV2DTO.getReferredEntityWithSetupUsageDetail(i).getType())
          .isEqualTo(SECRET_REFERRED_BY_SECRET.toString());
    }
    assertThat(capturedMessage).isNotNull();
  }
}
