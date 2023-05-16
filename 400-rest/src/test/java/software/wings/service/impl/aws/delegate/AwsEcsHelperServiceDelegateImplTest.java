/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.aws.delegate;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.RAGHVENDRA;
import static io.harness.rule.OwnerRule.SATYAM;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.util.AwsCallTracker;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.AwsConfig;
import software.wings.beans.SettingAttribute;
import software.wings.cloudprovider.aws.AwsClusterServiceImpl;
import software.wings.cloudprovider.aws.EcsContainerServiceImpl;
import software.wings.service.impl.AwsHelperService;
import software.wings.service.intfc.security.EncryptionService;

import com.amazonaws.services.ecs.AmazonECSClient;
import com.amazonaws.services.ecs.model.DescribeServicesResult;
import com.amazonaws.services.ecs.model.ListClustersResult;
import com.amazonaws.services.ecs.model.ListServicesResult;
import com.amazonaws.services.ecs.model.Service;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(CDP)
public class AwsEcsHelperServiceDelegateImplTest extends WingsBaseTest {
  @Mock private EncryptionService mockEncryptionService;
  @Mock private AwsCallTracker mockTracker;
  @Mock private AwsHelperService awsHelperService;
  @Mock(answer = Answers.RETURNS_DEEP_STUBS) private EcsContainerServiceImpl ecsContainerService;
  @InjectMocks private AwsClusterServiceImpl awsClusterService = new AwsClusterServiceImpl();
  @Spy @InjectMocks private AwsEcsHelperServiceDelegateImpl awsEcsHelperServiceDelegate;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    awsEcsHelperServiceDelegate.awsClusterService = awsClusterService;
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testListClusters() {
    AmazonECSClient mockClient = mock(AmazonECSClient.class);
    doReturn(mockClient).when(awsEcsHelperServiceDelegate).getAmazonEcsClient(anyString(), any());
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    doReturn(new ListClustersResult().withClusterArns("foo/bar")).when(mockClient).listClusters(any());
    doNothing().when(mockTracker).trackECSCall(anyString());
    List<String> result =
        awsEcsHelperServiceDelegate.listClusters(AwsConfig.builder().build(), emptyList(), "us-east-1");
    assertThat(result).isNotNull();
    assertThat(result.size()).isEqualTo(1);
    assertThat(result.get(0)).isEqualTo("bar");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testListServicesForCluster() {
    AmazonECSClient mockClient = mock(AmazonECSClient.class);
    doReturn(mockClient).when(awsEcsHelperServiceDelegate).getAmazonEcsClient(anyString(), any());
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    doReturn(new ListServicesResult().withServiceArns("arn0", "arn1")).when(mockClient).listServices(any());
    doReturn(new DescribeServicesResult().withServices(
                 new Service().withServiceArn("arn0"), new Service().withServiceArn("arn1")))
        .when(mockClient)
        .describeServices(any());
    doNothing().when(mockTracker).trackECSCall(anyString());
    List<Service> services = awsEcsHelperServiceDelegate.listServicesForCluster(
        AwsConfig.builder().build(), emptyList(), "us-east-1", "cluster");
    assertThat(services).isNotNull();
    assertThat(services.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testServiceExists() {
    AmazonECSClient mockClient = mock(AmazonECSClient.class);
    doReturn(mockClient).when(awsEcsHelperServiceDelegate).getAmazonEcsClient(anyString(), any());
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    Optional<Service> serviceOptional = Optional.of(new Service());
    doReturn(serviceOptional)
        .when(ecsContainerService)
        .getService(anyString(), any(), anyList(), anyString(), eq("test-service"));
    doNothing().when(mockTracker).trackECSCall(anyString());
    boolean serviceExists =
        awsEcsHelperServiceDelegate.serviceExists(SettingAttribute.Builder.aSettingAttribute().build().toDTO(),
            emptyList(), "us-east-1", "cluster", "test-service");
    assertThat(serviceExists).isTrue();
  }
}
