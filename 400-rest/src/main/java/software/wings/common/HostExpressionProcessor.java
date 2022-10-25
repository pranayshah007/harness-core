/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

/**
 *
 */

package software.wings.common;

import io.harness.context.ContextElementType;
import io.harness.serializer.MapperUtils;

import software.wings.api.HostElement;
import software.wings.beans.infrastructure.Host;
import software.wings.service.intfc.HostService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExpressionProcessor;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Class HostExpressionProcessor.
 *
 * @author Rishi
 */
public class HostExpressionProcessor implements ExpressionProcessor {
  /**
   * The Expression start pattern.
   */
  public static final String DEFAULT_EXPRESSION = "${hosts}";

  private static final String EXPRESSION_START_PATTERN = "hosts()";
  private static final String EXPRESSION_EQUAL_PATTERN = "hosts";
  private static final String HOST_EXPR_PROCESSOR = "hostExpressionProcessor";
  @Inject private HostService hostService;

  /**
   * Instantiates a new host expression processor.
   *
   * @param context the context
   */
  public HostExpressionProcessor(ExecutionContext context) {
    // Derive appId, serviceId, serviceTemplate and tags associated from the context
  }

  /**
   * Convert to applicationHost element applicationHost element.
   *
   * @param applicationHost the applicationHost
   * @return the applicationHost element
   */
  static HostElement convertToHostElement(Host applicationHost) {
    HostElement element = HostElement.builder().build();
    MapperUtils.mapObject(applicationHost, element);
    return element;
  }

  @Override
  public String getPrefixObjectName() {
    return HOST_EXPR_PROCESSOR;
  }

  @Override
  public List<String> getExpressionStartPatterns() {
    return Collections.singletonList(EXPRESSION_START_PATTERN);
  }

  @Override
  public List<String> getExpressionEqualPatterns() {
    return Collections.singletonList(EXPRESSION_EQUAL_PATTERN);
  }

  @Override
  public ContextElementType getContextElementType() {
    return ContextElementType.HOST;
  }

  /**
   * Gets hosts.
   *
   * @return the hosts
   */
  public HostExpressionProcessor getHosts() {
    return this;
  }

  private List<HostElement> convertToHostElements(List<Host> hosts) {
    if (hosts == null) {
      return null;
    }
    List<HostElement> hostElements = new ArrayList<>();
    for (Host applicationHost : hosts) {
      hostElements.add(convertToHostElement(applicationHost));
    }
    return hostElements;
  }
}
