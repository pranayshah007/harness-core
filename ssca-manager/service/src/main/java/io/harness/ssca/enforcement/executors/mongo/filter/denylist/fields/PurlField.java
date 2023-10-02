/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.enforcement.executors.mongo.filter.denylist.fields;

import io.harness.ssca.beans.DenyList.DenyListItem;
import io.harness.ssca.entities.NormalizedSBOMComponentEntity.NormalizedSBOMEntityKeys;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import org.bson.Document;

@AllArgsConstructor
public class PurlField implements Field {
  @Override
  public boolean isMatched(DenyListItem denyListItem) {
    return denyListItem.getPurl() != null;
  }

  @Override
  public Document getQueryDocument(DenyListItem denyListItem) {
    List<String> splitPurl = Arrays.asList(denyListItem.getPurl().split("/"));
    String deniedPackageManager = splitPurl.get(0).split(":")[1];

    // Sample Purl -> pkg:apk/alpine/alpine-baselayout@3.4.3-r1?arch=x86_64&distro=alpine-3.18.2
    switch (splitPurl.size()) {
      case 1:
        return new Document(NormalizedSBOMEntityKeys.packageManager.toLowerCase(), deniedPackageManager);
      case 2:
        String deniedNamespace = splitPurl.get(1);
        Map<String, Object> queryMap = new HashMap<>();
        queryMap.put(NormalizedSBOMEntityKeys.packageManager.toLowerCase(), deniedPackageManager);
        queryMap.put(NormalizedSBOMEntityKeys.packageNamespace.toLowerCase(), deniedNamespace);
        return new Document(queryMap);
      case 3:
        deniedNamespace = splitPurl.get(1);
        if (splitPurl.get(2).equals("*")) {
          queryMap = new HashMap<>();
          queryMap.put(NormalizedSBOMEntityKeys.packageManager.toLowerCase(), deniedPackageManager);
          queryMap.put(NormalizedSBOMEntityKeys.packageNamespace.toLowerCase(), deniedNamespace);
          return new Document(queryMap);
        } else {
          queryMap = new HashMap<>();
          queryMap.put(NormalizedSBOMEntityKeys.packageName.toLowerCase(), splitPurl.get(2));
          queryMap.put(NormalizedSBOMEntityKeys.packageManager.toLowerCase(), deniedPackageManager);
          queryMap.put(NormalizedSBOMEntityKeys.packageNamespace.toLowerCase(), deniedNamespace);
          return new Document(queryMap);
        }
      default:
        return null;
    }
  }
}
