/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.filter;

import software.wings.audit.AuditHeader;
import software.wings.common.AuditHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * AuditResponseFilter preserves the http response details.
 *
 * @author Rishi
 */
@Singleton
@Slf4j
public class AuditResponseFilter implements Filter {
  @Inject private AuditHelper auditHelper;

  /* (non-Javadoc)
   * @see javax.servlet.Filter#init(javax.servlet.FilterConfig)
   */
  ;
  @Override
  public void init(FilterConfig arg0) {}

  /* (non-Javadoc)
   * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest, javax.servlet.ServletResponse,
   * javax.servlet.FilterChain)
   */
  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    AuditHeader header = auditHelper.get();
    log.info("remote Ip Address is : {}", request.getRemoteAddr());
    if (header != null) {
      String path = ((HttpServletRequest) request).getPathInfo();
      if (log.isDebugEnabled()) {
        log.debug("path :" + path);
      }
      if (response.getCharacterEncoding() == null) {
        response.setCharacterEncoding("UTF-8");
      }

      HttpServletResponseCopier responseCopier = new HttpServletResponseCopier((HttpServletResponse) response);

      try {
        chain.doFilter(request, responseCopier);
        responseCopier.flushBuffer();
      } finally {
        byte[] copy = responseCopier.getCopy();
        header.setResponseTime(System.currentTimeMillis());
        header.setResponseStatusCode(((HttpServletResponse) response).getStatus());
        auditHelper.finalizeAudit(header, copy);
      }
    } else {
      chain.doFilter(request, response);
    }
  }

  /* (non-Javadoc)
   * @see javax.servlet.Filter#destroy()
   */
  @Override
  public void destroy() {}
}
