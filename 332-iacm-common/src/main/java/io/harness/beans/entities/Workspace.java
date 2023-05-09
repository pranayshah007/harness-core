/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.entities;

import static io.harness.annotations.dev.HarnessTeam.IACM;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(IACM)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Workspace {
  Long created;
  String name;
  String account;
  String org;
  String project;
  String identifier;
  Long updated;
  String provisioner;
  String provisioner_version;
  String provider_connector;
  String repository;
  String repository_branch;
  String repository_connector;
  String repository_path;
  String repository_commit;
}
