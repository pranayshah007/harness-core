/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.helm;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.util.Collections.emptyList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.k8s.manifest.ManifestHelper;
import io.harness.k8s.model.KubernetesResource;

import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.CDP)
public class HelmClientUtils {
  public List<KubernetesResource> readManifestFromHelmOutput(String output) {
    if (isEmpty(output)) {
      return emptyList();
    }

    int manifestStartIndex = output.indexOf("---");
    if (manifestStartIndex != -1) {
      output = output.substring(manifestStartIndex);
    }

    return ManifestHelper.processYaml(output);
  }
}
