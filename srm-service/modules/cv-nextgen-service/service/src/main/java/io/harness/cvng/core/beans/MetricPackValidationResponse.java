/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.cvng.beans.ThirdPartyApiResponseStatus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class MetricPackValidationResponse {
  private List<MetricValidationResponse> metricValidationResponses;
  private String metricPackName;
  private ThirdPartyApiResponseStatus overallStatus;

  public void updateStatus() {
    metricValidationResponses.forEach(metricValidationResponse -> {
      if (metricValidationResponse.getValue() == null) {
        metricValidationResponse.setStatus(ThirdPartyApiResponseStatus.NO_DATA);
      }
    });
    List<MetricValidationResponse> itemsWithData =
        metricValidationResponses.stream()
            .filter(metricValidationResponse
                -> metricValidationResponse.getStatus().equals(ThirdPartyApiResponseStatus.SUCCESS))
            .collect(Collectors.toList());
    if (isEmpty(itemsWithData)) {
      overallStatus = ThirdPartyApiResponseStatus.NO_DATA;
    }
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class MetricValidationResponse {
    private String metricName;
    private Double value;
    @Builder.Default private ThirdPartyApiResponseStatus status = ThirdPartyApiResponseStatus.SUCCESS;

    public ThirdPartyApiResponseStatus getStatus() {
      if (status == null && value != null) {
        status = ThirdPartyApiResponseStatus.SUCCESS;
        return status;
      } else if (value == null) {
        status = ThirdPartyApiResponseStatus.NO_DATA;
      }
      return status;
    }
  }
}
