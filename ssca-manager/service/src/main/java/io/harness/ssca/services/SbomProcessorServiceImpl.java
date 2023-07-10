/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.services;

import io.harness.repositories.ArtifactRepository;
import io.harness.repositories.SBOMComponentRepo;
import io.harness.spec.server.ssca.v1.model.SbomProcessRequestBody;
import io.harness.ssca.beans.SbomDTO;
import io.harness.ssca.beans.SettingsDTO;
import io.harness.ssca.entities.ArtifactEntity;
import io.harness.ssca.entities.NormalizedSBOMComponentEntity;
import io.harness.ssca.normalize.Normalizer;
import io.harness.ssca.normalize.NormalizerRegistry;
import io.harness.ssca.utils.SBOMUtils;

import com.google.inject.Inject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SbomProcessorServiceImpl implements SbomProcessorService {
  @Inject ArtifactService artifactService;
  @Inject ArtifactRepository artifactRepository;
  @Inject SBOMComponentRepo SBOMComponentRepo;
  @Inject NormalizerRegistry normalizerRegistry;

  @Override
  public String processSBOM(String accountId, String orgIdentifier, String projectIdentifier,
      SbomProcessRequestBody sbomProcessRequestBody) throws ParseException {
    // TODO: Check if we can prevent IO Operation.
    // TODO: Upload to gcp step.
    // TODO: Use Jackson instead of Gson.
    log.info("Starting SBOM Processing");
    String sbomFileName = UUID.randomUUID() + "_sbom";
    File sbomDumpFile = new File(sbomFileName);
    try (FileOutputStream fos = new FileOutputStream(sbomFileName)) {
      byte[] data = sbomProcessRequestBody.getSbomProcess().getData();
      fos.write(data);
    } catch (IOException e) {
      log.error(String.format("Error in writing sbom to file: %s", sbomDumpFile));
    }

    ArtifactEntity artifactEntity;
    SbomDTO sbomDTO = SBOMUtils.getSbomDTO(
        sbomProcessRequestBody.getSbomProcess().getData(), sbomProcessRequestBody.getSbomMetadata().getFormat());
    artifactEntity = artifactService.getArtifactFromSbomPayload(
        accountId, orgIdentifier, projectIdentifier, sbomProcessRequestBody, sbomDTO);

    artifactEntity = artifactRepository.save(artifactEntity);
    SettingsDTO settingsDTO =
        getSettingsDTO(accountId, orgIdentifier, projectIdentifier, sbomProcessRequestBody, artifactEntity);

    Normalizer normalizer = normalizerRegistry.getNormalizer(settingsDTO.getFormat()).get();
    List<NormalizedSBOMComponentEntity> sbomEntityList = normalizer.normaliseSBOM(sbomDTO, settingsDTO);

    SBOMComponentRepo.saveAll(sbomEntityList);
    sbomDumpFile.delete();

    log.info(String.format("SBOM Processed Successfully, Artifact ID: %s", artifactEntity.getArtifactId()));
    return artifactEntity.getArtifactId();
  }

  private SettingsDTO getSettingsDTO(String accountId, String orgIdentifier, String projectIdentifier,
      SbomProcessRequestBody sbomProcessRequestBody, ArtifactEntity artifactEntity) {
    return SettingsDTO.builder()
        .orchestrationID(sbomProcessRequestBody.getSbomMetadata().getStepExecutionId())
        .pipelineIdentifier(sbomProcessRequestBody.getSbomMetadata().getPipelineIdentifier())
        .projectIdentifier(projectIdentifier)
        .orgIdentifier(orgIdentifier)
        .sequenceID(sbomProcessRequestBody.getSbomMetadata().getSequenceId())
        .accountID(accountId)
        .artifactURL(sbomProcessRequestBody.getArtifact().getRegistryUrl())
        .artifactID(artifactEntity.getArtifactId())
        .format(sbomProcessRequestBody.getSbomMetadata().getFormat())
        .tool(
            SettingsDTO.Tool.builder().name(sbomProcessRequestBody.getSbomMetadata().getTool()).version("2.0").build())
        .build();
  }
}
