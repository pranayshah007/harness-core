/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.customDeployment.helper;

import static io.harness.ng.core.infrastructure.entity.InfrastructureEntity.InfrastructureEntityKeys;
import static io.harness.rule.OwnerRule.ANIL;
import static io.harness.rule.OwnerRule.RISHABH;

import static software.wings.beans.Service.ServiceKeys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

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
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.template.TemplateResponseDTO;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.google.common.io.Resources;
import com.google.protobuf.StringValue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class CustomDeploymentYamlHelperTest extends CategoryTest {
  @Spy @InjectMocks private CustomDeploymentYamlHelper customDeploymentYamlHelper;
  private static final String RESOURCE_PATH_PREFIX = "customDeployment/";
  private static final String INFRA_RESOURCE_PATH_PREFIX = "infrastructure/";
  private static final String TEMPLATE_RESOURCE_PATH_PREFIX = "templates/";
  private static final String SERVICE_RESOURCE_PATH_PREFIX = "service/";
  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String ORG_ID = "ORG_ID";
  private static final String PROJECT_ID = "PROJECT_ID";
  private static final String STABLE = "__STABLE__";

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
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  @SneakyThrows
  public void testGetVariablesFromYaml() {
    String template = readFile("entityTemplate.yaml", "");
    CustomDeploymentYamlRequestDTO customDeploymentYamlRequestDTO =
        CustomDeploymentYamlRequestDTO.builder().entityYaml(template).build();
    CustomDeploymentVariableResponseDTO customDeploymentVariableResponseDTO =
        customDeploymentYamlHelper.getVariablesFromYaml(customDeploymentYamlRequestDTO);

    YamlField uuidInjectedYaml = YamlUtils.readTree(customDeploymentVariableResponseDTO.getYaml());
    assertThat(customDeploymentVariableResponseDTO.getMetadataMap()).hasSize(2);
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
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetReferencesFromYaml() {
    String template = readFile("templateReferences.yaml", TEMPLATE_RESOURCE_PATH_PREFIX);
    doReturn(TemplateResponseDTO.builder().yaml(readFile("template.yaml", TEMPLATE_RESOURCE_PATH_PREFIX)).build())
        .when(customDeploymentYamlHelper)
        .getScopedTemplateResponseDTO(anyString(), anyString(), anyString(), anyString(), anyString());

    EntityDetailProtoDTO accountTemplate = EntityDetailProtoDTO.newBuilder()
                                               .setType(EntityTypeProtoEnum.TEMPLATE)
                                               .setTemplateRef(TemplateReferenceProtoDTO.newBuilder()
                                                                   .setScope(ScopeProtoEnum.ACCOUNT)
                                                                   .setAccountIdentifier(StringValue.of(ACCOUNT_ID))
                                                                   .setIdentifier(StringValue.of("accountTemplate"))
                                                                   .setVersionLabel(StringValue.of(STABLE))
                                                                   .build())
                                               .build();

    EntityDetailProtoDTO orgTemplate = EntityDetailProtoDTO.newBuilder()
                                           .setType(EntityTypeProtoEnum.TEMPLATE)
                                           .setTemplateRef(TemplateReferenceProtoDTO.newBuilder()
                                                               .setScope(ScopeProtoEnum.ORG)
                                                               .setAccountIdentifier(StringValue.of(ACCOUNT_ID))
                                                               .setIdentifier(StringValue.of("orgTemplate"))
                                                               .setOrgIdentifier(StringValue.of(ORG_ID))
                                                               .setVersionLabel(StringValue.of(STABLE))
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
                                                                   .setVersionLabel(StringValue.of(STABLE))
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
  public void testGetReferencesFromYamlInvalidRef() {
    String template = readFile("templateInvalidReferences.yaml", TEMPLATE_RESOURCE_PATH_PREFIX);
    doReturn(TemplateResponseDTO.builder().yaml(template).build())
        .when(customDeploymentYamlHelper)
        .getScopedTemplateResponseDTO(anyString(), anyString(), anyString(), anyString(), anyString());

    assertThatThrownBy(() -> customDeploymentYamlHelper.getReferencesFromYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, template))
        .hasMessage(
            "Template yaml provided does not have valid entity references: step template linked cannot have empty identifier");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetReferencesFromYamlDuplicateRef() {
    String template = readFile("templateDuplicateReferences.yaml", TEMPLATE_RESOURCE_PATH_PREFIX);
    doReturn(TemplateResponseDTO.builder().yaml(template).build())
        .when(customDeploymentYamlHelper)
        .getScopedTemplateResponseDTO(anyString(), anyString(), anyString(), anyString(), anyString());

    assertThatThrownBy(() -> customDeploymentYamlHelper.getReferencesFromYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, template))
        .hasMessage(
            "Template yaml provided does not have valid entity references: Duplicate step account.accountTemplate linked with the template");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetFilteredServiceEntities() {
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, ServiceKeys.createdAt));
    ServiceEntity service =
        ServiceEntity.builder().yaml(readFile("service.yaml", SERVICE_RESOURCE_PATH_PREFIX)).build();
    ServiceEntity invalidService =
        ServiceEntity.builder().yaml(readFile("infrastructure.yaml", INFRA_RESOURCE_PATH_PREFIX)).build();
    ServiceEntity serviceWithoutSpec =
        ServiceEntity.builder().yaml(readFile("serviceWithoutSpec.yaml", SERVICE_RESOURCE_PATH_PREFIX)).build();
    ServiceEntity serviceWithoutServiceDefinition =
        ServiceEntity.builder()
            .yaml(readFile("serviceWithoutServiceDefinition.yaml", SERVICE_RESOURCE_PATH_PREFIX))
            .build();
    ServiceEntity serviceWithoutDTRef =
        ServiceEntity.builder().yaml(readFile("serviceWithoutDTRef.yaml", SERVICE_RESOURCE_PATH_PREFIX)).build();
    ServiceEntity serviceWithoutVersionLabel =
        ServiceEntity.builder().yaml(readFile("serviceWithStableDT.yaml", SERVICE_RESOURCE_PATH_PREFIX)).build();
    Page<ServiceEntity> serviceEntities =
        new PageImpl<>(Arrays.asList(service, invalidService, serviceWithoutSpec, serviceWithoutServiceDefinition,
                           serviceWithoutDTRef, serviceWithoutVersionLabel),
            pageable, 1);

    assertThat(customDeploymentYamlHelper.getFilteredServiceEntities(
                   0, 10, new ArrayList<>(), "account.OpenStack", "V1", serviceEntities))
        .containsExactly(service);
  }
  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetFilteredServiceEntitiesWithStableTemplate() {
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, ServiceKeys.createdAt));
    ServiceEntity service =
        ServiceEntity.builder().yaml(readFile("service.yaml", SERVICE_RESOURCE_PATH_PREFIX)).build();
    ServiceEntity serviceWithStableDT =
        ServiceEntity.builder().yaml(readFile("serviceWithStableDT.yaml", SERVICE_RESOURCE_PATH_PREFIX)).build();
    Page<ServiceEntity> serviceEntities = new PageImpl<>(Arrays.asList(service, serviceWithStableDT), pageable, 1);

    assertThat(customDeploymentYamlHelper.getFilteredServiceEntities(
                   0, 10, new ArrayList<>(), "account.OpenStack", "", serviceEntities))
        .containsExactly(serviceWithStableDT);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testGetFilteredServiceEntitiesWithInValidServiceTemplate() {
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, ServiceKeys.createdAt));
    ServiceEntity service =
        ServiceEntity.builder().yaml(readFile("service.yaml", SERVICE_RESOURCE_PATH_PREFIX)).build();

    assertThat(customDeploymentYamlHelper.isDeploymentTemplateService("id", "V1", service)).isFalse();
    assertThat(customDeploymentYamlHelper.isDeploymentTemplateService("id", "V1", null)).isFalse();
    assertThat(customDeploymentYamlHelper.isDeploymentTemplateService("id", "1", null)).isFalse();

    service.setYaml(null);
    Page<ServiceEntity> serviceEntities = new PageImpl<>(Collections.singletonList(service), pageable, 1);
    assertThat(customDeploymentYamlHelper.getFilteredServiceEntities(
                   0, 10, new ArrayList<>(), "account.OpenStack", "", serviceEntities))
        .isEmpty();
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetFilteredInfraEntities() {
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, InfrastructureEntityKeys.createdAt));
    InfrastructureEntity infra =
        InfrastructureEntity.builder().yaml(readFile("infrastructure.yaml", INFRA_RESOURCE_PATH_PREFIX)).build();
    InfrastructureEntity invalidInfra =
        InfrastructureEntity.builder().yaml(readFile("service.yaml", SERVICE_RESOURCE_PATH_PREFIX)).build();
    InfrastructureEntity infraWithoutSpec =
        InfrastructureEntity.builder()
            .yaml(readFile("infrastructureWithoutSpec.yaml", INFRA_RESOURCE_PATH_PREFIX))
            .build();
    InfrastructureEntity infraWithoutDTRef =
        InfrastructureEntity.builder()
            .yaml(readFile("infrastructureWithoutDTRef.yaml", INFRA_RESOURCE_PATH_PREFIX))
            .build();
    InfrastructureEntity infraWithoutVersionLabel =
        InfrastructureEntity.builder()
            .yaml(readFile("infrastructureWithStableDT.yaml", INFRA_RESOURCE_PATH_PREFIX))
            .build();
    Page<InfrastructureEntity> infraEntities =
        new PageImpl<>(Arrays.asList(infra, invalidInfra, infraWithoutSpec, infraWithoutDTRef, infraWithoutDTRef,
                           infraWithoutVersionLabel),
            pageable, 1);

    assertThat(customDeploymentYamlHelper.getFilteredInfraEntities(
                   0, 10, new ArrayList<>(), "account.OpenStack", "V1", infraEntities))
        .containsExactly(infra);
  }
  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetFilteredInfraEntitiesWithStableTemplate() {
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, InfrastructureEntityKeys.createdAt));
    InfrastructureEntity infra =
        InfrastructureEntity.builder().yaml(readFile("infrastructure.yaml", INFRA_RESOURCE_PATH_PREFIX)).build();
    InfrastructureEntity infraWithStableDT =
        InfrastructureEntity.builder()
            .yaml(readFile("infrastructureWithStableDT.yaml", INFRA_RESOURCE_PATH_PREFIX))
            .build();
    Page<InfrastructureEntity> infraEntities = new PageImpl<>(Arrays.asList(infra, infraWithStableDT), pageable, 1);

    assertThat(customDeploymentYamlHelper.getFilteredInfraEntities(
                   0, 10, new ArrayList<>(), "account.OpenStack", "", infraEntities))
        .containsExactly(infraWithStableDT);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testValidateTemplateYaml() {
    String templateYaml = readFile("entityTemplate.yaml", "");
    customDeploymentYamlHelper.validateTemplateYaml(templateYaml);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetVariables() {
    String templateYaml = readFile("template.yaml", TEMPLATE_RESOURCE_PATH_PREFIX);
    assertThat(customDeploymentYamlHelper.getVariables(templateYaml))
        .isEqualTo(readFile("templateVariables.yaml", TEMPLATE_RESOURCE_PATH_PREFIX));
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetVariablesEmptyTemplate() {
    assertThatThrownBy(() -> customDeploymentYamlHelper.getVariables(""))
        .hasMessage("Template yaml to create template inputs cannot be empty");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetVariablesInvalidTemplate() {
    assertThatThrownBy(
        () -> customDeploymentYamlHelper.getVariables(readFile("service.yaml", SERVICE_RESOURCE_PATH_PREFIX)))
        .hasMessage("Yaml provided is not a template yaml.");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetVariablesTemplateWithNoSpec() {
    assertThatThrownBy(()
                           -> customDeploymentYamlHelper.getVariables(
                               readFile("templateWithNoSpec.yaml", TEMPLATE_RESOURCE_PATH_PREFIX)))
        .hasMessage("Template yaml provided does not have spec in it.");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetVariablesTemplateWithNoInfra() {
    assertThatThrownBy(()
                           -> customDeploymentYamlHelper.getVariables(
                               readFile("templateWithNoInfra.yaml", TEMPLATE_RESOURCE_PATH_PREFIX)))
        .hasMessage("Template yaml provided does not have infrastructure in it.");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetVariablesWithNoVariables() {
    String templateYaml = readFile("templateWithNoVariables.yaml", TEMPLATE_RESOURCE_PATH_PREFIX);
    assertThatThrownBy(() -> customDeploymentYamlHelper.getVariables(templateYaml))
        .hasMessage("Template yaml provided does not have variables in it.");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testValidateTemplateYamlWithNoFetchInstanceNode() {
    String templateYaml = readFile("templateWithNoScriptNode.yaml", TEMPLATE_RESOURCE_PATH_PREFIX);
    assertThatThrownBy(() -> customDeploymentYamlHelper.validateTemplateYaml(templateYaml))
        .hasMessage("Template yaml is not valid: Template yaml provided does not have Fetch Instance Script in it.");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetUpdatedYamlTemplateNoVariables() {
    String templateYaml = readFile("templateWithNoVariables.yaml", TEMPLATE_RESOURCE_PATH_PREFIX);
    String infraYaml = readFile("infrastructure.yaml", INFRA_RESOURCE_PATH_PREFIX);
    String updatedYaml = customDeploymentYamlHelper.getUpdatedYaml(templateYaml, infraYaml, ACCOUNT_ID);
    log.info(updatedYaml);
    assertThat(updatedYaml).isEqualTo(readFile("infrastructureWithEmptyVariables.yaml", INFRA_RESOURCE_PATH_PREFIX));
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetUpdatedYamlAllVariables() {
    String templateYaml = readFile("templateWithAllVariables.yaml", TEMPLATE_RESOURCE_PATH_PREFIX);
    String infraYaml = readFile("infrastructureWithNoVariables.yaml", INFRA_RESOURCE_PATH_PREFIX);
    assertThat(customDeploymentYamlHelper.getUpdatedYaml(templateYaml, infraYaml, ACCOUNT_ID))
        .isEqualTo(readFile("infrastructure.yaml", INFRA_RESOURCE_PATH_PREFIX));
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetUpdatedYamlDiffTypeVariable() {
    String templateYaml = readFile("template.yaml", TEMPLATE_RESOURCE_PATH_PREFIX);
    String infraYaml = readFile("infrastructureWithDiffVarType.yaml", INFRA_RESOURCE_PATH_PREFIX);
    assertThat(customDeploymentYamlHelper.getUpdatedYaml(templateYaml, infraYaml, ACCOUNT_ID))
        .isEqualTo(readFile("infrastructureWithSameVarType.yaml", INFRA_RESOURCE_PATH_PREFIX));
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetUpdatedYamlNoChange() {
    String templateYaml = readFile("template.yaml", TEMPLATE_RESOURCE_PATH_PREFIX);
    String infraYaml = readFile("infrastructureWithSameVarType.yaml", INFRA_RESOURCE_PATH_PREFIX);
    assertThat(customDeploymentYamlHelper.getUpdatedYaml(templateYaml, infraYaml, ACCOUNT_ID))
        .isEqualTo(readFile("infrastructureWithSameVarType.yaml", INFRA_RESOURCE_PATH_PREFIX));
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetUpdatedYamlInvalidTemplate() {
    String templateYaml = readFile("service.yaml", SERVICE_RESOURCE_PATH_PREFIX);
    String infraYaml = readFile("infrastructureWithSameVarType.yaml", INFRA_RESOURCE_PATH_PREFIX);
    assertThatThrownBy(() -> customDeploymentYamlHelper.getUpdatedYaml(templateYaml, infraYaml, ACCOUNT_ID))
        .hasMessage(
            "Error Encountered in infra updation while reading yamls for template and Infra: template yaml cannot be empty");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetUpdatedYamlNoTemplateSpec() {
    String templateYaml = readFile("templateWithNoSpec.yaml", TEMPLATE_RESOURCE_PATH_PREFIX);
    String infraYaml = readFile("infrastructureWithSameVarType.yaml", INFRA_RESOURCE_PATH_PREFIX);
    assertThatThrownBy(() -> customDeploymentYamlHelper.getUpdatedYaml(templateYaml, infraYaml, ACCOUNT_ID))
        .hasMessage(
            "Error Encountered in infra updation while reading yamls for template and Infra: template yaml spec cannot be empty");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetUpdatedYamlNoTemplateInfra() {
    String templateYaml = readFile("templateWithNoInfra.yaml", TEMPLATE_RESOURCE_PATH_PREFIX);
    String infraYaml = readFile("infrastructureWithSameVarType.yaml", INFRA_RESOURCE_PATH_PREFIX);
    assertThatThrownBy(() -> customDeploymentYamlHelper.getUpdatedYaml(templateYaml, infraYaml, ACCOUNT_ID))
        .hasMessage(
            "Error Encountered in infra updation while reading yamls for template and Infra: template yaml infra cannot be empty");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetUpdatedYamlNoInfraDef() {
    String templateYaml = readFile("template.yaml", TEMPLATE_RESOURCE_PATH_PREFIX);
    String infraYaml = readFile("template.yaml", TEMPLATE_RESOURCE_PATH_PREFIX);
    assertThatThrownBy(() -> customDeploymentYamlHelper.getUpdatedYaml(templateYaml, infraYaml, ACCOUNT_ID))
        .hasMessage(
            "Error Encountered in infra updation while reading yamls for template and Infra: infra yaml cannot be empty");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetUpdatedYamlNoInfraSpec() {
    String templateYaml = readFile("template.yaml", TEMPLATE_RESOURCE_PATH_PREFIX);
    String infraYaml = readFile("infrastructureWithoutSpec.yaml", INFRA_RESOURCE_PATH_PREFIX);
    assertThatThrownBy(() -> customDeploymentYamlHelper.getUpdatedYaml(templateYaml, infraYaml, ACCOUNT_ID))
        .hasMessage(
            "Error Encountered in infra updation while reading yamls for template and Infra: infra yaml spec cannot be empty");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testValidateTemplateYamlWithNoStore() {
    String templateYaml = readFile("templateWithNoStore.yaml", TEMPLATE_RESOURCE_PATH_PREFIX);
    assertThatThrownBy(() -> customDeploymentYamlHelper.validateTemplateYaml(templateYaml))
        .hasMessage("Template yaml is not valid: Template yaml provided does not have store in it.");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testValidateTemplateYamlWithNoStoreType() {
    String templateYaml = readFile("templateWithNoStoreType.yaml", TEMPLATE_RESOURCE_PATH_PREFIX);
    assertThatThrownBy(() -> customDeploymentYamlHelper.validateTemplateYaml(templateYaml))
        .hasMessage("Template yaml is not valid: Template yaml provided does not have store type in it.");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testValidateTemplateYamlWithMultipleScripts() {
    String templateYaml = readFile("templateWithMultipleScripts.yaml", TEMPLATE_RESOURCE_PATH_PREFIX);
    assertThatThrownBy(() -> customDeploymentYamlHelper.validateTemplateYaml(templateYaml))
        .hasMessage("Template yaml is not valid: Only one fetch instance script is allowed");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testValidateTemplateYamlWithNoScript() {
    String templateYaml = readFile("templateWithNoScript.yaml", TEMPLATE_RESOURCE_PATH_PREFIX);
    assertThatThrownBy(() -> customDeploymentYamlHelper.validateTemplateYaml(templateYaml))
        .hasMessage("Template yaml is not valid: Scoped file path cannot be null or empty");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testValidateTemplateYamlWithNoContent() {
    String templateYaml = readFile("templateWithNoContent.yaml", TEMPLATE_RESOURCE_PATH_PREFIX);
    assertThatThrownBy(() -> customDeploymentYamlHelper.validateTemplateYaml(templateYaml))
        .hasMessage("Template yaml is not valid: Fetch Instance script cannot be empty");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testValidateTemplateYamlWithInvalidStore() {
    String templateYaml = readFile("templateWithInvalidFileStore.yaml", TEMPLATE_RESOURCE_PATH_PREFIX);
    assertThatThrownBy(() -> customDeploymentYamlHelper.validateTemplateYaml(templateYaml))
        .hasMessage("Template yaml is not valid: Only Inline/Harness Store can be used for fetch instance script");
  }
}
