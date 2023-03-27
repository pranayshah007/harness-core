/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.steps.outcome;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.execution.PublishedFileArtifact;
import io.harness.beans.execution.PublishedImageArtifact;
import io.harness.ssca.execution.orchestration.outcome.PublishedSbomArtifact;

import java.util.List;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@OwnedBy(HarnessTeam.CI)
@TypeAlias("stepArtifacts")
@RecasterAlias("io.harness.beans.steps.outcome.StepArtifacts")
public class StepArtifacts {
  @Singular List<PublishedFileArtifact> publishedFileArtifacts;
  @Singular List<PublishedImageArtifact> publishedImageArtifacts;
  @Singular List<PublishedSbomArtifact> publishedSbomArtifacts;
}
