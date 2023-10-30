/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.aws.resources;

import static io.harness.rule.OwnerRule.VITALIE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.IdentifierRef;
import io.harness.category.element.UnitTests;
import io.harness.cdng.aws.service.AwsResourceServiceImpl;
import io.harness.cdng.infra.mapper.InfrastructureEntityConfigMapper;
import io.harness.cdng.infra.yaml.AsgInfrastructure;
import io.harness.cdng.infra.yaml.InfrastructureConfig;
import io.harness.cdng.infra.yaml.InfrastructureDefinitionConfig;
import io.harness.ng.core.artifacts.resources.util.ArtifactResourceUtils;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.utils.IdentifierRefHelper;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

public class AwsHelperResourceTest extends CategoryTest {
  private static final String CONNECTOR_REF = "connectorRef";
  private static final String ACCOUNT_IDENTIFIER = "accountIdentifier";
  private static final String ORG_IDENTIFIER = "orgIdentifier";
  private static final String PROJECT_IDENTIFIER = "projectIdentifier";

  private static final String ENV_ID = "envId";
  private static final String INFRA_DEFINITION_ID = "infraDefinitionId";
  private static final String REGION = "us-east";

  @Mock AwsResourceServiceImpl awsHelperService;
  @Mock InfrastructureEntityService infrastructureEntityService;
  @Mock ArtifactResourceUtils artifactResourceUtils;

  IdentifierRef identifierRef = mock(IdentifierRef.class);

  @InjectMocks AwsHelperResource awsHelperResource;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void getASGNames() {
    List<String> asgList = Arrays.asList("asg1", "asg2");
    try (MockedStatic<IdentifierRefHelper> ignore = mockStatic(IdentifierRefHelper.class)) {
      when(IdentifierRefHelper.getIdentifierRef(anyString(), anyString(), anyString(), anyString()))
          .thenAnswer(i -> identifierRef);
      when(awsHelperService.getASGNames(any(), anyString(), anyString(), anyString())).thenReturn(asgList);

      ResponseDTO<List<String>> result = awsHelperResource.getASGNames(
          CONNECTOR_REF, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, REGION, null, null);
      assertThat(result.getData()).isEqualTo(asgList);
    }
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void getASGNamesByInfra() {
    List<String> asgList = Arrays.asList("asg1", "asg2");
    try (MockedStatic<IdentifierRefHelper> ignore = mockStatic(IdentifierRefHelper.class);
         MockedStatic<InfrastructureEntityConfigMapper> ignore2 = mockStatic(InfrastructureEntityConfigMapper.class)) {
      when(IdentifierRefHelper.getIdentifierRef(anyString(), anyString(), anyString(), anyString()))
          .thenAnswer(i -> identifierRef);
      when(InfrastructureEntityConfigMapper.toInfrastructureConfig(any(InfrastructureEntity.class)))
          .thenAnswer(i
              -> InfrastructureConfig.builder()
                     .infrastructureDefinitionConfig(
                         InfrastructureDefinitionConfig.builder()
                             .spec(AsgInfrastructure.builder()
                                       .connectorRef(ParameterField.createValueField(CONNECTOR_REF))
                                       .region(ParameterField.createValueField(REGION))
                                       .build())
                             .build())
                     .build());
      when(awsHelperService.getASGNames(any(), anyString(), anyString(), anyString())).thenReturn(asgList);
      when(infrastructureEntityService.get(anyString(), anyString(), anyString(), anyString(), anyString()))
          .thenReturn(Optional.of(InfrastructureEntity.builder().build()));

      ResponseDTO<List<String>> result = awsHelperResource.getASGNames(
          null, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, null, ENV_ID, INFRA_DEFINITION_ID);
      assertThat(result.getData()).isEqualTo(asgList);
    }
  }
}
