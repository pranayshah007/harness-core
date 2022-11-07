/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.custom.deployment;

import static io.harness.rule.OwnerRule.ANIL;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.customdeployment.helper.CustomDeploymentEntitySetupHelper;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class CustomDeploymentEntitySetupHelperTest extends CategoryTest {
  private static final String INFRA = "infra";
  private static final String ACCOUNT = "accIdentifier";
  private static final String ORG = "orgIdentifier";
  private static final String PROJECT = "projectIdentifier";
  private static final String ENVIRONMENT = "envIdentifier";
  private static final String RESOURCE_PATH_PREFIX = "custom/deployment/";
  private static final String INFRA_RESOURCE_PATH_PREFIX = "infrastructure/";
  private static final String STABLE_VERSION = "__STABLE__";
  private static final String ERROR_FOR_INVALID_TEMPLATE =
      "Could not add the reference in entity setup usage for infraRef :[infra]"
      + " and [Could not fetch the template reference from yaml for infraRef : [infra]]";

  @Mock private Producer eventProducer;
  @Spy @InjectMocks private CustomDeploymentEntitySetupHelper customDeploymentEntitySetupHelper;
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
  public void testAddReferencesInEntitySetupUsageForAccountLevelTemplate() {
    String infraYaml = readFile("infrastructure.yaml", INFRA_RESOURCE_PATH_PREFIX);
    InfrastructureEntity infrastructureEntity = InfrastructureEntity.builder()
                                                    .name(INFRA)
                                                    .identifier(INFRA)
                                                    .accountId(ACCOUNT)
                                                    .orgIdentifier(ORG)
                                                    .projectIdentifier(PROJECT)
                                                    .envIdentifier(ENVIRONMENT)
                                                    .yaml(infraYaml)
                                                    .build();
    customDeploymentEntitySetupHelper.addReferencesInEntitySetupUsage(infrastructureEntity);
    ArgumentCaptor<EntityDetailProtoDTO> entityDTOArgumentCaptor = ArgumentCaptor.forClass(EntityDetailProtoDTO.class);
    final ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
    verify(customDeploymentEntitySetupHelper, times(1))
        .publishSetupUsageEvent(any(), entityDTOArgumentCaptor.capture());
    verify(eventProducer, times(1)).send(messageArgumentCaptor.capture());
    assertRedisEvent(
        messageArgumentCaptor.getAllValues().get(0), EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION, "TEMPLATE");
    assertThat(entityDTOArgumentCaptor.getValue().getTemplateRef().getIdentifier().getValue()).isEqualTo("OpenStack");
    assertThat(entityDTOArgumentCaptor.getValue().getTemplateRef().getVersionLabel().getValue()).isEqualTo("V1");
    assertThat(entityDTOArgumentCaptor.getValue().getTemplateRef().getAccountIdentifier().getValue())
        .isEqualTo(ACCOUNT);
  }

  @Test
  @Owner(developers = OwnerRule.SOURABH)
  @Category(UnitTests.class)
  public void testAddReferencesInEntitySetupUsageForOrgLevelTemplateStableVersion()
      throws IOException, ClassNotFoundException {
    String infraYaml = readFile("infrastructureOrg.yaml", INFRA_RESOURCE_PATH_PREFIX);
    InfrastructureEntity infrastructureEntity = InfrastructureEntity.builder()
                                                    .name(INFRA)
                                                    .identifier(INFRA)
                                                    .accountId(ACCOUNT)
                                                    .orgIdentifier(ORG)
                                                    .projectIdentifier(PROJECT)
                                                    .envIdentifier(ENVIRONMENT)
                                                    .yaml(infraYaml)
                                                    .build();
    customDeploymentEntitySetupHelper.addReferencesInEntitySetupUsage(infrastructureEntity);
    ArgumentCaptor<EntityDetailProtoDTO> entityDTOArgumentCaptor = ArgumentCaptor.forClass(EntityDetailProtoDTO.class);
    final ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
    verify(customDeploymentEntitySetupHelper, times(1))
        .publishSetupUsageEvent(any(), entityDTOArgumentCaptor.capture());
    verify(eventProducer, times(1)).send(messageArgumentCaptor.capture());
    assertRedisEvent(
        messageArgumentCaptor.getAllValues().get(0), EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION, "TEMPLATE");
    assertThat(entityDTOArgumentCaptor.getValue().getTemplateRef().getIdentifier().getValue()).isEqualTo("OpenStack");
    assertThat(entityDTOArgumentCaptor.getValue().getTemplateRef().getVersionLabel().getValue())
        .isEqualTo(STABLE_VERSION);
    assertThat(entityDTOArgumentCaptor.getValue().getTemplateRef().getAccountIdentifier().getValue())
        .isEqualTo(ACCOUNT);
    assertThat(entityDTOArgumentCaptor.getValue().getTemplateRef().getOrgIdentifier().getValue()).isEqualTo(ORG);
  }

  @Test
  @Owner(developers = OwnerRule.SOURABH)
  @Category(UnitTests.class)
  public void testAddReferencesInEntitySetupUsageForProjectLevelTemplate() throws IOException, ClassNotFoundException {
    String infraYaml = readFile("infrastructureProject.yaml", INFRA_RESOURCE_PATH_PREFIX);
    InfrastructureEntity infrastructureEntity = InfrastructureEntity.builder()
                                                    .name(INFRA)
                                                    .identifier(INFRA)
                                                    .accountId(ACCOUNT)
                                                    .orgIdentifier(ORG)
                                                    .projectIdentifier(PROJECT)
                                                    .envIdentifier(ENVIRONMENT)
                                                    .yaml(infraYaml)
                                                    .build();
    customDeploymentEntitySetupHelper.addReferencesInEntitySetupUsage(infrastructureEntity);
    ArgumentCaptor<EntityDetailProtoDTO> entityDTOArgumentCaptor = ArgumentCaptor.forClass(EntityDetailProtoDTO.class);
    final ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
    verify(customDeploymentEntitySetupHelper, times(1))
        .publishSetupUsageEvent(any(), entityDTOArgumentCaptor.capture());
    verify(eventProducer, times(1)).send(messageArgumentCaptor.capture());
    assertRedisEvent(
        messageArgumentCaptor.getAllValues().get(0), EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION, "TEMPLATE");
    assertThat(entityDTOArgumentCaptor.getValue().getTemplateRef().getIdentifier().getValue()).isEqualTo("OpenStack");
    assertThat(entityDTOArgumentCaptor.getValue().getTemplateRef().getVersionLabel().getValue())
        .isEqualTo(STABLE_VERSION);
    assertThat(entityDTOArgumentCaptor.getValue().getTemplateRef().getAccountIdentifier().getValue())
        .isEqualTo(ACCOUNT);
    assertThat(entityDTOArgumentCaptor.getValue().getTemplateRef().getOrgIdentifier().getValue()).isEqualTo(ORG);
    assertThat(entityDTOArgumentCaptor.getValue().getTemplateRef().getProjectIdentifier().getValue())
        .isEqualTo(PROJECT);
  }

  @Test
  @Owner(developers = OwnerRule.SOURABH)
  @Category(UnitTests.class)
  public void testDeleteReferencesInEntitySetupUsage() throws IOException, ClassNotFoundException {
    String infraYaml = readFile("infrastructure.yaml", INFRA_RESOURCE_PATH_PREFIX);
    InfrastructureEntity infrastructureEntity = InfrastructureEntity.builder()
                                                    .name(INFRA)
                                                    .identifier(INFRA)
                                                    .accountId(ACCOUNT)
                                                    .orgIdentifier(ORG)
                                                    .projectIdentifier(PROJECT)
                                                    .envIdentifier(ENVIRONMENT)
                                                    .yaml(infraYaml)
                                                    .build();
    customDeploymentEntitySetupHelper.deleteReferencesInEntitySetupUsage(infrastructureEntity);
    final ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
    verify(eventProducer, times(1)).send(messageArgumentCaptor.capture());
    Message message = messageArgumentCaptor.getAllValues().get(0);
    assertEquals(message.getMetadataOrThrow(EventsFrameworkMetadataConstants.ACTION),
        EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION);
    assertNotNull(message.getData());
  }

  @Test
  @Owner(developers = OwnerRule.SOURABH)
  @Category(UnitTests.class)
  public void testForEmptyInfraDefinition() {
    String infraYaml = readFile("emptyInfrastructure.yaml", INFRA_RESOURCE_PATH_PREFIX);
    InfrastructureEntity infrastructureEntity = InfrastructureEntity.builder()
                                                    .name(INFRA)
                                                    .identifier(INFRA)
                                                    .accountId(ACCOUNT)
                                                    .orgIdentifier(ORG)
                                                    .projectIdentifier(PROJECT)
                                                    .envIdentifier(ENVIRONMENT)
                                                    .yaml(infraYaml)
                                                    .build();
    assertThatThrownBy(() -> customDeploymentEntitySetupHelper.addReferencesInEntitySetupUsage(infrastructureEntity))
        .hasMessage(ERROR_FOR_INVALID_TEMPLATE);
  }

  @Test
  @Owner(developers = OwnerRule.SOURABH)
  @Category(UnitTests.class)
  public void testForEmptySpecInfraYaml() {
    String infraYaml = readFile("infrastructureWithoutSpec.yaml", INFRA_RESOURCE_PATH_PREFIX);
    InfrastructureEntity infrastructureEntity = InfrastructureEntity.builder()
                                                    .name(INFRA)
                                                    .identifier(INFRA)
                                                    .accountId(ACCOUNT)
                                                    .orgIdentifier(ORG)
                                                    .projectIdentifier(PROJECT)
                                                    .envIdentifier(ENVIRONMENT)
                                                    .yaml(infraYaml)
                                                    .build();
    assertThatThrownBy(() -> customDeploymentEntitySetupHelper.addReferencesInEntitySetupUsage(infrastructureEntity))
        .hasMessage(ERROR_FOR_INVALID_TEMPLATE);
  }
  @Test
  @Owner(developers = OwnerRule.SOURABH)
  @Category(UnitTests.class)
  public void testForNullTemplateRefInfraYaml() {
    String infraYaml = readFile("infrastructureWithoutTemplateRef.yaml", INFRA_RESOURCE_PATH_PREFIX);
    InfrastructureEntity infrastructureEntity = InfrastructureEntity.builder()
                                                    .name(INFRA)
                                                    .identifier(INFRA)
                                                    .accountId(ACCOUNT)
                                                    .orgIdentifier(ORG)
                                                    .projectIdentifier(PROJECT)
                                                    .envIdentifier(ENVIRONMENT)
                                                    .yaml(infraYaml)
                                                    .build();
    assertThatThrownBy(() -> customDeploymentEntitySetupHelper.addReferencesInEntitySetupUsage(infrastructureEntity))
        .hasMessage(ERROR_FOR_INVALID_TEMPLATE);
  }

  @Test
  @Owner(developers = OwnerRule.SOURABH)
  @Category(UnitTests.class)
  public void testForEmptyTemplateRefInfraYaml() {
    String infraYaml = readFile("infrastructureWithSameVarType.yaml", INFRA_RESOURCE_PATH_PREFIX);
    InfrastructureEntity infrastructureEntity = InfrastructureEntity.builder()
                                                    .name(INFRA)
                                                    .identifier(INFRA)
                                                    .accountId(ACCOUNT)
                                                    .orgIdentifier(ORG)
                                                    .projectIdentifier(PROJECT)
                                                    .envIdentifier(ENVIRONMENT)
                                                    .yaml(infraYaml)
                                                    .build();
    assertThatThrownBy(() -> customDeploymentEntitySetupHelper.addReferencesInEntitySetupUsage(infrastructureEntity))
        .hasMessage(ERROR_FOR_INVALID_TEMPLATE);
  }
  @Test
  @Owner(developers = OwnerRule.SOURABH)
  @Category(UnitTests.class)
  public void testForNullVersionLabel() {
    String infraYaml = readFile("infrastructureWithStableDT.yaml", INFRA_RESOURCE_PATH_PREFIX);
    InfrastructureEntity infrastructureEntity = InfrastructureEntity.builder()
                                                    .name(INFRA)
                                                    .identifier(INFRA)
                                                    .accountId(ACCOUNT)
                                                    .orgIdentifier(ORG)
                                                    .projectIdentifier(PROJECT)
                                                    .envIdentifier(ENVIRONMENT)
                                                    .yaml(infraYaml)
                                                    .build();
    customDeploymentEntitySetupHelper.addReferencesInEntitySetupUsage(infrastructureEntity);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testDeleteReferencesInEntitySetupUsageError() {
    String infraYaml = readFile("infrastructure.yaml", INFRA_RESOURCE_PATH_PREFIX);
    InfrastructureEntity infrastructureEntity = InfrastructureEntity.builder()
                                                    .name(INFRA)
                                                    .identifier(INFRA)
                                                    .accountId(ACCOUNT)
                                                    .orgIdentifier(ORG)
                                                    .projectIdentifier(PROJECT)
                                                    .envIdentifier(ENVIRONMENT)
                                                    .yaml(infraYaml)
                                                    .build();

    doThrow(InvalidRequestException.class).when(eventProducer).send(any());
    customDeploymentEntitySetupHelper.deleteReferencesInEntitySetupUsage(infrastructureEntity);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testAddReferencesInEntityInvalidInfraDefinition() {
    String infraYaml = readFile("infrastructureWithoutDefinition.yaml", INFRA_RESOURCE_PATH_PREFIX);
    InfrastructureEntity infrastructureEntity = InfrastructureEntity.builder()
                                                    .name(INFRA)
                                                    .identifier(INFRA)
                                                    .accountId(ACCOUNT)
                                                    .orgIdentifier(ORG)
                                                    .projectIdentifier(PROJECT)
                                                    .envIdentifier(ENVIRONMENT)
                                                    .yaml(infraYaml)
                                                    .build();

    assertThatThrownBy(() -> customDeploymentEntitySetupHelper.addReferencesInEntitySetupUsage(infrastructureEntity))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Could not add the reference in entity setup usage for infraRef :[infra] and [Could not fetch the template reference from yaml for infraRef : [infra]]");
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testAddReferencesInEntityInvalidDeploymentRef() {
    String infraYaml = readFile("infrastructureWithoutDTRef.yaml", INFRA_RESOURCE_PATH_PREFIX);
    InfrastructureEntity infrastructureEntity = InfrastructureEntity.builder()
                                                    .name(INFRA)
                                                    .identifier(INFRA)
                                                    .accountId(ACCOUNT)
                                                    .orgIdentifier(ORG)
                                                    .projectIdentifier(PROJECT)
                                                    .envIdentifier(ENVIRONMENT)
                                                    .yaml(infraYaml)
                                                    .build();

    assertThatThrownBy(() -> customDeploymentEntitySetupHelper.addReferencesInEntitySetupUsage(infrastructureEntity))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Could not add the reference in entity setup usage for infraRef :[infra] and [Could not fetch the template reference from yaml for infraRef : [infra]]");
  }

  private void assertRedisEvent(Message message, String action, String entityType) {
    assertEquals(message.getMetadataOrThrow(EventsFrameworkMetadataConstants.REFERRED_ENTITY_TYPE), entityType);
    assertEquals(message.getMetadataOrThrow(EventsFrameworkMetadataConstants.ACTION), action);
    assertNotNull(message.getData());
  }
}
