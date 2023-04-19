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

import static com.google.common.collect.Sets.newHashSet;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;

import software.wings.beans.AwsConfig;
import software.wings.service.impl.aws.model.AwsEc2ListInstancesResponse;
import software.wings.service.impl.aws.model.AwsEc2ListRegionsResponse;
import software.wings.service.impl.aws.model.AwsEc2ListSGsResponse;
import software.wings.service.impl.aws.model.AwsEc2ListSubnetsResponse;
import software.wings.service.impl.aws.model.AwsEc2ListTagsResponse;
import software.wings.service.impl.aws.model.AwsEc2ListVpcsResponse;
import software.wings.service.impl.aws.model.AwsEc2ValidateCredentialsResponse;
import software.wings.service.impl.aws.model.AwsSecurityGroup;
import software.wings.service.impl.aws.model.AwsSubnet;
import software.wings.service.impl.aws.model.AwsVPC;
import software.wings.service.intfc.DelegateService;

import com.amazonaws.services.ec2.model.Instance;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
public class AwsEc2HelperServiceManagerImplTest extends CategoryTest {
  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testValidateAwsAccountCredential() throws InterruptedException {
    AwsEc2HelperServiceManagerImpl service = spy(AwsEc2HelperServiceManagerImpl.class);
    DelegateService mockDelegateService = mock(DelegateService.class);
    on(service).set("delegateService", mockDelegateService);
    AwsHelperServiceManager mockHelper = mock(AwsHelperServiceManager.class);
    on(service).set("helper", mockHelper);
    doNothing().when(mockHelper).validateDelegateSuccessForSyncTask(any());
    doReturn(AwsEc2ValidateCredentialsResponse.builder().valid(false).build())
        .when(mockDelegateService)
        .executeTaskV2(any());
    assertThatThrownBy(() -> service.validateAwsAccountCredential(AwsConfig.builder().build(), emptyList()))
        .isInstanceOf(WingsException.class);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testListRegions() throws InterruptedException {
    AwsEc2HelperServiceManagerImpl service = spy(AwsEc2HelperServiceManagerImpl.class);
    DelegateService mockDelegateService = mock(DelegateService.class);
    on(service).set("delegateService", mockDelegateService);
    doReturn(AwsEc2ListRegionsResponse.builder().regions(asList("us-east-1", "us-east-2")).build())
        .when(mockDelegateService)
        .executeTaskV2(any());
    AwsHelperServiceManager mockHelper = mock(AwsHelperServiceManager.class);
    on(service).set("helper", mockHelper);
    doNothing().when(mockHelper).validateDelegateSuccessForSyncTask(any());
    List<String> regions = service.listRegions(AwsConfig.builder().build(), emptyList(), APP_ID);
    assertThat(regions).isNotNull();
    assertThat(regions.size()).isEqualTo(2);
    assertThat(regions.contains("us-east-1")).isTrue();
    assertThat(regions.contains("us-east-2")).isTrue();
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testListVPCs() throws InterruptedException {
    AwsEc2HelperServiceManagerImpl service = spy(AwsEc2HelperServiceManagerImpl.class);
    DelegateService mockDelegateService = mock(DelegateService.class);
    on(service).set("delegateService", mockDelegateService);
    doReturn(AwsEc2ListVpcsResponse.builder().vpcs(asList(AwsVPC.builder().build(), AwsVPC.builder().build())).build())
        .when(mockDelegateService)
        .executeTaskV2(any());
    doReturn(AwsEc2ListVpcsResponse.builder()
                 .vpcs(asList(AwsVPC.builder().id("vpc-00").build(), AwsVPC.builder().id("vpc-01").build()))
                 .build())
        .when(mockDelegateService)
        .executeTaskV2(any());
    AwsHelperServiceManager mockHelper = mock(AwsHelperServiceManager.class);
    on(service).set("helper", mockHelper);
    doNothing().when(mockHelper).validateDelegateSuccessForSyncTask(any());
    List<AwsVPC> vpcs = service.listVPCs(AwsConfig.builder().build(), emptyList(), "us-east-1", APP_ID);
    assertThat(vpcs).isNotNull();
    assertThat(vpcs.size()).isEqualTo(2);
    assertThat(vpcs.get(0).getId()).isEqualTo("vpc-00");
    assertThat(vpcs.get(1).getId()).isEqualTo("vpc-01");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testListSubnets() throws InterruptedException {
    AwsEc2HelperServiceManagerImpl service = spy(AwsEc2HelperServiceManagerImpl.class);
    DelegateService mockDelegateService = mock(DelegateService.class);
    on(service).set("delegateService", mockDelegateService);
    doReturn(AwsEc2ListSubnetsResponse.builder()
                 .subnets(asList(AwsSubnet.builder().id("sub-00").build(), AwsSubnet.builder().id("sub-01").build()))
                 .build())
        .when(mockDelegateService)
        .executeTaskV2(any());
    AwsHelperServiceManager mockHelper = mock(AwsHelperServiceManager.class);
    on(service).set("helper", mockHelper);
    doNothing().when(mockHelper).validateDelegateSuccessForSyncTask(any());
    List<AwsSubnet> subnets =
        service.listSubnets(AwsConfig.builder().build(), emptyList(), "us-east-1", singletonList("vpc-id"), APP_ID);
    assertThat(subnets).isNotNull();
    assertThat(subnets.size()).isEqualTo(2);
    assertThat(subnets.get(0).getId()).isEqualTo("sub-00");
    assertThat(subnets.get(1).getId()).isEqualTo("sub-01");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testListSGs() throws InterruptedException {
    AwsEc2HelperServiceManagerImpl service = spy(AwsEc2HelperServiceManagerImpl.class);
    DelegateService mockDelegateService = mock(DelegateService.class);
    on(service).set("delegateService", mockDelegateService);
    doReturn(AwsEc2ListSGsResponse.builder()
                 .securityGroups(asList(
                     AwsSecurityGroup.builder().id("sg-00").build(), AwsSecurityGroup.builder().id("sg-01").build()))
                 .build())
        .when(mockDelegateService)
        .executeTaskV2(any());
    AwsHelperServiceManager mockHelper = mock(AwsHelperServiceManager.class);
    on(service).set("helper", mockHelper);
    doNothing().when(mockHelper).validateDelegateSuccessForSyncTask(any());
    List<AwsSecurityGroup> groups =
        service.listSGs(AwsConfig.builder().build(), emptyList(), "us-east-1", singletonList("vpc-id"), APP_ID);
    assertThat(groups).isNotNull();
    assertThat(groups.size()).isEqualTo(2);
    assertThat(groups.get(0).getId()).isEqualTo("sg-00");
    assertThat(groups.get(1).getId()).isEqualTo("sg-01");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testListTags() throws InterruptedException {
    AwsEc2HelperServiceManagerImpl service = spy(AwsEc2HelperServiceManagerImpl.class);
    DelegateService mockDelegateService = mock(DelegateService.class);
    on(service).set("delegateService", mockDelegateService);
    doReturn(AwsEc2ListTagsResponse.builder().tags(newHashSet("tag-0", "tag-1")).build())
        .when(mockDelegateService)
        .executeTaskV2(any());
    AwsHelperServiceManager mockHelper = mock(AwsHelperServiceManager.class);
    on(service).set("helper", mockHelper);
    doNothing().when(mockHelper).validateDelegateSuccessForSyncTask(any());
    Set<String> tags = service.listTags(AwsConfig.builder().build(), emptyList(), "us-east-1", APP_ID);
    assertThat(tags).isNotNull();
    assertThat(tags.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testListEc2Instances() throws InterruptedException {
    AwsEc2HelperServiceManagerImpl service = spy(AwsEc2HelperServiceManagerImpl.class);
    DelegateService mockDelegateService = mock(DelegateService.class);
    on(service).set("delegateService", mockDelegateService);
    doReturn(AwsEc2ListInstancesResponse.builder()
                 .instances(asList(new Instance().withInstanceId("id-1"), new Instance().withInstanceId("id-2")))
                 .build())
        .when(mockDelegateService)
        .executeTaskV2(any());
    AwsHelperServiceManager mockHelper = mock(AwsHelperServiceManager.class);
    on(service).set("helper", mockHelper);
    doNothing().when(mockHelper).validateDelegateSuccessForSyncTask(any());
    List<Instance> instances =
        service.listEc2Instances(AwsConfig.builder().build(), emptyList(), "us-east-1", emptyList(), APP_ID);
    assertThat(instances).isNotNull();
    assertThat(instances.size()).isEqualTo(2);
    assertThat(instances.get(0).getInstanceId()).isEqualTo("id-1");
    assertThat(instances.get(1).getInstanceId()).isEqualTo("id-2");
  }
}
