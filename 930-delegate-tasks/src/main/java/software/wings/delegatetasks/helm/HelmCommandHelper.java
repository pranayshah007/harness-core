/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.helm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.eraro.ErrorCode;
import io.harness.exception.WingsException;

import software.wings.beans.dto.HelmChartSpecification;
import software.wings.delegatetasks.validation.capabilities.HelmCommandRequest;

import com.esotericsoftware.yamlbeans.YamlReader;
import com.google.inject.Singleton;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class HelmCommandHelper {
  public static final String URL = "url";
  public static final String NAME = "name";
  public static final String VERSION = "version";
  public static final String HARNESS = "harness";
  public static final String HELM = "helm";
  public static final String CHART = "chart";

  String getDeploymentMessage(HelmCommandRequest helmCommandRequest) {
    switch (helmCommandRequest.getHelmCommandType()) {
      case INSTALL:
        return "Installing";
      case ROLLBACK:
        return "Rolling back";
      case RELEASE_HISTORY:
        return "Getting release history";
      default:
        return "Unsupported operation";
    }
  }

  public Optional<HarnessHelmDeployConfig> generateHelmDeployChartSpecFromYaml(String yamlString) {
    YamlReader reader = new YamlReader(yamlString);
    try {
      // A YAML can contain more than one YAML document.
      // Call to YamlReader.read() deserializes the next document into an object.
      // YAML documents are delimited by "---"
      while (true) {
        Map map = (Map) reader.read();
        if (map == null) {
          break;
        }

        /*
         * harness:
         *   helm:
         *      chart:
         *         url: google.com
         *         name: abc
         *         version:1.0
         *      timeout:10  // this is a pseudo field
         *      releasePrefixName: aaaa // this is a pseudo field
         * */
        if (map.containsKey(HARNESS)) {
          Map harnessDataMap = (Map) map.get(HARNESS);

          if (isNotEmpty(harnessDataMap) && harnessDataMap.containsKey(HELM)) {
            Map harnessHelmDataMap = (Map) harnessDataMap.get(HELM);
            if (isNotEmpty(harnessHelmDataMap) && harnessHelmDataMap.containsKey(CHART)) {
              Map harnessHelmChartDataMap = (Map) harnessHelmDataMap.get(CHART);
              HelmDeployChartSpec helmDeployChartSpec = HelmDeployChartSpec.builder()
                                                            .url((String) harnessHelmChartDataMap.get(URL))
                                                            .name((String) harnessHelmChartDataMap.get(NAME))
                                                            .version((String) harnessHelmChartDataMap.get(VERSION))
                                                            .build();

              // Add any other fields under helm if added later
              return Optional.of(HarnessHelmDeployConfig.builder().helmDeployChartSpec(helmDeployChartSpec).build());
            }
          }
        }
      }
    } catch (Exception e) {
      log.error("Failed while parsing yamlString:" + yamlString, e);
      throw new WingsException(
          ErrorCode.GENERAL_ERROR, "Invalid Yaml, Failed while parsing yamlString", WingsException.SRE);
    }

    return Optional.empty();
  }

  public boolean isValidChartSpecification(HelmChartSpecification chartSpec) {
    return !(chartSpec == null || (isBlank(chartSpec.getChartName()) && isBlank(chartSpec.getChartUrl())));
  }
}
