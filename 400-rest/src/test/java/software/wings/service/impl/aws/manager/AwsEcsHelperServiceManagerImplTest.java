/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.aws.manager;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.SATYAM;

import static software.wings.utils.WingsTestConstants.APP_ID;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.AwsConfig;
import software.wings.service.impl.aws.model.AwsEcsListClusterServicesResponse;
import software.wings.service.impl.aws.model.AwsEcsListClustersResponse;
import software.wings.service.intfc.DelegateService;

import com.amazonaws.services.ecs.model.Service;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
public class AwsEcsHelperServiceManagerImplTest extends CategoryTest {
  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testListClusters() throws InterruptedException {
    AwsEcsHelperServiceManagerImpl service = spy(AwsEcsHelperServiceManagerImpl.class);
    DelegateService mockDelegateService = mock(DelegateService.class);
    on(service).set("delegateService", mockDelegateService);
    doReturn(AwsEcsListClustersResponse.builder().clusters(asList("cluster-0", "cluster-1")).build())
        .when(mockDelegateService)
        .executeTaskV2(any());
    AwsHelperServiceManager mockHelper = mock(AwsHelperServiceManager.class);
    on(service).set("helper", mockHelper);
    doNothing().when(mockHelper).validateDelegateSuccessForSyncTask(any());
    List<String> clusters = service.listClusters(AwsConfig.builder().build(), emptyList(), "us-east-1", APP_ID);
    assertThat(clusters).isNotNull();
    assertThat(clusters.size()).isEqualTo(2);
    assertThat(clusters.get(0)).isEqualTo("cluster-0");
    assertThat(clusters.get(1)).isEqualTo("cluster-1");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testListClusterServices() throws InterruptedException {
    AwsEcsHelperServiceManagerImpl service = spy(AwsEcsHelperServiceManagerImpl.class);
    DelegateService mockDelegateService = mock(DelegateService.class);
    on(service).set("delegateService", mockDelegateService);
    doReturn(AwsEcsListClusterServicesResponse.builder()
                 .services(asList(new Service().withServiceName("svc-00"), new Service().withServiceName("svc-01")))
                 .build())
        .when(mockDelegateService)
        .executeTaskV2(any());
    AwsHelperServiceManager mockHelper = mock(AwsHelperServiceManager.class);
    on(service).set("helper", mockHelper);
    doNothing().when(mockHelper).validateDelegateSuccessForSyncTask(any());
    List<Service> services =
        service.listClusterServices(AwsConfig.builder().build(), emptyList(), "us-east-1", APP_ID, "cluster");
    assertThat(services).isNotNull();
    assertThat(services.size()).isEqualTo(2);
  }
}
