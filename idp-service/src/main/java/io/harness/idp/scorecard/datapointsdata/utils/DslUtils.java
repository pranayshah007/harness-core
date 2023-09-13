/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.datapointsdata.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.datapointsdata.dsldataprovider.DslConstants;

import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

@OwnedBy(HarnessTeam.IDP)
@UtilityClass
@Slf4j
public class DslUtils {
  public Map<String, String> getCiPipelineUrlIdentifiers(String ciPipelineUrl) {
    String[] splitText = ciPipelineUrl.split("/");
    Map<String, String> returnMap = new HashMap<>();
    returnMap.put(DslConstants.CI_ACCOUNT_IDENTIFIER_KEY, splitText[5]);
    returnMap.put(DslConstants.CI_ORG_IDENTIFIER_KEY, splitText[8]);
    returnMap.put(DslConstants.CI_PROJECT_IDENTIFIER_KEY, splitText[10]);
    returnMap.put(DslConstants.CI_PIPELINE_IDENTIFIER_KEY, splitText[12]);
    return returnMap;
  }

  public Map<String, String> getCdServiceUrlIdentifiers(String cdServiceUrl) {
    String[] splitText = cdServiceUrl.split("/");
    Map<String, String> returnMap = new HashMap<>();
    returnMap.put(DslConstants.CD_ACCOUNT_IDENTIFIER_KEY, splitText[5]);
    returnMap.put(DslConstants.CD_ORG_IDENTIFIER_KEY, splitText[8]);
    returnMap.put(DslConstants.CD_PROJECT_IDENTIFIER_KEY, splitText[10]);
    returnMap.put(DslConstants.CD_SERVICE_IDENTIFIER_KEY, splitText[12]);
    returnMap.put(DslConstants.CD_SERVICE_HOST, splitText[2]);
    return returnMap;
  }

  public String getCiUrlFromCatalogInfoYaml(String catalogInfoYaml) {
    Map<String, Object> annotationObject = getAnnotationsFromCatalogInfoYaml(catalogInfoYaml);
    String ciPipelineUrls = (String) annotationObject.get("harness.io/pipelines");
    String[] ciPipelineUrlsAsList = ciPipelineUrls.split("\n");
    return ciPipelineUrlsAsList[0].split(":", 2)[1];
  }

  public String getServiceUrlFromCatalogInfoYaml(String catalogInfoYaml) {
    Map<String, Object> annotationObject = getAnnotationsFromCatalogInfoYaml(catalogInfoYaml);
    String serviceUrls = (String) annotationObject.get("harness.io/services");
    String[] serviceUrlsAsList = serviceUrls.split("\n");
    return serviceUrlsAsList[0].split(":", 2)[1];
  }

  public Map<String, Object> getAnnotationsFromCatalogInfoYaml(String catalogInfoYaml) {
    Yaml yaml = new Yaml();
    Map<String, Object> catalogInfoYamlObject = yaml.load(catalogInfoYaml);
    Map<String, Object> metadataObject = (Map<String, Object>) catalogInfoYamlObject.get("metadata");
    return (Map<String, Object>) metadataObject.get("annotations");
  }
}
