/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.googlefunction;

import static io.harness.rule.OwnerRule.ALLU_VAMSI;

import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.task.googlefunctionbeans.GcpGoogleFunctionInfraConfig;
import io.harness.rule.Owner;

import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class GoogleFunctionTaskHelperBaseTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  private final String FUNCTION = "fun";
  private final String PROJECT = "cd-play";
  private final String REGION = "us-east1";

  @Mock private GoogleFunctionCommandTaskHelper googleFunctionCommandTaskHelper;
  @Mock private GoogleFunctionInfraConfigHelper googleFunctionInfraConfigHelper;

  @InjectMocks private GoogleFunctionTaskHelperBase googleFunctionTaskHelperBase;
  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void createTaskDefinitionTest() throws InvalidProtocolBufferException {
    GcpGoogleFunctionInfraConfig gcpGoogleFunctionInfraConfig = GcpGoogleFunctionInfraConfig.builder()
                                                                    .gcpConnectorDTO(GcpConnectorDTO.builder().build())
                                                                    .region(REGION)
                                                                    .project(PROJECT)
                                                                    .build();

    GoogleFunctionDeploymentReleaseData deploymentReleaseData =
        GoogleFunctionDeploymentReleaseData.builder()
            .function(FUNCTION)
            .region(REGION)
            .googleFunctionInfraConfig(gcpGoogleFunctionInfraConfig)
            .build();

    googleFunctionTaskHelperBase.getGoogleFunctionServerInstanceInfo(deploymentReleaseData);

    verify(googleFunctionCommandTaskHelper)
        .getFunction(deploymentReleaseData.getFunction(), gcpGoogleFunctionInfraConfig.getGcpConnectorDTO(),
            gcpGoogleFunctionInfraConfig.getProject(), gcpGoogleFunctionInfraConfig.getRegion());
  }
}
