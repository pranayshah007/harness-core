/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.dtos;

import io.harness.beans.ContentType;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public enum ScmFileContentTypeDTO {
  INVALID_CONTENT_TYPE,
  UNKNOWN_CONTENT,
  FILE,
  DIRECTORY,
  SYMLINK,
  GITLINK,
  ;

  public static ScmFileContentTypeDTO toScmFileContentTypeDTO(ContentType contentType) {
    try {
      return ScmFileContentTypeDTO.valueOf(contentType.name());
    } catch (Exception ex) {
      log.error("Scm Content Type Enum mapping not found for : {}", contentType.name(), ex);
      return INVALID_CONTENT_TYPE;
    }
  }

  public static io.harness.gitsync.ContentType getProtoContentType(ScmFileContentTypeDTO scmFileContentTypeDTO) {
    try {
      return io.harness.gitsync.ContentType.valueOf(scmFileContentTypeDTO.name());
    } catch (Exception ex) {
      log.error("Git service GRPC Content Type Enum mapping not found for : {}", scmFileContentTypeDTO.name(), ex);
      return io.harness.gitsync.ContentType.INVALID_CONTENT_TYPE;
    }
  }
}
