/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.service.beans;

import static io.harness.rule.OwnerRule.LOVISH_BANSAL;
import static io.harness.rule.OwnerRule.vivekveman;
import static io.harness.walktree.visitor.utilities.VisitorParentPathUtils.PARENT_PATH_KEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.eventsframework.schemas.entity.ScopeProtoEnum;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.impl.ServiceEntityServiceImpl;
import io.harness.ng.core.service.services.impl.ServiceEntitySetupUsageHelper;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.merger.helpers.FQNMapGenerator;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import com.google.common.io.Resources;
import com.google.protobuf.StringValue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class ServiceEntityVisitorHelperV2Test extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock ServiceEntityServiceImpl serviceEntityService;

  @Mock ServiceEntitySetupUsageHelper serviceEntitySetupUsageHelper;

  @InjectMocks ServiceEntityVisitorHelperV2 serviceEntityVisitorHelperV2;

  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_IDENTIFIER = "orgIdentifier";
  private static final String PROJECT_IDENTIFIER = "projectIdentifier";

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testaddReference() throws IOException {
    Map<String, Object> contextMap = Collections.emptyMap();

    Map<String, Object> serviceInputs = new LinkedHashMap<>();

    ServiceYamlV2 serviceYamlV2 =
        ServiceYamlV2.builder()
            .serviceRef(ParameterField.<String>builder().value("serviceRef").build())
            .serviceInputs(ParameterField.<Map<String, Object>>builder().value(serviceInputs).build())
            .build();

    ServiceEntity serviceEntity = ServiceEntity.builder().build();

    doReturn(Optional.of(serviceEntity))
        .when(serviceEntityService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "serviceRef", false);

    EntityDetailProtoDTO entityDetailProtoDTOPrimaryArtifact =
        EntityDetailProtoDTO.newBuilder()
            .setType(EntityTypeProtoEnum.CONNECTORS)
            .setIdentifierRef(
                IdentifierRefProtoDTO.newBuilder()
                    .setScope(ScopeProtoEnum.ORG)
                    .setAccountIdentifier(StringValue.of(ACCOUNT_ID))
                    .setOrgIdentifier(StringValue.of(ORG_IDENTIFIER))
                    .setIdentifier(StringValue.of("<+input>"))
                    .putMetadata("fqn",
                        "service.serviceInputs.serviceDefinition.spec.artifacts.primary.sources.cs.spec.connectorRef")
                    .putMetadata("expression", "<+input>")
                    .build())
            .build();

    EntityDetailProtoDTO entityDetailProtoDTOManifest =
        EntityDetailProtoDTO.newBuilder()
            .setType(EntityTypeProtoEnum.CONNECTORS)
            .setIdentifierRef(
                IdentifierRefProtoDTO.newBuilder()
                    .setScope(ScopeProtoEnum.ORG)
                    .setAccountIdentifier(StringValue.of(ACCOUNT_ID))
                    .setOrgIdentifier(StringValue.of(ORG_IDENTIFIER))
                    .setIdentifier(StringValue.of("<+input>"))
                    .putMetadata("fqn",
                        "service.serviceInputs.serviceDefinition.spec.manifests.manifestId.spec.store.spec.connectorRef")
                    .putMetadata("expression", "<+input>")
                    .build())
            .build();

    YamlConfig yamlConfig = new YamlConfig(readFile("ServiceInputs.yaml"));

    Map<FQN, Object> fullMap = yamlConfig.getFqnToValueMap();

    Set<EntityDetailProtoDTO> set = new HashSet<>();
    set.add(entityDetailProtoDTOPrimaryArtifact);
    set.add(entityDetailProtoDTOManifest);

    doReturn(set).when(serviceEntitySetupUsageHelper).getAllReferredEntities(serviceEntity);

    mockStatic(FQNMapGenerator.class);

    Mockito.when(FQNMapGenerator.generateFQNMap(any())).thenReturn(fullMap);

    Set<String> entityIds = new HashSet<>();
    entityIds.add("dockerConnectorId");
    entityIds.add("gitConnectorId");
    entityIds.add("serviceRef");

    Set<EntityDetailProtoDTO> entityDetailProtoDTOS = serviceEntityVisitorHelperV2.addReference(
        serviceYamlV2, ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, contextMap);

    assertThat(entityIds.size()).isEqualTo(entityDetailProtoDTOS.size());

    Set<String> fetchedEntityIds = entityDetailProtoDTOS.stream()
                                       .map(a -> a.getIdentifierRef().getIdentifier().getValue())
                                       .collect(Collectors.toSet());

    for (String id : entityIds) {
      assertThat(fetchedEntityIds.contains(id)).isTrue();
    }

    serviceYamlV2 = ServiceYamlV2.builder().serviceRef(ParameterField.<String>builder().value("").build()).build();

    entityDetailProtoDTOS = serviceEntityVisitorHelperV2.addReference(
        serviceYamlV2, ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, contextMap);
    assertThat(entityDetailProtoDTOS).isEmpty();
  }

  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testaddReferenceMultiServiceCase() throws IOException {
    Map<String, Object> contextMap = new HashMap<>();
    LinkedList<String> linkedList = new LinkedList<>();
    linkedList.add("pipeline");
    linkedList.add("stages");
    linkedList.add("s1");
    linkedList.add("spec");
    linkedList.add("services");
    linkedList.add("values");
    contextMap.put(PARENT_PATH_KEY, linkedList);
    Map<String, Object> serviceInputs = new LinkedHashMap<>();
    ServiceYamlV2 serviceYamlV2 =
        ServiceYamlV2.builder()
            .serviceRef(ParameterField.createExpressionField(true, "<+input>", null, true))
            .serviceInputs(ParameterField.<Map<String, Object>>builder().value(serviceInputs).build())
            .build();

    EntityDetailProtoDTO entityDetailProtoDTO =
        EntityDetailProtoDTO.newBuilder()
            .setType(EntityTypeProtoEnum.SERVICE)
            .setIdentifierRef(IdentifierRefProtoDTO.newBuilder()
                                  .setScope(ScopeProtoEnum.UNKNOWN)
                                  .setAccountIdentifier(StringValue.of(ACCOUNT_ID))
                                  .setOrgIdentifier(StringValue.of(ORG_IDENTIFIER))
                                  .setProjectIdentifier(StringValue.of(PROJECT_IDENTIFIER))
                                  .setIdentifier(StringValue.of("<+input>"))
                                  .putMetadata("fqn", "pipeline.stages.s1.spec.services.values")
                                  .putMetadata("yamlTypeRefName", "serviceRef")
                                  .putMetadata("expression", "<+input>")
                                  .build())
            .build();

    Set<EntityDetailProtoDTO> entityDetailProtoDTOS = serviceEntityVisitorHelperV2.addReference(
        serviceYamlV2, ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, contextMap);

    assertThat(entityDetailProtoDTOS).contains(entityDetailProtoDTO);
  }
}
