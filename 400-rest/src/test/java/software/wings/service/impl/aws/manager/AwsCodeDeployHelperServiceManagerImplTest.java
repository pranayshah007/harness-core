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
import software.wings.service.impl.aws.model.AwsCodeDeployListAppResponse;
import software.wings.service.impl.aws.model.AwsCodeDeployListAppRevisionResponse;
import software.wings.service.impl.aws.model.AwsCodeDeployListDeploymentConfigResponse;
import software.wings.service.impl.aws.model.AwsCodeDeployListDeploymentGroupResponse;
import software.wings.service.impl.aws.model.AwsCodeDeployListDeploymentInstancesResponse;
import software.wings.service.impl.aws.model.AwsCodeDeployS3LocationData;
import software.wings.service.intfc.DelegateService;

import com.amazonaws.services.ec2.model.Instance;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
public class AwsCodeDeployHelperServiceManagerImplTest extends CategoryTest {
  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testListApplications() throws InterruptedException {
    AwsCodeDeployHelperServiceManagerImpl service = spy(AwsCodeDeployHelperServiceManagerImpl.class);
    DelegateService mockDelegateService = mock(DelegateService.class);
    on(service).set("delegateService", mockDelegateService);
    doReturn(AwsCodeDeployListAppResponse.builder().applications(asList("app_1", "app_2")).build())
        .when(mockDelegateService)
        .executeTaskV2(any());
    AwsHelperServiceManager mockHelper = mock(AwsHelperServiceManager.class);
    on(service).set("helper", mockHelper);
    doNothing().when(mockHelper).validateDelegateSuccessForSyncTask(any());
    List<String> applications = service.listApplications(AwsConfig.builder().build(), emptyList(), "us-east-1", APP_ID);
    assertThat(applications).isNotNull();
    assertThat(applications.size()).isEqualTo(2);
    assertThat(applications.contains("app_1")).isTrue();
    assertThat(applications.contains("app_2")).isTrue();
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testListDeploymentConfiguration() throws InterruptedException {
    AwsCodeDeployHelperServiceManagerImpl service = spy(AwsCodeDeployHelperServiceManagerImpl.class);
    DelegateService mockDelegateService = mock(DelegateService.class);
    on(service).set("delegateService", mockDelegateService);
    doReturn(AwsCodeDeployListDeploymentConfigResponse.builder().deploymentConfig(asList("conf_1", "conf_2")).build())
        .when(mockDelegateService)
        .executeTaskV2(any());
    AwsHelperServiceManager mockHelper = mock(AwsHelperServiceManager.class);
    on(service).set("helper", mockHelper);
    doNothing().when(mockHelper).validateDelegateSuccessForSyncTask(any());
    List<String> configs =
        service.listDeploymentConfiguration(AwsConfig.builder().build(), emptyList(), "us-east-1", APP_ID);
    assertThat(configs).isNotNull();
    assertThat(configs.size()).isEqualTo(2);
    assertThat(configs.contains("conf_1")).isTrue();
    assertThat(configs.contains("conf_2")).isTrue();
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testListDeploymentGroups() throws InterruptedException {
    AwsCodeDeployHelperServiceManagerImpl service = spy(AwsCodeDeployHelperServiceManagerImpl.class);
    DelegateService mockDelegateService = mock(DelegateService.class);
    on(service).set("delegateService", mockDelegateService);
    doReturn(AwsCodeDeployListDeploymentGroupResponse.builder().deploymentGroups(asList("gp_1", "gp_2")).build())
        .when(mockDelegateService)
        .executeTaskV2(any());
    AwsHelperServiceManager mockHelper = mock(AwsHelperServiceManager.class);
    on(service).set("helper", mockHelper);
    doNothing().when(mockHelper).validateDelegateSuccessForSyncTask(any());
    List<String> groups =
        service.listDeploymentGroups(AwsConfig.builder().build(), emptyList(), "us-east-1", "appName", APP_ID);
    assertThat(groups).isNotNull();
    assertThat(groups.size()).isEqualTo(2);
    assertThat(groups.contains("gp_1")).isTrue();
    assertThat(groups.contains("gp_2")).isTrue();
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testListDeploymentInstances() throws InterruptedException {
    AwsCodeDeployHelperServiceManagerImpl service = spy(AwsCodeDeployHelperServiceManagerImpl.class);
    DelegateService mockDelegateService = mock(DelegateService.class);
    on(service).set("delegateService", mockDelegateService);
    doReturn(AwsCodeDeployListDeploymentInstancesResponse.builder()
                 .instances(asList(new Instance().withInstanceId("i-1234"), new Instance().withInstanceId("i-2345")))
                 .build())
        .when(mockDelegateService)
        .executeTaskV2(any());
    AwsHelperServiceManager mockHelper = mock(AwsHelperServiceManager.class);
    on(service).set("helper", mockHelper);
    doNothing().when(mockHelper).validateDelegateSuccessForSyncTask(any());
    List<Instance> instances =
        service.listDeploymentInstances(AwsConfig.builder().build(), emptyList(), "us-east-1", "depId", APP_ID);
    assertThat(instances).isNotNull();
    assertThat(instances.size()).isEqualTo(2);
    assertThat(instances.get(0).getInstanceId()).isEqualTo("i-1234");
    assertThat(instances.get(1).getInstanceId()).isEqualTo("i-2345");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testListAppRevision() throws InterruptedException {
    AwsCodeDeployHelperServiceManagerImpl service = spy(AwsCodeDeployHelperServiceManagerImpl.class);
    DelegateService mockDelegateService = mock(DelegateService.class);
    on(service).set("delegateService", mockDelegateService);
    doReturn(AwsCodeDeployListAppRevisionResponse.builder()
                 .s3LocationData(
                     AwsCodeDeployS3LocationData.builder().bucket("bucket").bundleType("bundle").key("key").build())
                 .build())
        .when(mockDelegateService)
        .executeTaskV2(any());
    AwsHelperServiceManager mockHelper = mock(AwsHelperServiceManager.class);
    on(service).set("helper", mockHelper);
    doNothing().when(mockHelper).validateDelegateSuccessForSyncTask(any());
    AwsCodeDeployS3LocationData data =
        service.listAppRevision(AwsConfig.builder().build(), emptyList(), "us-east-1", "appName", "depGp", APP_ID);
    assertThat(data).isNotNull();
    assertThat(data.getBucket()).isEqualTo("bucket");
    assertThat(data.getBundleType()).isEqualTo("bundle");
    assertThat(data.getKey()).isEqualTo("key");
  }
}
