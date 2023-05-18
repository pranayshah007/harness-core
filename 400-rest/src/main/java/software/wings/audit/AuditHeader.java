/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.audit;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EmbeddedUser;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;

import software.wings.beans.Application;
import software.wings.beans.Base;
import software.wings.beans.Environment;
import software.wings.beans.HttpMethod;
import software.wings.beans.Service;
import software.wings.beans.User;
import software.wings.jersey.JsonViews;

import com.fasterxml.jackson.annotation.JsonView;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.HashMap;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.UtilityClass;

/**
 * HttpAuditHeader bean class.
 *
 * @author Rishi
 */
@OwnedBy(PL)
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants(innerTypeName = "AuditHeaderKeys")
@StoreIn(DbAliases.HARNESS)
@Entity(value = "audits", noClassnameStored = true)
@HarnessEntity(exportable = true)
@TargetModule(HarnessModule._940_CG_AUDIT_SERVICE)
public class AuditHeader extends Base implements AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(SortCompoundMongoIndex.builder()
                 .name("entityRecordIndex_1")
                 .field(AuditHeaderKeys.accountId)
                 .field(AuditHeaderKeys.appIdEntityRecord)
                 .field(AuditHeaderKeys.affectedResourceType)
                 .field(AuditHeaderKeys.affectedResourceOp)
                 .descSortField(AuditHeaderKeys.createdAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("entityRecordIndex_2")
                 .field(AuditHeaderKeys.accountId)
                 .field(AuditHeaderKeys.appIdEntityRecord)
                 .field(AuditHeaderKeys.affectedResourceId)
                 .field(AuditHeaderKeys.affectedResourceOp)
                 .descSortField(AuditHeaderKeys.createdAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("entityRecordIndex_3")
                 .field(AuditHeaderKeys.accountId)
                 .field(AuditHeaderKeys.affectedResourceType)
                 .field(AuditHeaderKeys.affectedResourceOp)
                 .descSortField(AuditHeaderKeys.createdAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("entityRecordIndex_4")
                 .field(AuditHeaderKeys.accountId)
                 .field(AuditHeaderKeys.appIdEntityRecord)
                 .descSortField(AuditHeaderKeys.createdAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_createdAt_earAffectedResourceOperation_earAffectedResourceType")
                 .field(AuditHeaderKeys.accountId)
                 .descSortField(AuditHeaderKeys.createdAt)
                 .rangeField(AuditHeaderKeys.affectedResourceOp)
                 .rangeField(AuditHeaderKeys.affectedResourceType)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_entityId_createdAt")
                 .field(AuditHeaderKeys.accountId)
                 .field(AuditHeaderKeys.entityId)
                 .descSortField(AuditHeaderKeys.createdAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_earAppId_createdByUUID_createdAt")
                 .field(AuditHeaderKeys.accountId)
                 .field(AuditHeaderKeys.appIdEntityRecord)
                 .field(AuditHeaderKeys.createdById)
                 .descSortField(AuditHeaderKeys.createdAt)
                 .build())
        .build();
  }

  /**
   * The Remote user.
   */
  protected User remoteUser;
  /**
   * The Application.
   */
  @JsonView(JsonViews.Internal.class) protected Application application;
  /**
   * The Component.
   */
  @JsonView(JsonViews.Internal.class) protected Service component;
  /**
   * The Environment.
   */
  @JsonView(JsonViews.Internal.class) protected Environment environment;
  private String url;
  private String resourcePath;
  @JsonView(JsonViews.Internal.class) private String queryParams;
  private HttpMethod requestMethod;
  @JsonView(JsonViews.Internal.class) private String headerString;
  @JsonView(JsonViews.Internal.class) private ResponseType responseType;
  private Integer responseStatusCode;
  @JsonView(JsonViews.Internal.class) private String errorCode;
  @JsonView(JsonViews.Internal.class) private String remoteHostName;
  @JsonView(JsonViews.Internal.class) private Integer remoteHostPort;
  private String remoteIpAddress;
  @JsonView(JsonViews.Internal.class) private String localHostName;
  @JsonView(JsonViews.Internal.class) private String localIpAddress;
  @JsonView(JsonViews.Internal.class) private String requestPayloadUuid;
  @JsonView(JsonViews.Internal.class) private String responsePayloadUuid;
  private Long requestTime;
  @JsonView(JsonViews.Internal.class) private Long responseTime;
  private String failureStatusMsg;
  @Getter @Setter private HashMap<String, Object> details;

  // For Audit Headers created by Git user actions
  @Getter @Setter private String accountId;
  @Getter @Setter private GitAuditDetails gitAuditDetails;
  @Getter @Setter private List<EntityAuditRecord> entityAuditRecords;
  @Getter @Setter private ApiKeyAuditDetails apiKeyAuditDetails;

  /**
   * Gets query params.
   *
   * @return the query params
   */
  public String getQueryParams() {
    return queryParams;
  }

  /**
   * Sets query params.
   *
   * @param queryParams the query params
   */
  public void setQueryParams(String queryParams) {
    this.queryParams = queryParams;
  }

  /**
   * Gets request method.
   *
   * @return the request method
   */
  public HttpMethod getRequestMethod() {
    return requestMethod;
  }

  /**
   * Sets request method.
   *
   * @param requestMethod the request method
   */
  public void setRequestMethod(HttpMethod requestMethod) {
    this.requestMethod = requestMethod;
  }

  /**
   * Gets resource path.
   *
   * @return the resource path
   */
  public String getResourcePath() {
    return resourcePath;
  }

  /**
   * Sets resource path.
   *
   * @param resourcePath the resource path
   */
  public void setResourcePath(String resourcePath) {
    this.resourcePath = resourcePath;
  }

  /**
   * Gets response status code.
   *
   * @return the response status code
   */
  public Integer getResponseStatusCode() {
    return responseStatusCode;
  }

  /**
   * Sets response status code.
   *
   * @param responseStatusCode the response status code
   */
  public void setResponseStatusCode(Integer responseStatusCode) {
    this.responseStatusCode = responseStatusCode;
  }

  /**
   * Gets response type.
   *
   * @return the response type
   */
  public ResponseType getResponseType() {
    return responseType;
  }

  /**
   * Sets response type.
   *
   * @param responseType the response type
   */
  public void setResponseType(ResponseType responseType) {
    this.responseType = responseType;
  }

  /**
   * Gets error code.
   *
   * @return the error code
   */
  public String getErrorCode() {
    return errorCode;
  }

  /**
   * Sets error code.
   *
   * @param errorCode the error code
   */
  public void setErrorCode(String errorCode) {
    this.errorCode = errorCode;
  }

  /**
   * Gets remote user.
   *
   * @return the remote user
   */
  public User getRemoteUser() {
    return remoteUser;
  }

  /**
   * Sets remote user.
   *
   * @param remoteUser the remote user
   */
  public void setRemoteUser(User remoteUser) {
    this.remoteUser = remoteUser;
  }

  /**
   * Gets remote ip address.
   *
   * @return the remote ip address
   */
  public String getRemoteIpAddress() {
    return remoteIpAddress;
  }

  /**
   * Sets remote ip address.
   *
   * @param remoteIpAddress the remote ip address
   */
  public void setRemoteIpAddress(String remoteIpAddress) {
    this.remoteIpAddress = remoteIpAddress;
  }

  /**
   * Gets remote host name.
   *
   * @return the remote host name
   */
  public String getRemoteHostName() {
    return remoteHostName;
  }

  /**
   * Sets remote host name.
   *
   * @param remoteHostName the remote host name
   */
  public void setRemoteHostName(String remoteHostName) {
    this.remoteHostName = remoteHostName;
  }

  /**
   * Gets remote host port.
   *
   * @return the remote host port
   */
  public Integer getRemoteHostPort() {
    return remoteHostPort;
  }

  /**
   * Sets remote host port.
   *
   * @param remoteHostPort the remote host port
   */
  public void setRemoteHostPort(Integer remoteHostPort) {
    this.remoteHostPort = remoteHostPort;
  }

  /**
   * Gets local ip address.
   *
   * @return the local ip address
   */
  public String getLocalIpAddress() {
    return localIpAddress;
  }

  /**
   * Sets local ip address.
   *
   * @param localIpAddress the local ip address
   */
  public void setLocalIpAddress(String localIpAddress) {
    this.localIpAddress = localIpAddress;
  }

  public String getLocalHostName() {
    return localHostName;
  }

  public void setLocalHostName(String localHostName) {
    this.localHostName = localHostName;
  }

  public Application getApplication() {
    return application;
  }

  public void setApplication(Application application) {
    this.application = application;
  }

  public Service getComponent() {
    return component;
  }

  public void setComponent(Service component) {
    this.component = component;
  }

  public Environment getEnvironment() {
    return environment;
  }

  public void setEnvironment(Environment environment) {
    this.environment = environment;
  }

  public Long getRequestTime() {
    return requestTime;
  }

  public void setRequestTime(Long requestTime) {
    this.requestTime = requestTime;
  }

  public Long getResponseTime() {
    return responseTime;
  }

  public void setResponseTime(Long responseTime) {
    this.responseTime = responseTime;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getHeaderString() {
    return headerString;
  }

  public void setHeaderString(String headerString) {
    this.headerString = headerString;
  }

  public String getRequestPayloadUuid() {
    return requestPayloadUuid;
  }

  public void setRequestPayloadUuid(String requestPayloadUuid) {
    this.requestPayloadUuid = requestPayloadUuid;
  }

  public String getResponsePayloadUuid() {
    return responsePayloadUuid;
  }

  public void setResponsePayloadUuid(String responsePayloadUuid) {
    this.responsePayloadUuid = responsePayloadUuid;
  }

  public String getFailureStatusMsg() {
    return failureStatusMsg;
  }

  public void setFailureStatusMsg(String failureStatusMsg) {
    this.failureStatusMsg = failureStatusMsg;
  }

  @UtilityClass
  public static final class AuditHeaderKeys {
    // Temporary
    public static final String createdAt = "createdAt";
    public static final String createdBy = "createdBy";
    public static final String createdByName = "createdBy.name";
    public static final String createdById = "createdBy.uuid";
    public static final String uuid = "uuid";
    public static final String appIdEntityRecord = "entityAuditRecords.appId";
    public static final String affectedResourceId = "entityAuditRecords.affectedResourceId";
    public static final String affectedResourceType = "entityAuditRecords.affectedResourceType";
    public static final String affectedResourceOp = "entityAuditRecords.affectedResourceOperation";
    public static final String entityId = "entityAuditRecords.entityId";
  }
  /**
   * The Enum RequestType.
   */
  public enum RequestType {
    /**
     * Request request type.
     */
    REQUEST,
    /**
     * Response request type.
     */
    RESPONSE
  }

  /**
   * The Enum ResponseType.
   */
  public enum ResponseType {
    /**
     * Success response type.
     */
    SUCCESS,
    /**
     * Failed response type.
     */
    FAILED,

    COMPLETED_WITH_ERRORS
  }

  /**
   * The Class Builder.
   */
  public static final class Builder {
    /**
     * The Remote user.
     */
    protected User remoteUser;
    /**
     * The Application.
     */
    protected Application application;
    /**
     * The Component.
     */
    protected Service component;
    /**
     * The Environment.
     */
    protected Environment environment;
    private String url;
    private String resourcePath;
    private String queryParams;
    private HttpMethod requestMethod;
    private String headerString;
    private ResponseType responseType;
    private Integer responseStatusCode;
    private String errorCode;
    private String remoteHostName;
    private Integer remoteHostPort;
    private String remoteIpAddress;
    private String localHostName;
    private String localIpAddress;
    private String requestPayloadUuid;
    private String responsePayloadUuid;
    private Long requestTime;
    private Long responseTime;
    private String uuid;
    private String appId;
    private EmbeddedUser createdBy;
    private long createdAt;
    private EmbeddedUser lastUpdatedBy;
    private long lastUpdatedAt;
    private GitAuditDetails gitAuditDetails;
    private List<EntityAuditRecord> entityAuditRecords;
    private String failureStatusMsg;
    private ApiKeyAuditDetails apiKeyAuditDetails;
    private HashMap<String, Object> details;

    private Builder() {}

    /**
     * An audit header.
     *
     * @return the builder
     */
    public static Builder anAuditHeader() {
      return new Builder();
    }

    public Builder withGitAuditDetails(GitAuditDetails gitAuditDetails) {
      this.gitAuditDetails = gitAuditDetails;
      return this;
    }

    public Builder withEntityAuditRecords(List<EntityAuditRecord> entityAuditRecords) {
      this.entityAuditRecords = entityAuditRecords;
      return this;
    }

    /**
     * With remote user.
     *
     * @param remoteUser the remote user
     * @return the builder
     */
    public Builder withRemoteUser(User remoteUser) {
      this.remoteUser = remoteUser;
      return this;
    }

    /**
     * With application.
     *
     * @param application the application
     * @return the builder
     */
    public Builder withApplication(Application application) {
      this.application = application;
      return this;
    }

    /**
     * With component.
     *
     * @param component the component
     * @return the builder
     */
    public Builder withComponent(Service component) {
      this.component = component;
      return this;
    }

    /**
     * With environment.
     *
     * @param environment the environment
     * @return the builder
     */
    public Builder withEnvironment(Environment environment) {
      this.environment = environment;
      return this;
    }

    /**
     * With url.
     *
     * @param url the url
     * @return the builder
     */
    public Builder withUrl(String url) {
      this.url = url;
      return this;
    }

    /**
     * With resource path.
     *
     * @param resourcePath the resource path
     * @return the builder
     */
    public Builder withResourcePath(String resourcePath) {
      this.resourcePath = resourcePath;
      return this;
    }

    /**
     * With query params.
     *
     * @param queryParams the query params
     * @return the builder
     */
    public Builder withQueryParams(String queryParams) {
      this.queryParams = queryParams;
      return this;
    }

    /**
     * With request method.
     *
     * @param requestMethod the request method
     * @return the builder
     */
    public Builder withRequestMethod(HttpMethod requestMethod) {
      this.requestMethod = requestMethod;
      return this;
    }

    /**
     * With header string.
     *
     * @param headerString the header string
     * @return the builder
     */
    public Builder withHeaderString(String headerString) {
      this.headerString = headerString;
      return this;
    }

    /**
     * With response type.
     *
     * @param responseType the response type
     * @return the builder
     */
    public Builder withResponseType(ResponseType responseType) {
      this.responseType = responseType;
      return this;
    }

    /**
     * With response status code.
     *
     * @param responseStatusCode the response status code
     * @return the builder
     */
    public Builder withResponseStatusCode(Integer responseStatusCode) {
      this.responseStatusCode = responseStatusCode;
      return this;
    }

    /**
     * With error code.
     *
     * @param errorCode the error code
     * @return the builder
     */
    public Builder withErrorCode(String errorCode) {
      this.errorCode = errorCode;
      return this;
    }

    /**
     * With remote host name.
     *
     * @param remoteHostName the remote host name
     * @return the builder
     */
    public Builder withRemoteHostName(String remoteHostName) {
      this.remoteHostName = remoteHostName;
      return this;
    }

    /**
     * With remote host port.
     *
     * @param remoteHostPort the remote host port
     * @return the builder
     */
    public Builder withRemoteHostPort(Integer remoteHostPort) {
      this.remoteHostPort = remoteHostPort;
      return this;
    }

    /**
     * With remote ip address.
     *
     * @param remoteIpAddress the remote ip address
     * @return the builder
     */
    public Builder withRemoteIpAddress(String remoteIpAddress) {
      this.remoteIpAddress = remoteIpAddress;
      return this;
    }

    /**
     * With local host name.
     *
     * @param localHostName the local host name
     * @return the builder
     */
    public Builder withLocalHostName(String localHostName) {
      this.localHostName = localHostName;
      return this;
    }

    /**
     * With local ip address.
     *
     * @param localIpAddress the local ip address
     * @return the builder
     */
    public Builder withLocalIpAddress(String localIpAddress) {
      this.localIpAddress = localIpAddress;
      return this;
    }

    /**
     * With request payload uuid.
     *
     * @param requestPayloadUuid the request payload uuid
     * @return the builder
     */
    public Builder withRequestPayloadUuid(String requestPayloadUuid) {
      this.requestPayloadUuid = requestPayloadUuid;
      return this;
    }

    /**
     * With response payload uuid.
     *
     * @param responsePayloadUuid the response payload uuid
     * @return the builder
     */
    public Builder withResponsePayloadUuid(String responsePayloadUuid) {
      this.responsePayloadUuid = responsePayloadUuid;
      return this;
    }

    /**
     * With request time.
     *
     * @param requestTime the request time
     * @return the builder
     */
    public Builder withRequestTime(Long requestTime) {
      this.requestTime = requestTime;
      return this;
    }

    /**
     * With response time.
     *
     * @param responseTime the response time
     * @return the builder
     */
    public Builder withResponseTime(Long responseTime) {
      this.responseTime = responseTime;
      return this;
    }

    public Builder withLastUpdatedBy(EmbeddedUser lastUpdatedBy) {
      this.lastUpdatedBy = lastUpdatedBy;
      return this;
    }

    public Builder withLastUpdatedAt(long lastUpdatedAt) {
      this.lastUpdatedAt = lastUpdatedAt;
      return this;
    }

    public Builder withUuid(String uuid) {
      this.uuid = uuid;
      return this;
    }

    public Builder withAppId(String appId) {
      this.appId = appId;
      return this;
    }

    public Builder withCreatedBy(EmbeddedUser createdBy) {
      this.createdBy = createdBy;
      return this;
    }

    public Builder withCreatedAt(long createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public Builder withFailureStatusMsg(String failureStatusMsg) {
      this.failureStatusMsg = failureStatusMsg;
      return this;
    }

    public Builder withApiKeyAuditDetails(ApiKeyAuditDetails apiKeyAuditDetails) {
      this.apiKeyAuditDetails = apiKeyAuditDetails;
      return this;
    }

    public Builder details(HashMap<String, Object> details) {
      this.details = details;
      return this;
    }

    /**
     * Builds the.
     *
     * @return the audit header
     */
    public AuditHeader build() {
      AuditHeader auditHeader = new AuditHeader();
      auditHeader.setRemoteUser(remoteUser);
      auditHeader.setApplication(application);
      auditHeader.setComponent(component);
      auditHeader.setEnvironment(environment);
      auditHeader.setUrl(url);
      auditHeader.setResourcePath(resourcePath);
      auditHeader.setQueryParams(queryParams);
      auditHeader.setRequestMethod(requestMethod);
      auditHeader.setHeaderString(headerString);
      auditHeader.setResponseType(responseType);
      auditHeader.setResponseStatusCode(responseStatusCode);
      auditHeader.setErrorCode(errorCode);
      auditHeader.setRemoteHostName(remoteHostName);
      auditHeader.setRemoteHostPort(remoteHostPort);
      auditHeader.setRemoteIpAddress(remoteIpAddress);
      auditHeader.setLocalHostName(localHostName);
      auditHeader.setLocalIpAddress(localIpAddress);
      auditHeader.setRequestPayloadUuid(requestPayloadUuid);
      auditHeader.setResponsePayloadUuid(responsePayloadUuid);
      auditHeader.setRequestTime(requestTime);
      auditHeader.setResponseTime(responseTime);
      auditHeader.setUuid(uuid);
      auditHeader.setAppId(appId);
      auditHeader.setCreatedBy(createdBy);
      auditHeader.setCreatedAt(createdAt);
      auditHeader.setLastUpdatedBy(lastUpdatedBy);
      auditHeader.setLastUpdatedAt(lastUpdatedAt);
      auditHeader.setGitAuditDetails(gitAuditDetails);
      auditHeader.setFailureStatusMsg(failureStatusMsg);
      auditHeader.setApiKeyAuditDetails(apiKeyAuditDetails);
      auditHeader.setDetails(details);
      return auditHeader;
    }
  }
}
