/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.cdn;

import io.harness.secret.ConfigSecret;

import java.util.Map;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CdnConfig {
  String url;
  String keyName;
  @ConfigSecret String keySecret;
  String delegateJarPath;
  String watcherJarBasePath;
  String watcherJarPath;
  String watcherMetaDataFilePath;

  // remove these two fields after examining where all its used
  Map<String, String> cdnJreTarPaths;
  String alpnJarPath;
}
