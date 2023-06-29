/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.executioncapability;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.beans.KeyValuePair;

import java.time.Duration;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import org.apache.http.client.utils.URIBuilder;

@Data
@Builder
public class HttpConnectionExecutionCapability implements ExecutionCapability {
  private final CapabilityType capabilityType = CapabilityType.HTTP;

  private String url;
  private List<KeyValuePair> headers;

  private String host;
  private String scheme;
  private int port;
  private String path;
  private String query;
  private boolean ignoreRedirect;
  private boolean ignoreResponseCode;

  @Override
  public EvaluationMode evaluationMode() {
    return EvaluationMode.AGENT;
  }

  @Override
  public String fetchCapabilityBasis() {
    if (url != null) {
      return url;
    }
    URIBuilder uriBuilder = new URIBuilder();
    if (isNotBlank(scheme)) {
      uriBuilder.setScheme(scheme);
    }
    uriBuilder.setHost(host);
    if (port != -1) {
      uriBuilder.setPort(port);
    }
    if (isNotBlank(path)) {
      uriBuilder.setPath('/' + path);
    }
    if (isNotBlank(query)) {
      uriBuilder.setCustomQuery(query);
    }
    if (isNotEmpty(headers)) {
      for (KeyValuePair entry : headers) {
        uriBuilder.setParameter(entry.getKey(), entry.getValue());
      }
    }
    return uriBuilder.toString();
  }

  // This is used when capability basis and URL which is tested for connectivity are different.
  // Eg. When headers are included in the request, URL should remain unchanged.
  public String fetchConnectableUrl() {
    if (url != null) {
      return url;
    }
    URIBuilder uriBuilder = new URIBuilder();
    if (isNotBlank(scheme)) {
      uriBuilder.setScheme(scheme);
    }
    uriBuilder.setHost(host);
    if (port != -1) {
      uriBuilder.setPort(port);
    }
    if (isNotBlank(path)) {
      uriBuilder.setPath('/' + path);
    }
    if (isNotBlank(query)) {
      uriBuilder.setCustomQuery(query);
    }
    return uriBuilder.toString();
  }

  @Override
  public Duration getMaxValidityPeriod() {
    return Duration.ofHours(6);
  }

  @Override
  public Duration getPeriodUntilNextValidation() {
    return Duration.ofHours(4);
  }

  @Override
  public String getCapabilityToString() {
    return isNotEmpty(fetchConnectableUrl()) ? String.format("Capability reach URL: %s ", fetchConnectableUrl()) : null;
  }

  /**
   * Error message to show mostly in delegate selection log if none of the delegates passed the validation check
   */
  @Override
  public String getCapabilityValidationError() {
    // Delegate(s) unable to connect to {url}, make sure to provide the connectivity with following delegates :[h1,h2]
    return isNotEmpty(fetchCapabilityBasis()) ? String.format(
               "Delegate(s) unable to connect to  %s, make sure to provide the connectivity with following delegates",
               fetchCapabilityBasis())
                                              : ExecutionCapability.super.getCapabilityValidationError();
  }
}
