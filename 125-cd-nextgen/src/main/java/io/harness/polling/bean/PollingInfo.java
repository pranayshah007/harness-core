/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.polling.bean;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.polling.bean.artifact.ArtifactInfo;
import io.harness.polling.bean.gitpolling.GitPollingInfo;
import io.harness.polling.bean.manifest.ManifestInfo;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@OwnedBy(HarnessTeam.CDC)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = ArtifactInfo.class, name = "ARTIFACT")
  , @JsonSubTypes.Type(value = ManifestInfo.class, name = "MANIFEST"),
      @JsonSubTypes.Type(value = GitPollingInfo.class, name = "GITPOLLING")
})
public interface PollingInfo {}
