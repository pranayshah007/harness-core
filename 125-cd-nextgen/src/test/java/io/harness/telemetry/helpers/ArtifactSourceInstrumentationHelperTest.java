/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.telemetry.helpers;

import static io.harness.rule.OwnerRule.SARTHAK_KASAT;

import static junit.framework.TestCase.assertTrue;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.artifact.bean.yaml.DockerHubArtifactConfig;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.concurrent.CompletableFuture;

import io.harness.telemetry.TelemetryReporter;
import org.jooq.tools.reflect.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ArtifactSourceInstrumentationHelperTest extends CategoryTest {
  private static final String ACCOUNT = "account";
  private static final String ORG = "org";
  private static final String PROJECT = "project";
  @InjectMocks ArtifactSourceInstrumentationHelper instrumentationHelper;
  @Mock TelemetryReporter telemetryReporter;
  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    Reflect.on(instrumentationHelper).set("telemetryReporter", telemetryReporter);
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testLastPublishedTagTrackSend() {
    DockerHubArtifactConfig dockerHubArtifactConfig =
        DockerHubArtifactConfig.builder().imagePath(ParameterField.createValueField("IMAGE")).build();
    CompletableFuture<Void> telemetryTask =
        instrumentationHelper.sendLastPublishedTagExpressionEvent(dockerHubArtifactConfig, ACCOUNT, ORG, PROJECT);
    telemetryTask.join();
    assertTrue(telemetryTask.isDone());
  }
}
