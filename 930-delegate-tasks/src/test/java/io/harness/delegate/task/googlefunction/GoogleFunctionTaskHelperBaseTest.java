/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.googlefunction;

import static io.harness.delegate.task.googlefunction.GoogleFunctionUtils.ENVIRONMENT_TYPE_GEN_ONE;
import static io.harness.rule.OwnerRule.PRAGYESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.GoogleFunctionServerInstanceInfo;
import io.harness.delegate.task.googlefunctionbeans.GcpGoogleFunctionInfraConfig;
import io.harness.delegate.task.googlefunctionbeans.GoogleFunction;
import io.harness.rule.Owner;

import com.google.api.client.util.Lists;
import com.google.cloud.functions.v1.CloudFunction;
import com.google.cloud.functions.v2.Function;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.List;
import java.util.Optional;
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
  private final String GEN_ONE_ENV = "GenOne";
  private final String GEN_TWO_ENV = "GenTwo";
  private final String REVISION = "revision";
  private final String REVISION_GEN_ONE = "LATEST";

  @Mock private GoogleFunctionCommandTaskHelper googleFunctionCommandTaskHelper;
  @Mock private GoogleFunctionGenOneCommandTaskHelper googleFunctionGenOneCommandTaskHelper;
  @Mock private GoogleFunctionInfraConfigHelper googleFunctionInfraConfigHelper;

  @InjectMocks private GoogleFunctionTaskHelperBase googleFunctionTaskHelperBase;

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void testGoogleFunctionGenOneServerInstances() throws InvalidProtocolBufferException {
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
            .environmentType(ENVIRONMENT_TYPE_GEN_ONE)
            .build();

    CloudFunction cloudFunction = CloudFunction.newBuilder().build();

    GoogleFunction googleFunction =
        GoogleFunction.builder()
            .functionName(FUNCTION)
            .environment(GEN_ONE_ENV)
            .cloudRunService(GoogleFunction.GoogleCloudRunService.builder().revision(REVISION_GEN_ONE).build())
            .activeCloudRunRevisions(Lists.newArrayList())
            .build();

    doReturn(Optional.of(cloudFunction))
        .when(googleFunctionGenOneCommandTaskHelper)
        .getFunction(eq(FUNCTION), eq(gcpGoogleFunctionInfraConfig), isNull());

    doReturn(googleFunction).when(googleFunctionGenOneCommandTaskHelper).getGoogleFunction(eq(cloudFunction), isNull());

    List<ServerInstanceInfo> serverInstanceInfos =
        googleFunctionTaskHelperBase.getGoogleFunctionServerInstanceInfo(deploymentReleaseData);

    assertThat(serverInstanceInfos.size()).isEqualTo(1);
    assertThat(serverInstanceInfos.get(0)).isInstanceOf(GoogleFunctionServerInstanceInfo.class);
    assertThat(((GoogleFunctionServerInstanceInfo) serverInstanceInfos.get(0)).getFunctionName()).isEqualTo(FUNCTION);
    assertThat(((GoogleFunctionServerInstanceInfo) serverInstanceInfos.get(0)).getRegion()).isEqualTo(REGION);
    assertThat(((GoogleFunctionServerInstanceInfo) serverInstanceInfos.get(0)).getProject()).isEqualTo(PROJECT);
    assertThat(((GoogleFunctionServerInstanceInfo) serverInstanceInfos.get(0)).getEnvironmentType())
        .isEqualTo(GEN_ONE_ENV);
    assertThat(((GoogleFunctionServerInstanceInfo) serverInstanceInfos.get(0)).getRevision())
        .isEqualTo(REVISION_GEN_ONE);
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void testGoogleFunctionGenTwoServerInstances() throws InvalidProtocolBufferException {
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
            .environmentType("Gen_2")
            .build();

    Function function = Function.newBuilder().build();

    GoogleFunction googleFunction =
        GoogleFunction.builder()
            .functionName(FUNCTION)
            .environment(GEN_TWO_ENV)
            .cloudRunService(GoogleFunction.GoogleCloudRunService.builder().revision(REVISION).build())
            .activeCloudRunRevisions(
                List.of(GoogleFunction.GoogleCloudRunRevision.builder().revision(REVISION).trafficPercent(100).build()))
            .build();

    doReturn(Optional.of(function))
        .when(googleFunctionCommandTaskHelper)
        .getFunction(eq(FUNCTION), any(), eq(PROJECT), eq(REGION), isNull());

    doReturn(googleFunction)
        .when(googleFunctionCommandTaskHelper)
        .getGoogleFunction(eq(function), eq(gcpGoogleFunctionInfraConfig), isNull());

    List<ServerInstanceInfo> serverInstanceInfos =
        googleFunctionTaskHelperBase.getGoogleFunctionServerInstanceInfo(deploymentReleaseData);

    assertThat(serverInstanceInfos.size()).isEqualTo(1);
    assertThat(serverInstanceInfos.get(0)).isInstanceOf(GoogleFunctionServerInstanceInfo.class);
    assertThat(((GoogleFunctionServerInstanceInfo) serverInstanceInfos.get(0)).getFunctionName()).isEqualTo(FUNCTION);
    assertThat(((GoogleFunctionServerInstanceInfo) serverInstanceInfos.get(0)).getRegion()).isEqualTo(REGION);
    assertThat(((GoogleFunctionServerInstanceInfo) serverInstanceInfos.get(0)).getProject()).isEqualTo(PROJECT);
    assertThat(((GoogleFunctionServerInstanceInfo) serverInstanceInfos.get(0)).getEnvironmentType())
        .isEqualTo(GEN_TWO_ENV);
    assertThat(((GoogleFunctionServerInstanceInfo) serverInstanceInfos.get(0)).getRevision()).isEqualTo(REVISION);
  }
}
