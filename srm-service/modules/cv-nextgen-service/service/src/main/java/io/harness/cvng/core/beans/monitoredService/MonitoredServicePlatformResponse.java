/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.monitoredService;

import io.harness.cvng.beans.MonitoredServiceType;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MonitoredServicePlatformResponse {
  String name;
  String identifier;
  String serviceRef;
  List<String> environmentRefs;
  String serviceName;
  MonitoredServiceType type;
  Map<String, String> tags;
  int configuredChangeSources;
  int configuredHealthSources;
}
