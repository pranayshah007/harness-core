package io.harness.notifications;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.notification.channeldetails.NotificationChannel;
import io.harness.notification.notificationclient.NotificationResult;
import io.harness.rest.RestResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Query;

@OwnedBy(HarnessTeam.CE)
public interface NotificationResourceClient {
  String BASE_API = "ccm/notification";

  @POST(BASE_API)
  Call<RestResponse<NotificationResult>> sendNotification(
      @Query(value = NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Body NotificationChannel notificationChannel);
}
