/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.polling.bean.gitpolling;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.gitpolling.bean.GitPollingConfig;
import io.harness.polling.bean.PollingInfo;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;

@OwnedBy(HarnessTeam.CDC)
@JsonTypeName("GITPOLLING")
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXISTING_PROPERTY)
@JsonSubTypes({ @JsonSubTypes.Type(value = GitHubPollingInfo.class, name = "GitHub") })
public interface GitPollingInfo extends PollingInfo {
  String getType();

  GitPollingConfig toGitPollingConfig();
}
