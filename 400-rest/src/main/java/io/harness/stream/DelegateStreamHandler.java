/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.stream;

import static io.harness.eraro.ErrorCode.UNKNOWN_ERROR;
import static io.harness.govern.Switch.unhandled;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.delegate.beans.ConnectionMode;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.DelegateConnectionHeartbeat;
import io.harness.delegate.beans.DelegateInstanceStatus;
import io.harness.delegate.task.DelegateLogContext;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.ErrorCodeName;
import io.harness.eraro.Level;
import io.harness.eraro.MessageManager;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.WingsException;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.serializer.JsonUtils;
import io.harness.service.intfc.DelegateCache;

import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.DelegateService;

import com.google.common.base.Splitter;
import com.google.common.io.CharStreams;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.List;
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

/**
 * Created by peeyushaggarwal on 8/15/16.
 */
@Slf4j
@AtmosphereHandlerService(path = "/stream/delegate/{accountId}",
    interceptors = {AtmosphereResourceLifecycleInterceptor.class}, broadcasterCache = UUIDBroadcasterCache.class,
    broadcastFilters = {DelegateEventFilter.class})
public class DelegateStreamHandler extends AtmosphereHandlerAdapter {
  public static final Splitter SPLITTER = Splitter.on("/").omitEmptyStrings();

  @Inject private AuthService authService;
  @Inject private DelegateService delegateService;
  @Inject private DelegateCache delegateCache;

  @Override
  public void onRequest(AtmosphereResource resource) throws IOException {
    AtmosphereRequest req = resource.getRequest();

    if (req.getMethod().equals("GET")) {
      try {
        List<String> pathSegments = SPLITTER.splitToList(req.getPathInfo());
        String accountId = pathSegments.get(1);
        String delegateId = req.getParameter("delegateId");
        String delegateTokenName = req.getParameter("delegateTokenName");

        authService.validateDelegateToken(accountId, req.getParameter("token"), delegateId, delegateTokenName, false);
        try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
             AutoLogContext ignore2 = new DelegateLogContext(delegateId, OVERRIDE_ERROR)) {
          String delegateConnectionId = req.getParameter("delegateConnectionId");
          String delegateVersion = req.getParameter("version");

          // These 2 will be sent by ECS delegate only
          String sequenceNum = req.getParameter("sequenceNum");
          String delegateToken = req.getParameter("delegateToken");

          Delegate delegate = delegateCache.get(accountId, delegateId, true);
          delegate.setStatus(DelegateInstanceStatus.ENABLED);

          updateIfEcsDelegate(delegate, sequenceNum, delegateToken);

          delegateService.register(delegate);
          delegateService.registerHeartbeat(accountId, delegateId,
              DelegateConnectionHeartbeat.builder()
                  .delegateConnectionId(delegateConnectionId)
                  .version(delegateVersion)
                  .build(),
              ConnectionMode.STREAMING);

          resource.addEventListener(new AtmosphereResourceEventListenerAdapter() {
            @Override
            public void onDisconnect(AtmosphereResourceEvent event) {
              try (AccountLogContext ignore = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
                Delegate delegate = delegateCache.get(accountId, delegateId, true);
                delegateService.register(delegate);
                delegateService.delegateDisconnected(accountId, delegateId, delegateConnectionId);
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
      resource.suspend();
    } else if (req.getMethod().equalsIgnoreCase("POST")) {
      List<String> pathSegments = SPLITTER.splitToList(req.getPathInfo());
      String accountId = pathSegments.get(1);
      String delegateId = req.getParameter("delegateId");

      try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
           AutoLogContext ignore2 = new DelegateLogContext(delegateId, OVERRIDE_ERROR)) {
        String delegateConnectionId = req.getParameter("delegateConnectionId");
        String delegateVersion = req.getParameter("version");

        Delegate delegate = JsonUtils.asObject(CharStreams.toString(req.getReader()), Delegate.class);
        delegate.setUuid(delegateId);
        delegateService.register(delegate);
        delegateService.registerHeartbeat(accountId, delegateId,
            DelegateConnectionHeartbeat.builder()
                .delegateConnectionId(delegateConnectionId)
                .version(delegateVersion)
                .location(delegate.getLocation())
                .build(),
            ConnectionMode.STREAMING);
      }
    }
  }

  @Override
  public void onStateChange(AtmosphereResourceEvent event) throws IOException {
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
    if ("ECS".equals(delegate.getDelegateType())) {
      delegate.setSequenceNum(sequenceNum);
      delegate.setDelegateRandomToken(delegateToken);
      String hostName = delegate.getHostName();
      delegate.setHostName(hostName.substring(0, hostName.lastIndexOf('_')));
    }
  }
}
