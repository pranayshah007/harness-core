/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.beans.EmbeddedUser;
import io.harness.ng.DbAliases;

import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.EqualsAndHashCode;
import org.mongodb.morphia.annotations.Entity;

/**
 * Created by peeyushaggarwal on 3/22/17.
 */
@EqualsAndHashCode(callSuper = false)
@StoreIn(DbAliases.HARNESS)
@Entity(value = "licenses", noClassnameStored = true)
@HarnessEntity(exportable = true)
public class License extends Base {
  private String name;
  private List<Entitlement> entitlements;
  private boolean isActive;
  private long expiryDuration;

  public static class Entitlement {
    private String operation;
    private int limit;
    private int rate;
    private TimeUnit rateUnit;

    /**
     * Getter for property 'operation'.
     *
     * @return Value for property 'operation'.
     */
    public String getOperation() {
      return operation;
    }

    /**
     * Setter for property 'operation'.
     *
     * @param operation Value to set for property 'operation'.
     */
    public void setOperation(String operation) {
      this.operation = operation;
    }

    /**
     * Getter for property 'limit'.
     *
     * @return Value for property 'limit'.
     */
    public int getLimit() {
      return limit;
    }

    /**
     * Setter for property 'limit'.
     *
     * @param limit Value to set for property 'limit'.
     */
    public void setLimit(int limit) {
      this.limit = limit;
    }

    /**
     * Getter for property 'rate'.
     *
     * @return Value for property 'rate'.
     */
    public int getRate() {
      return rate;
    }

    /**
     * Setter for property 'rate'.
     *
     * @param rate Value to set for property 'rate'.
     */
    public void setRate(int rate) {
      this.rate = rate;
    }

    /**
     * Getter for property 'rateUnit'.
     *
     * @return Value for property 'rateUnit'.
     */
    public TimeUnit getRateUnit() {
      return rateUnit;
    }

    /**
     * Setter for property 'rateUnit'.
     *
     * @param rateUnit Value to set for property 'rateUnit'.
     */
    public void setRateUnit(TimeUnit rateUnit) {
      this.rateUnit = rateUnit;
    }
  }

  /**
   * Getter for property 'name'.
   *
   * @return Value for property 'name'.
   */
  public String getName() {
    return name;
  }

  /**
   * Setter for property 'name'.
   *
   * @param name Value to set for property 'name'.
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Getter for property 'entitlements'.
   *
   * @return Value for property 'entitlements'.
   */
  public List<Entitlement> getEntitlements() {
    return entitlements;
  }

  /**
   * Setter for property 'entitlements'.
   *
   * @param entitlements Value to set for property 'entitlements'.
   */
  public void setEntitlements(List<Entitlement> entitlements) {
    this.entitlements = entitlements;
  }

  /**
   * Getter for property 'active'.
   *
   * @return Value for property 'active'.
   */
  public boolean isActive() {
    return isActive;
  }

  /**
   * Setter for property 'active'.
   *
   * @param active Value to set for property 'active'.
   */
  public void setActive(boolean active) {
    isActive = active;
  }

  /**
   * Getter for property 'expiryDuration'.
   *
   * @return Value for property 'expiryDuration'.
   */
  public long getExpiryDuration() {
    return expiryDuration;
  }

  /**
   * Setter for property 'expiryDuration'.
   *
   * @param expiryDuration Value to set for property 'expiryDuration'.
   */
  public void setExpiryDuration(long expiryDuration) {
    this.expiryDuration = expiryDuration;
  }

  public static final class Builder {
    private String name;
    private List<Entitlement> entitlements;
    private boolean isActive;
    private long expiryDuration;
    private String uuid;
    private String appId;
    private EmbeddedUser createdBy;
    private long createdAt;
    private EmbeddedUser lastUpdatedBy;
    private long lastUpdatedAt;

    private Builder() {}

    public static Builder aLicense() {
      return new Builder();
    }

    public Builder withName(String name) {
      this.name = name;
      return this;
    }

    public Builder withEntitlements(List<Entitlement> entitlements) {
      this.entitlements = entitlements;
      return this;
    }

    public Builder withIsActive(boolean isActive) {
      this.isActive = isActive;
      return this;
    }

    public Builder withExpiryDuration(long expiryDuration) {
      this.expiryDuration = expiryDuration;
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

    public Builder withLastUpdatedBy(EmbeddedUser lastUpdatedBy) {
      this.lastUpdatedBy = lastUpdatedBy;
      return this;
    }

    public Builder withLastUpdatedAt(long lastUpdatedAt) {
      this.lastUpdatedAt = lastUpdatedAt;
      return this;
    }

    public Builder but() {
      return aLicense()
          .withName(name)
          .withEntitlements(entitlements)
          .withIsActive(isActive)
          .withExpiryDuration(expiryDuration)
          .withUuid(uuid)
          .withAppId(appId)
          .withCreatedBy(createdBy)
          .withCreatedAt(createdAt)
          .withLastUpdatedBy(lastUpdatedBy)
          .withLastUpdatedAt(lastUpdatedAt);
    }

    public License build() {
      License license = new License();
      license.setName(name);
      license.setEntitlements(entitlements);
      license.setExpiryDuration(expiryDuration);
      license.setUuid(uuid);
      license.setAppId(appId);
      license.setCreatedBy(createdBy);
      license.setCreatedAt(createdAt);
      license.setLastUpdatedBy(lastUpdatedBy);
      license.setLastUpdatedAt(lastUpdatedAt);
      license.isActive = this.isActive;
      return license;
    }
  }
}
