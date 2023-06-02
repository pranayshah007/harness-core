/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.servicenow;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.databind.JsonNode;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

@OwnedBy(CDC)
public interface ServiceNowRestClient {
  @GET("api/now/table/task?sysparm_limit=1")
  Call<JsonNode> validateConnection(@Header("Authorization") String authorization);

  @Headers("Content-Type: application/json")
  @POST("api/now/table/{ticket-type}")
  Call<JsonNode> createTicket(@Header("Authorization") String authorization, @Path("ticket-type") String ticketType,
      @Query("sysparm_display_value") String displayValue, @Query("sysparm_fields") String returnFields,
      @Body Object jsonBody);

  @Headers("Content-Type: application/json")
  @POST("api/now/import/{staging-table-name}")
  Call<JsonNode> createImportSet(@Header("Authorization") String authorization,
      @Path("staging-table-name") String stagingTableName, @Query("sysparm_display_value") String displayValue,
      @Body Object jsonBody);

  @Headers("Content-Type: application/json")
  @PATCH("api/now/table/{ticket-type}/{ticket-id}")
  Call<JsonNode> updateTicket(@Header("Authorization") String authorization, @Path("ticket-type") String ticketType,
      @Path("ticket-id") String ticketId, @Query("sysparm_display_value") String displayValue,
      @Query("sysparm_fields") String returnFields, @Body Object jsonBody);

  @GET("api/now/table/{ticket-type}")
  Call<JsonNode> fetchChangeTasksFromCR(@Header("Authorization") String authorization,
      @Path("ticket-type") String ticketType, @Query("sysparm_fields") String returnFields,
      @Query("sysparm_query") String query);

  @GET("/api/now/table/sys_choice?sysparm_query=element=state%5Ename=incident%5Einactive=false%5Elanguage=en")
  Call<JsonNode> getIncidentStates(@Header("Authorization") String authorization);

  @GET("/api/now/table/sys_choice?sysparm_query=element=risk%5Ename=change_request%5Einactive=false%5Elanguage=en")
  Call<JsonNode> getRisk(@Header("Authorization") String authorization);

  @GET("/api/now/table/sys_choice?sysparm_query=element=priority%5Ename=change_request%5Einactive=false%5Elanguage=en")
  Call<JsonNode> getPriority(@Header("Authorization") String authorization);

  @GET("/api/now/table/sys_choice?sysparm_query=element=impact%5Einactive=false%5Elanguage=en")
  Call<JsonNode> getImpact(@Header("Authorization") String authorization);

  @GET("/api/now/table/sys_choice?sysparm_query=element=urgency%5Einactive=false%5Elanguage=en")
  Call<JsonNode> getUrgency(@Header("Authorization") String authorization);

  @GET("/api/now/table/sys_choice?sysparm_query=element=state%5Ename=problem%5Einactive=false%5Elanguage=en")
  Call<JsonNode> getProblemStates(@Header("Authorization") String authorization);

  @GET("/api/now/table/sys_choice?sysparm_query=element=state%5Ename=change_request%5Einactive=false%5Elanguage=en")
  Call<JsonNode> getChangeRequestStates(@Header("Authorization") String authorization);

  @GET("/api/now/table/sys_choice?sysparm_query=element=state%5Ename=change_task%5Einactive=false%5Elanguage=en")
  Call<JsonNode> getChangeTaskStates(@Header("Authorization") String authorization);

  @GET("/api/now/table/sys_choice?sysparm_query=element=type%5Ename=change_request%5Einactive=false%5Elanguage=en")
  Call<JsonNode> getChangeRequestTypes(@Header("Authorization") String authorization);

  @GET(
      "/api/now/table/sys_choice?sysparm_query=element=change_task_type%5Ename=change_task%5Einactive=false%5Elanguage=en")
  Call<JsonNode>
  getChangeTaskTypes(@Header("Authorization") String authorization);

  @GET("/api/now/table/{ticketType}")
  Call<JsonNode> getIssue(@Header("Authorization") String authorization, @Path("ticketType") String ticketType,
      @Query("sysparm_query") String query, @Query("sysparm_display_value") String displayValue);

  @GET("api/now/doc/table/schema/{ticketType}")
  Call<JsonNode> getAdditionalFields(
      @Header("Authorization") String authorization, @Path("ticketType") String ticketType);

  @GET("/api/now/table/sys_choice?sysparm_query=element=approval%5Ename=task%5Einactive=false%5Elanguage=en")
  Call<JsonNode> getChangeApprovalTypes(@Header("Authorization") String authorization);

  @GET("api/now/ui/meta/{ticketType}")
  Call<JsonNode> getMetadata(@Header("Authorization") String authorization, @Path("ticketType") String ticketType);

  @GET("/api/now/table/sys_template?{ticketType}")
  Call<JsonNode> getTemplate(@Header("Authorization") String authorization, @Path("ticketType") String ticketType,
      @Query("sysparm_query") String query, @Query("sysparm_display_value") String displayValue);

  @GET("/api/now/table/sys_db_object?sysparm_query=super_class.label=Import%20Set%20Row")
  Call<JsonNode> getStagingTableList(@Header("Authorization") String authorization);

  @GET(
      "api/now/table/sys_db_object?sysparm_query=super_class.nameINchange_request%2Cincident%2Cproblem%2Cchange_task%2Ctask%5EORDERBYlabel&sysparm_fields=name%2Clabel&sysparm_limit=60")
  Call<JsonNode>
  getTicketTypes(@Header("Authorization") String authorization);

  // Scripted API to list templates
  @GET("/api/x_harne_harness_ap/template/list")
  Call<JsonNode> getTemplateList(@Header("Authorization") String authorization, @Header("ticketType") String ticketType,
      @Header("limit") int limit, @Header("offset") int offset, @Header("templateName") String templateName);

  // Scripted API to create ticket using template
  @POST("/api/x_harne_harness_ap/template/create")
  Call<JsonNode> createUsingTemplate(@Header("Authorization") String authorization,
      @Header("ticketType") String ticketType, @Header("templateName") String templateName);

  // Scripted API to update ticket using template
  @PUT("/api/x_harne_harness_ap/template/update")
  Call<JsonNode> updateUsingTemplate(@Header("Authorization") String authorization,
      @Header("ticketType") String ticketType, @Header("templateName") String templateName,
      @Header("ticketNumber") String ticketNumber);
}
