/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.custom.deployment.ng;

import static io.harness.rule.OwnerRule.ANIL;
import static io.harness.rule.OwnerRule.RISHABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.customdeployment.CustomDeploymentConnectorNGVariable;
import io.harness.cdng.customdeployment.CustomDeploymentNGVariable;
import io.harness.cdng.customdeployment.CustomDeploymentNumberNGVariable;
import io.harness.cdng.customdeployment.CustomDeploymentSecretNGVariable;
import io.harness.cdng.customdeploymentng.CustomDeploymentInfrastructureHelper;
import io.harness.cdng.infra.yaml.CustomDeploymentInfrastructure;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorInfoOutcomeDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterDetailsDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesUserNamePasswordDTO;
import io.harness.delegate.beans.connector.k8Connector.outcome.KubernetesClusterConfigOutcomeDTO;
import io.harness.delegate.beans.connector.k8Connector.outcome.KubernetesClusterDetailsOutcomeDTO;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.filestore.service.impl.FileStoreServiceImpl;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.template.TemplateResponseDTO;
import io.harness.plancreator.customDeployment.StepTemplateRef;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.steps.OutputExpressionConstants;
import io.harness.steps.environment.EnvironmentOutcome;
import io.harness.template.remote.TemplateResourceClient;
import io.harness.yaml.utils.NGVariablesUtils;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.powermock.core.classloader.annotations.PrepareForTest;

@PrepareForTest({NGVariablesUtils.class})
public class CustomDeploymentInfrastructureHelperTest extends CategoryTest {
  @Mock ConnectorService connectorService;
  @Mock InfrastructureEntityService infrastructureEntityService;
  @Mock ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock FileStoreServiceImpl fileStoreService;

  @Mock TemplateResourceClient templateResourceClient;
  @Spy @InjectMocks private CustomDeploymentInfrastructureHelper customDeploymentInfrastructureHelper;
  private static final String ACCOUNT = "accIdentifier";
  private static final String ORG = "orgIdentifier";
  private static final String PROJECT = "projectIdentifier";
  private static final String SECRET = "secret";
  private static final String NUMBER = "number";
  private static final String RESOURCE_PATH_PREFIX = "customdeployment/";
  private static final String INFRA_RESOURCE_PATH_PREFIX = "infrastructure/";
  private static final String TEMPLATE_RESOURCE_PATH_PREFIX = "template/";
  private static final String EXECUTION_ID = "executionId";
  private static final String ENVIRONMENT_IDENTIFIER = "envId";
  private static final String INFRA_NAME = "OpenStackInfra";
  private static final String CUSTOM_DEPLOYMENT_INFRA_ID = "customDeploymentInfraId";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  private String readFile(String filename, String folder) {
    String relativePath = RESOURCE_PATH_PREFIX + folder + filename;
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(relativePath)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  @Test
  @Owner(developers = OwnerRule.SOURABH)
  @Category(UnitTests.class)
  public void testConvertListVariablesToMap() {
    final String EXPRESSION = "expression";

    MockedStatic<NGVariablesUtils> utilities = Mockito.mockStatic(NGVariablesUtils.class);
    utilities.when(() -> NGVariablesUtils.fetchSecretExpression(anyString())).thenReturn(SECRET);

    KubernetesClusterDetailsDTO kubernetesClusterDetailsDTO =
        KubernetesClusterDetailsDTO.builder()
            .masterUrl("URL")
            .auth(KubernetesAuthDTO.builder()
                      .credentials(KubernetesUserNamePasswordDTO.builder()
                                       .passwordRef(SecretRefData.builder().identifier("pass").build())
                                       .build())
                      .build())
            .build();
    KubernetesClusterConfigDTO kubernetesClusterConfigDTO =
        KubernetesClusterConfigDTO.builder()
            .credential(KubernetesCredentialDTO.builder().config(kubernetesClusterDetailsDTO).build())
            .build();
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder().connectorConfig(kubernetesClusterConfigDTO).build();
    ConnectorResponseDTO connectorResponseDTO = ConnectorResponseDTO.builder().connector(connectorInfoDTO).build();

    CustomDeploymentConnectorNGVariable connector1 =
        CustomDeploymentConnectorNGVariable.builder()
            .name("connector1")
            .value(ParameterField.<String>builder().value("ConnectorDisconnectHandler").build())
            .connector(ParameterField.<ConnectorInfoDTO>builder().value(connectorInfoDTO).build())
            .build();
    CustomDeploymentConnectorNGVariable connector2 =
        CustomDeploymentConnectorNGVariable.builder()
            .name("connector2")
            .value(ParameterField.<String>builder().expressionValue(EXPRESSION).build())
            .connector(ParameterField.<ConnectorInfoDTO>builder().value(connectorInfoDTO).build())
            .build();
    CustomDeploymentConnectorNGVariable connector3 =
        CustomDeploymentConnectorNGVariable.builder()
            .name("connector3")
            .value(ParameterField.<String>builder().value("account.ConnectorDisconnectHandler").build())
            .connector(ParameterField.<ConnectorInfoDTO>builder().value(connectorInfoDTO).build())
            .build();
    CustomDeploymentConnectorNGVariable connector4 =
        CustomDeploymentConnectorNGVariable.builder()
            .name("connector4")
            .value(ParameterField.<String>builder().value("org.ConnectorDisconnectHandler").build())
            .connector(ParameterField.<ConnectorInfoDTO>builder().value(connectorInfoDTO).build())
            .build();

    SecretRefData secretRefData = SecretRefData.builder().identifier(SECRET).build();
    CustomDeploymentSecretNGVariable secret1 =
        CustomDeploymentSecretNGVariable.builder()
            .name(SECRET)
            .value(ParameterField.<SecretRefData>builder().value(secretRefData).build())
            .build();

    SecretRefData secretRefDataOrg = SecretRefData.builder().identifier(SECRET).scope(Scope.ORG).build();
    CustomDeploymentSecretNGVariable secret2 =
        CustomDeploymentSecretNGVariable.builder()
            .name("org." + SECRET)
            .value(ParameterField.<SecretRefData>builder().value(secretRefDataOrg).build())
            .build();

    SecretRefData secretRefDataAcc = SecretRefData.builder().identifier(SECRET).scope(Scope.ACCOUNT).build();
    CustomDeploymentSecretNGVariable secret3 =
        CustomDeploymentSecretNGVariable.builder()
            .name("account." + SECRET)
            .value(ParameterField.<SecretRefData>builder().value(secretRefDataAcc).build())
            .build();

    CustomDeploymentSecretNGVariable secret4 =
        CustomDeploymentSecretNGVariable.builder()
            .name("exp" + SECRET)
            .value(ParameterField.<SecretRefData>builder().expressionValue(EXPRESSION).build())
            .build();

    CustomDeploymentNumberNGVariable number = CustomDeploymentNumberNGVariable.builder()
                                                  .name(NUMBER)
                                                  .value(ParameterField.<Double>builder().value(2.0).build())
                                                  .build();
    List<CustomDeploymentNGVariable> variableList = new ArrayList<>();

    variableList.add(connector1);
    variableList.add(connector2);
    variableList.add(connector3);
    variableList.add(connector4);
    variableList.add(secret1);
    variableList.add(secret2);
    variableList.add(secret3);
    variableList.add(secret4);
    variableList.add(number);

    doReturn(Optional.of(connectorResponseDTO)).when(connectorService).get(any(), any(), any(), any());
    Map<String, Object> variables =
        customDeploymentInfrastructureHelper.convertListVariablesToMap(variableList, ACCOUNT, ORG, PROJECT);
    assertThat(
        ((KubernetesClusterDetailsOutcomeDTO) (((KubernetesClusterConfigOutcomeDTO) ((ConnectorInfoOutcomeDTO)
                                                                                         variables.get("connector1"))
                                                       .getSpec())
                                                   .getCredential())
                .getSpec())
            .getMasterUrl())
        .isEqualTo("URL");
    assertThat(
        ((KubernetesClusterDetailsOutcomeDTO) (((KubernetesClusterConfigOutcomeDTO) ((ConnectorInfoOutcomeDTO)
                                                                                         variables.get("connector3"))
                                                       .getSpec())
                                                   .getCredential())
                .getSpec())
            .getMasterUrl())
        .isEqualTo("URL");
    assertThat(
        ((KubernetesClusterDetailsOutcomeDTO) (((KubernetesClusterConfigOutcomeDTO) ((ConnectorInfoOutcomeDTO)
                                                                                         variables.get("connector4"))
                                                       .getSpec())
                                                   .getCredential())
                .getSpec())
            .getMasterUrl())
        .isEqualTo("URL");
    assertThat((String) variables.get("connector2")).isEqualTo(EXPRESSION);

    assertThat((String) variables.get(SECRET)).isEqualTo(SECRET);
    assertThat((Double) variables.get(NUMBER)).isEqualTo(2.0);
    assertThat((String) variables.get("org." + SECRET)).isEqualTo(SECRET);
    assertThat((String) variables.get("account." + SECRET)).isEqualTo(SECRET);
    assertThat((String) variables.get("exp" + SECRET)).isEqualTo(EXPRESSION);
  }

  @Test
  @Owner(developers = OwnerRule.ANIL)
  @Category(UnitTests.class)
  public void testConvertListVariablesToMapForNonExistingConnectors() {
    MockedStatic<NGVariablesUtils> utilities = Mockito.mockStatic(NGVariablesUtils.class);
    utilities.when(() -> NGVariablesUtils.fetchSecretExpression(anyString())).thenReturn(SECRET);

    KubernetesClusterDetailsDTO kubernetesClusterDetailsDTO =
        KubernetesClusterDetailsDTO.builder()
            .masterUrl("URL")
            .auth(KubernetesAuthDTO.builder()
                      .credentials(KubernetesUserNamePasswordDTO.builder()
                                       .passwordRef(SecretRefData.builder().identifier("pass").build())
                                       .build())
                      .build())
            .build();
    KubernetesClusterConfigDTO kubernetesClusterConfigDTO =
        KubernetesClusterConfigDTO.builder()
            .credential(KubernetesCredentialDTO.builder().config(kubernetesClusterDetailsDTO).build())
            .build();
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder().connectorConfig(kubernetesClusterConfigDTO).build();
    ConnectorResponseDTO connectorResponseDTO = ConnectorResponseDTO.builder().connector(connectorInfoDTO).build();

    CustomDeploymentConnectorNGVariable connector1 =
        CustomDeploymentConnectorNGVariable.builder()
            .name("connector1")
            .value(ParameterField.<String>builder().value("ConnectorDisconnectHandler").build())
            .connector(ParameterField.<ConnectorInfoDTO>builder().value(connectorInfoDTO).build())
            .build();

    List<CustomDeploymentNGVariable> variableList = Collections.singletonList(connector1);

    doReturn(Optional.empty()).when(connectorService).get(any(), any(), any(), any());

    String errorMessage = "Connector not found for given ConnectorDisconnectHandler ref :[ConnectorDisconnectHandler]";

    assertThatThrownBy(
        () -> customDeploymentInfrastructureHelper.convertListVariablesToMap(variableList, ACCOUNT, ORG, PROJECT))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(errorMessage);
  }

  @Test
  @Owner(developers = OwnerRule.SOURABH)
  @Category(UnitTests.class)
  public void testGetTemplateYamlForAccountLevelTemplate() throws IOException {
    String templateYaml = readFile("template.yaml", TEMPLATE_RESOURCE_PATH_PREFIX);
    TemplateResponseDTO templateResponseDTO = TemplateResponseDTO.builder()
                                                  .identifier("temp")
                                                  .versionLabel("1")
                                                  .yaml(templateYaml)
                                                  .isStableTemplate(true)
                                                  .build();
    MockedStatic<NGRestUtils> utilities = Mockito.mockStatic(NGRestUtils.class);
    utilities.when(() -> NGRestUtils.getResponse(any())).thenReturn(templateResponseDTO);
    String returnedTemplateYaml =
        customDeploymentInfrastructureHelper.getTemplateYaml(ACCOUNT, null, null, "account.temp", "1");
    assertThat(returnedTemplateYaml).isEqualTo(templateYaml);
  }

  @Test
  @Owner(developers = OwnerRule.SOURABH)
  @Category(UnitTests.class)
  public void testGetTemplateYamlForOrgLevelTemplate() {
    String templateYaml = readFile("template.yaml", TEMPLATE_RESOURCE_PATH_PREFIX);
    TemplateResponseDTO templateResponseDTO = TemplateResponseDTO.builder()
                                                  .identifier("temp")
                                                  .versionLabel("1")
                                                  .yaml(templateYaml)
                                                  .isStableTemplate(true)
                                                  .build();
    MockedStatic<NGRestUtils> utilities = Mockito.mockStatic(NGRestUtils.class);
    utilities.when(() -> NGRestUtils.getResponse(any())).thenReturn(templateResponseDTO);
    String returnedTemplateYaml =
        customDeploymentInfrastructureHelper.getTemplateYaml(ACCOUNT, null, null, "org.temp", "1");
    assertThat(returnedTemplateYaml).isEqualTo(templateYaml);
  }

  @Test
  @Owner(developers = OwnerRule.SOURABH)
  @Category(UnitTests.class)
  public void testGetTemplateYamlForProjectLevelTemplate() throws IOException {
    String templateYaml = readFile("template.yaml", TEMPLATE_RESOURCE_PATH_PREFIX);
    TemplateResponseDTO templateResponseDTO = TemplateResponseDTO.builder()
                                                  .identifier("temp")
                                                  .versionLabel("1")
                                                  .yaml(templateYaml)
                                                  .isStableTemplate(true)
                                                  .build();
    MockedStatic<NGRestUtils> utilities = Mockito.mockStatic(NGRestUtils.class);
    utilities.when(() -> NGRestUtils.getResponse(any())).thenReturn(templateResponseDTO);
    String returnedTemplateYaml =
        customDeploymentInfrastructureHelper.getTemplateYaml(ACCOUNT, null, null, "temp", "1");
    assertThat(returnedTemplateYaml).isEqualTo(templateYaml);
  }

  @Test
  @Owner(developers = OwnerRule.SOURABH)
  @Category(UnitTests.class)
  public void testGetScriptWithEmptyFileNode() {
    String templateYaml = readFile("template.yaml", TEMPLATE_RESOURCE_PATH_PREFIX);
    doReturn(Optional.empty())
        .when(fileStoreService)
        .getWithChildrenByPath(eq(ACCOUNT), eq(ORG), eq(PROJECT), any(), anyBoolean());
    assertThatThrownBy(() -> customDeploymentInfrastructureHelper.getScript(templateYaml, ACCOUNT, ORG, PROJECT))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = OwnerRule.SOURABH)
  @Category(UnitTests.class)
  public void testGetScriptWithEmptyScripteNode() {
    String templateYaml = readFile("templateWithEmptyScript.yaml", TEMPLATE_RESOURCE_PATH_PREFIX);
    assertThatThrownBy(() -> customDeploymentInfrastructureHelper.getScript(templateYaml, ACCOUNT, ORG, PROJECT))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Error occurred while fetching script for custom deployment ");
  }

  @Test
  @Owner(developers = OwnerRule.SOURABH)
  @Category(UnitTests.class)
  public void testInstancePath() {
    String templateYaml = readFile("template.yaml", TEMPLATE_RESOURCE_PATH_PREFIX);
    String instancePath = customDeploymentInfrastructureHelper.getInstancePath(templateYaml, ACCOUNT);
    assertThat(instancePath).isEqualTo("instances");
  }
  @Test
  @Owner(developers = OwnerRule.SOURABH)
  @Category(UnitTests.class)
  public void testInstancePathWithNULLPath() {
    String templateYaml = readFile("templateWithNullInstancePath.yaml", TEMPLATE_RESOURCE_PATH_PREFIX);
    assertThatThrownBy(() -> customDeploymentInfrastructureHelper.getInstancePath(templateYaml, ACCOUNT))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Template yaml provided does not have instancePath in it.");
  }

  @Test
  @Owner(developers = OwnerRule.SOURABH)
  @Category(UnitTests.class)
  public void testInstanceAttributes() {
    String templateYaml = readFile("template.yaml", TEMPLATE_RESOURCE_PATH_PREFIX);
    Map<String, String> attributes = customDeploymentInfrastructureHelper.getInstanceAttributes(templateYaml, ACCOUNT);
    assertThat(attributes.get("hostName")).isEqualTo("<+input>");
  }

  @Test
  @Owner(developers = OwnerRule.SOURABH)
  @Category(UnitTests.class)
  public void testInstanceAttributesWithNullAttributes() {
    String templateYaml = readFile("templateWithNoAttributes.yaml", TEMPLATE_RESOURCE_PATH_PREFIX);
    assertThatThrownBy(() -> customDeploymentInfrastructureHelper.getInstanceAttributes(templateYaml, ACCOUNT))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Template yaml provided does not have attributes in it.");
  }
  @Test
  @Owner(developers = OwnerRule.SOURABH)
  @Category(UnitTests.class)
  public void testGetInfraWithNullTemplateYaml() {
    assertThatThrownBy(() -> customDeploymentInfrastructureHelper.getInstanceAttributes(null, ACCOUNT))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Template yaml to create template inputs cannot be empty");
  }

  @Test
  @Owner(developers = OwnerRule.SOURABH)
  @Category(UnitTests.class)
  public void testGetInfraWithInvalidTemplateYaml() {
    assertThatThrownBy(
        () -> customDeploymentInfrastructureHelper.getInstanceAttributes("templateVariables.yaml", ACCOUNT))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Yaml provided is not a template yaml.");
  }

  @Test
  @Owner(developers = OwnerRule.SOURABH)
  @Category(UnitTests.class)
  public void testGetInfraWithNoSpecTemplateYaml() {
    String templateYaml = readFile("templateWithNoSpec.yaml", TEMPLATE_RESOURCE_PATH_PREFIX);
    assertThatThrownBy(() -> customDeploymentInfrastructureHelper.getInstanceAttributes(templateYaml, ACCOUNT))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Template yaml provided does not have spec in it.");
  }

  @Test
  @Owner(developers = OwnerRule.SOURABH)
  @Category(UnitTests.class)
  public void testGetInfraWithNoInfraTemplateYaml() {
    String templateYaml = readFile("templateWithNoInfra.yaml", TEMPLATE_RESOURCE_PATH_PREFIX);
    assertThatThrownBy(() -> customDeploymentInfrastructureHelper.getInstanceAttributes(templateYaml, ACCOUNT))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Template yaml provided does not have infrastructure in it.");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetStepTemplateRefFromYaml() {
    String infraYaml = readFile("infrastructure.yaml", INFRA_RESOURCE_PATH_PREFIX);
    StepTemplateRef stepTemplateRef =
        customDeploymentInfrastructureHelper.getStepTemplateRefFromInfraYaml(infraYaml, ACCOUNT);

    assertThat(stepTemplateRef.getTemplateRef()).isEqualTo("account.OpenStack");
    assertThat(stepTemplateRef.getVersionLabel()).isEqualTo("V1");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetStepTemplateRefFromYamlInvalidInfraDef() {
    String infraYaml = readFile("template.yaml", TEMPLATE_RESOURCE_PATH_PREFIX);

    assertThatThrownBy(() -> customDeploymentInfrastructureHelper.getStepTemplateRefFromInfraYaml(infraYaml, ACCOUNT))
        .hasMessage("Could not fetch the template reference from yaml Infra definition is null in yaml");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetStepTemplateRefFromYamlWithoutVersion() {
    String infraYaml = readFile("infrastructureWithStableDT.yaml", INFRA_RESOURCE_PATH_PREFIX);
    StepTemplateRef stepTemplateRef =
        customDeploymentInfrastructureHelper.getStepTemplateRefFromInfraYaml(infraYaml, ACCOUNT);

    assertThat(stepTemplateRef.getTemplateRef()).isEqualTo("account.OpenStack");
    assertThat(stepTemplateRef.getVersionLabel()).isEqualTo(null);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetStepTemplateRefFromYamlWithoutSpec() {
    String infraYaml = readFile("infrastructureWithoutSpec.yaml", INFRA_RESOURCE_PATH_PREFIX);
    assertThatThrownBy(() -> customDeploymentInfrastructureHelper.getStepTemplateRefFromInfraYaml(infraYaml, ACCOUNT))
        .hasMessage("Could not fetch the template reference from yaml Infra definition spec is null in yaml");
  }

  @Test()
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetStepTemplateRefFromYamlWithoutRef() {
    String infraYaml = readFile("infrastructureWithoutDTRef.yaml", INFRA_RESOURCE_PATH_PREFIX);
    assertThatThrownBy(() -> customDeploymentInfrastructureHelper.getStepTemplateRefFromInfraYaml(infraYaml, ACCOUNT))
        .hasMessage("Could not fetch the template reference from yaml customDeploymentRef is null in yaml");
  }

  @Test()
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetStepTemplateRefFromYamlWithoutTemplateRef() {
    String infraYaml = readFile("infrastructureWithoutTemplateRef.yaml", INFRA_RESOURCE_PATH_PREFIX);
    assertThatThrownBy(() -> customDeploymentInfrastructureHelper.getStepTemplateRefFromInfraYaml(infraYaml, ACCOUNT))
        .hasMessage("Could not fetch the template reference from yaml templateRef is null in yaml");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testValidateInfrastructureYamlWrongVariables() {
    InfrastructureEntity infrastructureEntity = InfrastructureEntity.builder()
                                                    .accountId(ACCOUNT)
                                                    .orgIdentifier(ORG)
                                                    .projectIdentifier(PROJECT)
                                                    .yaml(readFile("infrastructure.yaml", INFRA_RESOURCE_PATH_PREFIX))
                                                    .build();
    doReturn(readFile("template.yaml", TEMPLATE_RESOURCE_PATH_PREFIX))
        .when(customDeploymentInfrastructureHelper)
        .getTemplateYaml(ACCOUNT, ORG, PROJECT, "account.OpenStack", "V1");
    assertThat(customDeploymentInfrastructureHelper.isNotValidInfrastructureYaml(infrastructureEntity)).isTrue();
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testValidateInfrastructureYaml() {
    InfrastructureEntity infrastructureEntity = InfrastructureEntity.builder()
                                                    .accountId(ACCOUNT)
                                                    .orgIdentifier(ORG)
                                                    .projectIdentifier(PROJECT)
                                                    .yaml(readFile("infrastructure.yaml", INFRA_RESOURCE_PATH_PREFIX))
                                                    .build();
    doReturn(readFile("templateWithAllVariables.yaml", TEMPLATE_RESOURCE_PATH_PREFIX))
        .when(customDeploymentInfrastructureHelper)
        .getTemplateYaml(ACCOUNT, ORG, PROJECT, "account.OpenStack", "V1");
    customDeploymentInfrastructureHelper.isNotValidInfrastructureYaml(infrastructureEntity);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testValidateInfrastructureYamlWithInvalidInfra() {
    InfrastructureEntity infrastructureEntity = InfrastructureEntity.builder()
                                                    .accountId(ACCOUNT)
                                                    .orgIdentifier(ORG)
                                                    .projectIdentifier(PROJECT)
                                                    .yaml(readFile("template.yaml", TEMPLATE_RESOURCE_PATH_PREFIX))
                                                    .build();
    doReturn(readFile("templateWithAllVariables.yaml", TEMPLATE_RESOURCE_PATH_PREFIX))
        .when(customDeploymentInfrastructureHelper)
        .getTemplateYaml(ACCOUNT, ORG, PROJECT, "account.OpenStack", "V1");
    assertThatThrownBy(() -> customDeploymentInfrastructureHelper.isNotValidInfrastructureYaml(infrastructureEntity))
        .hasMessage("Could not fetch the template reference from yaml Infra definition is null in yaml");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testValidateInfrastructureYamlWithoutInfraSpec() {
    InfrastructureEntity infrastructureEntity =
        InfrastructureEntity.builder()
            .accountId(ACCOUNT)
            .orgIdentifier(ORG)
            .projectIdentifier(PROJECT)
            .yaml(readFile("infrastructureWithoutSpec.yaml", INFRA_RESOURCE_PATH_PREFIX))
            .build();
    doReturn(readFile("templateWithAllVariables.yaml", TEMPLATE_RESOURCE_PATH_PREFIX))
        .when(customDeploymentInfrastructureHelper)
        .getTemplateYaml(ACCOUNT, ORG, PROJECT, "account.OpenStack", "V1");
    assertThatThrownBy(() -> customDeploymentInfrastructureHelper.isNotValidInfrastructureYaml(infrastructureEntity))
        .hasMessage("Could not fetch the template reference from yaml Infra definition spec is null in yaml");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testValidateInfrastructureYamlWrongVariableType() {
    InfrastructureEntity infrastructureEntity = InfrastructureEntity.builder()
                                                    .accountId(ACCOUNT)
                                                    .orgIdentifier(ORG)
                                                    .projectIdentifier(PROJECT)
                                                    .yaml(readFile("infrastructure.yaml", INFRA_RESOURCE_PATH_PREFIX))
                                                    .build();
    doReturn(readFile("templateWithDiffType.yaml", TEMPLATE_RESOURCE_PATH_PREFIX))
        .when(customDeploymentInfrastructureHelper)
        .getTemplateYaml(ACCOUNT, ORG, PROJECT, "account.OpenStack", "V1");
    assertThat(customDeploymentInfrastructureHelper.isNotValidInfrastructureYaml(infrastructureEntity)).isTrue();
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testValidateInfrastructureYamlWrongVariableName() {
    InfrastructureEntity infrastructureEntity = InfrastructureEntity.builder()
                                                    .accountId(ACCOUNT)
                                                    .orgIdentifier(ORG)
                                                    .projectIdentifier(PROJECT)
                                                    .yaml(readFile("infrastructure.yaml", INFRA_RESOURCE_PATH_PREFIX))
                                                    .build();
    doReturn(readFile("templateWithDiffName.yaml", TEMPLATE_RESOURCE_PATH_PREFIX))
        .when(customDeploymentInfrastructureHelper)
        .getTemplateYaml(ACCOUNT, ORG, PROJECT, "account.OpenStack", "V1");
    assertThat(customDeploymentInfrastructureHelper.isNotValidInfrastructureYaml(infrastructureEntity)).isTrue();
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testValidateInfrastructureYamlWithNoInfraVariables() {
    InfrastructureEntity infrastructureEntity =
        InfrastructureEntity.builder()
            .accountId(ACCOUNT)
            .orgIdentifier(ORG)
            .projectIdentifier(PROJECT)
            .yaml(readFile("infrastructureWithNoVariables.yaml", INFRA_RESOURCE_PATH_PREFIX))
            .build();
    doReturn(readFile("templateWithAllVariables.yaml", TEMPLATE_RESOURCE_PATH_PREFIX))
        .when(customDeploymentInfrastructureHelper)
        .getTemplateYaml(ACCOUNT, ORG, PROJECT, "account.OpenStack", "V1");
    assertThat(customDeploymentInfrastructureHelper.isNotValidInfrastructureYaml(infrastructureEntity)).isTrue();
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testValidateInfrastructureYamlWithNoTemplateVariables() {
    InfrastructureEntity infrastructureEntity = InfrastructureEntity.builder()
                                                    .accountId(ACCOUNT)
                                                    .orgIdentifier(ORG)
                                                    .projectIdentifier(PROJECT)
                                                    .yaml(readFile("infrastructure.yaml", INFRA_RESOURCE_PATH_PREFIX))
                                                    .build();
    doReturn(readFile("templateWithNoVariables.yaml", TEMPLATE_RESOURCE_PATH_PREFIX))
        .when(customDeploymentInfrastructureHelper)
        .getTemplateYaml(ACCOUNT, ORG, PROJECT, "account.OpenStack", "V1");
    assertThat(customDeploymentInfrastructureHelper.isNotValidInfrastructureYaml(infrastructureEntity)).isTrue();
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testValidateInfrastructureYamlWithNoVariables() {
    InfrastructureEntity infrastructureEntity =
        InfrastructureEntity.builder()
            .accountId(ACCOUNT)
            .orgIdentifier(ORG)
            .projectIdentifier(PROJECT)
            .yaml(readFile("infrastructureWithNoVariables.yaml", INFRA_RESOURCE_PATH_PREFIX))
            .build();
    doReturn(readFile("templateWithNoVariables.yaml", TEMPLATE_RESOURCE_PATH_PREFIX))
        .when(customDeploymentInfrastructureHelper)
        .getTemplateYaml(ACCOUNT, ORG, PROJECT, "account.OpenStack", "V1");
    customDeploymentInfrastructureHelper.isNotValidInfrastructureYaml(infrastructureEntity);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testInValidateInfrastructureYaml() {
    String templateYaml = readFile("template.yaml", TEMPLATE_RESOURCE_PATH_PREFIX);
    doReturn(templateYaml)
        .when(customDeploymentInfrastructureHelper)
        .getTemplateYaml(ACCOUNT, ORG, PROJECT, "account.OpenStack", "V1");
    assertThat(customDeploymentInfrastructureHelper.checkIfInfraIsObsolete(null, templateYaml, "accountId")).isFalse();

    String infraYamlWithoutDefinition = readFile("infraWithoutDefinition.yaml", INFRA_RESOURCE_PATH_PREFIX);
    assertThat(customDeploymentInfrastructureHelper.checkIfInfraIsObsolete(
                   infraYamlWithoutDefinition, templateYaml, "accountId"))
        .isFalse();

    String infrastructureWithoutSpec = readFile("infrastructureWithoutSpec.yaml", INFRA_RESOURCE_PATH_PREFIX);
    assertThat(customDeploymentInfrastructureHelper.checkIfInfraIsObsolete(
                   infrastructureWithoutSpec, templateYaml, "accountId"))
        .isFalse();

    String infrastructureWithEmptyVariableName =
        readFile("infrastructureWithEmptyVariableName.yaml", INFRA_RESOURCE_PATH_PREFIX);
    assertThat(customDeploymentInfrastructureHelper.checkIfInfraIsObsolete(
                   infrastructureWithEmptyVariableName, templateYaml, "accountId"))
        .isFalse();
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testInValidateDeploymentTemplateYaml() {
    String infrastructureYaml = readFile("infrastructure.yaml", INFRA_RESOURCE_PATH_PREFIX);
    InfrastructureEntity infrastructureEntity = InfrastructureEntity.builder()
                                                    .accountId(ACCOUNT)
                                                    .orgIdentifier(ORG)
                                                    .projectIdentifier(PROJECT)
                                                    .yaml(infrastructureYaml)
                                                    .build();

    String templateYaml = readFile("template.yaml", TEMPLATE_RESOURCE_PATH_PREFIX);
    doReturn(templateYaml)
        .when(customDeploymentInfrastructureHelper)
        .getTemplateYaml(ACCOUNT, ORG, PROJECT, "account.OpenStack", "V1");
    assertThat(customDeploymentInfrastructureHelper.checkIfInfraIsObsolete(infrastructureYaml, null, "accountId"))
        .isFalse();

    String templateWithoutDefinitionYaml = readFile("templateWithoutDefinition.yaml", TEMPLATE_RESOURCE_PATH_PREFIX);
    assertThat(customDeploymentInfrastructureHelper.checkIfInfraIsObsolete(
                   infrastructureYaml, templateWithoutDefinitionYaml, "accountId"))
        .isFalse();

    String templateWithoutSpecYaml = readFile("templateWithNoSpec.yaml", TEMPLATE_RESOURCE_PATH_PREFIX);
    assertThat(customDeploymentInfrastructureHelper.checkIfInfraIsObsolete(
                   infrastructureYaml, templateWithoutSpecYaml, "accountId"))
        .isFalse();

    String templateWithNoInfra = readFile("templateWithNoInfra.yaml", TEMPLATE_RESOURCE_PATH_PREFIX);
    assertThat(customDeploymentInfrastructureHelper.checkIfInfraIsObsolete(
                   infrastructureYaml, templateWithNoInfra, "accountId"))
        .isFalse();

    String templateWithEmptyVariableName =
        readFile("templateWithEmptyVariableName.yaml", TEMPLATE_RESOURCE_PATH_PREFIX);
    assertThat(customDeploymentInfrastructureHelper.checkIfInfraIsObsolete(
                   infrastructureYaml, templateWithEmptyVariableName, "accountId"))
        .isFalse();
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testValidInfrastructureEntity() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT)
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, ORG)
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, PROJECT)
                            .setStageExecutionId(EXECUTION_ID)
                            .build();

    EnvironmentOutcome environmentOutcome = EnvironmentOutcome.builder().identifier(ENVIRONMENT_IDENTIFIER).build();

    CustomDeploymentInfrastructure customDeploymentInfrastructure = CustomDeploymentInfrastructure.builder().build();
    customDeploymentInfrastructure.setInfraName(INFRA_NAME);
    customDeploymentInfrastructure.setInfraIdentifier(CUSTOM_DEPLOYMENT_INFRA_ID);

    doReturn(environmentOutcome)
        .when(executionSweepingOutputService)
        .resolve(eq(ambiance), eq(RefObjectUtils.getSweepingOutputRefObject(OutputExpressionConstants.ENVIRONMENT)));

    InfrastructureEntity entity = InfrastructureEntity.builder().build();

    doReturn(Optional.of(entity))
        .when(infrastructureEntityService)
        .get(eq(ACCOUNT), eq(ORG), eq(PROJECT), eq(ENVIRONMENT_IDENTIFIER), eq(CUSTOM_DEPLOYMENT_INFRA_ID));

    customDeploymentInfrastructureHelper.validateInfra(ambiance, customDeploymentInfrastructure);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testInfrastructureEntityNotPresent() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT)
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, ORG)
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, PROJECT)
                            .setStageExecutionId(EXECUTION_ID)
                            .build();

    EnvironmentOutcome environmentOutcome = EnvironmentOutcome.builder().identifier(ENVIRONMENT_IDENTIFIER).build();

    CustomDeploymentInfrastructure customDeploymentInfrastructure = CustomDeploymentInfrastructure.builder().build();
    customDeploymentInfrastructure.setInfraName(INFRA_NAME);
    customDeploymentInfrastructure.setInfraIdentifier(CUSTOM_DEPLOYMENT_INFRA_ID);

    doReturn(environmentOutcome)
        .when(executionSweepingOutputService)
        .resolve(eq(ambiance), eq(RefObjectUtils.getSweepingOutputRefObject(OutputExpressionConstants.ENVIRONMENT)));

    doReturn(Optional.empty())
        .when(infrastructureEntityService)
        .get(eq(ACCOUNT), eq(ORG), eq(PROJECT), eq(ENVIRONMENT_IDENTIFIER), eq(CUSTOM_DEPLOYMENT_INFRA_ID));

    assertThatThrownBy(
        () -> customDeploymentInfrastructureHelper.validateInfra(ambiance, customDeploymentInfrastructure))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Infra does not exist for this infra id - [customDeploymentInfraId], and env id - [envId], infra - [OpenStackInfra]");
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testInfrastructureEntityObsolete() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT)
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, ORG)
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, PROJECT)
                            .setStageExecutionId(EXECUTION_ID)
                            .build();

    EnvironmentOutcome environmentOutcome = EnvironmentOutcome.builder().identifier(ENVIRONMENT_IDENTIFIER).build();

    CustomDeploymentInfrastructure customDeploymentInfrastructure =
        CustomDeploymentInfrastructure.builder()
            .customDeploymentRef(StepTemplateRef.builder().templateRef("template").build())
            .build();
    customDeploymentInfrastructure.setInfraName(INFRA_NAME);
    customDeploymentInfrastructure.setInfraIdentifier(CUSTOM_DEPLOYMENT_INFRA_ID);

    doReturn(environmentOutcome)
        .when(executionSweepingOutputService)
        .resolve(eq(ambiance), eq(RefObjectUtils.getSweepingOutputRefObject(OutputExpressionConstants.ENVIRONMENT)));

    InfrastructureEntity entity = InfrastructureEntity.builder().obsolete(true).build();

    doReturn(Optional.of(entity))
        .when(infrastructureEntityService)
        .get(eq(ACCOUNT), eq(ORG), eq(PROJECT), eq(ENVIRONMENT_IDENTIFIER), eq(CUSTOM_DEPLOYMENT_INFRA_ID));

    assertThatThrownBy(
        () -> customDeploymentInfrastructureHelper.validateInfra(ambiance, customDeploymentInfrastructure))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Infrastructure - [OpenStackInfra] is obsolete as Deployment Template with Id: [template] has been updated, please reconcile the infrastructure");
  }
}
