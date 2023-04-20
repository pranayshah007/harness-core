/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc.aws.manager;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.SATYAM;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.AwsConfig;
import software.wings.service.impl.aws.model.AwsS3ListBucketNamesResponse;
import software.wings.service.intfc.DelegateService;

import com.google.inject.Inject;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import wiremock.com.google.common.collect.ImmutableList;

@OwnedBy(CDP)
public class AwsS3HelperServiceManagerTest extends WingsBaseTest {
  @Mock private DelegateService mockDelegateService;
  @Inject @InjectMocks private AwsS3HelperServiceManager helper;

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testListBucketNames() throws Exception {
    List<String> bucketNames = ImmutableList.of("name_00", "name_01");
    doReturn(AwsS3ListBucketNamesResponse.builder().bucketNames(bucketNames).build())
        .when(mockDelegateService)
        .executeTaskV2(any());
    List<String> result = helper.listBucketNames(AwsConfig.builder().build(), emptyList());
    assertThat(result).isNotNull();
    assertThat(result.size()).isEqualTo(2);
    assertThat(result.contains("name_00")).isTrue();
    assertThat(result.contains("name_01")).isTrue();
  }
}
