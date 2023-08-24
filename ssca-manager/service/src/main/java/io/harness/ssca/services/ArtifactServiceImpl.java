/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.services;

import io.harness.repositories.ArtifactRepository;
import io.harness.spec.server.ssca.v1.model.SbomProcessRequestBody;
import io.harness.ssca.beans.SbomDTO;
import io.harness.ssca.entities.ArtifactEntity;
import io.harness.ssca.s3.S3Store;
import io.harness.ssca.utils.SBOMUtils;

import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;

@Slf4j
public class ArtifactServiceImpl implements ArtifactService {
  @Inject ArtifactRepository artifactRepository;

  @Inject S3Store s3Store;

  @Override
  public ArtifactEntity getArtifactFromSbomPayload(
      String accountId, String orgIdentifier, String projectIdentifier, SbomProcessRequestBody body, SbomDTO sbomDTO) {
    String artifactId = UUID.nameUUIDFromBytes(body.getArtifact().getRegistryUrl().getBytes()).toString();
    return ArtifactEntity.builder()
        .id(UUID.randomUUID().toString())
        .artifactId(artifactId)
        .orchestrationId(body.getSbomMetadata().getStepExecutionId())
        .pipelineExecutionId(body.getSbomMetadata().getPipelineExecutionId())
        .name(body.getArtifact().getName())
        .orgId(orgIdentifier)
        .projectId(projectIdentifier)
        .accountId(accountId)
        .sbomName(body.getSbomProcess().getName())
        .type(body.getArtifact().getType())
        .url(body.getArtifact().getRegistryUrl())
        .pipelineId(body.getSbomMetadata().getPipelineIdentifier())
        .stageId(body.getSbomMetadata().getStageIdentifier())
        .tag(body.getArtifact().getTag())
        .isAttested(body.getAttestation().isIsAttested())
        .attestedFileUrl(body.getAttestation().getUrl())
        .stepId(body.getSbomMetadata().getStepIdentifier())
        .sequenceId(body.getSbomMetadata().getSequenceId())
        .createdOn(Instant.now())
        .sbom(ArtifactEntity.Sbom.builder()
                  .tool(body.getSbomMetadata().getTool())
                  .toolVersion("2.0")
                  .sbomFormat(body.getSbomProcess().getFormat())
                  .sbomVersion(SBOMUtils.getSbomVersion(sbomDTO))
                  .build())
        .build();
  }

  @Override
  public String sbom(String artifactId, String stepExecutionId) {
    ArtifactEntity artifactEntity = artifactRepository
                                        .findFirstByArtifactIdAndOrchestrationIdLike(artifactId, stepExecutionId,
                                            Sort.by(ArtifactEntity.ArtifactEntityKeys.createdOn).descending())
                                        .get();

    File sbomFile = s3Store.DownloadSBOMToGCP(artifactEntity);

    try {
      String fileContent = Files.readString(Paths.get(sbomFile.getPath()), StandardCharsets.UTF_8);
      sbomFile.delete();
      return fileContent;
    } catch (IOException e) {
      throw new RuntimeException("Error reading the SBOM file");
    }
  }
}
