/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.infrastructure.resource;

import static io.harness.rule.OwnerRule.ABHINAV2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.category.element.UnitTests;
import io.harness.cdng.infra.mapper.InfrastructureEntityConfigMapper;
import io.harness.cdng.infra.yaml.InfrastructureConfig;
import io.harness.cdng.infra.yaml.InfrastructureDefinitionConfig;
import io.harness.cdng.infra.yaml.K8sAwsInfrastructure;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.utils.IdentifierRefHelper;

import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.CDP)
public class InfrastructureHelperTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @InjectMocks InfrastructureHelper infrastructureHelper = new InfrastructureHelper();
  @Mock InfrastructureEntityService infrastructureEntityService;

  @Mock IdentifierRef identifierRef;

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testGetConnectorRefNegativeCases() {
    assertThatThrownBy(() -> infrastructureHelper.getConnectorRef("", "org", "proj", "env", "inf"))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> infrastructureHelper.getConnectorRef("acc", "org", "proj", "", "inf"))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> infrastructureHelper.getConnectorRef("acc", "org", "proj", "env", ""))
        .isInstanceOf(InvalidRequestException.class);

    doReturn(Optional.empty()).when(infrastructureEntityService).get(any(), any(), any(), any(), any());
    assertThatThrownBy(() -> infrastructureHelper.getConnectorRef("acc", "org", "proj", "env", "inf"))
        .isInstanceOf(GeneralException.class);

    try (MockedStatic<InfrastructureEntityConfigMapper> mockedMapper =
             mockStatic(InfrastructureEntityConfigMapper.class)) {
      doReturn(Optional.of(InfrastructureEntity.builder().build()))
          .when(infrastructureEntityService)
          .get(any(), any(), any(), any(), any());
      mockedMapper.when(() -> InfrastructureEntityConfigMapper.toInfrastructureConfig(any(InfrastructureEntity.class)))
          .thenReturn(
              InfrastructureConfig.builder()
                  .infrastructureDefinitionConfig(InfrastructureDefinitionConfig.builder()
                                                      .spec(K8sAwsInfrastructure.builder()
                                                                .connectorRef(ParameterField.<String>builder().build())
                                                                .build())
                                                      .build())
                  .build());
      assertThatThrownBy(() -> infrastructureHelper.getConnectorRef("acc", "org", "proj", "env", "inf"))
          .isInstanceOf(InvalidRequestException.class);
    }
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testGetConnectorRef() {
    try (MockedStatic<InfrastructureEntityConfigMapper> mockedInfraMapper =
             mockStatic(InfrastructureEntityConfigMapper.class);
         MockedStatic<IdentifierRefHelper> mockedIdMapper = mockStatic(IdentifierRefHelper.class)) {
      doReturn(Optional.of(InfrastructureEntity.builder().build()))
          .when(infrastructureEntityService)
          .get(any(), any(), any(), any(), any());
      mockedInfraMapper
          .when(() -> InfrastructureEntityConfigMapper.toInfrastructureConfig(any(InfrastructureEntity.class)))
          .thenReturn(
              InfrastructureConfig.builder()
                  .infrastructureDefinitionConfig(
                      InfrastructureDefinitionConfig.builder()
                          .spec(K8sAwsInfrastructure.builder()
                                    .connectorRef(ParameterField.<String>builder().value("connectorRef").build())
                                    .build())
                          .build())
                  .build());
      mockedIdMapper.when(() -> IdentifierRefHelper.getIdentifierRef(any(), any(), any(), any()))
          .thenReturn(identifierRef);
      IdentifierRef actualRef = infrastructureHelper.getConnectorRef("acc", "org", "proj", "env", "inf");
      assertThat(actualRef).isEqualTo(identifierRef);
    }
  }
}
