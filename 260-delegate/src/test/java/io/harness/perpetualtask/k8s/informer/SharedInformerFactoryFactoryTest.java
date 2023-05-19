/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.k8s.informer;

import static io.harness.rule.OwnerRule.AVMOHAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.event.client.EventPublisher;
import io.harness.rule.Owner;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.models.V1DaemonSet;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1ReplicaSet;
import io.kubernetes.client.openapi.models.V1StatefulSet;
import io.kubernetes.client.openapi.models.V1beta1CronJob;
import io.kubernetes.client.util.ClientBuilder;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SharedInformerFactoryFactoryTest extends CategoryTest {
  private SharedInformerFactoryFactory sharedInformerFactoryFactory;

  @Before
  public void setUp() throws Exception {
    sharedInformerFactoryFactory = new SharedInformerFactoryFactory(mock(EventPublisher.class));
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void createSharedInformerFactory() throws Exception {
    ApiClient apiClient = new ClientBuilder().build().setReadTimeout(0);

    ClusterDetails clusterDetails = ClusterDetails.builder()
                                        .clusterName("my-k8s-cluster")
                                        .cloudProviderId("123454")
                                        .clusterId("423t123")
                                        .kubeSystemUid("8823a382-37b8-459d-a522-4444b3dbb159")
                                        .build();

    assertThat(sharedInformerFactoryFactory.createSharedInformerFactory(apiClient, clusterDetails))
        .isNotNull()
        .satisfies(sharedInformerFactory -> {
          // handlers registered
          assertThat(sharedInformerFactory.getExistingSharedIndexInformer(V1ReplicaSet.class)).isNotNull();
          assertThat(sharedInformerFactory.getExistingSharedIndexInformer(V1Deployment.class)).isNotNull();
          assertThat(sharedInformerFactory.getExistingSharedIndexInformer(V1DaemonSet.class)).isNotNull();
          assertThat(sharedInformerFactory.getExistingSharedIndexInformer(V1StatefulSet.class)).isNotNull();
          assertThat(sharedInformerFactory.getExistingSharedIndexInformer(V1Job.class)).isNotNull();
          assertThat(sharedInformerFactory.getExistingSharedIndexInformer(V1beta1CronJob.class)).isNotNull();
        });
  }
}
