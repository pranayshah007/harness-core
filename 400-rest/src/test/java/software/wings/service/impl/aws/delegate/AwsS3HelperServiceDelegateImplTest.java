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
import static wiremock.com.google.common.collect.Lists.newArrayList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.util.AwsCallTracker;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.AwsConfig;
import software.wings.service.intfc.security.EncryptionService;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.Bucket;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

@OwnedBy(CDP)
public class AwsS3HelperServiceDelegateImplTest extends WingsBaseTest {
  @Mock private EncryptionService mockEncryptionService;
  @Mock private AwsCallTracker mockTracker;
  @Spy @InjectMocks private AwsS3HelperServiceDelegateImpl s3HelperServiceDelegate;

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testListBucketNames() {
    AmazonS3Client mockClient = mock(AmazonS3Client.class);
    doReturn(mockClient).when(s3HelperServiceDelegate).getAmazonS3Client(any());
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    doNothing().when(mockTracker).trackS3Call(anyString());
    Bucket b_00 = new Bucket();
    b_00.setName("name_00");
    Bucket b_01 = new Bucket();
    b_01.setName("name_01");
    doReturn(newArrayList(b_00, b_01)).when(mockClient).listBuckets();
    List<String> names = s3HelperServiceDelegate.listBucketNames(AwsConfig.builder().build(), emptyList());
    assertThat(names).isNotNull();
    assertThat(names.size()).isEqualTo(2);
    assertThat(names.contains("name_00")).isTrue();
    assertThat(names.contains("name_01")).isTrue();
  }
}
