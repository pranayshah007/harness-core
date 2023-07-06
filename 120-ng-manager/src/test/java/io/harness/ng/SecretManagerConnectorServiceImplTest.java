/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.BOOPESH;
import static io.harness.rule.OwnerRule.PHOENIKX;
import static io.harness.rule.OwnerRule.VIKAS_M;

import static io.github.benas.randombeans.api.EnhancedRandom.random;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.connector.services.NGVaultService;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.customsecretmanager.CustomSecretManagerConnectorDTO;
import io.harness.delegate.beans.connector.customsecretmanager.TemplateLinkConfigForCustomSecretManager;
import io.harness.delegate.beans.connector.vaultconnector.VaultConnectorDTO;
import io.harness.encryption.SecretRefData;
import io.harness.enforcement.client.services.EnforcementClientService;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.api.NGSecretManagerService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.ConnectorRepository;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.dto.SecretManagerConfigDTO;
import io.harness.secretmanagerclient.dto.VaultConfigDTO;
import io.harness.template.remote.TemplateResourceClient;

import software.wings.beans.NameValuePairWithDefault;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import retrofit2.Call;

@OwnedBy(PL)
public class SecretManagerConnectorServiceImplTest extends CategoryTest {
  private static final String ACCOUNT = "account";
  private static final String ACCOUNT_IDENTIFIER = randomAlphabetic(10);
  private static final String CONNECTOR_IDENTIFIER = randomAlphabetic(10);
  private static final String ORG_IDENTIFIER = randomAlphabetic(10);
  private static final String PROJECT_IDENTIFIER = randomAlphabetic(10);
  private static final String PROJECT_TEMPLATE_REF = "templateRef";
  private static final String ACCOUNT_TEMPLATE_REF = "account.templateRef";
  private static final String ORG_TEMPLATE_REF = "org.templateRef";
  private static final String VERSION = "VERSION1";
  private static final String ENVIRONMENT_VARIABLES = "environmentVariables";
  private NGSecretManagerService ngSecretManagerService;
  private ConnectorService defaultConnectorService;
  private SecretManagerConnectorServiceImpl secretManagerConnectorService;
  private ConnectorRepository connectorRepository;
  private NGVaultService ngVaultService;
  private EnforcementClientService enforcementClientService;
  private TemplateResourceClient templateResourceClient;

  @Before
  public void setup() {
    ngSecretManagerService = mock(NGSecretManagerService.class);
    defaultConnectorService = mock(ConnectorService.class);
    connectorRepository = mock(ConnectorRepository.class);
    ngVaultService = mock(NGVaultService.class);
    enforcementClientService = mock(EnforcementClientService.class);
    templateResourceClient = mock(TemplateResourceClient.class);
    secretManagerConnectorService = new SecretManagerConnectorServiceImpl(
        defaultConnectorService, connectorRepository, ngVaultService, enforcementClientService, templateResourceClient);
  }

  private InvalidRequestException getInvalidRequestException() {
    return new InvalidRequestException("Invalid request");
  }

  private ConnectorDTO getRequestDTO() {
    SecretRefData secretRefData = new SecretRefData(randomAlphabetic(10));
    secretRefData.setDecryptedValue(randomAlphabetic(5).toCharArray());
    ConnectorInfoDTO connectorInfo = ConnectorInfoDTO.builder().build();
    connectorInfo.setConnectorType(ConnectorType.VAULT);
    connectorInfo.setConnectorConfig(VaultConnectorDTO.builder()
                                         .vaultUrl("http://abc.com:8200")
                                         .secretEngineVersion(1)
                                         .authToken(secretRefData)
                                         .renewalIntervalMinutes(10)
                                         .build());
    connectorInfo.setName("name");
    connectorInfo.setIdentifier("identifier");
    connectorInfo.setOrgIdentifier("orgIdentifier");
    connectorInfo.setProjectIdentifier("projectIdentifier");
    return ConnectorDTO.builder().connectorInfo(connectorInfo).build();
  }

  private ConnectorInfoDTO nonDuplicateEntries_template() {
    Map<String, List<NameValuePairWithDefault>> inputValues = new HashMap<>();
    NameValuePairWithDefault var1 =
        NameValuePairWithDefault.builder().name("var1").value("value1").type("String").build();
    NameValuePairWithDefault var2 =
        NameValuePairWithDefault.builder().name("var2").value("value2").type("String").build();
    NameValuePairWithDefault var3 =
        NameValuePairWithDefault.builder().name("var3").value("value3").type("String").useAsDefault(true).build();

    List<NameValuePairWithDefault> inputEnvironmentVariables = new LinkedList<>();
    inputEnvironmentVariables.add(var1);
    inputEnvironmentVariables.add(var2);
    inputEnvironmentVariables.add(var3);
    inputValues.put(ENVIRONMENT_VARIABLES, inputEnvironmentVariables);
    return getConnectorWithProjectTemplateRef(inputValues);
  }

  private ConnectorInfoDTO duplicateEntries_template() {
    Map<String, List<NameValuePairWithDefault>> inputValues = new HashMap<>();
    NameValuePairWithDefault var1 =
        NameValuePairWithDefault.builder().name("var1").value("value1").type("String").build();
    NameValuePairWithDefault var2 =
        NameValuePairWithDefault.builder().name("var2").value("value2").type("String").build();
    NameValuePairWithDefault var3 =
        NameValuePairWithDefault.builder().name("var2").value("value3").type("String").useAsDefault(true).build();

    List<NameValuePairWithDefault> inputEnvironmentVariables = new LinkedList<>();
    inputEnvironmentVariables.add(var1);
    inputEnvironmentVariables.add(var2);
    inputEnvironmentVariables.add(var3);
    inputValues.put(ENVIRONMENT_VARIABLES, inputEnvironmentVariables);
    return getConnectorWithProjectTemplateRef(inputValues);
  }

  private ConnectorInfoDTO getConnectorWithProjectTemplateRef(
      Map<String, List<NameValuePairWithDefault>> templateInputs) {
    return ConnectorInfoDTO.builder()
        .connectorType(ConnectorType.CUSTOM_SECRET_MANAGER)
        .name("customSM")
        .identifier(CONNECTOR_IDENTIFIER)
        .projectIdentifier(PROJECT_IDENTIFIER)
        .orgIdentifier(ORG_IDENTIFIER)
        .connectorConfig(CustomSecretManagerConnectorDTO.builder()
                             .template(TemplateLinkConfigForCustomSecretManager.builder()
                                           .templateRef(PROJECT_TEMPLATE_REF)
                                           .versionLabel(VERSION)
                                           .templateInputs(templateInputs)
                                           .build())
                             .build())
        .build();
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testCreateSecretManagerConnector() {
    SecretManagerConfigDTO secretManagerConfigDTO = random(VaultConfigDTO.class);
    when(defaultConnectorService.get(any(), any(), any(), any())).thenReturn(Optional.empty());
    when(ngSecretManagerService.createSecretManager(any())).thenReturn(secretManagerConfigDTO);
    when(defaultConnectorService.create(any(), any())).thenReturn(null);
    when(connectorRepository.updateMultiple(any(), any())).thenReturn(null);
    ConnectorResponseDTO connectorDTO = secretManagerConnectorService.create(getRequestDTO(), ACCOUNT);
    assertThat(connectorDTO).isEqualTo(null);
    verify(defaultConnectorService).create(any(), any(), any());
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testCreateSecretManagerConnectorShouldFail_ManagerReturnsDuplicate() {
    when(defaultConnectorService.get(any(), any(), any(), any()))
        .thenReturn(Optional.ofNullable(ConnectorResponseDTO.builder().build()));
    when(connectorRepository.updateMultiple(any(), any())).thenReturn(null);
    try {
      secretManagerConnectorService.create(getRequestDTO(), ACCOUNT);
      fail("Should fail if execution reaches here");
    } catch (DuplicateFieldException exception) {
      // do nothing
    }
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testCreateVaultSecretManagerConnector_withRenewalIntervalZero() {
    SecretManagerConfigDTO secretManagerConfigDTO = random(VaultConfigDTO.class);
    when(defaultConnectorService.get(any(), any(), any(), any())).thenReturn(Optional.empty());
    when(ngSecretManagerService.createSecretManager(any())).thenReturn(secretManagerConfigDTO);
    when(defaultConnectorService.create(any(), any())).thenReturn(null);
    when(connectorRepository.updateMultiple(any(), any())).thenReturn(null);
    ConnectorDTO requestDTO = getRequestDTO();
    ((VaultConnectorDTO) requestDTO.getConnectorInfo().getConnectorConfig()).setRenewalIntervalMinutes(0);
    ConnectorResponseDTO connectorDTO = secretManagerConnectorService.create(requestDTO, ACCOUNT);
    assertThat(connectorDTO).isEqualTo(null);
    verify(defaultConnectorService).create(any(), any(), any());
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testCreateCustomSM_withCorrectTemplateInputs() throws IOException {
    when(defaultConnectorService.get(any(), any(), any(), any())).thenReturn(Optional.empty());
    when(defaultConnectorService.create(any(), any())).thenReturn(null);
    ConnectorDTO requestDTO = ConnectorDTO.builder().connectorInfo(nonDuplicateEntries_template()).build();
    String templateInputs = "type: \"ShellScript\"\n"
        + "spec:\n"
        + "  source:\n"
        + "    type: \"Inline\"\n"
        + "    spec:\n"
        + "      script: \"<+input>\"\n"
        + "environmentVariables:\n"
        + "  - name: var1\n"
        + "  - name: var2\n"
        + "  - name: var3\n"
        + "timeout: \"<+input>\"\n";
    Call<ResponseDTO<String>> request = mock(Call.class);
    doReturn(request)
        .when(templateResourceClient)
        .getTemplateInputsYaml(
            PROJECT_TEMPLATE_REF, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, VERSION, false);
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(templateInputs);
    ConnectorResponseDTO connectorDTO = secretManagerConnectorService.create(requestDTO, ACCOUNT_IDENTIFIER);
    assertThat(connectorDTO).isEqualTo(null);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testCreateCustomSM_withDuplicateInput_shouldThrowError() throws IOException {
    when(defaultConnectorService.get(any(), any(), any(), any())).thenReturn(Optional.empty());
    when(defaultConnectorService.create(any(), any())).thenReturn(null);
    ConnectorDTO requestDTO = ConnectorDTO.builder().connectorInfo(duplicateEntries_template()).build();
    String templateInputs = "type: \"ShellScript\"\n"
        + "spec:\n"
        + "  source:\n"
        + "    type: \"Inline\"\n"
        + "    spec:\n"
        + "      script: \"<+input>\"\n"
        + "environmentVariables:\n"
        + "  - name: var1\n"
        + "  - name: var2\n"
        + "  - name: var3\n"
        + "timeout: \"<+input>\"\n";
    Call<ResponseDTO<String>> request = mock(Call.class);
    doReturn(request)
        .when(templateResourceClient)
        .getTemplateInputsYaml(
            PROJECT_TEMPLATE_REF, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, VERSION, false);
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(templateInputs);
    try {
      ConnectorResponseDTO connectorDTO = secretManagerConnectorService.create(requestDTO, ACCOUNT_IDENTIFIER);
    } catch (Exception ex) {
      assertThat(ex).isInstanceOf(InvalidRequestException.class);
      assertThat(ex.getMessage()).isEqualTo("Multiple values for the same input Parameter is passed. Please check.");
    }
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testCreateCustomSM_withSomeRunTimeParametersMissing_shouldThrowError() throws IOException {
    when(defaultConnectorService.get(any(), any(), any(), any())).thenReturn(Optional.empty());
    when(defaultConnectorService.create(any(), any())).thenReturn(null);
    ConnectorDTO requestDTO = ConnectorDTO.builder().connectorInfo(nonDuplicateEntries_template()).build();
    String templateInputs = "type: \"ShellScript\"\n"
        + "spec:\n"
        + "  source:\n"
        + "    type: \"Inline\"\n"
        + "    spec:\n"
        + "      script: \"<+input>\"\n"
        + "environmentVariables:\n"
        + "  - name: var1\n"
        + "  - name: var2\n"
        + "  - name: var3\n"
        + "  - name: var4\n"
        + "timeout: \"<+input>\"\n";
    Call<ResponseDTO<String>> request = mock(Call.class);
    doReturn(request)
        .when(templateResourceClient)
        .getTemplateInputsYaml(
            PROJECT_TEMPLATE_REF, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, VERSION, false);
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(templateInputs);
    try {
      ConnectorResponseDTO connectorDTO = secretManagerConnectorService.create(requestDTO, ACCOUNT_IDENTIFIER);
    } catch (Exception ex) {
      assertThat(ex).isInstanceOf(InvalidRequestException.class);
      assertThat(ex.getMessage()).isEqualTo("Run time inputs of templates should be passed in connector.");
    }
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testCreateCustomSM_withAccountLevelTemplate_inProjectScope() {
    when(defaultConnectorService.get(any(), any(), any(), any())).thenReturn(Optional.empty());
    when(defaultConnectorService.create(any(), any())).thenReturn(null);
    ConnectorInfoDTO infoDTO = ConnectorInfoDTO.builder()
                                   .connectorType(ConnectorType.CUSTOM_SECRET_MANAGER)
                                   .name("customSM")
                                   .identifier(CONNECTOR_IDENTIFIER)
                                   .projectIdentifier(PROJECT_IDENTIFIER)
                                   .orgIdentifier(ORG_IDENTIFIER)
                                   .connectorConfig(CustomSecretManagerConnectorDTO.builder()
                                                        .template(TemplateLinkConfigForCustomSecretManager.builder()
                                                                      .templateRef(ACCOUNT_TEMPLATE_REF)
                                                                      .versionLabel(VERSION)
                                                                      .build())
                                                        .build())
                                   .build();
    ConnectorDTO requestDTO = ConnectorDTO.builder().connectorInfo(infoDTO).build();
    Call<ResponseDTO<String>> request = mock(Call.class);
    ArgumentCaptor<String> argumentCaptorAccountId = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> argumentCaptorOrgId = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> argumentCaptorProjectId = ArgumentCaptor.forClass(String.class);
    when(templateResourceClient.getTemplateInputsYaml(any(), argumentCaptorAccountId.capture(),
             argumentCaptorOrgId.capture(), argumentCaptorProjectId.capture(), any(), anyBoolean()))
        .thenReturn(request);
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(null);
    ConnectorResponseDTO connectorDTO = secretManagerConnectorService.create(requestDTO, ACCOUNT_IDENTIFIER);
    assertThat(argumentCaptorAccountId.getValue()).isEqualTo(ACCOUNT_IDENTIFIER);
    assertThat(argumentCaptorOrgId.getValue()).isEqualTo(null);
    assertThat(argumentCaptorProjectId.getValue()).isEqualTo(null);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testCreateCustomSM_withOrgLevelTemplate_inProjectScope() {
    when(defaultConnectorService.get(any(), any(), any(), any())).thenReturn(Optional.empty());
    when(defaultConnectorService.create(any(), any())).thenReturn(null);
    ConnectorInfoDTO infoDTO = ConnectorInfoDTO.builder()
                                   .connectorType(ConnectorType.CUSTOM_SECRET_MANAGER)
                                   .name("customSM")
                                   .identifier(CONNECTOR_IDENTIFIER)
                                   .projectIdentifier(PROJECT_IDENTIFIER)
                                   .orgIdentifier(ORG_IDENTIFIER)
                                   .connectorConfig(CustomSecretManagerConnectorDTO.builder()
                                                        .template(TemplateLinkConfigForCustomSecretManager.builder()
                                                                      .templateRef(ORG_TEMPLATE_REF)
                                                                      .versionLabel(VERSION)
                                                                      .build())
                                                        .build())
                                   .build();
    ConnectorDTO requestDTO = ConnectorDTO.builder().connectorInfo(infoDTO).build();
    Call<ResponseDTO<String>> request = mock(Call.class);
    ArgumentCaptor<String> argumentCaptorAccountId = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> argumentCaptorOrgId = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> argumentCaptorProjectId = ArgumentCaptor.forClass(String.class);
    when(templateResourceClient.getTemplateInputsYaml(any(), argumentCaptorAccountId.capture(),
             argumentCaptorOrgId.capture(), argumentCaptorProjectId.capture(), any(), anyBoolean()))
        .thenReturn(request);
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(null);
    ConnectorResponseDTO connectorDTO = secretManagerConnectorService.create(requestDTO, ACCOUNT_IDENTIFIER);
    assertThat(argumentCaptorAccountId.getValue()).isEqualTo(ACCOUNT_IDENTIFIER);
    assertThat(argumentCaptorOrgId.getValue()).isEqualTo(ORG_IDENTIFIER);
    assertThat(argumentCaptorProjectId.getValue()).isEqualTo(null);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testCreateCustomSM_withProjectLevelTemplate_inProjectScope() {
    when(defaultConnectorService.get(any(), any(), any(), any())).thenReturn(Optional.empty());
    when(defaultConnectorService.create(any(), any())).thenReturn(null);
    ConnectorInfoDTO infoDTO = ConnectorInfoDTO.builder()
                                   .connectorType(ConnectorType.CUSTOM_SECRET_MANAGER)
                                   .name("customSM")
                                   .identifier(CONNECTOR_IDENTIFIER)
                                   .projectIdentifier(PROJECT_IDENTIFIER)
                                   .orgIdentifier(ORG_IDENTIFIER)
                                   .connectorConfig(CustomSecretManagerConnectorDTO.builder()
                                                        .template(TemplateLinkConfigForCustomSecretManager.builder()
                                                                      .templateRef(PROJECT_TEMPLATE_REF)
                                                                      .versionLabel(VERSION)
                                                                      .build())
                                                        .build())
                                   .build();
    ConnectorDTO requestDTO = ConnectorDTO.builder().connectorInfo(infoDTO).build();
    Call<ResponseDTO<String>> request = mock(Call.class);
    ArgumentCaptor<String> argumentCaptorAccountId = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> argumentCaptorOrgId = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> argumentCaptorProjectId = ArgumentCaptor.forClass(String.class);
    when(templateResourceClient.getTemplateInputsYaml(any(), argumentCaptorAccountId.capture(),
             argumentCaptorOrgId.capture(), argumentCaptorProjectId.capture(), any(), anyBoolean()))
        .thenReturn(request);
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(null);
    ConnectorResponseDTO connectorDTO = secretManagerConnectorService.create(requestDTO, ACCOUNT_IDENTIFIER);
    assertThat(argumentCaptorAccountId.getValue()).isEqualTo(ACCOUNT_IDENTIFIER);
    assertThat(argumentCaptorOrgId.getValue()).isEqualTo(ORG_IDENTIFIER);
    assertThat(argumentCaptorProjectId.getValue()).isEqualTo(PROJECT_IDENTIFIER);
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void updateSecretManager() {
    SecretRefData secretRefData = new SecretRefData(randomAlphabetic(10));
    secretRefData.setDecryptedValue(randomAlphabetic(5).toCharArray());
    when(ngSecretManagerService.updateSecretManager(any(), any(), any(), any(), any()))
        .thenReturn(random(VaultConfigDTO.class));
    when(defaultConnectorService.update(any(), any())).thenReturn(null);
    when(connectorRepository.updateMultiple(any(), any())).thenReturn(null);
    when(defaultConnectorService.get(any(), any(), any(), any()))
        .thenReturn(Optional.of(ConnectorResponseDTO.builder()
                                    .connector(ConnectorInfoDTO.builder()
                                                   .connectorType(ConnectorType.VAULT)
                                                   .connectorConfig(VaultConnectorDTO.builder()
                                                                        .isDefault(false)
                                                                        .authToken(secretRefData)
                                                                        .vaultUrl(randomAlphabetic(10))
                                                                        .namespace(randomAlphabetic(5))
                                                                        .build())
                                                   .build())
                                    .build()));
    ConnectorResponseDTO connectorDTO = secretManagerConnectorService.update(getRequestDTO(), ACCOUNT);
    assertThat(connectorDTO).isEqualTo(null);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testthrowExceptionIfMandatoryFieldsNotPassed() {
    SecretManagerConfigDTO secretManagerConfigDTO = random(VaultConfigDTO.class);
    when(defaultConnectorService.get(any(), any(), any(), any())).thenReturn(Optional.empty());
    when(ngSecretManagerService.createSecretManager(any())).thenReturn(secretManagerConfigDTO);
    when(defaultConnectorService.create(any(), any())).thenReturn(null);
    when(connectorRepository.updateMultiple(any(), any())).thenReturn(null);
    ConnectorDTO requestDTO = getRequestDTO();
    ((VaultConnectorDTO) requestDTO.getConnectorInfo().getConnectorConfig()).setVaultUrl(null);
    secretManagerConnectorService.create(requestDTO, ACCOUNT);
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testDeleteSecretManager() {
    when(ngSecretManagerService.getSecretManager(any(), any(), any(), any(), eq(true))).thenReturn(null);
    when(defaultConnectorService.delete(any(), any(), any(), any(), any(), eq(false))).thenReturn(true);
    boolean success = secretManagerConnectorService.delete(ACCOUNT, null, null, "identifier", false);
    assertThat(success).isEqualTo(true);
  }
}