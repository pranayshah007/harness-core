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
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.util.AwsCallTracker;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.AwsConfig;
import software.wings.service.impl.aws.model.AwsRoute53HostedZoneData;
import software.wings.service.intfc.security.EncryptionService;

import com.amazonaws.services.route53.AmazonRoute53;
import com.amazonaws.services.route53.model.HostedZone;
import com.amazonaws.services.route53.model.ListHostedZonesResult;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

@OwnedBy(CDP)
public class AwsRoute53HelperServiceDelegateImplTest extends WingsBaseTest {
  @Mock private EncryptionService mockEncryptionService;
  @Mock private AwsCallTracker mockTracker;
  @Spy @InjectMocks private AwsRoute53HelperServiceDelegateImpl awsRoute53HelperServiceDelegate;

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testListHostedZones() {
    AmazonRoute53 mockClient = mock(AmazonRoute53.class);
    doReturn(mockClient).when(awsRoute53HelperServiceDelegate).getAmazonRoute53Client(anyString(), any());
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    doReturn(new ListHostedZonesResult().withHostedZones(new HostedZone().withId("id").withName("name")))
        .when(mockClient)
        .listHostedZones();
    doNothing().when(mockTracker).trackR53Call(anyString());
    List<AwsRoute53HostedZoneData> zoneData =
        awsRoute53HelperServiceDelegate.listHostedZones(AwsConfig.builder().build(), emptyList(), "us-east-1");
    assertThat(zoneData).isNotNull();
    assertThat(zoneData.size()).isEqualTo(1);
    assertThat(zoneData.get(0).getHostedZoneId()).isEqualTo("id");
    assertThat(zoneData.get(0).getHostedZoneName()).isEqualTo("name");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testUpsertRoute53ParentRecord() {
    AmazonRoute53 mockClient = mock(AmazonRoute53.class);
    doReturn(mockClient).when(awsRoute53HelperServiceDelegate).getAmazonRoute53Client(anyString(), any());
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    doNothing().when(mockTracker).trackR53Call(anyString());
    awsRoute53HelperServiceDelegate.upsertRoute53ParentRecord(
        AwsConfig.builder().build(), emptyList(), "us-east-1", "parent", "id", 100, "blue", 0, "green", 60);
    verify(mockClient).changeResourceRecordSets(any());
  }
}
