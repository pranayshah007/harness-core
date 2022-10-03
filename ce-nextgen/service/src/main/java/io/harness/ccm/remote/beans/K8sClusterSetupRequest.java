/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.beans;

import io.harness.ccm.commons.beans.config.CEFeatures;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class K8sClusterSetupRequest {
  @Deprecated List<CEFeatures> featuresEnabled;

  // used by VISIBILITY feature
  String connectorIdentifier;
  String orgIdentifier;
  String projectIdentifier;

  // used by OPTIMIZATION feature
  String ccmConnectorIdentifier;
}
