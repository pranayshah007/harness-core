/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.eraro.ErrorCode;
import io.harness.exception.VerificationOperationException;

import software.wings.beans.apm.Method;
import software.wings.beans.apm.ResponseType;
import software.wings.metrics.MetricType;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApmMetricCollectionInfo {
  private String metricName;
  private MetricType metricType;
  private String tag;
  private String collectionUrl;
  private String baselineCollectionUrl;
  private String collectionBody;
  private ResponseType responseType;
  private ApmResponseMapping responseMapping;
  private Method method;

  public String getCollectionUrl() {
    try {
      return collectionUrl == null ? collectionUrl : collectionUrl.replaceAll("`", URLEncoder.encode("`", "UTF-8"));
    } catch (UnsupportedEncodingException e) {
      throw new VerificationOperationException(ErrorCode.APM_CONFIGURATION_ERROR,
          "Unsupported encoding exception while encoding backticks in " + collectionUrl);
    }
  }

  public String getBaselineCollectionUrl() {
    if (isEmpty(baselineCollectionUrl)) {
      return null;
    }
    try {
      return baselineCollectionUrl.replaceAll("`", URLEncoder.encode("`", "UTF-8"));
    } catch (UnsupportedEncodingException e) {
      throw new VerificationOperationException(ErrorCode.APM_CONFIGURATION_ERROR,
          "Unsupported encoding exception while encoding backticks in " + baselineCollectionUrl);
    }
  }
}
