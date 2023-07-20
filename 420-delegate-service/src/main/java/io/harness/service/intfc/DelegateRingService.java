/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.intfc;

import java.util.List;
import java.util.Map;

public interface DelegateRingService {
  String getDelegateImageTag(String accountId);

  String getUpgraderImageTag(String accountId);

  List<String> getDelegateVersions(String accountId);

  Map<String, List<String>> getDelegateVersionsForAllRings(boolean skipCache);

  List<String> getDelegateVersionsForRing(String ringName, boolean skipCache);

  String getWatcherVersions(String accountId);

  String getJREVersion(String accountId, boolean isDelegate);

  Map<String, String> getWatcherVersionsAllRings(boolean skipCache);

  String getWatcherVersionsForRing(String ringName, boolean skipCache);
}
