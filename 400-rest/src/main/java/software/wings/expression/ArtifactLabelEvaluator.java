/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.expression;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.annotations.dev.OwnedBy;
import io.harness.expression.LateBindingMap;
import io.harness.logging.AutoLogContext;

import software.wings.beans.artifact.ArtifactStream;
import software.wings.delegatetasks.buildsource.ArtifactStreamLogContext;
import software.wings.service.intfc.BuildSourceService;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Value
@Builder
@EqualsAndHashCode(callSuper = true)
@Slf4j
public class ArtifactLabelEvaluator extends LateBindingMap {
  private transient String buildNo;
  private transient ArtifactStream artifactStream;
  private transient BuildSourceService buildSourceService;
  private transient Map<String, String> dockerLabels = new HashMap<>();

  public synchronized Object output(String labelKey) {
    if (dockerLabels.containsKey(labelKey)) {
      return dockerLabels.get(labelKey);
    }
    try (AutoLogContext ignore2 = new ArtifactStreamLogContext(
             artifactStream.getUuid(), artifactStream.getArtifactStreamType(), OVERRIDE_ERROR)) {
      List<Map<String, String>> labelsList =
          buildSourceService.getLabels(artifactStream, Collections.singletonList(buildNo));
      if (isNotEmpty(labelsList)) {
        Optional<Map<String, String>> labelMap = labelsList.stream().findFirst();
        if (labelMap.isPresent()) {
          dockerLabels.putAll(labelMap.get());
        }
        if (labelMap.isPresent() && labelMap.get().containsKey(labelKey)) {
          return labelMap.get().get(labelKey);
        } else {
          log.error("Label key + [" + labelKey + "] for buildNumber + [" + buildNo + "]");
          return null;
        }
      } else {
        log.error("Labels list is empty for buildNumber + [" + buildNo + "]");
        return null;
      }
    }
  }

  @Override
  public synchronized Object get(Object key) {
    return output((String) key);
  }
}
