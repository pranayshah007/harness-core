/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.provenance;

import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.CODEBASE;
import static io.harness.rule.OwnerRule.INDER;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.ContainerTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.sweepingoutputs.CodebaseSweepingOutput;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.rule.Owner;
import io.harness.version.VersionInfoManager;

import com.google.api.client.util.DateTime;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.SSCA)
public class ProvenanceGeneratorTest extends ContainerTestBase {
  @InjectMocks ProvenanceGenerator provenanceGenerator;
  @Mock ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Mock VersionInfoManager versionInfoManager;

  private static final String ACCOUNT_ID = "accountId";
  private static final String PIPELINE_EXECUTION_ID = "pipelineExecutionId";
  private static final String PIPELINE_IDENTIFIER = "pipelineIdentifier";
  private static final String STEP_EXECUTION_ID = "stepExecutionId";
  private static final String IMAGE_NAME = "harness/ssca-plugin";
  private static final String IMAGE_TAG = "0.11.0";
  private static final long START_TIME = 100000000;
  private static final BuildMetadata buildMetadata =
      BuildMetadata.builder().image("someImage:latest").dockerFile("dockerFile").build();

  private static final String REPO_URL = "https://somerepo.com";
  private static final String BRANCH = "master";
  private static final String COMMIT_SHA = "someSha";

  private Ambiance ambiance;

  @Before
  public void setup() {
    ambiance = Ambiance.newBuilder().build();
    when(versionInfoManager.getFullVersion()).thenReturn("0.0.0");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testBuildProvenancePredicate_noCodeMetadata() {
    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    BuildDefinition expectedBuildDefinition =
        BuildDefinition.builder()
            .buildType("https://developer.harness.io/docs/continuous-integration")
            .internalParameters(InternalParameters.builder()
                                    .accountId(ACCOUNT_ID)
                                    .pipelineExecutionId(PIPELINE_EXECUTION_ID)
                                    .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                    .build())
            .externalParameters(ExternalParameters.builder().buildMetadata(buildMetadata).build())
            .build();
    Map<String, String> versionMap = new HashMap<>();
    versionMap.put("ci-manager", "0.0.0");
    versionMap.put(IMAGE_NAME, IMAGE_TAG);

    ProvenancePredicate predicate = provenanceGenerator.buildProvenancePredicate(getProvenanceBuilderData(), ambiance);
    assertThat(predicate).isNotNull();
    assertThat(predicate.getBuildDefinition()).isEqualTo(expectedBuildDefinition);
    assertThat(predicate.getRunDetails().getBuilder())
        .isEqualTo(ProvenanceBuilder.builder()
                       .id("https://developer.harness.io/docs/continuous-integration")
                       .version(versionMap)
                       .build());
    assertThat(predicate.getRunDetails().getRunDetailsMetadata().getInvocationId()).isEqualTo(STEP_EXECUTION_ID);
    assertThat(predicate.getRunDetails().getRunDetailsMetadata().getStartedOn())
        .isEqualTo(new DateTime(START_TIME).toStringRfc3339());
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testBuildProvenancePredicate_WithCodeMetadata() {
    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(CODEBASE)))
        .thenReturn(
            OptionalSweepingOutput.builder()
                .output(CodebaseSweepingOutput.builder().repoUrl(REPO_URL).branch(BRANCH).commitSha(COMMIT_SHA).build())
                .found(true)
                .build());

    BuildDefinition expectedBuildDefinition =
        BuildDefinition.builder()
            .buildType("https://developer.harness.io/docs/continuous-integration")
            .internalParameters(InternalParameters.builder()
                                    .accountId(ACCOUNT_ID)
                                    .pipelineExecutionId(PIPELINE_EXECUTION_ID)
                                    .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                    .build())
            .externalParameters(ExternalParameters.builder()
                                    .buildMetadata(buildMetadata)
                                    .codeMetadata(new CodeMetadata(REPO_URL, BRANCH, null, null, COMMIT_SHA))
                                    .build())
            .build();
    Map<String, String> versionMap = new HashMap<>();
    versionMap.put("ci-manager", "0.0.0");
    versionMap.put(IMAGE_NAME, IMAGE_TAG);

    ProvenancePredicate predicate = provenanceGenerator.buildProvenancePredicate(getProvenanceBuilderData(), ambiance);
    assertThat(predicate).isNotNull();
    assertThat(predicate.getBuildDefinition()).isEqualTo(expectedBuildDefinition);
    assertThat(predicate.getRunDetails().getBuilder())
        .isEqualTo(ProvenanceBuilder.builder()
                       .id("https://developer.harness.io/docs/continuous-integration")
                       .version(versionMap)
                       .build());
    assertThat(predicate.getRunDetails().getRunDetailsMetadata().getInvocationId()).isEqualTo(STEP_EXECUTION_ID);
    assertThat(predicate.getRunDetails().getRunDetailsMetadata().getStartedOn())
        .isEqualTo(new DateTime(START_TIME).toStringRfc3339());
  }

  private ProvenanceBuilderData getProvenanceBuilderData() {
    return ProvenanceBuilderData.builder()
        .accountId(ACCOUNT_ID)
        .pipelineExecutionId(PIPELINE_EXECUTION_ID)
        .pipelineIdentifier(PIPELINE_IDENTIFIER)
        .pluginInfo(IMAGE_NAME + ":" + IMAGE_TAG)
        .stepExecutionId(STEP_EXECUTION_ID)
        .buildMetadata(buildMetadata)
        .startTime(START_TIME)
        .build();
  }
}
