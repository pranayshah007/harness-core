/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception.bean;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ngexception.ErrorMetadataConstants;
import io.harness.exception.ngexception.ErrorMetadataDTO;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;

@OwnedBy(HarnessTeam.PIPELINE)
@JsonTypeName(ErrorMetadataConstants.FILTER_CREATOR_ERROR)
@Builder
public class FilterCreatorErrorResponse implements ErrorMetadataDTO {
  List<ErrorMetadata> errorMetadataList;

  public void addErrorMetadata(ErrorMetadata errorMetadata) {
    if (errorMetadataList == null) {
      errorMetadataList = new ArrayList<>();
    }
    errorMetadataList.add(errorMetadata);
  }

  @Override
  public String getType() {
    return ErrorMetadataConstants.FILTER_CREATOR_ERROR;
  }
}
