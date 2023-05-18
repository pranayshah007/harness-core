/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.nexus.model;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Date;

@lombok.Data
@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(HarnessTeam.CDC)
public class Asset {
  private String downloadUrl;
  private String path;
  private String id;
  private String repository;
  private String format;
  private Checksum checksum;
  private Date lastModified;

  @lombok.Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class Checksum {
    private String sha1;
    private String sha512;
  }
}
