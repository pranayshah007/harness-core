/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.client;

import io.harness.cvng.beans.errortracking.ErrorTrackingNotificationData;
import io.harness.cvng.notification.beans.ErrorTrackingEventStatus;
import io.harness.cvng.notification.beans.ErrorTrackingEventType;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface ErrorTrackingClient {
  @GET("dashboard/notificationEvents")
  Call<ErrorTrackingNotificationData> getNotificationData(@Query("orgId") String orgIdentifier,
      @Query("accountId") String accountId, @Query("projectId") String projectIdentifier,
      @Query("serviceId") String serviceIdentifier, @Query("environmentId") String environmentIdentifier,
      @Query("eventStatus") List<ErrorTrackingEventStatus> eventStatus,
      @Query("eventTypes") List<ErrorTrackingEventType> eventTypes, @Query("notificationId") String notificationId);
}
