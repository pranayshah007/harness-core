/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.remote;

import io.harness.ng.core.dto.ResponseDTO;
import io.harness.notification.Team;
import io.harness.notification.remote.dto.NotificationSettingDTO;
import io.harness.notification.remote.dto.TemplateDTO;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.Query;

public interface NotificationHTTPClient {
  @POST("channels/test")
  Call<ResponseDTO<Boolean>> testChannelSettings(@Body NotificationSettingDTO notificationSettingDTO);

  @Multipart
  @PUT("templates/insertOrUpdate")
  Call<ResponseDTO<TemplateDTO>> saveNotificationTemplate(@Part MultipartBody.Part file, @Query("team") Team team,
      @Query("identifier") String identifier, @Query("harnessManaged") Boolean harnessManaged);
}
