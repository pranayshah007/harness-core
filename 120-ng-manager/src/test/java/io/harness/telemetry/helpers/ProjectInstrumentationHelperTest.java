/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.telemetry.helpers;

import static io.harness.ng.core.remote.ProjectMapper.toProject;
import static io.harness.rule.OwnerRule.TEJAS;

import static junit.framework.TestCase.assertTrue;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.ng.core.entities.Project;
import io.harness.rule.Owner;
import io.harness.telemetry.TelemetryReporter;

import java.util.concurrent.CompletableFuture;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PL)
public class ProjectInstrumentationHelperTest {
  @InjectMocks ProjectInstrumentationHelper instrumentationHelper;
  @Mock TelemetryReporter telemetryReporter;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  private ProjectDTO createProjectDTO(String orgIdentifier, String identifier) {
    return ProjectDTO.builder()
        .orgIdentifier(orgIdentifier)
        .identifier(identifier)
        .name(randomAlphabetic(10))
        .color(randomAlphabetic(10))
        .build();
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testCreateProjectFinishedTrackSend() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    ProjectDTO projectDTO = createProjectDTO(orgIdentifier, randomAlphabetic(10));
    Project project = toProject(projectDTO);
    CompletableFuture<Void> telemetryTask = instrumentationHelper.sendProjectCreateEvent(project, accountIdentifier);
    telemetryTask.join();
    assertTrue(telemetryTask.isDone());
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testDeleteProjectTrackSend() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    ProjectDTO projectDTO = createProjectDTO(orgIdentifier, randomAlphabetic(10));
    Project project = toProject(projectDTO);
    CompletableFuture<Void> telemetryTask = instrumentationHelper.sendProjectDeleteEvent(project, accountIdentifier);
    telemetryTask.join();
    assertTrue(telemetryTask.isDone());
  }
}
