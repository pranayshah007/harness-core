/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.deploymentfreezewindow;

import static io.harness.rule.OwnerRule.VED;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ff.FeatureFlagService;
import io.harness.governance.AllAppFilter;
import io.harness.governance.AllEnvFilter;
import io.harness.governance.ApplicationFilter;
import io.harness.governance.BlackoutWindowFilterType;
import io.harness.governance.EnvironmentFilter;
import io.harness.governance.EnvironmentFilter.EnvironmentFilterType;
import io.harness.governance.ServiceFilter;
import io.harness.governance.ServiceFilter.ServiceFilterType;
import io.harness.governance.TimeRangeBasedFreezeConfig;
import io.harness.governance.TimeRangeOccurrence;
import io.harness.rule.Owner;

import software.wings.beans.governance.GovernanceConfig;
import software.wings.graphql.datafetcher.MutationContext;
import software.wings.graphql.schema.mutation.deploymentfreezewindow.input.QLDeleteDeploymentFreezeWindowInput;
import software.wings.graphql.schema.mutation.deploymentfreezewindow.payload.QLDeleteDeploymentFreezeWindowPayload;
import software.wings.resources.stats.model.TimeRange;
import software.wings.service.intfc.compliance.GovernanceConfigService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class DeleteDeploymentFreezeWindowDataFetcherTest extends CategoryTest {
  @Mock GovernanceConfigService governanceConfigService;
  @Mock FeatureFlagService featureFlagService;
  @Mock GovernanceConfig governanceConfig;
  @InjectMocks
  @Spy
  DeleteDeploymentFreezeWindowDataFetcher deleteDeploymentFreezeWindowDataFetcher =
      new DeleteDeploymentFreezeWindowDataFetcher(governanceConfigService);

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void delete() throws Exception {
    QLDeleteDeploymentFreezeWindowInput qlDeleteDeploymentFreezeWindowInput =
        QLDeleteDeploymentFreezeWindowInput.builder().id("id").build();

    MutationContext mutationContext = MutationContext.builder().accountId("ACCOUNT_ID").build();

    List<TimeRangeBasedFreezeConfig> timeRangeBasedFreezeConfigList = new ArrayList<>();

    EnvironmentFilter environmentFilter = new AllEnvFilter(EnvironmentFilterType.ALL);

    ServiceFilter serviceFilter = new ServiceFilter(ServiceFilterType.ALL, null);

    ApplicationFilter applicationFilter =
        new AllAppFilter(BlackoutWindowFilterType.ALL, environmentFilter, serviceFilter);

    TimeRange timeRange = new TimeRange(System.currentTimeMillis(), System.currentTimeMillis() + 3600000, "time-zone",
        true, 3600000L, System.currentTimeMillis() + 3600000, TimeRangeOccurrence.ANNUAL, false);

    TimeRangeBasedFreezeConfig timeRangeBasedFreeze = TimeRangeBasedFreezeConfig.builder()
                                                          .uuid("id")
                                                          .name("DFW")
                                                          .description("freeze description")
                                                          .userGroups(Arrays.asList("usergroups"))
                                                          .appSelections(Arrays.asList(applicationFilter))
                                                          .timeRange(timeRange)
                                                          .build();

    timeRangeBasedFreezeConfigList.add(timeRangeBasedFreeze);

    GovernanceConfig governanceConfiguration = new GovernanceConfig();
    governanceConfiguration.setTimeRangeBasedFreezeConfigs(timeRangeBasedFreezeConfigList);

    doReturn(true).when(featureFlagService).isEnabled(any(), anyString());
    doReturn(governanceConfiguration).when(governanceConfigService).get(anyString());
    doReturn(timeRangeBasedFreezeConfigList).when(governanceConfig).getTimeRangeBasedFreezeConfigs();

    QLDeleteDeploymentFreezeWindowPayload qlDeleteDeploymentFreezeWindowPayload =
        deleteDeploymentFreezeWindowDataFetcher.mutateAndFetch(qlDeleteDeploymentFreezeWindowInput, mutationContext);

    verify(governanceConfigService, times(1)).upsert("ACCOUNT_ID", new GovernanceConfig());
  }
}
