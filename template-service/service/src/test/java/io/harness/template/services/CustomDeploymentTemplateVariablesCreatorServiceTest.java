/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.services;

import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import io.harness.TemplateServiceTestBase;
import io.harness.category.element.UnitTests;
import io.harness.customDeployment.remote.CustomDeploymentResourceClient;
import io.harness.ng.core.customDeployment.CustomDeploymentVariableResponseDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.pms.variables.VariableMergeServiceResponse;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;

import java.util.Collections;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;

public class CustomDeploymentTemplateVariablesCreatorServiceTest extends TemplateServiceTestBase {
  @Mock CustomDeploymentResourceClient customDeploymentResourceClient;
  @InjectMocks CustomDeploymentTemplateVariablesCreatorService customDeploymentTemplateVariablesCreatorService;

  private final String ACCOUNT_ID = RandomStringUtils.randomAlphanumeric(6);
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    on(customDeploymentTemplateVariablesCreatorService)
        .set("customDeploymentResourceClient", customDeploymentResourceClient);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testSupportVariables() {
    boolean supportsVariables = customDeploymentTemplateVariablesCreatorService.supportsVariables();
    assertThat(supportsVariables).isTrue();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetVariables() {
    Call<ResponseDTO<CustomDeploymentVariableResponseDTO>> requestCall = mock(Call.class);
    doReturn(requestCall).when(customDeploymentResourceClient).getExpressionVariables(any());
    try (MockedStatic<NGRestUtils> mockStatic = Mockito.mockStatic(NGRestUtils.class)) {
      CustomDeploymentVariableResponseDTO customDeploymentVariableResponseDTO =
          CustomDeploymentVariableResponseDTO.builder().yaml("testYaml").metadataMap(Collections.emptyMap()).build();
      mockStatic.when(() -> NGRestUtils.getResponse(requestCall)).thenReturn(customDeploymentVariableResponseDTO);
      VariableMergeServiceResponse variables = customDeploymentTemplateVariablesCreatorService.getVariables(
          ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "yaml", TemplateEntityType.CUSTOM_DEPLOYMENT_TEMPLATE);
      assertThat(variables.getYaml()).isEqualTo("testYaml");
    }
  }
}