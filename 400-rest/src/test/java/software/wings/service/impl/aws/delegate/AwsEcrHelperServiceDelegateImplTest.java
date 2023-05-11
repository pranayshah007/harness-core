/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.aws.delegate;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.SATYAM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.util.AwsCallTracker;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.AwsConfig;
import software.wings.service.impl.delegate.AwsEcrApiHelperServiceDelegate;
import software.wings.service.intfc.security.EncryptionService;

import com.amazonaws.services.ecr.AmazonECRClient;
import com.amazonaws.services.ecr.model.AuthorizationData;
import com.amazonaws.services.ecr.model.DescribeRepositoriesResult;
import com.amazonaws.services.ecr.model.GetAuthorizationTokenResult;
import com.amazonaws.services.ecr.model.Repository;
import java.util.Collections;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

@OwnedBy(CDP)
public class AwsEcrHelperServiceDelegateImplTest extends WingsBaseTest {
  @Mock private EncryptionService mockEncryptionService;
  @Mock private AwsCallTracker mockTracker;
  @Mock private AwsCallTracker tracker;
  @Mock private AwsEcrApiHelperServiceDelegate awsEcrApiHelperServiceDelegate;
  @Spy @InjectMocks private AwsEcrHelperServiceDelegateImpl awsEcrHelperServiceDelegate;

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testGetEcrImageUrl() {
    AmazonECRClient mockClient = mock(AmazonECRClient.class);
    doReturn(mockClient).when(awsEcrHelperServiceDelegate).getAmazonEcrClient(any(), anyString());
    doReturn(mockClient).when(awsEcrApiHelperServiceDelegate).getAmazonEcrClient(any(), anyString());
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    doReturn(new DescribeRepositoriesResult().withRepositories(new Repository().withRepositoryUri("uri")))
        .when(mockClient)
        .describeRepositories(any());
    doNothing().when(mockTracker).trackECRCall(anyString());
    on(awsEcrApiHelperServiceDelegate).set("tracker", tracker);
    doCallRealMethod().when(awsEcrApiHelperServiceDelegate).getEcrImageUrl(any(), any(), any());
    String uri = awsEcrHelperServiceDelegate.getEcrImageUrl(
        AwsConfig.builder().build(), Collections.emptyList(), "us-east-1", "imageName");
    assertThat(uri).isEqualTo("uri");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testGetAmazonEcrAuthToken() {
    AmazonECRClient mockClient = mock(AmazonECRClient.class);
    doReturn(mockClient).when(awsEcrHelperServiceDelegate).getAmazonEcrClient(any(), anyString());
    doReturn(mockClient).when(awsEcrApiHelperServiceDelegate).getAmazonEcrClient(any(), anyString());
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    doReturn(new GetAuthorizationTokenResult().withAuthorizationData(
                 new AuthorizationData().withAuthorizationToken("token")))
        .when(mockClient)
        .getAuthorizationToken(any());
    doNothing().when(mockTracker).trackECRCall(anyString());
    doCallRealMethod().when(awsEcrApiHelperServiceDelegate).getAmazonEcrAuthToken(any(), any(), any());
    on(awsEcrApiHelperServiceDelegate).set("tracker", tracker);
    String token = awsEcrHelperServiceDelegate.getAmazonEcrAuthToken(
        AwsConfig.builder().build(), Collections.emptyList(), "account", "us-east-1");
    assertThat(token).isEqualTo("token");
  }
}
