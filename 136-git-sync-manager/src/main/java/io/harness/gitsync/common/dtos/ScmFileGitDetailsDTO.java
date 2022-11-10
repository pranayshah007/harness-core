/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.dtos;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FileGitDetails;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@OwnedBy(HarnessTeam.PL)
@Value
@Builder
public class ScmFileGitDetailsDTO {
  String path;
  String commitId;
  String blobId;
  ScmFileContentTypeDTO contentType;

  public static List<ScmFileGitDetailsDTO> toScmFileGitDetailsDTOList(List<FileGitDetails> fileGitDetailsList) {
    List<ScmFileGitDetailsDTO> fileGitDetailsDTOList = new ArrayList<>();
    fileGitDetailsList.forEach(fileGitDetails
        -> fileGitDetailsDTOList.add(
            ScmFileGitDetailsDTO.builder()
                .commitId(fileGitDetails.getCommitId())
                .blobId(fileGitDetails.getBlobId())
                .contentType(ScmFileContentTypeDTO.toScmFileContentTypeDTO(fileGitDetails.getContentType()))
                .path(fileGitDetails.getPath())
                .build()));
    return fileGitDetailsDTOList;
  }
}
