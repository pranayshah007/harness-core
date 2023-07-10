/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.proxy.ngmanager;

import static io.harness.idp.proxy.ngmanager.ManagerAllowList.VALIDATE_SUPPORT_USER;
import static io.harness.idp.proxy.ngmanager.NgManagerAllowList.TOKEN_VALIDATE;
import static io.harness.idp.proxy.ngmanager.NgManagerAllowList.USERS;
import static io.harness.idp.proxy.ngmanager.NgManagerAllowList.USER_GROUPS;

import io.harness.exception.InvalidRequestException;

import io.vavr.collection.List;
import java.io.IOException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import lombok.experimental.UtilityClass;
import okhttp3.HttpUrl;
import okhttp3.Request;

@UtilityClass
public class ProxyUtils {
  private static final String NG_MANAGER_PROXY_PATH = "v1/idp-proxy/ng-manager";
  private static final String MANAGER_PROXY_PATH = "v1/idp-proxy/manager";
  private static final String QUERY_PARAMS_DELIMITER = "\\?";
  private static final String PATH_DELIMITER = "/";
  private static final List<String> NG_MANAGER_ALLOW_LIST = List.of(USERS, USER_GROUPS, TOKEN_VALIDATE);
  private static final List<String> MANAGER_ALLOW_LIST = List.of(VALIDATE_SUPPORT_USER);
  private static final String CONTENT_TYPE_HEADER = "Content-Type";

  public void filterAndCopyPath(UriInfo uriInfo, HttpUrl.Builder urlBuilder) {
    String proxyPath = uriInfo.getPath().contains(NG_MANAGER_PROXY_PATH) ? NG_MANAGER_PROXY_PATH : MANAGER_PROXY_PATH;
    List<String> allowList =
        uriInfo.getPath().contains(NG_MANAGER_PROXY_PATH) ? NG_MANAGER_ALLOW_LIST : MANAGER_ALLOW_LIST;
    String suffixUrl = uriInfo.getPath().split(proxyPath)[1];
    String path = suffixUrl.split(QUERY_PARAMS_DELIMITER)[0];
    filterPath(path, allowList);
    copyPath(path, urlBuilder);
  }

  private void filterPath(String paths, List<String> allowList) {
    boolean isAllowed = false;
    for (String allowedPath : allowList) {
      if (paths.startsWith(allowedPath)) {
        isAllowed = true;
        break;
      }
    }
    if (!isAllowed) {
      throw new InvalidRequestException(String.format("Path %s is not allowed", paths));
    }
  }

  private void copyPath(String path, HttpUrl.Builder urlBuilder) {
    for (String s : path.split(PATH_DELIMITER)) {
      urlBuilder.addPathSegment(s);
    }
  }

  public void copyHeaders(HttpHeaders headers, Request.Builder requestBuilder) {
    headers.getRequestHeaders().forEach((key, values) -> {
      if (!key.equals(IdpAuthInterceptor.AUTHORIZATION)) {
        values.forEach(value -> requestBuilder.header(key, value));
      }
    });
  }

  public void copyQueryParams(UriInfo uriInfo, HttpUrl.Builder urlBuilder) {
    uriInfo.getQueryParameters().forEach(
        (key, values) -> values.forEach(value -> urlBuilder.addQueryParameter(key, value)));
  }

  public Response buildResponseObject(okhttp3.Response response) throws IOException {
    Object entity = null;
    if (response.body() != null) {
      entity = response.body().string();
    }
    return Response.status(response.code())
        .entity(entity)
        .header(CONTENT_TYPE_HEADER, javax.ws.rs.core.MediaType.APPLICATION_JSON)
        .build();
  }
}
