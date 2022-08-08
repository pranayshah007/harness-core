/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.utils;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;

import software.wings.beans.NameValuePair;
import software.wings.service.impl.yaml.handler.NameValuePairYamlHandler;

import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author rktummala on 10/11/17
 */
@Slf4j
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class YamlUtils {
  public static List<NameValuePair.Yaml> toNameValuePairYamlList(
      Map<String, Object> properties, String appId, NameValuePairYamlHandler nameValuePairYamlHandler) {
    return properties.entrySet()
        .stream()
        .map(entry -> {
          NameValuePair nameValuePair = NameValuePair.builder()
                                            .name(entry.getKey())
                                            .value(entry.getValue() != null ? entry.getValue().toString() : null)
                                            .build();
          return nameValuePairYamlHandler.toYaml(nameValuePair, appId);
        })
        .collect(toList());
  }
}
