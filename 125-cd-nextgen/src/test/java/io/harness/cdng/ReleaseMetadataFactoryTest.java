/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng;

import static io.harness.rule.OwnerRule.ABHINAV2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import io.harness.category.element.UnitTests;
import io.harness.cdng.infra.beans.K8sAwsInfrastructureOutcome;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.delegate.task.k8s.ReleaseMetadata;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.rule.Owner;
import io.harness.steps.environment.EnvironmentOutcome;

import io.grpc.StatusRuntimeException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class ReleaseMetadataFactoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Spy @InjectMocks ReleaseMetadataFactory factory;

  @Mock CDStepHelper cdStepHelper;
  private final String serviceId = "serviceId";
  private final String infraId = "infraId";
  private final String infraKey = "infraKey";
  private final String envId = "envId";

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testMetadataCreation() {
    doReturn(ServiceStepOutcome.builder().identifier(serviceId).build())
        .when(cdStepHelper)
        .getServiceStepOutcome(any(Ambiance.class));
    K8sAwsInfrastructureOutcome infraOutcome = K8sAwsInfrastructureOutcome.builder()
                                                   .environment(EnvironmentOutcome.builder().identifier(envId).build())
                                                   .infrastructureKey(infraKey)
                                                   .build();
    infraOutcome.setInfraIdentifier(infraId);

    ReleaseMetadata releaseMetadata = factory.createReleaseMetadata(infraOutcome, Ambiance.newBuilder().build());
    assertThat(releaseMetadata.getInfraId()).isEqualTo(infraId);
    assertThat(releaseMetadata.getInfraKey()).isEqualTo(infraKey);
    assertThat(releaseMetadata.getServiceId()).isEqualTo(serviceId);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testMetadataCreationFailure() {
    doThrow(StatusRuntimeException.class).when(cdStepHelper).getServiceStepOutcome(any(Ambiance.class));
    K8sAwsInfrastructureOutcome infraOutcome =
        K8sAwsInfrastructureOutcome.builder().infrastructureKey(infraKey).build();
    infraOutcome.setInfraIdentifier(infraId);

    ReleaseMetadata releaseMetadata = factory.createReleaseMetadata(infraOutcome, Ambiance.newBuilder().build());
    assertThat(releaseMetadata).isNull();
  }
}
