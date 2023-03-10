/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.stream;

import static io.harness.agent.AgentGatewayConstants.HEADER_AGENT_MTLS_AUTHORITY;
import static io.harness.agent.AgentGatewayUtils.isAgentConnectedUsingMtls;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.UNKNOWN_ERROR;
import static io.harness.govern.Switch.unhandled;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.DelegateParams;
import io.harness.delegate.heartbeat.stream.DelegateStreamHeartbeatService;
import io.harness.delegate.task.DelegateLogContext;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.ErrorCodeName;
import io.harness.eraro.Level;
import io.harness.eraro.MessageManager;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.WingsException;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.metrics.impl.CachedMetricsPublisher;
import io.harness.serializer.JsonUtils;
import io.harness.service.intfc.DelegateAuthService;
import io.harness.service.intfc.DelegateCache;

import software.wings.logcontext.WebsocketLogContext;
import software.wings.service.intfc.DelegateService;

import com.google.common.base.Splitter;
import com.google.common.io.CharStreams;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.atmosphere.cache.UUIDBroadcasterCache;
import org.atmosphere.config.service.AtmosphereHandlerService;
import org.atmosphere.cpr.AtmosphereRequest;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.AtmosphereResourceEvent;
import org.atmosphere.cpr.AtmosphereResourceEventListenerAdapter;
import org.atmosphere.cpr.AtmosphereResponse;
import org.atmosphere.handler.AtmosphereHandlerAdapter;
import org.atmosphere.interceptor.AtmosphereResourceLifecycleInterceptor;

@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
@AtmosphereHandlerService(path = "/stream/delegate/{accountId}",
    interceptors = {AtmosphereResourceLifecycleInterceptor.class}, broadcasterCache = UUIDBroadcasterCache.class,
    broadcastFilters = {DelegateEventFilter.class})
public class DelegateStreamHandler extends AtmosphereHandlerAdapter {
  public static final Splitter SPLITTER = Splitter.on("/").omitEmptyStrings();
  private final DelegateService delegateService;
  private final DelegateCache delegateCache;
  private final CachedMetricsPublisher cachedMetrics;
  private final DelegateStreamHeartbeatService delegateStreamHeartbeatService;

  private final DelegateAuthService delegateAuthService;

  @Override
  public void onRequest(AtmosphereResource resource) throws IOException {
    final AtmosphereRequest req = resource.getRequest();
    final String websocketId = resource.uuid();

    // get mTLS information independent of request type
    final String agentMtlsAuthority = req.getHeader(HEADER_AGENT_MTLS_AUTHORITY);
    final boolean isConnectedUsingMtls = isAgentConnectedUsingMtls(agentMtlsAuthority);

    if (req.getMethod().equals("GET")) {
      String accountId;
      String delegateId;
      try {
        List<String> pathSegments = SPLITTER.splitToList(req.getPathInfo());
        accountId = pathSegments.get(1);
        delegateId = req.getParameter("delegateId");
        final String delegateConnectionId = req.getParameter("delegateConnectionId");

        cachedMetrics.recordDelegateProcess(accountId, delegateConnectionId);
        cachedMetrics.recordDelegateWebsocketConnection(accountId, websocketId);

        try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
             AutoLogContext ignore2 = new DelegateLogContext(delegateId, OVERRIDE_ERROR);
             AutoLogContext ignore3 = new WebsocketLogContext(websocketId, OVERRIDE_ERROR)) {
          log.info("delegate socket connected");

          resource.addEventListener(new AtmosphereResourceEventListenerAdapter() {
            @Override
            public void onDisconnect(AtmosphereResourceEvent event) {
              try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
                   AutoLogContext ignore2 = new DelegateLogContext(delegateId, OVERRIDE_ERROR);
                   AutoLogContext ignore3 = new WebsocketLogContext(event.getResource().uuid(), OVERRIDE_ERROR)) {
                log.info("delegate socket disconnected {}", event);
                delegateService.delegateDisconnected(accountId, delegateId, delegateConnectionId);
              }
            }

            @Override
            public void onClose(AtmosphereResourceEvent event) {
              try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
                   AutoLogContext ignore2 = new DelegateLogContext(delegateId, OVERRIDE_ERROR);
                   AutoLogContext ignore3 = new WebsocketLogContext(event.getResource().uuid(), OVERRIDE_ERROR)) {
                log.info("delegate socket closed {}", event);
              }
            }
          });
        }
      } catch (WingsException e) {
        sendError(resource, e.getCode());
        return;
      } catch (Exception e) {
        sendError(resource, UNKNOWN_ERROR);
        return;
      }
      try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
           AutoLogContext ignore2 = new DelegateLogContext(delegateId, OVERRIDE_ERROR)) {
        resource.suspend();
      }
    } else if (req.getMethod().equalsIgnoreCase("POST")) {
      List<String> pathSegments = SPLITTER.splitToList(req.getPathInfo());
      String accountId = pathSegments.get(1);
      String delegateId = req.getParameter("delegateId");
      String delegateConnectionId = req.getParameter("delegateConnectionId");

      cachedMetrics.recordDelegateProcess(accountId, delegateConnectionId);
      cachedMetrics.recordDelegateWebsocketConnection(accountId, websocketId);

      try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
           AutoLogContext ignore2 = new DelegateLogContext(delegateId, OVERRIDE_ERROR);
           AutoLogContext ignore3 = new WebsocketLogContext(websocketId, OVERRIDE_ERROR)) {
        DelegateParams delegateParams = JsonUtils.asObject(CharStreams.toString(req.getReader()), DelegateParams.class);
        if (isNotEmpty(delegateParams.getToken())) {
          delegateAuthService.validateDelegateToken(accountId, delegateParams.getToken(), delegateId,
              delegateParams.getTokenName(), agentMtlsAuthority, false);
        }
        if ("ECS".equals(delegateParams.getDelegateType())) {
          Delegate delegate = Delegate.getDelegateFromParams(delegateParams, isConnectedUsingMtls);
          delegate.setUuid(delegateId);
          delegateService.register(delegate);
        } else {
          delegateStreamHeartbeatService.process(delegateParams.toBuilder().delegateId(delegateId).build());
        }
      } catch (WingsException e) {
        sendError(resource, e.getCode());
        return;
      } catch (Exception e) {
        log.error("Unknown error on socket ", e);
        return;
      }
    }
  }

  @Override
  public void onStateChange(AtmosphereResourceEvent event) throws IOException {
    AtmosphereRequest req = event.getResource().getRequest();
    String delegateId = req.getParameter("delegateId");
    try (AutoLogContext ignore2 = new DelegateLogContext(delegateId, OVERRIDE_ERROR);
         AutoLogContext ignore3 = new WebsocketLogContext(event.getResource().uuid(), OVERRIDE_ERROR)) {
      AtmosphereResource r = event.getResource();
      AtmosphereResponse res = r.getResponse();

      if (r.isSuspended()) {
        Object message = event.getMessage();
        if (message != null) {
          if (message instanceof String) {
            event.getResource().write((String) message);
          } else {
            event.getResource().write(JsonUtils.asJson(message));
          }
        }
        AtmosphereResource.TRANSPORT transport = r.transport();
        switch (transport) {
          case JSONP:
          case LONG_POLLING:
            event.getResource().resume();
            break;
          case WEBSOCKET:
            break;
          case STREAMING:
            res.getWriter().flush();
            break;
          default:
            unhandled(transport);
        }
      }
    }
  }

  private void sendError(AtmosphereResource resource, ErrorCode errorCode) throws IOException {
    AtmosphereResource.TRANSPORT transport = resource.transport();
    switch (resource.transport()) {
      case JSONP:
      case LONG_POLLING:
        resource.getResponse().sendError(errorCode.getStatus().getCode());
        break;
      case WEBSOCKET:
        break;
      case STREAMING:
        break;
      default:
        unhandled(transport);
    }
    resource.write(JsonUtils.asJson(ResponseMessage.builder()
                                        .code(errorCode)
                                        .level(Level.ERROR)
                                        .message(MessageManager.getInstance().prepareMessage(
                                            ErrorCodeName.builder().value(errorCode.name()).build(), null, null))
                                        .build()));
    resource.close();
  }

  /**
   * While registering delegate, actual hostName will be generated by appending sequenceNum.
   * So need to strip seqNum off from hostName before its sent to register().
   */
  private void updateIfEcsDelegate(Delegate delegate, String sequenceNum, String delegateToken) {
    delegate.setSequenceNum(sequenceNum);
    delegate.setDelegateRandomToken(delegateToken);
    String hostName = delegate.getHostName();
    delegate.setHostName(hostName.substring(0, hostName.lastIndexOf('_')));
  }
}
