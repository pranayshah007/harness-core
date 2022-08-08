/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.exception;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.HarnessStringUtils;
import io.harness.eraro.ErrorCode;
import io.harness.exception.FilterCreatorException;
import io.harness.exception.PlanCreatorException;
import io.harness.exception.bean.FilterCreatorErrorResponse;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.ErrorMetadata;
import io.harness.pms.contracts.plan.ErrorResponse;
import io.harness.pms.contracts.plan.ErrorResponseV2;
import io.harness.pms.contracts.plan.YamlFieldBlob;
import io.harness.pms.yaml.YamlField;

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
public class PmsExceptionUtils {
  public String getUnresolvedDependencyPathsErrorMessage(Dependencies dependencies) {
    return String.format(
        "Following yaml paths could not be parsed: %s", String.join(",", dependencies.getDependenciesMap().values()));
  }

  @VisibleForTesting
  List<YamlNodeErrorInfo> getYamlNodeErrorInfo(Collection<YamlFieldBlob> yamlFieldBlobs) throws IOException {
    List<YamlNodeErrorInfo> yamlNodeErrorInfos = new ArrayList<>();
    for (YamlFieldBlob yamlFieldBlob : yamlFieldBlobs) {
      YamlField yamlField = YamlField.fromFieldBlob(yamlFieldBlob);
      yamlNodeErrorInfos.add(YamlNodeErrorInfo.fromField(yamlField));
    }
    return yamlNodeErrorInfos;
  }

  public void checkAndThrowFilterCreatorException(
      List<ErrorResponse> errorResponses, List<ErrorResponseV2> errorResponsesV2) {
    if (EmptyPredicate.isEmpty(errorResponses) && EmptyPredicate.isEmpty(errorResponsesV2)) {
      return;
    }
    List<String> messages = new ArrayList<>();
    FilterCreatorErrorResponse filterCreatorErrorResponse = FilterCreatorErrorResponse.builder().build();
    if (EmptyPredicate.isNotEmpty(errorResponsesV2)) {
      for (ErrorResponseV2 errorResponseV2 : errorResponsesV2) {
        for (ErrorMetadata errorMetadata : errorResponseV2.getErrorsList()) {
          ErrorCode wingsErrorCode;
          try {
            wingsErrorCode = ErrorCode.valueOf(errorMetadata.getWingsExceptionErrorCode());
          } catch (Exception ex) {
            wingsErrorCode = ErrorCode.GENERAL_ERROR;
          }
          filterCreatorErrorResponse.addErrorMetadata(io.harness.exception.bean.ErrorMetadata.builder()
                                                          .errorCode(wingsErrorCode)
                                                          .errorMessage(errorMetadata.getErrorMessage())
                                                          .build());
          messages.add(errorMetadata.getErrorMessage());
        }
      }
    }
    if (EmptyPredicate.isNotEmpty(errorResponses)) {
      messages.addAll(
          errorResponses.stream().flatMap(resp -> resp.getMessagesList().stream()).collect(Collectors.toList()));
    }
    throw new FilterCreatorException(HarnessStringUtils.join(",", messages), filterCreatorErrorResponse);
  }

  public void checkAndThrowPlanCreatorException(List<ErrorResponse> errorResponses) {
    if (EmptyPredicate.isEmpty(errorResponses)) {
      return;
    }
    List<String> messages =
        errorResponses.stream().flatMap(resp -> resp.getMessagesList().stream()).collect(Collectors.toList());
    throw new PlanCreatorException(String.format("Error creating Plan: %s", HarnessStringUtils.join(",", messages)));
  }
}
