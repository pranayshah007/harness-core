/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.api;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.DHRUVX;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.client.NextGenService;
import io.harness.cvng.client.VerificationManagerService;
import io.harness.cvng.core.beans.aws.AwsPrometheusWorkspaceDTO;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class AwsServiceImplTest extends CvNextGenTestBase {
  @Inject private AwsService awsService;
  @Inject private OnboardingService onboardingService;
  @Mock private NextGenService nextGenService;
  @Mock private VerificationManagerService verificationManagerService;
  private String accountId;
  private String connectorIdentifier;

  @Before
  public void setup() throws IllegalAccessException, IOException {
    accountId = generateUuid();
    connectorIdentifier = generateUuid();

    FieldUtils.writeField(awsService, "onboardingService", onboardingService, true);
    FieldUtils.writeField(onboardingService, "nextGenService", nextGenService, true);
    FieldUtils.writeField(onboardingService, "verificationManagerService", verificationManagerService, true);

    when(nextGenService.get(anyString(), anyString(), anyString(), anyString()))
        .then(invocation
            -> Optional.of(ConnectorInfoDTO.builder().connectorConfig(AwsConnectorDTO.builder().build()).build()));
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testFetchRegions() {
    List<String> regions = awsService.fetchRegions();
    assertThat(regions).isNotNull();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testFetchAllWorkspaces() throws IOException {
    Map<String, Object> awsWorkspacesDataResponse = JsonUtils.asMap(
        Resources.toString(AwsServiceImplTest.class.getResource("/aws/aws-workspaces-data.json"), Charsets.UTF_8));
    when(verificationManagerService.getDataCollectionResponse(
             anyString(), anyString(), anyString(), any(DataCollectionRequest.class)))
        .thenReturn(JsonUtils.asJson(awsWorkspacesDataResponse));
    List<AwsPrometheusWorkspaceDTO> workspaces = awsService.fetchAllWorkspaces(
        ProjectParams.builder().accountIdentifier(accountId).projectIdentifier("").orgIdentifier("").build(),
        connectorIdentifier, "", "tracingId");
    assertThat(workspaces).isNotNull();
    assertThat(workspaces).hasSize(1);
    assertThat(workspaces.get(0).getWorkspaceId()).isEqualTo("ws-bd297196-b5ca-48c5-9857-972fe759354f");
    assertThat(workspaces.get(0).getName()).isEqualTo("cvtest");
  }
}
