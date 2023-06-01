/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.k8s.kubectl;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.version.Version;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.NoArgsConstructor;

@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
public enum Flag {
  dryrun(ImmutableMap.of("0", "dry-run", "1.18", "dry-run=client"),
      ImmutableMap.of("0", "dry-run", "4.5", "dry-run=client")) {
    @Override
    public String toString() {
      return "dry-run";
    }
  },
  export,
  record(ImmutableMap.of("0", "record", "1.22", ""), ImmutableMap.of("0", "record", "4.9", "")),
  watch,
  watchOnly {
    @Override
    public String toString() {
      return "watch-only";
    }
  },
  client,
  output;

  TreeMap<Version, String> kubectlVersionMap;
  TreeMap<Version, String> ocVersionMap;

  Flag(Map<String, String> kubectlVersionMap, Map<String, String> ocVersionMap) {
    this.kubectlVersionMap = kubectlVersionMap.entrySet().stream().collect(
        Collectors.toMap(entry -> Version.parse(entry.getKey()), Map.Entry::getValue, (o1, o2) -> o1, TreeMap::new));
    this.ocVersionMap = ocVersionMap.entrySet().stream().collect(
        Collectors.toMap(entry -> Version.parse(entry.getKey()), Map.Entry::getValue, (o1, o2) -> o1, TreeMap::new));
  }

  public String getForVersion(Version version, Kubectl.ClientType clientType) {
    TreeMap<Version, String> versionMap = clientType == Kubectl.ClientType.OC ? ocVersionMap : kubectlVersionMap;
    String flag = getFlagFromMap(version, versionMap);
    return isNotEmpty(flag) ? "--" + flag : "";
  }

  private String getFlagFromMap(Version version, TreeMap<Version, String> versionMap) {
    if (version != null && isNotEmpty(versionMap)) {
      for (Version v : versionMap.descendingKeySet()) {
        if (version.compareTo(v) >= 0) {
          return versionMap.get(v);
        }
      }
    }
    return toString();
  }
}
