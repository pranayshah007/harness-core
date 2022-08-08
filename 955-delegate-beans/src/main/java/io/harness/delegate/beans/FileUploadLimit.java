/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@OwnedBy(HarnessTeam.DEL)
public class FileUploadLimit {
  @JsonProperty private long appContainerLimit = 1000000000L;
  @JsonProperty private long configFileLimit = 100000000L;
  @JsonProperty private long fileStoreFileLimit = 100000000L;
  @JsonProperty private long hostUploadLimit = 100000000L;
  @JsonProperty private long profileResultLimit = 100000000L;
  @JsonProperty private long encryptedFileLimit = 10L * 1024 * 1024 /*10 MB */;
  @JsonProperty private long credentialFileLimit = 20L * 1024; /*20 KB */
  @JsonProperty private long commandUploadLimit = 10L * 1024 * 1024 /*10 MB */;
}
