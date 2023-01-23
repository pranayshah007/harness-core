package io.harness.delegate.task.artifacts.googlecloudsource;

import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.response.ArtifactBuildDetailsNG;
import io.harness.delegate.task.artifacts.response.ArtifactDelegateResponse;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = false)
public class GoogleCloudSourceArtifactDelegateResponse extends ArtifactDelegateResponse {
    /** refers to GCP Project*/
    String project;
    /** refers to GCS repository*/
    String repository;
    /** refers to sourceDirectory in GCS repository*/
    String sourceDirectory;

    @Builder
    public GoogleCloudSourceArtifactDelegateResponse(ArtifactBuildDetailsNG buildDetails, ArtifactSourceType sourceType,
                                                      String project, String repository, String sourceDirectory) {
        super(buildDetails, sourceType);

        this.project = project;
        this.repository = repository;
        this.sourceDirectory = sourceDirectory;
    }
}

