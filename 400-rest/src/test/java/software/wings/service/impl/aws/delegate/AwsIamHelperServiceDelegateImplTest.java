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

import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.amazonaws.services.identitymanagement.model.InstanceProfile;
import com.amazonaws.services.identitymanagement.model.ListInstanceProfilesResult;
import com.amazonaws.services.identitymanagement.model.ListRolesResult;
import com.amazonaws.services.identitymanagement.model.Role;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

@OwnedBy(CDP)
public class AwsIamHelperServiceDelegateImplTest extends WingsBaseTest {
  @Mock private EncryptionService mockEncryptionService;
  @Mock private AwsCallTracker mockTracker;
  @Spy @InjectMocks private AwsIamHelperServiceDelegateImpl awsIamHelperServiceDelegate;

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testListIAMRoles() {
    AmazonIdentityManagementClient mockClient = mock(AmazonIdentityManagementClient.class);
    doReturn(mockClient).when(awsIamHelperServiceDelegate).getAmazonIdentityManagementClient(any());
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    doReturn(new ListRolesResult().withRoles(
                 new Role().withArn("a1").withRoleName("n1"), new Role().withArn("a2").withRoleName("n2")))
        .when(mockClient)
        .listRoles(any());
    doNothing().when(mockTracker).trackIAMCall(anyString());
    Map<String, String> result = awsIamHelperServiceDelegate.listIAMRoles(AwsConfig.builder().build(), emptyList());
    assertThat(result).isNotNull();
    assertThat(result.size()).isEqualTo(2);
    assertThat(result.get("a1")).isEqualTo("n1");
    assertThat(result.get("a2")).isEqualTo("n2");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testListIamInstanceRoles() {
    AmazonIdentityManagementClient mockClient = mock(AmazonIdentityManagementClient.class);
    doReturn(mockClient).when(awsIamHelperServiceDelegate).getAmazonIdentityManagementClient(any());
    doReturn(
        new ListInstanceProfilesResult().withInstanceProfiles(new InstanceProfile().withInstanceProfileName("name1"),
            new InstanceProfile().withInstanceProfileName("name2")))
        .when(mockClient)
        .listInstanceProfiles(any());
    doNothing().when(mockTracker).trackIAMCall(anyString());
    List<String> result = awsIamHelperServiceDelegate.listIamInstanceRoles(AwsConfig.builder().build(), emptyList());
    assertThat(result).isNotNull();
    assertThat(result.size()).isEqualTo(2);
    assertThat(result.get(0)).isEqualTo("name1");
    assertThat(result.get(1)).isEqualTo("name2");
  }
}
