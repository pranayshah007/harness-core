/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.servicenow;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.eraro.ErrorCode.SERVICENOW_ERROR;
import static io.harness.exception.WingsException.USER;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.exception.ServiceNowException;
import io.harness.exception.WingsException;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

@OwnedBy(CDC)
@UtilityClass
@Slf4j
public class ServiceNowUtils {
  public String prepareTicketUrlFromTicketNumber(String baseUrl, String ticketNumber, String ticketType) {
    try {
      URL issueUrl = new URL(getUrlWithPath(baseUrl, ticketType) + ".do?sysparm_query=number=" + ticketNumber);
      return issueUrl.toString();
    } catch (MalformedURLException e) {
      throw new ServiceNowException(
          String.format("Invalid serviceNow base url: %s", baseUrl), ErrorCode.SERVICENOW_ERROR, WingsException.USER);
    }
  }

  public String prepareTicketUrlFromTicketId(String baseUrl, String ticketId, String ticketType) {
    return getUrlWithPath(baseUrl, ticketType) + ".do?sys_id=" + ticketId;
  }

  public String prepareTicketUrlFromTicketIdV2(String baseUrl, String ticketId, String ticketType) {
    try {
      if (isBlank(ticketId) || isBlank(ticketType)) {
        return null;
      }
      URL issueUrl = new URL(getUrlWithPath(baseUrl, ticketType) + ".do?sys_id=" + ticketId);
      return issueUrl.toString();
    } catch (MalformedURLException ex) {
      throw new ServiceNowException(
          String.format("Invalid serviceNow base url: %s", baseUrl), SERVICENOW_ERROR, USER, ex);
    }
  }

  @NotNull
  private static String getUrlWithPath(String baseUrl, String ticketType) {
    return baseUrl + (baseUrl.endsWith("/") ? "" : "/") + "nav_to.do?uri=/" + ticketType.toLowerCase();
  }

  public static Map<String, ServiceNowFieldValueNG> getListOfFieldsFromStringOfFields(
      String serviceNowFieldJsonNGListAsString) {
    if (StringUtils.isBlank(serviceNowFieldJsonNGListAsString)) {
      return Collections.emptyMap();
    }
    String[] fieldList = serviceNowFieldJsonNGListAsString.split("\\^");

    Map<String, ServiceNowFieldValueNG> parsedFields = new HashMap<>();

    for (String field : fieldList) {
      if ("EQ".equals(field)) {
        break;
      }
      String[] keyValue = field.split("=");
      if (keyValue.length == 2) {
        parsedFields.put(keyValue[0].trim(), ServiceNowFieldValueNG.builder().value(keyValue[1].trim()).build());
      }
    }
    return parsedFields;
  }

  public static String getTicketUrlFromTicketIdOrNumber(
      String serviceNowTicketType, String url, ServiceNowTicketNG ticketNg) {
    String ticketUrlFromTicketId;

    if (ticketNg.getNumber() != null) {
      ticketUrlFromTicketId =
          ServiceNowUtils.prepareTicketUrlFromTicketNumber(url, ticketNg.getNumber(), serviceNowTicketType);
    } else if (ticketNg.getFields() != null && ticketNg.getFields().get("sys_id") != null
        && ticketNg.getFields().get("sys_id").getDisplayValue() != null) {
      log.info("ServiceNow response is not having field number");
      ticketUrlFromTicketId = ServiceNowUtils.prepareTicketUrlFromTicketId(
          url, ticketNg.getFields().get("sys_id").getDisplayValue(), serviceNowTicketType);
    } else {
      log.info("ServiceNow response is not having field number and sys_id");
      throw new ServiceNowException(
          String.format("Both sysId and ticketNumber are not present in serviceNow Response"), SERVICENOW_ERROR, USER);
    }
    return ticketUrlFromTicketId;
  }
}
