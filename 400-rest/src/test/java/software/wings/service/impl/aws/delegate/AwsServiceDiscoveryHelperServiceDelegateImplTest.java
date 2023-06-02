/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.aws.delegate;

import static io.harness.annotations.dev.HarnessTeam.CDP;
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
import software.wings.service.intfc.security.EncryptionService;

import com.amazonaws.services.servicediscovery.AWSServiceDiscovery;
import com.amazonaws.services.servicediscovery.model.DnsConfig;
import com.amazonaws.services.servicediscovery.model.GetNamespaceResult;
import com.amazonaws.services.servicediscovery.model.GetServiceResult;
import com.amazonaws.services.servicediscovery.model.Namespace;
import com.amazonaws.services.servicediscovery.model.Service;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

@OwnedBy(CDP)
public class AwsServiceDiscoveryHelperServiceDelegateImplTest extends WingsBaseTest {
  @Mock private EncryptionService mockEncryptionService;
  @Mock private AwsCallTracker mockTracker;
  @Spy @InjectMocks private AwsServiceDiscoveryHelperServiceDelegateImpl awsServiceDiscoveryHelperServiceDelegate;

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testGetRecordValueForService() {
    AWSServiceDiscovery mockClient = mock(AWSServiceDiscovery.class);
    doReturn(mockClient)
        .when(awsServiceDiscoveryHelperServiceDelegate)
        .getAmazonServiceDiscoveryClient(anyString(), any());
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    doReturn(new GetServiceResult().withService(
                 new Service().withDnsConfig(new DnsConfig().withNamespaceId("namespaceId")).withName("serviceName")))
        .when(mockClient)
        .getService(any());
    doReturn(new GetNamespaceResult().withNamespace(new Namespace().withName("namespaceName")))
        .when(mockClient)
        .getNamespace(any());
    doNothing().when(mockTracker).trackSDSCall(anyString());
    String val = awsServiceDiscoveryHelperServiceDelegate.getRecordValueForService(
        AwsConfig.builder().build(), emptyList(), "us-east-1", "id");
    assertThat(val).isEqualTo("serviceName.namespaceName");
  }
}
