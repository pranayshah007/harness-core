/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package ci.pipeline.execution;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.telemetry.Destination.AMPLITUDE;

import io.harness.ci.pipeline.executions.beans.CIImageDetails;
import io.harness.ci.pipeline.executions.beans.CIInfraDetails;
import io.harness.ci.pipeline.executions.beans.CIScmDetails;
import io.harness.ci.pipeline.executions.beans.TIBuildDetails;
import io.harness.ci.plan.creator.execution.CIPipelineModuleInfo;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.events.OrchestrationEvent;
import io.harness.pms.sdk.core.events.OrchestrationEventHandler;
import io.harness.pms.sdk.execution.beans.PipelineModuleInfo;
import io.harness.repositories.CIAccountExecutionMetadataRepository;
import io.harness.telemetry.TelemetryReporter;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

@Singleton
public class CIPipelineEndEventHandler implements OrchestrationEventHandler {
  @Inject CIAccountExecutionMetadataRepository ciAccountExecutionMetadataRepository;
  @Inject TelemetryReporter telemetryReporter;

  private static final String CI_EXECUTED = "ci_built";
  private static final String USED_CODEBASE = "used_codebase";
  private static final String URL = "url";
  private static final String BRANCH = "branch";
  private static final String BUILD_TYPE = "build_type";
  private static final String PRIVATE_REPO = "private_repo";
  private static final String REPO_NAME = "repo_name";

  private static final String SCM_URL_LIST = "scm_url_list";
  private static final String SCM_PROVIDER_LIST = "scm_provider_list";
  private static final String SCM_AUTH_METHOD_LIST = "scm_auth_method_list";
  private static final String SCM_HOST_TYPE_LIST = "scm_host_type_list";

  private static final String INFRA_TYPE_LIST = "infra_type_list";
  private static final String INFRA_OS_LIST = "infra_os_list";
  private static final String INFRA_HOST_LIST = "infra_host_list";

  private static final String IMAGES = "images";
  private static final String TI_BUILD_TOOL_LIST = "ti_build_tool_list";
  private static final String TI_LANGUAGE_LIST = "ti_language_list";

  @Override
  public void handleEvent(OrchestrationEvent event) {
    PipelineModuleInfo moduleInfo = event.getModuleInfo();
    if (moduleInfo instanceof CIPipelineModuleInfo) {
      CIPipelineModuleInfo ciModuleInfo = (CIPipelineModuleInfo) moduleInfo;
      updateExecutionCount(ciModuleInfo, event);
      sendCITelemetryEvents(ciModuleInfo, event);
    }
  }

  private void updateExecutionCount(CIPipelineModuleInfo moduleInfo, OrchestrationEvent event) {
    if (moduleInfo.getIsPrivateRepo()) {
      ciAccountExecutionMetadataRepository.updateAccountExecutionMetadata(
          AmbianceUtils.getAccountId(event.getAmbiance()), event.getEndTs());
    }
  }

  private void sendCITelemetryEvents(CIPipelineModuleInfo moduleInfo, OrchestrationEvent event) {
    Ambiance ambiance = event.getAmbiance();
    String identity = ambiance.getMetadata().getTriggerInfo().getTriggeredBy().getExtraInfoMap().get("email");
    String accountId = AmbianceUtils.getAccountId(ambiance);

    sendCIExecutedEvent(ambiance, event, moduleInfo, identity, accountId);
  }

  private void sendCIExecutedEvent(
      Ambiance ambiance, OrchestrationEvent event, CIPipelineModuleInfo moduleInfo, String identity, String accountId) {
    HashMap<String, Object> ciBuiltMap = new HashMap<>();

    // Git details
    ciBuiltMap.put(BRANCH, moduleInfo.getBranch());
    ciBuiltMap.put(BUILD_TYPE, moduleInfo.getBuildType());
    ciBuiltMap.put(PRIVATE_REPO, moduleInfo.getIsPrivateRepo());
    ciBuiltMap.put(REPO_NAME, moduleInfo.getRepoName());

    // SCM Vendor details
    if (isNotEmpty(moduleInfo.getScmDetailsList())) {
      List<String> scmUrlList = new ArrayList<>();
      List<String> scmProviderList = new ArrayList<>();
      List<String> scmAuthTypeList = new ArrayList<>();
      List<String> scmHostTypeList = new ArrayList<>();
      for (CIScmDetails scmDetails : moduleInfo.getScmDetailsList()) {
        ciBuiltMap.put(URL, scmDetails.getScmUrl());
        scmUrlList.add(scmDetails.getScmUrl());
        scmProviderList.add(scmDetails.getScmProvider());
        scmAuthTypeList.add(scmDetails.getScmAuthType());
        scmHostTypeList.add(scmDetails.getScmHostType());
      }
      ciBuiltMap.put(SCM_URL_LIST, scmUrlList);
      ciBuiltMap.put(SCM_PROVIDER_LIST, scmProviderList);
      ciBuiltMap.put(SCM_AUTH_METHOD_LIST, scmAuthTypeList);
      ciBuiltMap.put(SCM_HOST_TYPE_LIST, scmHostTypeList);
    }

    ciBuiltMap.put(USED_CODEBASE, false);
    if (ciBuiltMap.get(URL) != null) {
      ciBuiltMap.put(USED_CODEBASE, true);
    }

    // Image details
    HashMap<String, List<String>> imagesMap = new HashMap<>();
    for (CIImageDetails image: moduleInfo.getImageDetailsList()) {
      String imageName = image.getImageName();
      String imageTag = image.getImageTag();
      imagesMap.computeIfAbsent(imageName, k -> new ArrayList<String>());
      imagesMap.get(imageName).add(imageTag);
    }
    ciBuiltMap.put(IMAGES, imagesMap);

    // Infrastructure details
    List<String> infraTypeList = new ArrayList<>();
    List<String> infraOsTypeList = new ArrayList<>();
    List<String> infraHostTypeList = new ArrayList<>();
    for (CIInfraDetails infraDetails : moduleInfo.getInfraDetailsList()) {
      infraTypeList.add(infraDetails.getInfraType());
      infraOsTypeList.add(infraDetails.getInfraOSType());
      infraHostTypeList.add(infraDetails.getInfraHostType());
    }
    ciBuiltMap.put(INFRA_TYPE_LIST, infraTypeList);
    ciBuiltMap.put(INFRA_OS_LIST, infraOsTypeList);
    ciBuiltMap.put(INFRA_HOST_LIST, infraHostTypeList);

    // Test Intelligence details
    if (moduleInfo.getTiBuildDetailsList() != null && moduleInfo.getTiBuildDetailsList().size() != 0) {
      List<String> tiBuildToolList = new ArrayList<>();
      List<String> tiLanguageList = new ArrayList<>();

      for (TIBuildDetails tiBuildDetails : moduleInfo.getTiBuildDetailsList()){
        tiBuildToolList.add(tiBuildDetails.getBuildTool());
        tiLanguageList.add(tiBuildDetails.getLanguage());
      }
      ciBuiltMap.put(TI_BUILD_TOOL_LIST, tiBuildToolList);
      ciBuiltMap.put(TI_LANGUAGE_LIST, tiLanguageList);
    }

    telemetryReporter.sendTrackEvent(CI_EXECUTED, identity, accountId, ciBuiltMap,
        Collections.singletonMap(AMPLITUDE, true), io.harness.telemetry.Category.GLOBAL,
        io.harness.telemetry.TelemetryOption.builder().sendForCommunity(false).build());
  }
}
