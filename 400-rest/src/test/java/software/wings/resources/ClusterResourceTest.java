/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.rule.OwnerRule.HANTANG;

import static java.lang.String.format;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.cluster.ClusterRecordService;
import io.harness.ccm.cluster.entities.ClusterRecord;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;

import software.wings.utils.ResourceTestRule;

import java.util.List;
import javax.ws.rs.core.GenericType;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ClusterResourceTest extends CategoryTest {
  private String accountId = "ACCOUNT_ID";
  private String clusterId = "CLUSTER_ID";
  private String cloudProviderId = "CLOUD_PROVIDER_ID";

  private static ClusterRecordService clusterRecordService = mock(ClusterRecordService.class);
  @ClassRule
  public static ResourceTestRule RESOURCES =
      ResourceTestRule.builder().instance(new ClusterResource(clusterRecordService)).build();

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void testGet() {
    RESOURCES.client()
        .target(format("/clusters/%s/?accountId=%s", clusterId, accountId))
        .request()
        .get(new GenericType<RestResponse<ClusterRecord>>() {});
    verify(clusterRecordService).get(eq(clusterId));
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void testList() {
    Integer count = 1;
    Integer startIndex = 0;
    RESOURCES.client()
        .target(format(
            "/clusters?accountId=%s&cloudProviderId=%s&count=%d&startIndex=%d", accountId, cloudProviderId, count, 0))
        .request()
        .get(new GenericType<RestResponse<List<ClusterRecord>>>() {});
    verify(clusterRecordService).list(eq(accountId), eq(cloudProviderId), eq(false), eq(count), eq(startIndex));
  }
}
