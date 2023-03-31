/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.dto;

import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@FieldNameConstants(innerTypeName = "HelmChartKeys")
public class HelmChart {
  private String uuid;
  private String version;
  private String applicationManifestId;
  private String appManifestName;
  private String name;
  private String displayName;
  private String accountId;
  private String appId;
  private String serviceId;
  private long createdAt;
  private long lastUpdatedAt;
  private String appVersion;
  private String description;
  private Map<String, String> metadata;
}
