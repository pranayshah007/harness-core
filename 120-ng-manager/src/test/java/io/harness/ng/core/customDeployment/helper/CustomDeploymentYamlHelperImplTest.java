/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.customDeployment.helper;

import static io.harness.rule.OwnerRule.RISHABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.eventsframework.schemas.entity.ScopeProtoEnum;
import io.harness.eventsframework.schemas.entity.TemplateReferenceProtoDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.customDeployment.CustomDeploymentVariableResponseDTO;
import io.harness.ng.core.customDeployment.CustomDeploymentYamlRequestDTO;
import io.harness.plancreator.customDeployment.StepTemplateRef;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.google.common.io.Resources;
import com.google.protobuf.StringValue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class CustomDeploymentYamlHelperImplTest extends CategoryTest {
  @InjectMocks private CustomDeploymentYamlHelper customDeploymentYamlHelper;
  private static final String RESOURCE_PATH_PREFIX = "customDeployment/";
  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String ORG_ID = "ORG_ID";
  private static final String PROJECT_ID = "PROJECT_ID";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  private String readFile(String filename) {
    String relativePath = RESOURCE_PATH_PREFIX + filename;
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(relativePath)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  @SneakyThrows
  public void testGetVariablesFromYaml() {
    String template = readFile("template.yaml");
    CustomDeploymentYamlRequestDTO customDeploymentYamlRequestDTO =
        CustomDeploymentYamlRequestDTO.builder().entityYaml(template).build();
    CustomDeploymentVariableResponseDTO customDeploymentVariableResponseDTO =
        customDeploymentYamlHelper.getVariablesFromYaml(customDeploymentYamlRequestDTO);

    YamlField uuidInjectedYaml = YamlUtils.readTree(customDeploymentVariableResponseDTO.getYaml());
    assertThat(customDeploymentVariableResponseDTO.getMetadataMap()).hasSize(4);
    List<YamlNode> variablesNode = uuidInjectedYaml.getNode()
                                       .getField("customDeployment")
                                       .getNode()
                                       .getField("infrastructure")
                                       .getNode()
                                       .getField("variables")
                                       .getNode()
                                       .asArray();

    String clusterUrlUUID = variablesNode.get(0).getField("value").getNode().asText();
    assertThat(customDeploymentVariableResponseDTO.getMetadataMap().get(clusterUrlUUID).getFqn())
        .isEqualTo("stage.spec.infrastructure.output.variables.clusterUrl");
    assertThat(customDeploymentVariableResponseDTO.getMetadataMap().get(clusterUrlUUID).getLocalName())
        .isEqualTo("infra.variables.clusterUrl");
    assertThat(customDeploymentVariableResponseDTO.getMetadataMap().get(clusterUrlUUID).getVariableName())
        .isEqualTo("clusterUrl");

    String imageUUID = variablesNode.get(1).getField("value").getNode().asText();
    assertThat(customDeploymentVariableResponseDTO.getMetadataMap().get(imageUUID).getFqn())
        .isEqualTo("stage.spec.infrastructure.output.variables.image");
    assertThat(customDeploymentVariableResponseDTO.getMetadataMap().get(imageUUID).getLocalName())
        .isEqualTo("infra.variables.image");
    assertThat(customDeploymentVariableResponseDTO.getMetadataMap().get(imageUUID).getVariableName())
        .isEqualTo("image");

    String instancesListPathUUID = uuidInjectedYaml.getNode()
                                       .getField("customDeployment")
                                       .getNode()
                                       .getField("infrastructure")
                                       .getNode()
                                       .getField("instancesListPath")
                                       .getNode()
                                       .asText();
    assertThat(customDeploymentVariableResponseDTO.getMetadataMap().get(instancesListPathUUID).getFqn())
        .isEqualTo("stage.spec.infrastructure.output.instancesListPath");
    assertThat(customDeploymentVariableResponseDTO.getMetadataMap().get(instancesListPathUUID).getLocalName())
        .isEqualTo("infra.instancesListPath");
    assertThat(customDeploymentVariableResponseDTO.getMetadataMap().get(instancesListPathUUID).getVariableName())
        .isEqualTo("instancesListPath");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetReferencesFromYaml() {
    String template = readFile("templateReferences.yaml");

    EntityDetailProtoDTO accountTemplate = EntityDetailProtoDTO.newBuilder()
                                               .setType(EntityTypeProtoEnum.TEMPLATE)
                                               .setTemplateRef(TemplateReferenceProtoDTO.newBuilder()
                                                                   .setScope(ScopeProtoEnum.ACCOUNT)
                                                                   .setAccountIdentifier(StringValue.of(ACCOUNT_ID))
                                                                   .setIdentifier(StringValue.of("accountTemplate"))
                                                                   .setVersionLabel(StringValue.of("v1"))
                                                                   .build())
                                               .build();

    EntityDetailProtoDTO orgTemplate = EntityDetailProtoDTO.newBuilder()
                                           .setType(EntityTypeProtoEnum.TEMPLATE)
                                           .setTemplateRef(TemplateReferenceProtoDTO.newBuilder()
                                                               .setScope(ScopeProtoEnum.ORG)
                                                               .setAccountIdentifier(StringValue.of(ACCOUNT_ID))
                                                               .setIdentifier(StringValue.of("orgTemplate"))
                                                               .setOrgIdentifier(StringValue.of(ORG_ID))
                                                               .setVersionLabel(StringValue.of("v2"))
                                                               .build())
                                           .build();

    EntityDetailProtoDTO projectTemplate = EntityDetailProtoDTO.newBuilder()
                                               .setType(EntityTypeProtoEnum.TEMPLATE)
                                               .setTemplateRef(TemplateReferenceProtoDTO.newBuilder()
                                                                   .setScope(ScopeProtoEnum.PROJECT)
                                                                   .setAccountIdentifier(StringValue.of(ACCOUNT_ID))
                                                                   .setIdentifier(StringValue.of("projectTemplate"))
                                                                   .setOrgIdentifier(StringValue.of(ORG_ID))
                                                                   .setProjectIdentifier(StringValue.of(PROJECT_ID))
                                                                   .setVersionLabel(StringValue.of("v3"))
                                                                   .build())
                                               .build();

    EntityDetailProtoDTO accountConnector =
        EntityDetailProtoDTO.newBuilder()
            .setType(EntityTypeProtoEnum.CONNECTORS)
            .setIdentifierRef(IdentifierRefProtoDTO.newBuilder()
                                  .setScope(ScopeProtoEnum.ACCOUNT)
                                  .setAccountIdentifier(StringValue.of(ACCOUNT_ID))
                                  .setIdentifier(StringValue.of("accountConnector"))
                                  .putMetadata("fqn", "stage.spec.infrastructure.output.variables.accountConnector")
                                  .build())
            .build();

    EntityDetailProtoDTO orgConnector =
        EntityDetailProtoDTO.newBuilder()
            .setType(EntityTypeProtoEnum.CONNECTORS)
            .setIdentifierRef(IdentifierRefProtoDTO.newBuilder()
                                  .setScope(ScopeProtoEnum.ORG)
                                  .setAccountIdentifier(StringValue.of(ACCOUNT_ID))
                                  .setOrgIdentifier(StringValue.of(ORG_ID))
                                  .setIdentifier(StringValue.of("orgConnector"))
                                  .putMetadata("fqn", "stage.spec.infrastructure.output.variables.orgConnector")
                                  .build())
            .build();

    EntityDetailProtoDTO projectConnector =
        EntityDetailProtoDTO.newBuilder()
            .setType(EntityTypeProtoEnum.CONNECTORS)
            .setIdentifierRef(IdentifierRefProtoDTO.newBuilder()
                                  .setScope(ScopeProtoEnum.PROJECT)
                                  .setAccountIdentifier(StringValue.of(ACCOUNT_ID))
                                  .setOrgIdentifier(StringValue.of(ORG_ID))
                                  .setProjectIdentifier(StringValue.of(PROJECT_ID))
                                  .setIdentifier(StringValue.of("projectConnector"))
                                  .putMetadata("fqn", "stage.spec.infrastructure.output.variables.projectConnector")
                                  .build())
            .build();

    List<EntityDetailProtoDTO> entities =
        customDeploymentYamlHelper.getReferencesFromYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, template);
    assertThat(entities).containsExactly(
        accountConnector, orgConnector, projectConnector, accountTemplate, orgTemplate, projectTemplate);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetStepTemplateRefFromYaml() {
    String infraYaml = readFile("infrastructure.yaml");
    StepTemplateRef stepTemplateRef = customDeploymentYamlHelper.getStepTemplateRefFromYaml(infraYaml, "ACCOUNT");

    assertThat(stepTemplateRef.getTemplateRef()).isEqualTo("account.OpenStack");
    assertThat(stepTemplateRef.getVersionLabel()).isEqualTo("V1");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetStepTemplateRefFromYamlWithoutVersion() {
    String infraYaml = readFile("infrastructureWithoutVersionLabel.yaml");
    StepTemplateRef stepTemplateRef = customDeploymentYamlHelper.getStepTemplateRefFromYaml(infraYaml, "ACCOUNT");

    assertThat(stepTemplateRef.getTemplateRef()).isEqualTo("account.OpenStack");
    assertThat(stepTemplateRef.getVersionLabel()).isEqualTo(null);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetStepTemplateRefFromYamlWithoutSpec() {
    String infraYaml = readFile("infrastructureWithoutSpec.yaml");
    assertThatThrownBy(() -> customDeploymentYamlHelper.getStepTemplateRefFromYaml(infraYaml, ACCOUNT_ID))
        .hasMessage("Could not fetch the template reference from yaml customDeploymentRef is null in yaml");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testValidateTemplateYaml() {
    String templateYaml = readFile("template.yaml");
    customDeploymentYamlHelper.validateTemplateYaml(templateYaml);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testValidateTemplateYamlWithMultipleScripts() {
    String templateYaml = readFile("templateWithMultipleScripts.yaml");
    assertThatThrownBy(() -> customDeploymentYamlHelper.validateTemplateYaml(templateYaml))
        .hasMessage("Template yaml is not valid: Only one fetch instance script is allowed");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testValidateTemplateYamlWithNoScript() {
    String templateYaml = readFile("templateWithNoScript.yaml");
    assertThatThrownBy(() -> customDeploymentYamlHelper.validateTemplateYaml(templateYaml))
        .hasMessage("Template yaml is not valid: Scoped file path cannot be null or empty");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testValidateTemplateYamlWithNoContent() {
    String templateYaml = readFile("templateWithNoContent.yaml");
    assertThatThrownBy(() -> customDeploymentYamlHelper.validateTemplateYaml(templateYaml))
        .hasMessage("Template yaml is not valid: Fetch Instance script cannot be empty");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testValidateTemplateYamlWithInvalidStore() {
    String templateYaml = readFile("templateWithInvalidFileStore.yaml");
    assertThatThrownBy(() -> customDeploymentYamlHelper.validateTemplateYaml(templateYaml))
        .hasMessage("Template yaml is not valid: Only Inline/Harness Store can be used for fetch instance script");
  }

  @Test()
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetStepTemplateRefFromYamlWithoutRef() {
    String infraYaml = readFile("infrastructureWithoutRef.yaml");
    assertThatThrownBy(() -> customDeploymentYamlHelper.getStepTemplateRefFromYaml(infraYaml, ACCOUNT_ID))
        .hasMessage("Could not fetch the template reference from yaml customDeploymentRef is null in yaml");
  }
}
