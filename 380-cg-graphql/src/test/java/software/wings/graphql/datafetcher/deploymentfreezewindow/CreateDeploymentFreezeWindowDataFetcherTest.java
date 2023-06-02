/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.deploymentfreezewindow;

import static io.harness.rule.OwnerRule.VED;

import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_DEPLOYMENT_FREEZES;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;

import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.governance.AllAppFilter;
import io.harness.governance.AllEnvFilter;
import io.harness.governance.ApplicationFilter;
import io.harness.governance.BlackoutWindowFilterType;
import io.harness.governance.CustomAppFilter;
import io.harness.governance.EnvironmentFilter;
import io.harness.governance.EnvironmentFilter.EnvironmentFilterType;
import io.harness.governance.ServiceFilter;
import io.harness.governance.ServiceFilter.ServiceFilterType;
import io.harness.governance.TimeRangeBasedFreezeConfig;
import io.harness.governance.TimeRangeOccurrence;
import io.harness.rule.Owner;

import software.wings.beans.governance.GovernanceConfig;
import software.wings.beans.security.UserGroup;
import software.wings.graphql.datafetcher.AbstractDataFetcherTestBase;
import software.wings.graphql.datafetcher.MutationContext;
import software.wings.graphql.schema.mutation.deploymentfreezewindow.input.QLCreateDeploymentFreezeWindowInput;
import software.wings.graphql.schema.mutation.deploymentfreezewindow.input.QLEnvironmentTypeFilterInput;
import software.wings.graphql.schema.mutation.deploymentfreezewindow.input.QLFreezeWindowInput;
import software.wings.graphql.schema.mutation.deploymentfreezewindow.input.QLServiceTypeFilterInput;
import software.wings.graphql.schema.mutation.deploymentfreezewindow.input.QLSetupInput;
import software.wings.graphql.schema.mutation.deploymentfreezewindow.payload.QLDeploymentFreezeWindowPayload;
import software.wings.graphql.schema.type.deploymentfreezewindow.QLDeploymentFreezeWindow;
import software.wings.graphql.schema.type.deploymentfreezewindow.QLFreezeWindow;
import software.wings.graphql.schema.type.deploymentfreezewindow.QLSetup;
import software.wings.resources.stats.model.TimeRange;
import software.wings.security.annotations.AuthRule;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.compliance.GovernanceConfigService;

import com.google.inject.Inject;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class CreateDeploymentFreezeWindowDataFetcherTest extends AbstractDataFetcherTestBase {
  @Mock GovernanceConfigService governanceConfigService;
  @Mock UserGroupService userGroupService;
  @Mock FeatureFlagService featureFlagService;
  @Mock DeploymentFreezeWindowController deploymentFreezeWindowController;
  @Mock TimeRangeBasedFreezeConfig timeRangeBasedFreezeConfig;

  @Inject
  @InjectMocks
  CreateDeploymentFreezeWindowDataFetcher createDeploymentFreezeWindowDataFetcher =
      new CreateDeploymentFreezeWindowDataFetcher();

  private static final String ACCOUNT_ID = "accountId";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void checkIfPermissionCorrect() throws NoSuchMethodException {
    Method method = CreateDeploymentFreezeWindowDataFetcher.class.getDeclaredMethod(
        "mutateAndFetch", QLCreateDeploymentFreezeWindowInput.class, MutationContext.class);
    AuthRule annotation = method.getAnnotation(AuthRule.class);
    assertThat(annotation.permissionType()).isEqualTo(MANAGE_DEPLOYMENT_FREEZES);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void durationBased() throws Exception {
    QLFreezeWindowInput qlValidFreezeWindowInput = QLFreezeWindowInput.builder()
                                                       .appFilter(BlackoutWindowFilterType.ALL)
                                                       .envTypeFilter(QLEnvironmentTypeFilterInput.ALL)
                                                       .serviceTypeFilter(QLServiceTypeFilterInput.ALL)
                                                       .appIds(null)
                                                       .envIds(null)
                                                       .servIds(null)
                                                       .build();
    QLFreezeWindowInput qlValidExcludeFreezeWindowInput = QLFreezeWindowInput.builder()
                                                              .appFilter(BlackoutWindowFilterType.CUSTOM)
                                                              .envTypeFilter(QLEnvironmentTypeFilterInput.ALL)
                                                              .serviceTypeFilter(QLServiceTypeFilterInput.ALL)
                                                              .appIds(Collections.singletonList("app1"))
                                                              .envIds(null)
                                                              .servIds(null)
                                                              .build();

    QLSetupInput qlValidSetupInput = QLSetupInput.builder().isDurationBased(true).duration(3600000L).build();

    QLCreateDeploymentFreezeWindowInput qlCreateDeploymentFreezeWindowInput =
        QLCreateDeploymentFreezeWindowInput.builder()
            .clientMutationId("req1")
            .name("DFW")
            .description("freeze description")
            .freezeWindows(Arrays.asList(qlValidFreezeWindowInput))
            .excludeFreezeWindows(Arrays.asList(qlValidExcludeFreezeWindowInput))
            .setup(qlValidSetupInput)
            .notifyTo(Arrays.asList("usergroups"))
            .build();

    EnvironmentFilter environmentFilter = new AllEnvFilter(EnvironmentFilterType.ALL);

    ServiceFilter serviceFilter = new ServiceFilter(ServiceFilterType.ALL, null);

    ApplicationFilter applicationFilter =
        new AllAppFilter(BlackoutWindowFilterType.ALL, environmentFilter, serviceFilter);
    ApplicationFilter excludeApplicationFilter = new CustomAppFilter(
        BlackoutWindowFilterType.CUSTOM, environmentFilter, Collections.singletonList("app1"), serviceFilter);

    TimeRange timeRange = new TimeRange(System.currentTimeMillis(), System.currentTimeMillis() + 3600000, "time-zone",
        true, 3600000L, System.currentTimeMillis() + 3600000, TimeRangeOccurrence.ANNUAL, false);

    TimeRangeBasedFreezeConfig timeRangeBasedFreeze = TimeRangeBasedFreezeConfig.builder()
                                                          .uuid("req1")
                                                          .name("DFW")
                                                          .description("freeze description")
                                                          .userGroups(Arrays.asList("usergroups"))
                                                          .appSelections(Arrays.asList(applicationFilter))
                                                          .excludeAppSelections(Arrays.asList(excludeApplicationFilter))
                                                          .timeRange(timeRange)
                                                          .build();

    MutationContext mutationContext = MutationContext.builder().accountId(ACCOUNT_ID).build();
    doReturn(true).when(featureFlagService).isEnabled(any(), anyString());
    doReturn(new GovernanceConfig()).when(governanceConfigService).get(anyString());
    doReturn(new UserGroup()).when(userGroupService).get(eq(ACCOUNT_ID), eq("usergroups"));
    doNothing().when(deploymentFreezeWindowController).validateDeploymentFreezeWindowInput(any(), anyString());
    doReturn(timeRangeBasedFreeze).when(deploymentFreezeWindowController).populateDeploymentFreezeWindowEntity(any());

    QLFreezeWindow qlFreezeWindow = QLFreezeWindow.builder()
                                        .appFilter(BlackoutWindowFilterType.ALL)
                                        .envFilterType(EnvironmentFilterType.ALL)
                                        .servFilterType(ServiceFilterType.ALL)
                                        .appIds(null)
                                        .envIds(null)
                                        .servIds(null)
                                        .build();
    QLFreezeWindow qlExcludeFreezeWindow = QLFreezeWindow.builder()
                                               .appFilter(BlackoutWindowFilterType.CUSTOM)
                                               .envFilterType(EnvironmentFilterType.ALL)
                                               .servFilterType(ServiceFilterType.ALL)
                                               .appIds(Collections.singletonList("app1"))
                                               .envIds(null)
                                               .servIds(null)
                                               .build();
    QLSetup qlSetup = QLSetup.builder().isDurationBased(true).duration(3600000L).build();

    QLDeploymentFreezeWindow qlDeploymentFreezeWindow = QLDeploymentFreezeWindow.builder()
                                                            .id("req1")
                                                            .name("DFW")
                                                            .description("freeze description")
                                                            .freezeWindows(Arrays.asList(qlFreezeWindow))
                                                            .excludeFreezeWindows(Arrays.asList(qlExcludeFreezeWindow))
                                                            .setup(qlSetup)
                                                            .notifyTo(Arrays.asList("usergroups"))
                                                            .build();

    doReturn(qlDeploymentFreezeWindow)
        .when(deploymentFreezeWindowController)
        .populateDeploymentFreezeWindowPayload(any());
    doReturn("req1").when(timeRangeBasedFreezeConfig).getUuid();

    QLDeploymentFreezeWindowPayload payload =
        createDeploymentFreezeWindowDataFetcher.mutateAndFetch(qlCreateDeploymentFreezeWindowInput, mutationContext);

    assertThat(payload).isNotNull();
    assertThat(payload.getDeploymentFreezeWindow().getName()).isEqualTo("DFW");
    assertThat(payload.getDeploymentFreezeWindow().getDescription()).isEqualTo("freeze description");
    assertThat(payload.getDeploymentFreezeWindow().getSetup().getIsDurationBased()).isEqualTo(true);
    assertThat(payload.getDeploymentFreezeWindow().getSetup().getDuration()).isEqualTo(3600000L);
    assertThat(payload.getDeploymentFreezeWindow().getFreezeWindows().get(0).getAppFilter())
        .isEqualTo(BlackoutWindowFilterType.ALL);
    assertThat(payload.getDeploymentFreezeWindow().getFreezeWindows().get(0).getEnvFilterType())
        .isEqualTo(EnvironmentFilterType.ALL);
    assertThat(payload.getDeploymentFreezeWindow().getExcludeFreezeWindows().get(0).getAppFilter())
        .isEqualTo(BlackoutWindowFilterType.CUSTOM);
    assertThat(payload.getDeploymentFreezeWindow().getExcludeFreezeWindows().get(0).getAppIds().get(0))
        .isEqualTo("app1");
    assertThat(payload.getDeploymentFreezeWindow().getExcludeFreezeWindows().get(0).getEnvFilterType())
        .isEqualTo(EnvironmentFilterType.ALL);
    assertThat(payload.getDeploymentFreezeWindow().getNotifyTo()).isEqualTo(Arrays.asList("usergroups"));
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void scheduledBased() throws Exception {
    QLFreezeWindowInput qlValidFreezeWindowInput = QLFreezeWindowInput.builder()
                                                       .appFilter(BlackoutWindowFilterType.ALL)
                                                       .envTypeFilter(QLEnvironmentTypeFilterInput.ALL)
                                                       .serviceTypeFilter(QLServiceTypeFilterInput.ALL)
                                                       .appIds(null)
                                                       .envIds(null)
                                                       .servIds(null)
                                                       .build();

    QLSetupInput qlValidSetupInput =
        QLSetupInput.builder().isDurationBased(false).from(163400000L).to(1635000000L).build();

    QLCreateDeploymentFreezeWindowInput qlCreateDeploymentFreezeWindowInput =
        QLCreateDeploymentFreezeWindowInput.builder()
            .clientMutationId("req1")
            .name("DFW")
            .description("freeze description")
            .freezeWindows(Arrays.asList(qlValidFreezeWindowInput))
            .setup(qlValidSetupInput)
            .notifyTo(Arrays.asList("usergroups"))
            .build();

    EnvironmentFilter environmentFilter = new AllEnvFilter(EnvironmentFilterType.ALL);

    ServiceFilter serviceFilter = new ServiceFilter(ServiceFilterType.ALL, null);

    ApplicationFilter applicationFilter =
        new AllAppFilter(BlackoutWindowFilterType.ALL, environmentFilter, serviceFilter);

    ApplicationFilter excludeApplicationFilter = new CustomAppFilter(
        BlackoutWindowFilterType.CUSTOM, environmentFilter, Collections.singletonList("app1"), serviceFilter);

    TimeRange timeRange = new TimeRange(163400000L, 1635000000L, "time-zone", true, 3600000L,
        System.currentTimeMillis() + 3600000, TimeRangeOccurrence.ANNUAL, false);

    TimeRangeBasedFreezeConfig timeRangeBasedFreeze = TimeRangeBasedFreezeConfig.builder()
                                                          .uuid("req1")
                                                          .name("DFW")
                                                          .description("freeze description")
                                                          .userGroups(Arrays.asList("usergroups"))
                                                          .appSelections(Arrays.asList(applicationFilter))
                                                          .excludeAppSelections(Arrays.asList(excludeApplicationFilter))
                                                          .timeRange(timeRange)
                                                          .build();

    MutationContext mutationContext = MutationContext.builder().accountId(ACCOUNT_ID).build();
    doReturn(true).when(featureFlagService).isEnabled(any(), anyString());
    doReturn(new GovernanceConfig()).when(governanceConfigService).get(anyString());
    doReturn(new UserGroup()).when(userGroupService).get(eq(ACCOUNT_ID), eq("usergroups"));
    doNothing().when(deploymentFreezeWindowController).validateDeploymentFreezeWindowInput(any(), anyString());
    doReturn(timeRangeBasedFreeze).when(deploymentFreezeWindowController).populateDeploymentFreezeWindowEntity(any());

    QLFreezeWindow qlFreezeWindow = QLFreezeWindow.builder()
                                        .appFilter(BlackoutWindowFilterType.ALL)
                                        .envFilterType(EnvironmentFilterType.ALL)
                                        .servFilterType(ServiceFilterType.ALL)
                                        .appIds(null)
                                        .envIds(null)
                                        .servIds(null)
                                        .build();
    QLFreezeWindow qlExcludeFreezeWindow = QLFreezeWindow.builder()
                                               .appFilter(BlackoutWindowFilterType.CUSTOM)
                                               .envFilterType(EnvironmentFilterType.ALL)
                                               .servFilterType(ServiceFilterType.ALL)
                                               .appIds(Collections.singletonList("app1"))
                                               .envIds(null)
                                               .servIds(null)
                                               .build();

    QLSetup qlSetup = QLSetup.builder().isDurationBased(false).from(163400000L).to(163500000L).build();

    QLDeploymentFreezeWindow qlDeploymentFreezeWindow = QLDeploymentFreezeWindow.builder()
                                                            .id("req1")
                                                            .name("DFW")
                                                            .description("freeze description")
                                                            .freezeWindows(Arrays.asList(qlFreezeWindow))
                                                            .excludeFreezeWindows(Arrays.asList(qlExcludeFreezeWindow))
                                                            .setup(qlSetup)
                                                            .notifyTo(Arrays.asList("usergroups"))
                                                            .build();

    doReturn(qlDeploymentFreezeWindow)
        .when(deploymentFreezeWindowController)
        .populateDeploymentFreezeWindowPayload(any());
    doReturn("req1").when(timeRangeBasedFreezeConfig).getUuid();

    QLDeploymentFreezeWindowPayload payload =
        createDeploymentFreezeWindowDataFetcher.mutateAndFetch(qlCreateDeploymentFreezeWindowInput, mutationContext);

    assertThat(payload).isNotNull();
    assertThat(payload.getDeploymentFreezeWindow().getName()).isEqualTo("DFW");
    assertThat(payload.getDeploymentFreezeWindow().getDescription()).isEqualTo("freeze description");
    assertThat(payload.getDeploymentFreezeWindow().getSetup().getIsDurationBased()).isEqualTo(false);
    assertThat(payload.getDeploymentFreezeWindow().getSetup().getFrom()).isEqualTo(163400000L);
    assertThat(payload.getDeploymentFreezeWindow().getSetup().getTo()).isEqualTo(163500000L);
    assertThat(payload.getDeploymentFreezeWindow().getFreezeWindows().get(0).getAppFilter())
        .isEqualTo(BlackoutWindowFilterType.ALL);
    assertThat(payload.getDeploymentFreezeWindow().getFreezeWindows().get(0).getEnvFilterType())
        .isEqualTo(EnvironmentFilterType.ALL);
    assertThat(payload.getDeploymentFreezeWindow().getExcludeFreezeWindows().get(0).getAppFilter())
        .isEqualTo(BlackoutWindowFilterType.CUSTOM);
    assertThat(payload.getDeploymentFreezeWindow().getExcludeFreezeWindows().get(0).getAppIds().get(0))
        .isEqualTo("app1");
    assertThat(payload.getDeploymentFreezeWindow().getExcludeFreezeWindows().get(0).getEnvFilterType())
        .isEqualTo(EnvironmentFilterType.ALL);
    assertThat(payload.getDeploymentFreezeWindow().getNotifyTo()).isEqualTo(Arrays.asList("usergroups"));
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void featureFlagOff() {
    QLFreezeWindowInput qlValidFreezeWindowInput = QLFreezeWindowInput.builder()
                                                       .appFilter(BlackoutWindowFilterType.ALL)
                                                       .envTypeFilter(QLEnvironmentTypeFilterInput.ALL)
                                                       .appIds(null)
                                                       .envIds(null)
                                                       .build();

    QLSetupInput qlValidSetupInput =
        QLSetupInput.builder().isDurationBased(false).from(163400000L).to(1635000000L).build();

    QLCreateDeploymentFreezeWindowInput qlCreateDeploymentFreezeWindowInput =
        QLCreateDeploymentFreezeWindowInput.builder()
            .clientMutationId("req1")
            .name("DFW")
            .description("freeze description")
            .freezeWindows(Arrays.asList(qlValidFreezeWindowInput))
            .setup(qlValidSetupInput)
            .notifyTo(Arrays.asList("usergroups"))
            .build();

    doReturn(false).when(featureFlagService).isEnabled(any(), anyString());

    MutationContext mutationContext = MutationContext.builder().accountId(ACCOUNT_ID).build();
    QLDeploymentFreezeWindowPayload payload =
        createDeploymentFreezeWindowDataFetcher.mutateAndFetch(qlCreateDeploymentFreezeWindowInput, mutationContext);
  }
}
