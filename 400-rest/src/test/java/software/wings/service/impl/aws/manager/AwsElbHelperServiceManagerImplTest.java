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
import static java.util.Collections.singletonList;
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
import io.harness.delegate.task.aws.AwsElbListener;
import io.harness.delegate.task.aws.AwsLoadBalancerDetails;
import io.harness.rule.Owner;

import software.wings.beans.AwsConfig;
import software.wings.service.impl.aws.model.AwsElbListAppElbsResponse;
import software.wings.service.impl.aws.model.AwsElbListClassicElbsResponse;
import software.wings.service.impl.aws.model.AwsElbListListenerResponse;
import software.wings.service.impl.aws.model.AwsElbListTargetGroupsResponse;
import software.wings.service.intfc.DelegateService;

import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
public class AwsElbHelperServiceManagerImplTest extends CategoryTest {
  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testListClassicLoadBalancers() throws InterruptedException {
    AwsElbHelperServiceManagerImpl service = spy(AwsElbHelperServiceManagerImpl.class);
    DelegateService mockDelegateService = mock(DelegateService.class);
    on(service).set("delegateService", mockDelegateService);
    doReturn(AwsElbListClassicElbsResponse.builder().classicElbs(asList("class-0", "class-1")).build())
        .when(mockDelegateService)
        .executeTaskV2(any());
    AwsHelperServiceManager mockHelper = mock(AwsHelperServiceManager.class);
    on(service).set("helper", mockHelper);
    doNothing().when(mockHelper).validateDelegateSuccessForSyncTask(any());
    List<String> lbs = service.listClassicLoadBalancers(AwsConfig.builder().build(), emptyList(), "us-east-1", APP_ID);
    assertThat(lbs).isNotNull();
    assertThat(lbs.size()).isEqualTo(2);
    assertThat(lbs.get(0)).isEqualTo("class-0");
    assertThat(lbs.get(1)).isEqualTo("class-1");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testListApplicationLoadBalancers() throws InterruptedException {
    AwsElbHelperServiceManagerImpl service = spy(AwsElbHelperServiceManagerImpl.class);
    DelegateService mockDelegateService = mock(DelegateService.class);
    on(service).set("delegateService", mockDelegateService);
    doReturn(AwsElbListAppElbsResponse.builder()
                 .appElbs(asList(AwsLoadBalancerDetails.builder().name("lb-0").build(),
                     AwsLoadBalancerDetails.builder().name("lb-1").build()))
                 .build())
        .when(mockDelegateService)
        .executeTaskV2(any());
    AwsHelperServiceManager mockHelper = mock(AwsHelperServiceManager.class);
    on(service).set("helper", mockHelper);
    doNothing().when(mockHelper).validateDelegateSuccessForSyncTask(any());
    List<String> lbs =
        service.listApplicationLoadBalancers(AwsConfig.builder().build(), emptyList(), "us-east-1", APP_ID);
    assertThat(lbs).isNotNull();
    assertThat(lbs.size()).isEqualTo(2);
    assertThat(lbs.get(0)).isEqualTo("lb-0");
    assertThat(lbs.get(1)).isEqualTo("lb-1");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testListApplicationLoadBalancerDetails() throws InterruptedException {
    AwsElbHelperServiceManagerImpl service = spy(AwsElbHelperServiceManagerImpl.class);
    DelegateService mockDelegateService = mock(DelegateService.class);
    on(service).set("delegateService", mockDelegateService);
    doReturn(AwsElbListAppElbsResponse.builder()
                 .appElbs(asList(AwsLoadBalancerDetails.builder().name("lb-0").build(),
                     AwsLoadBalancerDetails.builder().name("lb-1").build()))
                 .build())
        .when(mockDelegateService)
        .executeTaskV2(any());
    AwsHelperServiceManager mockHelper = mock(AwsHelperServiceManager.class);
    on(service).set("helper", mockHelper);
    doNothing().when(mockHelper).validateDelegateSuccessForSyncTask(any());
    List<AwsLoadBalancerDetails> details =
        service.listApplicationLoadBalancerDetails(AwsConfig.builder().build(), emptyList(), "us-east-1", APP_ID);
    assertThat(details).isNotNull();
    assertThat(details.size()).isEqualTo(2);
    assertThat(details.get(0).getName()).isEqualTo("lb-0");
    assertThat(details.get(1).getName()).isEqualTo("lb-1");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testListElasticLoadBalancers() throws InterruptedException {
    AwsElbHelperServiceManagerImpl service = spy(AwsElbHelperServiceManagerImpl.class);
    DelegateService mockDelegateService = mock(DelegateService.class);
    on(service).set("delegateService", mockDelegateService);
    doReturn(AwsElbListAppElbsResponse.builder()
                 .appElbs(asList(AwsLoadBalancerDetails.builder().name("lb-0").build(),
                     AwsLoadBalancerDetails.builder().name("lb-1").build()))
                 .build())
        .when(mockDelegateService)
        .executeTaskV2(any());
    AwsHelperServiceManager mockHelper = mock(AwsHelperServiceManager.class);
    on(service).set("helper", mockHelper);
    doNothing().when(mockHelper).validateDelegateSuccessForSyncTask(any());
    List<String> lbs = service.listElasticLoadBalancers(AwsConfig.builder().build(), emptyList(), "us-east-1", APP_ID);
    assertThat(lbs).isNotNull();
    assertThat(lbs.size()).isEqualTo(2);
    assertThat(lbs.get(0)).isEqualTo("lb-0");
    assertThat(lbs.get(1)).isEqualTo("lb-1");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testListElasticLoadBalancerDetails() throws InterruptedException {
    AwsElbHelperServiceManagerImpl service = spy(AwsElbHelperServiceManagerImpl.class);
    DelegateService mockDelegateService = mock(DelegateService.class);
    on(service).set("delegateService", mockDelegateService);
    doReturn(AwsElbListAppElbsResponse.builder()
                 .appElbs(asList(AwsLoadBalancerDetails.builder().name("lb-0").build(),
                     AwsLoadBalancerDetails.builder().name("lb-1").build()))
                 .build())
        .when(mockDelegateService)
        .executeTaskV2(any());
    AwsHelperServiceManager mockHelper = mock(AwsHelperServiceManager.class);
    on(service).set("helper", mockHelper);
    doNothing().when(mockHelper).validateDelegateSuccessForSyncTask(any());
    List<AwsLoadBalancerDetails> details =
        service.listElasticLoadBalancerDetails(AwsConfig.builder().build(), emptyList(), "us-east-1", APP_ID);
    assertThat(details).isNotNull();
    assertThat(details.size()).isEqualTo(2);
    assertThat(details.get(0).getName()).isEqualTo("lb-0");
    assertThat(details.get(1).getName()).isEqualTo("lb-1");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testListNetworkLoadBalancers() throws InterruptedException {
    AwsElbHelperServiceManagerImpl service = spy(AwsElbHelperServiceManagerImpl.class);
    DelegateService mockDelegateService = mock(DelegateService.class);
    on(service).set("delegateService", mockDelegateService);
    doReturn(AwsElbListAppElbsResponse.builder()
                 .appElbs(asList(AwsLoadBalancerDetails.builder().name("lb-0").build(),
                     AwsLoadBalancerDetails.builder().name("lb-1").build()))
                 .build())
        .when(mockDelegateService)
        .executeTaskV2(any());
    AwsHelperServiceManager mockHelper = mock(AwsHelperServiceManager.class);
    on(service).set("helper", mockHelper);
    doNothing().when(mockHelper).validateDelegateSuccessForSyncTask(any());
    List<String> lbs = service.listNetworkLoadBalancers(AwsConfig.builder().build(), emptyList(), "us-east-1", APP_ID);
    assertThat(lbs).isNotNull();
    assertThat(lbs.size()).isEqualTo(2);
    assertThat(lbs.get(0)).isEqualTo("lb-0");
    assertThat(lbs.get(1)).isEqualTo("lb-1");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testListNetworkLoadBalancerDetails() throws InterruptedException {
    AwsElbHelperServiceManagerImpl service = spy(AwsElbHelperServiceManagerImpl.class);
    DelegateService mockDelegateService = mock(DelegateService.class);
    on(service).set("delegateService", mockDelegateService);
    doReturn(AwsElbListAppElbsResponse.builder()
                 .appElbs(asList(AwsLoadBalancerDetails.builder().name("lb-0").build(),
                     AwsLoadBalancerDetails.builder().name("lb-1").build()))
                 .build())
        .when(mockDelegateService)
        .executeTaskV2(any());
    AwsHelperServiceManager mockHelper = mock(AwsHelperServiceManager.class);
    on(service).set("helper", mockHelper);
    doNothing().when(mockHelper).validateDelegateSuccessForSyncTask(any());
    List<AwsLoadBalancerDetails> details =
        service.listNetworkLoadBalancerDetails(AwsConfig.builder().build(), emptyList(), "us-east-1", APP_ID);
    assertThat(details).isNotNull();
    assertThat(details.size()).isEqualTo(2);
    assertThat(details.get(0).getName()).isEqualTo("lb-0");
    assertThat(details.get(1).getName()).isEqualTo("lb-1");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testListTargetGroupsForAlb() throws InterruptedException {
    AwsElbHelperServiceManagerImpl service = spy(AwsElbHelperServiceManagerImpl.class);
    DelegateService mockDelegateService = mock(DelegateService.class);
    on(service).set("delegateService", mockDelegateService);
    doReturn(AwsElbListTargetGroupsResponse.builder().targetGroups(ImmutableMap.of("k", "v")).build())
        .when(mockDelegateService)
        .executeTaskV2(any());
    AwsHelperServiceManager mockHelper = mock(AwsHelperServiceManager.class);
    on(service).set("helper", mockHelper);
    doNothing().when(mockHelper).validateDelegateSuccessForSyncTask(any());
    Map<String, String> groups =
        service.listTargetGroupsForAlb(AwsConfig.builder().build(), emptyList(), "us-east-1", "lbName", APP_ID);
    assertThat(groups).isNotNull();
    assertThat(groups.size()).isEqualTo(1);
    assertThat(groups.containsKey("k")).isTrue();
    assertThat(groups.get("k")).isEqualTo("v");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testListListenersForElb() throws InterruptedException {
    AwsElbHelperServiceManagerImpl service = spy(AwsElbHelperServiceManagerImpl.class);
    DelegateService mockDelegateService = mock(DelegateService.class);
    on(service).set("delegateService", mockDelegateService);
    doReturn(AwsElbListListenerResponse.builder()
                 .awsElbListeners(singletonList(
                     AwsElbListener.builder().listenerArn("list-Arn").protocol("HTTP").port(8080).build()))
                 .build())
        .when(mockDelegateService)
        .executeTaskV2(any());
    AwsHelperServiceManager mockHelper = mock(AwsHelperServiceManager.class);
    on(service).set("helper", mockHelper);
    doNothing().when(mockHelper).validateDelegateSuccessForSyncTask(any());
    List<AwsElbListener> listeners =
        service.listListenersForElb(AwsConfig.builder().build(), emptyList(), "us-east-1", "lbName", APP_ID);
    assertThat(listeners).isNotNull();
    assertThat(listeners.size()).isEqualTo(1);
    assertThat(listeners.get(0).getProtocol()).isEqualTo("HTTP");
    assertThat(listeners.get(0).getPort()).isEqualTo(8080);
    assertThat(listeners.get(0).getListenerArn()).isEqualTo("list-Arn");
  }
}
