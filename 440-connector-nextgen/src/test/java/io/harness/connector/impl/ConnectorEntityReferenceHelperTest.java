/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.impl;

import static io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.TEMPLATE;
import static io.harness.ng.core.entitysetupusage.dto.SetupUsageDetailType.TEMPLATE_REFERRED_BY_CONNECTOR;
import static io.harness.rule.OwnerRule.MEENAKSHI;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorsTestBase;
import io.harness.connector.utils.TemplateDetails;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.customsecretmanager.CustomSecretManagerConnectorDTO;
import io.harness.delegate.beans.connector.customsecretmanager.TemplateLinkConfigForCustomSecretManager;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.eventsframework.schemas.entity.ScopeProtoEnum;
import io.harness.eventsframework.schemas.entity.TemplateReferenceProtoDTO;
import io.harness.eventsframework.schemas.entitysetupusage.EntityDetailWithSetupUsageDetailProtoDTO;
import io.harness.rule.Owner;

import com.google.protobuf.StringValue;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class ConnectorEntityReferenceHelperTest extends ConnectorsTestBase {
  @Mock SecretRefInputValidationHelper secretRefInputValidationHelper;
  @Mock Producer eventProducer;
  @Spy @InjectMocks ConnectorEntityReferenceHelper connectorEntityReferenceHelper;
  @Rule public ExpectedException exceptionRule = ExpectedException.none();

  String CONNECTOR_IDENTIFIER = randomAlphabetic(10);
  String ACCOUNT_IDENTIFIER = randomAlphabetic(10);
  String ORG_IDENTIFIER = randomAlphabetic(10);
  String PROJECT_IDENTIFIER = randomAlphabetic(10);
  String TEMPLATE_REF = "templateRef";
  String VERSION = "VERSION1";

  IdentifierRefProtoDTO connectorRef = IdentifierRefProtoDTO.newBuilder()
                                           .setIdentifier(StringValue.of(CONNECTOR_IDENTIFIER))
                                           .setAccountIdentifier(StringValue.of(ACCOUNT_IDENTIFIER))
                                           .build();

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }
  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testProduceEventForTemplateSetupUsage() {
    final ConnectorInfoDTO connectorInfo = getConnectorWithTemplateRef(TEMPLATE_REF);
    final List<EntityDetailWithSetupUsageDetailProtoDTO> allTemplate =
        connectorEntityReferenceHelper.getTemplateDTOFromConnectorInfoDTO(connectorInfo, "accountId");
    when(eventProducer.send(any())).thenReturn("eventId");
    boolean result = connectorEntityReferenceHelper.produceEventForTemplateSetupUsage(
        connectorInfo, allTemplate, ACCOUNT_IDENTIFIER, null, null, "");
    assertThat(result).isEqualTo(true);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testProduceEventForTemplateSetupUsage_throwException() {
    final ConnectorInfoDTO connectorInfo = getConnectorWithTemplateRef(TEMPLATE_REF);
    final List<EntityDetailWithSetupUsageDetailProtoDTO> allTemplate =
        connectorEntityReferenceHelper.getTemplateDTOFromConnectorInfoDTO(connectorInfo, "accountId");
    when(eventProducer.send(any())).thenThrow(EventsFrameworkDownException.class);
    boolean result = connectorEntityReferenceHelper.produceEventForTemplateSetupUsage(
        connectorInfo, allTemplate, ACCOUNT_IDENTIFIER, null, null, "");
    assertThat(result).isEqualTo(false);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testGetTemplateDTOFromConnectorInfoDTO() {
    final ConnectorInfoDTO connectorInfo = getConnectorWithTemplateRef(TEMPLATE_REF);
    List<EntityDetailWithSetupUsageDetailProtoDTO> expectedTemplateDetails = new ArrayList<>();
    EntityDetailWithSetupUsageDetailProtoDTO.TemplateReferredByConnectorSetupUsageDetailProtoDTO detailProtoDTO =
        EntityDetailWithSetupUsageDetailProtoDTO.TemplateReferredByConnectorSetupUsageDetailProtoDTO.newBuilder()
            .setIdentifier(TEMPLATE_REF)
            .setVersion(VERSION)
            .build();

    expectedTemplateDetails.add(
        EntityDetailWithSetupUsageDetailProtoDTO.newBuilder()
            .setReferredEntity(EntityDetailProtoDTO.newBuilder()
                                   .setType(TEMPLATE)
                                   .setTemplateRef(TemplateReferenceProtoDTO.newBuilder()
                                                       .setScope(ScopeProtoEnum.PROJECT)
                                                       .setVersionLabel(StringValue.of(VERSION))
                                                       .setAccountIdentifier(StringValue.of(ACCOUNT_IDENTIFIER))
                                                       .setProjectIdentifier(StringValue.of(PROJECT_IDENTIFIER))
                                                       .setOrgIdentifier(StringValue.of(ORG_IDENTIFIER))
                                                       .setIdentifier(StringValue.of(TEMPLATE_REF))
                                                       .build())
                                   .build())
            .setType(TEMPLATE_REFERRED_BY_CONNECTOR.toString())
            .setTemplateConnectorDetail(detailProtoDTO)
            .build());

    List<EntityDetailWithSetupUsageDetailProtoDTO> actualTemplateDetails =
        connectorEntityReferenceHelper.getTemplateDTOFromConnectorInfoDTO(connectorInfo, ACCOUNT_IDENTIFIER);
    assertThat(actualTemplateDetails).isEqualTo(expectedTemplateDetails);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testGetEntityDetailProtoDTO_accountScopeTemplate() {
    EntityDetailProtoDTO expectedEntityDetailProtoDTO =
        EntityDetailProtoDTO.newBuilder()
            .setType(TEMPLATE)
            .setTemplateRef(TemplateReferenceProtoDTO.newBuilder()
                                .setScope(ScopeProtoEnum.ACCOUNT)
                                .setVersionLabel(StringValue.of(VERSION))
                                .setAccountIdentifier(StringValue.of(ACCOUNT_IDENTIFIER))

                                .setIdentifier(StringValue.of(TEMPLATE_REF))
                                .build())
            .build();

    TemplateDetails templateDetails =
        TemplateDetails.builder().templateRef("account." + TEMPLATE_REF).versionLabel(VERSION).build();
    EntityDetailProtoDTO entityDetailProtoDTO = connectorEntityReferenceHelper.getEntityDetailProtoDTO(
        templateDetails, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    assertThat(entityDetailProtoDTO).isEqualToComparingFieldByField(expectedEntityDetailProtoDTO);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testGetEntityDetailProtoDTO_orgScopeTemplate() {
    EntityDetailProtoDTO expectedEntityDetailProtoDTO =
        EntityDetailProtoDTO.newBuilder()
            .setType(TEMPLATE)
            .setTemplateRef(TemplateReferenceProtoDTO.newBuilder()
                                .setScope(ScopeProtoEnum.ORG)
                                .setVersionLabel(StringValue.of(VERSION))
                                .setAccountIdentifier(StringValue.of(ACCOUNT_IDENTIFIER))
                                .setOrgIdentifier(StringValue.of(ORG_IDENTIFIER))
                                .setIdentifier(StringValue.of(TEMPLATE_REF))
                                .build())
            .build();

    TemplateDetails templateDetails =
        TemplateDetails.builder().templateRef("org." + TEMPLATE_REF).versionLabel(VERSION).build();
    EntityDetailProtoDTO entityDetailProtoDTO = connectorEntityReferenceHelper.getEntityDetailProtoDTO(
        templateDetails, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    assertThat(entityDetailProtoDTO).isEqualToComparingFieldByField(expectedEntityDetailProtoDTO);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testGetEntityDetailProtoDTO_projectScopeTemplate() {
    EntityDetailProtoDTO expectedEntityDetailProtoDTO =
        EntityDetailProtoDTO.newBuilder()
            .setType(TEMPLATE)
            .setTemplateRef(TemplateReferenceProtoDTO.newBuilder()
                                .setScope(ScopeProtoEnum.PROJECT)
                                .setVersionLabel(StringValue.of(VERSION))
                                .setAccountIdentifier(StringValue.of(ACCOUNT_IDENTIFIER))
                                .setProjectIdentifier(StringValue.of(PROJECT_IDENTIFIER))
                                .setOrgIdentifier(StringValue.of(ORG_IDENTIFIER))
                                .setIdentifier(StringValue.of(TEMPLATE_REF))
                                .build())
            .build();

    TemplateDetails templateDetails = TemplateDetails.builder().templateRef(TEMPLATE_REF).versionLabel(VERSION).build();
    EntityDetailProtoDTO entityDetailProtoDTO = connectorEntityReferenceHelper.getEntityDetailProtoDTO(
        templateDetails, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    assertThat(entityDetailProtoDTO).isEqualToComparingFieldByField(expectedEntityDetailProtoDTO);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testGetEntityDetailProtoDTO_withEmptyVersion() {
    EntityDetailProtoDTO expectedEntityDetailProtoDTO =
        EntityDetailProtoDTO.newBuilder()
            .setType(TEMPLATE)
            .setTemplateRef(TemplateReferenceProtoDTO.newBuilder()
                                .setScope(ScopeProtoEnum.PROJECT)
                                .setVersionLabel(StringValue.of("__STABLE__"))
                                .setAccountIdentifier(StringValue.of(ACCOUNT_IDENTIFIER))
                                .setProjectIdentifier(StringValue.of(PROJECT_IDENTIFIER))
                                .setOrgIdentifier(StringValue.of(ORG_IDENTIFIER))
                                .setIdentifier(StringValue.of(TEMPLATE_REF))
                                .build())
            .build();

    TemplateDetails templateDetails = TemplateDetails.builder().templateRef(TEMPLATE_REF).build();
    EntityDetailProtoDTO entityDetailProtoDTO = connectorEntityReferenceHelper.getEntityDetailProtoDTO(
        templateDetails, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    assertThat(expectedEntityDetailProtoDTO).isEqualToComparingFieldByField(entityDetailProtoDTO);
  }

  private ConnectorInfoDTO getConnectorWithTemplateRef(String templateRef) {
    return ConnectorInfoDTO.builder()
        .connectorType(ConnectorType.CUSTOM_SECRET_MANAGER)
        .name("customSM")
        .identifier(CONNECTOR_IDENTIFIER)
        .projectIdentifier(PROJECT_IDENTIFIER)
        .orgIdentifier(ORG_IDENTIFIER)
        .connectorConfig(CustomSecretManagerConnectorDTO.builder()
                             .template(TemplateLinkConfigForCustomSecretManager.builder()
                                           .templateRef(templateRef)
                                           .versionLabel(VERSION)
                                           .build())
                             .build())
        .build();
  }
}
