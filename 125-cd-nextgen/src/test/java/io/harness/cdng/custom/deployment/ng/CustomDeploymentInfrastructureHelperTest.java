package io.harness.cdng.custom.deployment.ng;

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
import io.harness.ng.core.template.TemplateResponseDTO;
import io.harness.pms.yaml.ParameterField;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.template.remote.TemplateResourceClient;
import io.harness.yaml.utils.NGVariablesUtils;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
import org.powermock.core.classloader.annotations.PrepareForTest;

@PrepareForTest({NGVariablesUtils.class})
public class CustomDeploymentInfrastructureHelperTest extends CategoryTest {
  @Mock ConnectorService connectorService;
  @Mock FileStoreServiceImpl fileStoreService;
  @Mock TemplateResourceClient templateResourceClient;
  @InjectMocks private CustomDeploymentInfrastructureHelper customDeploymentInfrastructureHelper;
  private static final String ACCOUNT = "accIdentifier";
  private static final String ORG = "orgIdentifier";
  private static final String PROJECT = "projectIdentifier";
  private static final String SECRET = "secret";
  private static final String NUMBER = "number";
  private static final String RESOURCE_PATH_PREFIX = "customdeployment/";
  private static final String INFRA_RESOURCE_PATH_PREFIX = "infrastructure/";
  private static final String TEMPLATE_RESOURCE_PATH_PREFIX = "template/";

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
            .value(ParameterField.<String>builder().value("connector").build())
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
            .value(ParameterField.<String>builder().value("account.connector").build())
            .connector(ParameterField.<ConnectorInfoDTO>builder().value(connectorInfoDTO).build())
            .build();
    CustomDeploymentConnectorNGVariable connector4 =
        CustomDeploymentConnectorNGVariable.builder()
            .name("connector4")
            .value(ParameterField.<String>builder().value("org.connector").build())
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
}
