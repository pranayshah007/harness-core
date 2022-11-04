/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PL)
@Slf4j
public enum ContentType {
  UNKNOWN_CONTENT,
  FILE,
  DIRECTORY,
  SYMLINK,
  GITLINK,
  INVALID_CONTENT_TYPE,
  ;

  public static ContentType mapFromScmProtoValue(io.harness.product.ci.scm.proto.ContentType scmContentType) {
    try {
      return ContentType.valueOf(scmContentType.name());
    } catch (Exception ex) {
      log.error("Scm Content Type Enum mapping missing in java client for : {}", scmContentType.name(), ex);
      return INVALID_CONTENT_TYPE;
    }
  }
}
