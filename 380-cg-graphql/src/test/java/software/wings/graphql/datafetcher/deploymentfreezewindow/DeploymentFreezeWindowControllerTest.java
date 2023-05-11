/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.deploymentfreezewindow;

import static io.harness.rule.OwnerRule.VED;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.governance.BlackoutWindowFilterType;
import io.harness.governance.EnvironmentFilter.EnvironmentFilterType;
import io.harness.governance.TimeRangeBasedFreezeConfig;
import io.harness.governance.TimeRangeOccurrence;
import io.harness.rule.Owner;

import software.wings.beans.security.UserGroup;
import software.wings.graphql.datafetcher.AbstractDataFetcherTestBase;
import software.wings.graphql.schema.mutation.deploymentfreezewindow.input.QLCreateDeploymentFreezeWindowInput;
import software.wings.graphql.schema.mutation.deploymentfreezewindow.input.QLEnvironmentTypeFilterInput;
import software.wings.graphql.schema.mutation.deploymentfreezewindow.input.QLFreezeWindowInput;
import software.wings.graphql.schema.mutation.deploymentfreezewindow.input.QLServiceTypeFilterInput;
import software.wings.graphql.schema.mutation.deploymentfreezewindow.input.QLSetupInput;
import software.wings.graphql.schema.mutation.deploymentfreezewindow.input.QLUpdateDeploymentFreezeWindowInput;
import software.wings.service.intfc.UserGroupService;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class DeploymentFreezeWindowControllerTest extends AbstractDataFetcherTestBase {
  @Mock UserGroupService userGroupService;
  @Inject @InjectMocks DeploymentFreezeWindowController deploymentFreezeWindowController;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void populateDeploymentFreezeWindowEntityTest() {
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

    doReturn(new UserGroup()).when(userGroupService).get(anyString(), eq("usergroups"));

    TimeRangeBasedFreezeConfig timeRangeBasedFreezeConfig =
        deploymentFreezeWindowController.populateDeploymentFreezeWindowEntity(qlCreateDeploymentFreezeWindowInput);

    assertThat(timeRangeBasedFreezeConfig).isNotNull();
    assertThat(timeRangeBasedFreezeConfig.getName()).isEqualTo("DFW");
    assertThat(timeRangeBasedFreezeConfig.getDescription()).isEqualTo("freeze description");
    assertThat(timeRangeBasedFreezeConfig.getTimeRange().isDurationBased()).isEqualTo(true);
    assertThat(timeRangeBasedFreezeConfig.getTimeRange().getDuration()).isEqualTo(3600000L);
    assertThat(timeRangeBasedFreezeConfig.getAppSelections().get(0).getFilterType())
        .isEqualTo(BlackoutWindowFilterType.ALL);
    assertThat(timeRangeBasedFreezeConfig.getAppSelections().get(0).getEnvSelection().getFilterType())
        .isEqualTo(EnvironmentFilterType.ALL);
    assertThat(timeRangeBasedFreezeConfig.getExcludeAppSelections().get(0).getFilterType())
        .isEqualTo(BlackoutWindowFilterType.CUSTOM);
    assertThat(timeRangeBasedFreezeConfig.getExcludeAppSelections().get(0).getEnvSelection().getFilterType())
        .isEqualTo(EnvironmentFilterType.ALL);
    assertThat(timeRangeBasedFreezeConfig.getUserGroups()).isEqualTo(Arrays.asList("usergroups"));
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void updateDeploymentFreezeWindowEntityTest() {
    // Already existing DFW
    QLFreezeWindowInput qlValidFreezeWindowInput = QLFreezeWindowInput.builder()
                                                       .appFilter(BlackoutWindowFilterType.ALL)
                                                       .envTypeFilter(QLEnvironmentTypeFilterInput.ALL)
                                                       .serviceTypeFilter(QLServiceTypeFilterInput.ALL)
                                                       .appIds(null)
                                                       .envIds(null)
                                                       .servIds(null)
                                                       .build();
    QLFreezeWindowInput qlValidExcludeFreezeWindowInput = QLFreezeWindowInput.builder()
                                                              .appFilter(BlackoutWindowFilterType.ALL)
                                                              .envTypeFilter(QLEnvironmentTypeFilterInput.ALL)
                                                              .serviceTypeFilter(QLServiceTypeFilterInput.ALL)
                                                              .appIds(null)
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

    doReturn(new UserGroup()).when(userGroupService).get(anyString(), eq("usergroups"));

    TimeRangeBasedFreezeConfig timeRangeBasedFreezeConfig =
        deploymentFreezeWindowController.populateDeploymentFreezeWindowEntity(qlCreateDeploymentFreezeWindowInput);

    // Updating the DFW
    QLFreezeWindowInput updatedFreezeWindowInput = QLFreezeWindowInput.builder()
                                                       .appFilter(BlackoutWindowFilterType.ALL)
                                                       .envTypeFilter(QLEnvironmentTypeFilterInput.ALL_PROD)
                                                       .serviceTypeFilter(QLServiceTypeFilterInput.ALL)
                                                       .appIds(null)
                                                       .envIds(null)
                                                       .servIds(null)
                                                       .build();
    QLFreezeWindowInput updatedExcludeFreezeWindowInput = QLFreezeWindowInput.builder()
                                                              .appFilter(BlackoutWindowFilterType.CUSTOM)
                                                              .envTypeFilter(QLEnvironmentTypeFilterInput.CUSTOM)
                                                              .serviceTypeFilter(QLServiceTypeFilterInput.ALL)
                                                              .appIds(Collections.singletonList("app1"))
                                                              .envIds(Collections.singletonList("env1"))
                                                              .servIds(null)
                                                              .build();

    QLSetupInput updatedSetupInput = QLSetupInput.builder().isDurationBased(true).duration(4600000L).build();

    QLUpdateDeploymentFreezeWindowInput updateDeploymentFreezeWindowInput =
        QLUpdateDeploymentFreezeWindowInput.builder()
            .id(timeRangeBasedFreezeConfig.getUuid())
            .clientMutationId("req1")
            .name("Updated_DFW")
            .description("updated")
            .freezeWindows(Arrays.asList(updatedFreezeWindowInput))
            .excludeFreezeWindows(Arrays.asList(updatedExcludeFreezeWindowInput))
            .setup(updatedSetupInput)
            .notifyTo(Arrays.asList("usergroups"))
            .build();

    TimeRangeBasedFreezeConfig updatedTimeRangeBasedFreezeConfig =
        deploymentFreezeWindowController.updateDeploymentFreezeWindowEntity(
            updateDeploymentFreezeWindowInput, timeRangeBasedFreezeConfig);

    assertThat(updatedTimeRangeBasedFreezeConfig).isNotNull();
    assertThat(updatedTimeRangeBasedFreezeConfig.getName()).isEqualTo("Updated_DFW");
    assertThat(updatedTimeRangeBasedFreezeConfig.getDescription()).isEqualTo("updated");
    assertThat(updatedTimeRangeBasedFreezeConfig.getTimeRange().isDurationBased()).isEqualTo(true);
    assertThat(updatedTimeRangeBasedFreezeConfig.getTimeRange().getDuration()).isEqualTo(4600000L);
    assertThat(updatedTimeRangeBasedFreezeConfig.getAppSelections().get(0).getFilterType())
        .isEqualTo(BlackoutWindowFilterType.ALL);
    assertThat(updatedTimeRangeBasedFreezeConfig.getAppSelections().get(0).getEnvSelection().getFilterType())
        .isEqualTo(EnvironmentFilterType.ALL_PROD);
    assertThat(updatedTimeRangeBasedFreezeConfig.getExcludeAppSelections().get(0).getFilterType())
        .isEqualTo(BlackoutWindowFilterType.CUSTOM);
    assertThat(updatedTimeRangeBasedFreezeConfig.getExcludeAppSelections().get(0).getEnvSelection().getFilterType())
        .isEqualTo(EnvironmentFilterType.CUSTOM);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void invalidUserGroupIds() {
    QLFreezeWindowInput qlValidFreezeWindowInput = QLFreezeWindowInput.builder()
                                                       .appFilter(BlackoutWindowFilterType.ALL)
                                                       .envTypeFilter(QLEnvironmentTypeFilterInput.ALL)
                                                       .serviceTypeFilter(QLServiceTypeFilterInput.ALL)
                                                       .appIds(null)
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
            .setup(qlValidSetupInput)
            .notifyTo(Arrays.asList("usergroups"))
            .build();

    TimeRangeBasedFreezeConfig timeRangeBasedFreezeConfig =
        deploymentFreezeWindowController.populateDeploymentFreezeWindowEntity(qlCreateDeploymentFreezeWindowInput);

    // deploymentFreezeWindowController.validateDeploymentFreezeWindowInput(timeRangeBasedFreezeConfig, "accountId");

    assertThatThrownBy(()
                           -> deploymentFreezeWindowController.validateDeploymentFreezeWindowInput(
                               timeRangeBasedFreezeConfig, "accountId"))
        .hasMessage("Invalid UserGroup Id: usergroups")
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void invalidTimeRangeEntity() {
    QLFreezeWindowInput qlValidFreezeWindowInput = QLFreezeWindowInput.builder()
                                                       .appFilter(BlackoutWindowFilterType.ALL)
                                                       .envTypeFilter(QLEnvironmentTypeFilterInput.ALL)
                                                       .serviceTypeFilter(QLServiceTypeFilterInput.ALL)
                                                       .appIds(null)
                                                       .envIds(null)
                                                       .servIds(null)
                                                       .build();

    QLSetupInput qlValidSetupInput =
        QLSetupInput.builder().isDurationBased(true).duration(3600000L).from(1633743784L).build();

    QLCreateDeploymentFreezeWindowInput qlCreateDeploymentFreezeWindowInput =
        QLCreateDeploymentFreezeWindowInput.builder()
            .clientMutationId("req1")
            .name("DFW")
            .description("freeze description")
            .freezeWindows(Arrays.asList(qlValidFreezeWindowInput))
            .setup(qlValidSetupInput)
            .notifyTo(Arrays.asList("usergroups"))
            .build();

    assertThatThrownBy(()
                           -> deploymentFreezeWindowController.populateDeploymentFreezeWindowEntity(
                               qlCreateDeploymentFreezeWindowInput))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "You have provided some unnecessary input(s) for Duration-Based deployment freeze window. Please provide only valid inputs as applicable to the Duration-Based deployment freeze windows.");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void validUserGroupIds() {
    QLFreezeWindowInput qlValidFreezeWindowInput = QLFreezeWindowInput.builder()
                                                       .appFilter(BlackoutWindowFilterType.ALL)
                                                       .envTypeFilter(QLEnvironmentTypeFilterInput.ALL)
                                                       .serviceTypeFilter(QLServiceTypeFilterInput.ALL)
                                                       .appIds(null)
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
            .setup(qlValidSetupInput)
            .notifyTo(Arrays.asList("usergroups"))
            .build();

    doReturn(new UserGroup()).when(userGroupService).get(anyString(), eq("usergroups"));

    TimeRangeBasedFreezeConfig timeRangeBasedFreezeConfig =
        deploymentFreezeWindowController.populateDeploymentFreezeWindowEntity(qlCreateDeploymentFreezeWindowInput);

    deploymentFreezeWindowController.validateDeploymentFreezeWindowInput(timeRangeBasedFreezeConfig, "accountId");

    assertThat(timeRangeBasedFreezeConfig).isNotNull();
    assertThat(timeRangeBasedFreezeConfig.getName()).isEqualTo("DFW");
    assertThat(timeRangeBasedFreezeConfig.getDescription()).isEqualTo("freeze description");
    assertThat(timeRangeBasedFreezeConfig.getTimeRange().isDurationBased()).isEqualTo(true);
    assertThat(timeRangeBasedFreezeConfig.getTimeRange().getDuration()).isEqualTo(3600000L);
    assertThat(timeRangeBasedFreezeConfig.getAppSelections().get(0).getFilterType())
        .isEqualTo(BlackoutWindowFilterType.ALL);
    assertThat(timeRangeBasedFreezeConfig.getAppSelections().get(0).getEnvSelection().getFilterType())
        .isEqualTo(EnvironmentFilterType.ALL);
    assertThat(timeRangeBasedFreezeConfig.getUserGroups()).isEqualTo(Arrays.asList("usergroups"));
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void allAppCustomEnvironment() {
    QLFreezeWindowInput qlValidFreezeWindowInput = QLFreezeWindowInput.builder()
                                                       .appFilter(BlackoutWindowFilterType.ALL)
                                                       .envTypeFilter(QLEnvironmentTypeFilterInput.CUSTOM)
                                                       .serviceTypeFilter(QLServiceTypeFilterInput.ALL)
                                                       .appIds(null)
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
            .setup(qlValidSetupInput)
            .notifyTo(Arrays.asList("usergroups"))
            .build();

    assertThatThrownBy(()
                           -> deploymentFreezeWindowController.populateDeploymentFreezeWindowEntity(
                               qlCreateDeploymentFreezeWindowInput))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Invalid filter type. 'Custom' environment filter type is applicable only for a single application. You have selected all applications.");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void allAppCustomService() {
    QLFreezeWindowInput qlValidFreezeWindowInput = QLFreezeWindowInput.builder()
                                                       .appFilter(BlackoutWindowFilterType.ALL)
                                                       .envTypeFilter(QLEnvironmentTypeFilterInput.ALL)
                                                       .serviceTypeFilter(QLServiceTypeFilterInput.CUSTOM)
                                                       .appIds(null)
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
            .setup(qlValidSetupInput)
            .notifyTo(Arrays.asList("usergroups"))
            .build();

    assertThatThrownBy(()
                           -> deploymentFreezeWindowController.populateDeploymentFreezeWindowEntity(
                               qlCreateDeploymentFreezeWindowInput))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Invalid filter type. 'Custom' service filter type is applicable only for a single application. You have selected all applications.");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void allAppFilterWithAppIdsGiven() {
    QLFreezeWindowInput qlValidFreezeWindowInput = QLFreezeWindowInput.builder()
                                                       .appFilter(BlackoutWindowFilterType.ALL)
                                                       .envTypeFilter(QLEnvironmentTypeFilterInput.ALL)
                                                       .serviceTypeFilter(QLServiceTypeFilterInput.ALL)
                                                       .appIds(Arrays.asList("appId1", "appId2", "appId3"))
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
            .setup(qlValidSetupInput)
            .notifyTo(Arrays.asList("usergroups"))
            .build();

    assertThatThrownBy(()
                           -> deploymentFreezeWindowController.populateDeploymentFreezeWindowEntity(
                               qlCreateDeploymentFreezeWindowInput))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("'appIds', 'envIds' and 'servIds' must not be given when 'appFilter' is selected as 'ALL'.");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void allEnvFilterWithEnvIdsGiven() {
    QLFreezeWindowInput qlValidFreezeWindowInput = QLFreezeWindowInput.builder()
                                                       .appFilter(BlackoutWindowFilterType.CUSTOM)
                                                       .envTypeFilter(QLEnvironmentTypeFilterInput.ALL)
                                                       .serviceTypeFilter(QLServiceTypeFilterInput.ALL)
                                                       .appIds(Arrays.asList("appId1"))
                                                       .envIds(Arrays.asList("envId1", "envId2", "envId3"))
                                                       .servIds(null)
                                                       .build();

    QLSetupInput qlValidSetupInput = QLSetupInput.builder().isDurationBased(true).duration(3600000L).build();

    QLCreateDeploymentFreezeWindowInput qlCreateDeploymentFreezeWindowInput =
        QLCreateDeploymentFreezeWindowInput.builder()
            .clientMutationId("req1")
            .name("DFW")
            .description("freeze description")
            .freezeWindows(Arrays.asList(qlValidFreezeWindowInput))
            .setup(qlValidSetupInput)
            .notifyTo(Arrays.asList("usergroups"))
            .build();

    assertThatThrownBy(()
                           -> deploymentFreezeWindowController.populateDeploymentFreezeWindowEntity(
                               qlCreateDeploymentFreezeWindowInput))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("'envIds' must not be given when 'envTypeFilter' is given as 'ALL'.");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void allServiceFilterWithServiceIdsGiven() {
    QLFreezeWindowInput qlValidFreezeWindowInput = QLFreezeWindowInput.builder()
                                                       .appFilter(BlackoutWindowFilterType.CUSTOM)
                                                       .envTypeFilter(QLEnvironmentTypeFilterInput.ALL)
                                                       .serviceTypeFilter(QLServiceTypeFilterInput.ALL)
                                                       .appIds(Arrays.asList("appId1"))
                                                       .envIds(null)
                                                       .servIds(Arrays.asList("servId1", "servId2", "servId3"))
                                                       .build();

    QLSetupInput qlValidSetupInput = QLSetupInput.builder().isDurationBased(true).duration(3600000L).build();

    QLCreateDeploymentFreezeWindowInput qlCreateDeploymentFreezeWindowInput =
        QLCreateDeploymentFreezeWindowInput.builder()
            .clientMutationId("req1")
            .name("DFW")
            .description("freeze description")
            .freezeWindows(Arrays.asList(qlValidFreezeWindowInput))
            .setup(qlValidSetupInput)
            .notifyTo(Arrays.asList("usergroups"))
            .build();

    assertThatThrownBy(()
                           -> deploymentFreezeWindowController.populateDeploymentFreezeWindowEntity(
                               qlCreateDeploymentFreezeWindowInput))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("'servIds' must not be given when 'serviceTypeFilter' is given as 'ALL'.");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void multipleAppIdsWithCustomEnvFilter() {
    QLFreezeWindowInput qlValidFreezeWindowInput = QLFreezeWindowInput.builder()
                                                       .appFilter(BlackoutWindowFilterType.CUSTOM)
                                                       .envTypeFilter(QLEnvironmentTypeFilterInput.CUSTOM)
                                                       .serviceTypeFilter(QLServiceTypeFilterInput.ALL)
                                                       .appIds(Arrays.asList("appId1", "appId2", "appId3"))
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
            .setup(qlValidSetupInput)
            .notifyTo(Arrays.asList("usergroups"))
            .build();

    assertThatThrownBy(()
                           -> deploymentFreezeWindowController.populateDeploymentFreezeWindowEntity(
                               qlCreateDeploymentFreezeWindowInput))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Invalid filter type. 'Custom' environment filter type is applicable only for a single application. You have selected multiple applications.");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void multipleAppIdsWithCustomServiceFilter() {
    QLFreezeWindowInput qlValidFreezeWindowInput = QLFreezeWindowInput.builder()
                                                       .appFilter(BlackoutWindowFilterType.CUSTOM)
                                                       .envTypeFilter(QLEnvironmentTypeFilterInput.ALL)
                                                       .serviceTypeFilter(QLServiceTypeFilterInput.CUSTOM)
                                                       .appIds(Arrays.asList("appId1", "appId2", "appId3"))
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
            .setup(qlValidSetupInput)
            .notifyTo(Arrays.asList("usergroups"))
            .build();

    assertThatThrownBy(()
                           -> deploymentFreezeWindowController.populateDeploymentFreezeWindowEntity(
                               qlCreateDeploymentFreezeWindowInput))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "'servIds' cannot be empty. Please enter the service Ids applicable to the deployment freeze window.");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void customAppFilterWithNoAppIds() {
    QLFreezeWindowInput qlValidFreezeWindowInput = QLFreezeWindowInput.builder()
                                                       .appFilter(BlackoutWindowFilterType.CUSTOM)
                                                       .envTypeFilter(QLEnvironmentTypeFilterInput.ALL)
                                                       .serviceTypeFilter(QLServiceTypeFilterInput.ALL)
                                                       .appIds(null)
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
            .setup(qlValidSetupInput)
            .notifyTo(Arrays.asList("usergroups"))
            .build();

    assertThatThrownBy(()
                           -> deploymentFreezeWindowController.populateDeploymentFreezeWindowEntity(
                               qlCreateDeploymentFreezeWindowInput))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "'appIds' cannot be empty. Please enter the application ids applicable to the deployment freeze window.");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void customEnvFilterWithNoEnvIds() {
    QLFreezeWindowInput qlValidFreezeWindowInput = QLFreezeWindowInput.builder()
                                                       .appFilter(BlackoutWindowFilterType.CUSTOM)
                                                       .envTypeFilter(QLEnvironmentTypeFilterInput.CUSTOM)
                                                       .serviceTypeFilter(QLServiceTypeFilterInput.ALL)
                                                       .appIds(Arrays.asList("appId1"))
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
            .setup(qlValidSetupInput)
            .notifyTo(Arrays.asList("usergroups"))
            .build();

    assertThatThrownBy(()
                           -> deploymentFreezeWindowController.populateDeploymentFreezeWindowEntity(
                               qlCreateDeploymentFreezeWindowInput))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "'envIds' cannot be empty. Please enter the environment ids applicable to the deployment freeze window.");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void customServiceFilterWithNoServiceIds() {
    QLFreezeWindowInput qlValidFreezeWindowInput = QLFreezeWindowInput.builder()
                                                       .appFilter(BlackoutWindowFilterType.CUSTOM)
                                                       .envTypeFilter(QLEnvironmentTypeFilterInput.ALL)
                                                       .serviceTypeFilter(QLServiceTypeFilterInput.CUSTOM)
                                                       .appIds(Arrays.asList("appId1"))
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
            .setup(qlValidSetupInput)
            .notifyTo(Arrays.asList("usergroups"))
            .build();

    assertThatThrownBy(()
                           -> deploymentFreezeWindowController.populateDeploymentFreezeWindowEntity(
                               qlCreateDeploymentFreezeWindowInput))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "'servIds' cannot be empty. Please enter the service Ids applicable to the deployment freeze window.");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void emptyAppId() {
    QLFreezeWindowInput qlValidFreezeWindowInput = QLFreezeWindowInput.builder()
                                                       .appFilter(BlackoutWindowFilterType.CUSTOM)
                                                       .envTypeFilter(QLEnvironmentTypeFilterInput.ALL)
                                                       .serviceTypeFilter(QLServiceTypeFilterInput.ALL)
                                                       .appIds(Arrays.asList(""))
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
            .setup(qlValidSetupInput)
            .notifyTo(Arrays.asList("usergroups"))
            .build();

    doReturn(new UserGroup()).when(userGroupService).get(anyString(), eq("usergroups"));

    TimeRangeBasedFreezeConfig timeRangeBasedFreezeConfig =
        deploymentFreezeWindowController.populateDeploymentFreezeWindowEntity(qlCreateDeploymentFreezeWindowInput);

    assertThatThrownBy(()
                           -> deploymentFreezeWindowController.validateDeploymentFreezeWindowInput(
                               timeRangeBasedFreezeConfig, "accountId"))
        .hasMessage("'appId' cannot be an empty string. Please insert a valid appId.")
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void emptyUserGroupId() {
    QLFreezeWindowInput qlValidFreezeWindowInput = QLFreezeWindowInput.builder()
                                                       .appFilter(BlackoutWindowFilterType.CUSTOM)
                                                       .envTypeFilter(QLEnvironmentTypeFilterInput.ALL)
                                                       .serviceTypeFilter(QLServiceTypeFilterInput.ALL)
                                                       .appIds(Arrays.asList("appId1"))
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
            .setup(qlValidSetupInput)
            .notifyTo(Arrays.asList(""))
            .build();

    TimeRangeBasedFreezeConfig timeRangeBasedFreezeConfig =
        deploymentFreezeWindowController.populateDeploymentFreezeWindowEntity(qlCreateDeploymentFreezeWindowInput);

    assertThatThrownBy(()
                           -> deploymentFreezeWindowController.validateDeploymentFreezeWindowInput(
                               timeRangeBasedFreezeConfig, "accountId"))
        .hasMessage("User group Id cannot be empty")
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void emptyName() {
    QLFreezeWindowInput qlValidFreezeWindowInput = QLFreezeWindowInput.builder()
                                                       .appFilter(BlackoutWindowFilterType.CUSTOM)
                                                       .envTypeFilter(QLEnvironmentTypeFilterInput.ALL)
                                                       .serviceTypeFilter(QLServiceTypeFilterInput.ALL)
                                                       .appIds(Arrays.asList("appId1"))
                                                       .envIds(null)
                                                       .servIds(null)
                                                       .build();

    QLSetupInput qlValidSetupInput = QLSetupInput.builder().isDurationBased(true).duration(3600000L).build();

    QLCreateDeploymentFreezeWindowInput qlCreateDeploymentFreezeWindowInput =
        QLCreateDeploymentFreezeWindowInput.builder()
            .clientMutationId("req1")
            .name("")
            .description("freeze description")
            .freezeWindows(Arrays.asList(qlValidFreezeWindowInput))
            .setup(qlValidSetupInput)
            .notifyTo(Arrays.asList("usergroups"))
            .build();

    doReturn(new UserGroup()).when(userGroupService).get(anyString(), eq("usergroups"));

    TimeRangeBasedFreezeConfig timeRangeBasedFreezeConfig =
        deploymentFreezeWindowController.populateDeploymentFreezeWindowEntity(qlCreateDeploymentFreezeWindowInput);

    assertThatThrownBy(()
                           -> deploymentFreezeWindowController.validateDeploymentFreezeWindowInput(
                               timeRangeBasedFreezeConfig, "accountId"))
        .hasMessage("Name cannot be empty for a freeze window")
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void durationBasedWithNullDuration() {
    QLFreezeWindowInput qlValidFreezeWindowInput = QLFreezeWindowInput.builder()
                                                       .appFilter(BlackoutWindowFilterType.ALL)
                                                       .envTypeFilter(QLEnvironmentTypeFilterInput.ALL)
                                                       .serviceTypeFilter(QLServiceTypeFilterInput.ALL)
                                                       .appIds(null)
                                                       .envIds(null)
                                                       .servIds(null)
                                                       .build();

    QLSetupInput qlValidSetupInput = QLSetupInput.builder().isDurationBased(true).duration(null).build();

    QLCreateDeploymentFreezeWindowInput qlCreateDeploymentFreezeWindowInput =
        QLCreateDeploymentFreezeWindowInput.builder()
            .clientMutationId("req1")
            .name("DFW")
            .description("freeze description")
            .freezeWindows(Arrays.asList(qlValidFreezeWindowInput))
            .setup(qlValidSetupInput)
            .notifyTo(Arrays.asList("usergroups"))
            .build();

    doReturn(new UserGroup()).when(userGroupService).get(anyString(), eq("usergroups"));

    assertThatThrownBy(()
                           -> deploymentFreezeWindowController.populateDeploymentFreezeWindowEntity(
                               qlCreateDeploymentFreezeWindowInput))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Please provide 'duration' parameter for Duration-based deployment freeze windows");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void durationBasedWithDurationLessThanHalfHour() {
    QLFreezeWindowInput qlValidFreezeWindowInput = QLFreezeWindowInput.builder()
                                                       .appFilter(BlackoutWindowFilterType.ALL)
                                                       .envTypeFilter(QLEnvironmentTypeFilterInput.ALL)
                                                       .serviceTypeFilter(QLServiceTypeFilterInput.ALL)
                                                       .appIds(null)
                                                       .envIds(null)
                                                       .servIds(null)
                                                       .build();

    QLSetupInput qlValidSetupInput = QLSetupInput.builder().isDurationBased(true).duration(11111L).build();

    QLCreateDeploymentFreezeWindowInput qlCreateDeploymentFreezeWindowInput =
        QLCreateDeploymentFreezeWindowInput.builder()
            .clientMutationId("req1")
            .name("DFW")
            .description("freeze description")
            .freezeWindows(Arrays.asList(qlValidFreezeWindowInput))
            .setup(qlValidSetupInput)
            .notifyTo(Arrays.asList("usergroups"))
            .build();

    doReturn(new UserGroup()).when(userGroupService).get(anyString(), eq("usergroups"));

    assertThatThrownBy(()
                           -> deploymentFreezeWindowController.populateDeploymentFreezeWindowEntity(
                               qlCreateDeploymentFreezeWindowInput))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "'duration' parameter must be greater or equal to 30 minutes(1800000 ms). Please enter a valid duration");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void durationBasedWithDurationMoreThanOneYear() {
    QLFreezeWindowInput qlValidFreezeWindowInput = QLFreezeWindowInput.builder()
                                                       .appFilter(BlackoutWindowFilterType.ALL)
                                                       .envTypeFilter(QLEnvironmentTypeFilterInput.ALL)
                                                       .serviceTypeFilter(QLServiceTypeFilterInput.ALL)
                                                       .appIds(null)
                                                       .envIds(null)
                                                       .servIds(null)
                                                       .build();

    QLSetupInput qlValidSetupInput = QLSetupInput.builder().isDurationBased(true).duration(32000000000L).build();

    QLCreateDeploymentFreezeWindowInput qlCreateDeploymentFreezeWindowInput =
        QLCreateDeploymentFreezeWindowInput.builder()
            .clientMutationId("req1")
            .name("DFW")
            .description("freeze description")
            .freezeWindows(Arrays.asList(qlValidFreezeWindowInput))
            .setup(qlValidSetupInput)
            .notifyTo(Arrays.asList("usergroups"))
            .build();

    doReturn(new UserGroup()).when(userGroupService).get(anyString(), eq("usergroups"));

    assertThatThrownBy(()
                           -> deploymentFreezeWindowController.populateDeploymentFreezeWindowEntity(
                               qlCreateDeploymentFreezeWindowInput))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("'duration' parameter cannot exceed one year.");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void scheduledBasedWithFromParameterNotProvided() {
    QLFreezeWindowInput qlValidFreezeWindowInput = QLFreezeWindowInput.builder()
                                                       .appFilter(BlackoutWindowFilterType.ALL)
                                                       .envTypeFilter(QLEnvironmentTypeFilterInput.ALL)
                                                       .serviceTypeFilter(QLServiceTypeFilterInput.ALL)
                                                       .appIds(null)
                                                       .envIds(null)
                                                       .servIds(null)
                                                       .build();

    QLSetupInput qlValidSetupInput = QLSetupInput.builder().isDurationBased(false).to(1650000000000L).build();

    QLCreateDeploymentFreezeWindowInput qlCreateDeploymentFreezeWindowInput =
        QLCreateDeploymentFreezeWindowInput.builder()
            .clientMutationId("req1")
            .name("DFW")
            .description("freeze description")
            .freezeWindows(Arrays.asList(qlValidFreezeWindowInput))
            .setup(qlValidSetupInput)
            .notifyTo(Arrays.asList("usergroups"))
            .build();

    doReturn(new UserGroup()).when(userGroupService).get(anyString(), eq("usergroups"));

    assertThatThrownBy(()
                           -> deploymentFreezeWindowController.populateDeploymentFreezeWindowEntity(
                               qlCreateDeploymentFreezeWindowInput))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Please provide 'from' parameter for Schedule-based deployment freeze windows");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void scheduledBasedWithToParameterNotProvided() {
    QLFreezeWindowInput qlValidFreezeWindowInput = QLFreezeWindowInput.builder()
                                                       .appFilter(BlackoutWindowFilterType.ALL)
                                                       .envTypeFilter(QLEnvironmentTypeFilterInput.ALL)
                                                       .serviceTypeFilter(QLServiceTypeFilterInput.ALL)
                                                       .appIds(null)
                                                       .envIds(null)
                                                       .servIds(null)
                                                       .build();

    QLSetupInput qlValidSetupInput = QLSetupInput.builder().isDurationBased(false).from(1650000000000L).build();

    QLCreateDeploymentFreezeWindowInput qlCreateDeploymentFreezeWindowInput =
        QLCreateDeploymentFreezeWindowInput.builder()
            .clientMutationId("req1")
            .name("DFW")
            .description("freeze description")
            .freezeWindows(Arrays.asList(qlValidFreezeWindowInput))
            .setup(qlValidSetupInput)
            .notifyTo(Arrays.asList("usergroups"))
            .build();

    doReturn(new UserGroup()).when(userGroupService).get(anyString(), eq("usergroups"));

    assertThatThrownBy(()
                           -> deploymentFreezeWindowController.populateDeploymentFreezeWindowEntity(
                               qlCreateDeploymentFreezeWindowInput))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Please provide 'to' parameter for Schedule-based deployment freeze windows");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void scheduledBasedWithToParameterNotExceedingFiveYearsFromNow() {
    QLFreezeWindowInput qlValidFreezeWindowInput = QLFreezeWindowInput.builder()
                                                       .appFilter(BlackoutWindowFilterType.ALL)
                                                       .envTypeFilter(QLEnvironmentTypeFilterInput.ALL)
                                                       .serviceTypeFilter(QLServiceTypeFilterInput.ALL)
                                                       .appIds(null)
                                                       .envIds(null)
                                                       .servIds(null)
                                                       .build();

    QLSetupInput qlValidSetupInput =
        QLSetupInput.builder().isDurationBased(false).from(1650000000000L).to(160000000000000L).build();

    QLCreateDeploymentFreezeWindowInput qlCreateDeploymentFreezeWindowInput =
        QLCreateDeploymentFreezeWindowInput.builder()
            .clientMutationId("req1")
            .name("DFW")
            .description("freeze description")
            .freezeWindows(Arrays.asList(qlValidFreezeWindowInput))
            .setup(qlValidSetupInput)
            .notifyTo(Arrays.asList("usergroups"))
            .build();

    doReturn(new UserGroup()).when(userGroupService).get(anyString(), eq("usergroups"));

    assertThatThrownBy(()
                           -> deploymentFreezeWindowController.populateDeploymentFreezeWindowEntity(
                               qlCreateDeploymentFreezeWindowInput))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("'to' parameter cannot be more than 5 years from now.");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void scheduledBasedWithToParameterLessThanFromParameter() {
    QLFreezeWindowInput qlValidFreezeWindowInput = QLFreezeWindowInput.builder()
                                                       .appFilter(BlackoutWindowFilterType.ALL)
                                                       .envTypeFilter(QLEnvironmentTypeFilterInput.ALL)
                                                       .serviceTypeFilter(QLServiceTypeFilterInput.ALL)
                                                       .appIds(null)
                                                       .envIds(null)
                                                       .servIds(null)
                                                       .build();

    QLSetupInput qlValidSetupInput =
        QLSetupInput.builder().isDurationBased(false).from(1650000000000L).to(160000L).build();

    QLCreateDeploymentFreezeWindowInput qlCreateDeploymentFreezeWindowInput =
        QLCreateDeploymentFreezeWindowInput.builder()
            .clientMutationId("req1")
            .name("DFW")
            .description("freeze description")
            .freezeWindows(Arrays.asList(qlValidFreezeWindowInput))
            .setup(qlValidSetupInput)
            .notifyTo(Arrays.asList("usergroups"))
            .build();

    doReturn(new UserGroup()).when(userGroupService).get(anyString(), eq("usergroups"));

    assertThatThrownBy(()
                           -> deploymentFreezeWindowController.populateDeploymentFreezeWindowEntity(
                               qlCreateDeploymentFreezeWindowInput))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("'to' parameter should always be greater than 'from' parameter.");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void scheduledBasedWithToMinusFromLessThanHalfHour() {
    QLFreezeWindowInput qlValidFreezeWindowInput = QLFreezeWindowInput.builder()
                                                       .appFilter(BlackoutWindowFilterType.ALL)
                                                       .envTypeFilter(QLEnvironmentTypeFilterInput.ALL)
                                                       .serviceTypeFilter(QLServiceTypeFilterInput.ALL)
                                                       .appIds(null)
                                                       .envIds(null)
                                                       .servIds(null)
                                                       .build();

    QLSetupInput qlValidSetupInput =
        QLSetupInput.builder().isDurationBased(false).from(1650000000000L).to(1650000000001L).build();

    QLCreateDeploymentFreezeWindowInput qlCreateDeploymentFreezeWindowInput =
        QLCreateDeploymentFreezeWindowInput.builder()
            .clientMutationId("req1")
            .name("DFW")
            .description("freeze description")
            .freezeWindows(Arrays.asList(qlValidFreezeWindowInput))
            .setup(qlValidSetupInput)
            .notifyTo(Arrays.asList("usergroups"))
            .build();

    doReturn(new UserGroup()).when(userGroupService).get(anyString(), eq("usergroups"));

    assertThatThrownBy(()
                           -> deploymentFreezeWindowController.populateDeploymentFreezeWindowEntity(
                               qlCreateDeploymentFreezeWindowInput))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "'to' parameter must be greater than 'from' parameter by atleast 30 minutes(1800000 ms). Please enter a valid duration.");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void scheduledBasedWithToMinusFromMoreThanOneYear() {
    QLFreezeWindowInput qlValidFreezeWindowInput = QLFreezeWindowInput.builder()
                                                       .appFilter(BlackoutWindowFilterType.ALL)
                                                       .envTypeFilter(QLEnvironmentTypeFilterInput.ALL)
                                                       .serviceTypeFilter(QLServiceTypeFilterInput.ALL)
                                                       .appIds(null)
                                                       .envIds(null)
                                                       .servIds(null)
                                                       .build();

    QLSetupInput qlValidSetupInput =
        QLSetupInput.builder().isDurationBased(false).from(1650000000000L).to(1750000000000L).build();

    QLCreateDeploymentFreezeWindowInput qlCreateDeploymentFreezeWindowInput =
        QLCreateDeploymentFreezeWindowInput.builder()
            .clientMutationId("req1")
            .name("DFW")
            .description("freeze description")
            .freezeWindows(Arrays.asList(qlValidFreezeWindowInput))
            .setup(qlValidSetupInput)
            .notifyTo(Arrays.asList("usergroups"))
            .build();

    doReturn(new UserGroup()).when(userGroupService).get(anyString(), eq("usergroups"));

    assertThatThrownBy(()
                           -> deploymentFreezeWindowController.populateDeploymentFreezeWindowEntity(
                               qlCreateDeploymentFreezeWindowInput))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("duration of the freeze cannot exceed one year.");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void scheduledBasedWithExpiryTimeLessThanToParameter() {
    QLFreezeWindowInput qlValidFreezeWindowInput = QLFreezeWindowInput.builder()
                                                       .appFilter(BlackoutWindowFilterType.ALL)
                                                       .envTypeFilter(QLEnvironmentTypeFilterInput.ALL)
                                                       .serviceTypeFilter(QLServiceTypeFilterInput.ALL)
                                                       .appIds(null)
                                                       .envIds(null)
                                                       .servIds(null)
                                                       .build();

    QLSetupInput qlValidSetupInput = QLSetupInput.builder()
                                         .isDurationBased(false)
                                         .from(1650000000000L)
                                         .to(1660000000000L)
                                         .freezeOccurrence(TimeRangeOccurrence.ANNUAL)
                                         .untilForever(false)
                                         .expiryTime(1655000000000L)
                                         .build();

    QLCreateDeploymentFreezeWindowInput qlCreateDeploymentFreezeWindowInput =
        QLCreateDeploymentFreezeWindowInput.builder()
            .clientMutationId("req1")
            .name("DFW")
            .description("freeze description")
            .freezeWindows(Arrays.asList(qlValidFreezeWindowInput))
            .setup(qlValidSetupInput)
            .notifyTo(Arrays.asList("usergroups"))
            .build();

    doReturn(new UserGroup()).when(userGroupService).get(anyString(), eq("usergroups"));

    assertThatThrownBy(()
                           -> deploymentFreezeWindowController.populateDeploymentFreezeWindowEntity(
                               qlCreateDeploymentFreezeWindowInput))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("'endTime' parameter cannot be less than the 'to' parameter.");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void scheduledBasedForeverButExpiryTimeProvided() {
    QLFreezeWindowInput qlValidFreezeWindowInput = QLFreezeWindowInput.builder()
                                                       .appFilter(BlackoutWindowFilterType.ALL)
                                                       .envTypeFilter(QLEnvironmentTypeFilterInput.ALL)
                                                       .serviceTypeFilter(QLServiceTypeFilterInput.ALL)
                                                       .appIds(null)
                                                       .envIds(null)
                                                       .servIds(null)
                                                       .build();

    QLSetupInput qlValidSetupInput = QLSetupInput.builder()
                                         .isDurationBased(false)
                                         .from(1650000000000L)
                                         .to(1660000000000L)
                                         .freezeOccurrence(TimeRangeOccurrence.ANNUAL)
                                         .untilForever(true)
                                         .expiryTime(1655000000000L)
                                         .build();

    QLCreateDeploymentFreezeWindowInput qlCreateDeploymentFreezeWindowInput =
        QLCreateDeploymentFreezeWindowInput.builder()
            .clientMutationId("req1")
            .name("DFW")
            .description("freeze description")
            .freezeWindows(Arrays.asList(qlValidFreezeWindowInput))
            .setup(qlValidSetupInput)
            .notifyTo(Arrays.asList("usergroups"))
            .build();

    doReturn(new UserGroup()).when(userGroupService).get(anyString(), eq("usergroups"));

    assertThatThrownBy(()
                           -> deploymentFreezeWindowController.populateDeploymentFreezeWindowEntity(
                               qlCreateDeploymentFreezeWindowInput))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "'expiryTime' parameter must not be provided for Schedule-based deployment freeze windows that do not expire");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void scheduledBasedFreezeOccurrenceButUntilForeverParameterNotProvided() {
    QLFreezeWindowInput qlValidFreezeWindowInput = QLFreezeWindowInput.builder()
                                                       .appFilter(BlackoutWindowFilterType.ALL)
                                                       .envTypeFilter(QLEnvironmentTypeFilterInput.ALL)
                                                       .serviceTypeFilter(QLServiceTypeFilterInput.ALL)
                                                       .appIds(null)
                                                       .envIds(null)
                                                       .servIds(null)
                                                       .build();

    QLSetupInput qlValidSetupInput = QLSetupInput.builder()
                                         .isDurationBased(false)
                                         .from(1650000000000L)
                                         .to(1660000000000L)
                                         .freezeOccurrence(TimeRangeOccurrence.ANNUAL)
                                         .untilForever(null)
                                         .build();

    QLCreateDeploymentFreezeWindowInput qlCreateDeploymentFreezeWindowInput =
        QLCreateDeploymentFreezeWindowInput.builder()
            .clientMutationId("req1")
            .name("DFW")
            .description("freeze description")
            .freezeWindows(Arrays.asList(qlValidFreezeWindowInput))
            .setup(qlValidSetupInput)
            .notifyTo(Arrays.asList("usergroups"))
            .build();

    doReturn(new UserGroup()).when(userGroupService).get(anyString(), eq("usergroups"));

    assertThatThrownBy(()
                           -> deploymentFreezeWindowController.populateDeploymentFreezeWindowEntity(
                               qlCreateDeploymentFreezeWindowInput))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("'untilForever' parameter cannot be null in a recurring deployment freeze window.");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void scheduledBasedNonRecurringFreezeButUntilForeverParameterProvided() {
    QLFreezeWindowInput qlValidFreezeWindowInput = QLFreezeWindowInput.builder()
                                                       .appFilter(BlackoutWindowFilterType.ALL)
                                                       .envTypeFilter(QLEnvironmentTypeFilterInput.ALL)
                                                       .serviceTypeFilter(QLServiceTypeFilterInput.ALL)
                                                       .appIds(null)
                                                       .envIds(null)
                                                       .servIds(null)
                                                       .build();

    QLSetupInput qlValidSetupInput = QLSetupInput.builder()
                                         .isDurationBased(false)
                                         .from(1650000000000L)
                                         .to(1660000000000L)
                                         .untilForever(true)
                                         .build();

    QLCreateDeploymentFreezeWindowInput qlCreateDeploymentFreezeWindowInput =
        QLCreateDeploymentFreezeWindowInput.builder()
            .clientMutationId("req1")
            .name("DFW")
            .description("freeze description")
            .freezeWindows(Arrays.asList(qlValidFreezeWindowInput))
            .setup(qlValidSetupInput)
            .notifyTo(Arrays.asList("usergroups"))
            .build();

    doReturn(new UserGroup()).when(userGroupService).get(anyString(), eq("usergroups"));

    assertThatThrownBy(()
                           -> deploymentFreezeWindowController.populateDeploymentFreezeWindowEntity(
                               qlCreateDeploymentFreezeWindowInput))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("'untilForever' parameter must be null in a non-recurring deployment freeze window.");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void scheduledBasedNonRecurringFreezeButExpiryTimeParameterProvided() {
    QLFreezeWindowInput qlValidFreezeWindowInput = QLFreezeWindowInput.builder()
                                                       .appFilter(BlackoutWindowFilterType.ALL)
                                                       .envTypeFilter(QLEnvironmentTypeFilterInput.ALL)
                                                       .serviceTypeFilter(QLServiceTypeFilterInput.ALL)
                                                       .appIds(null)
                                                       .envIds(null)
                                                       .servIds(null)
                                                       .build();

    QLSetupInput qlValidSetupInput = QLSetupInput.builder()
                                         .isDurationBased(false)
                                         .from(1650000000000L)
                                         .to(1660000000000L)
                                         .expiryTime(1665000000000L)
                                         .build();

    QLCreateDeploymentFreezeWindowInput qlCreateDeploymentFreezeWindowInput =
        QLCreateDeploymentFreezeWindowInput.builder()
            .clientMutationId("req1")
            .name("DFW")
            .description("freeze description")
            .freezeWindows(Arrays.asList(qlValidFreezeWindowInput))
            .setup(qlValidSetupInput)
            .notifyTo(Arrays.asList("usergroups"))
            .build();

    doReturn(new UserGroup()).when(userGroupService).get(anyString(), eq("usergroups"));

    assertThatThrownBy(()
                           -> deploymentFreezeWindowController.populateDeploymentFreezeWindowEntity(
                               qlCreateDeploymentFreezeWindowInput))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("'expiryTime' parameter must be null in a non-recurring deployment freeze window.");
  }
}
