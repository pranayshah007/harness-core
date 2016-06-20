package software.wings.api;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

import software.wings.common.Constants;
import software.wings.sm.ExecutionStatus;
import software.wings.sm.StateExecutionData;

import java.util.LinkedHashMap;
import java.util.Map;

// TODO: Auto-generated Javadoc

/**
 * Created by peeyushaggarwal on 5/26/16.
 */
public class EmailStateExecutionData extends StateExecutionData {
  private static final long serialVersionUID = -8664130788122512084L;
  private String toAddress;
  private String ccAddress;
  private String subject;
  private String body;

  /**
   * Gets to address.
   *
   * @return the to address
   */
  public String getToAddress() {
    return toAddress;
  }

  /**
   * Sets to address.
   *
   * @param toAddress the to address
   */
  public void setToAddress(String toAddress) {
    this.toAddress = toAddress;
  }

  /**
   * Gets cc address.
   *
   * @return the cc address
   */
  public String getCcAddress() {
    return ccAddress;
  }

  /**
   * Sets cc address.
   *
   * @param ccAddress the cc address
   */
  public void setCcAddress(String ccAddress) {
    this.ccAddress = ccAddress;
  }

  /**
   * Gets subject.
   *
   * @return the subject
   */
  public String getSubject() {
    return subject;
  }

  /**
   * Sets subject.
   *
   * @param subject the subject
   */
  public void setSubject(String subject) {
    this.subject = subject;
  }

  /**
   * Gets body.
   *
   * @return the body
   */
  public String getBody() {
    return body;
  }

  /**
   * Sets body.
   *
   * @param body the body
   */
  public void setBody(String body) {
    this.body = body;
  }

  /* (non-Javadoc)
   * @see java.lang.Object#equals(java.lang.Object)
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    EmailStateExecutionData that = (EmailStateExecutionData) o;
    return Objects.equal(toAddress, that.toAddress) && Objects.equal(ccAddress, that.ccAddress)
        && Objects.equal(subject, that.subject) && Objects.equal(body, that.body);
  }

  @Override
  public Object getExecutionSummary() {
    LinkedHashMap<String, Object> execData = fillExecutionData();
    if (body != null && body.length() > Constants.SUMMARY_PAYLOAD_LIMIT) {
      execData.put("body", body.substring(0, Constants.SUMMARY_PAYLOAD_LIMIT));
    }
    execData.putAll((Map<String, Object>) super.getExecutionSummary());
    return execData;
  }

  @Override
  public Object getExecutionDetails() {
    LinkedHashMap<String, Object> execData = fillExecutionData();
    execData.putAll((Map<String, Object>) super.getExecutionSummary());
    return execData;
  }

  private LinkedHashMap<String, Object> fillExecutionData() {
    LinkedHashMap<String, Object> orderedMap = new LinkedHashMap<>();
    putNotNull(orderedMap, "toAddress", toAddress);
    putNotNull(orderedMap, "ccAddress", ccAddress);
    putNotNull(orderedMap, "subject", subject);
    putNotNull(orderedMap, "body", body);
    return orderedMap;
  }

  /* (non-Javadoc)
   * @see java.lang.Object#hashCode()
   */
  @Override
  public int hashCode() {
    return Objects.hashCode(toAddress, ccAddress, subject, body);
  }

  /* (non-Javadoc)
   * @see java.lang.Object#toString()
   */
  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("toAddress", toAddress)
        .add("ccAddress", ccAddress)
        .add("subject", subject)
        .add("body", body)
        .toString();
  }

  /**
   * The Class Builder.
   */
  public static final class Builder {
    private String toAddress;
    private String ccAddress;
    private String subject;
    private String body;
    private String stateName;
    private long startTs;
    private long endTs;
    private ExecutionStatus status;

    private Builder() {}

    /**
     * An email state execution data.
     *
     * @return the builder
     */
    public static Builder anEmailStateExecutionData() {
      return new Builder();
    }

    /**
     * With to address.
     *
     * @param toAddress the to address
     * @return the builder
     */
    public Builder withToAddress(String toAddress) {
      this.toAddress = toAddress;
      return this;
    }

    /**
     * With cc address.
     *
     * @param ccAddress the cc address
     * @return the builder
     */
    public Builder withCcAddress(String ccAddress) {
      this.ccAddress = ccAddress;
      return this;
    }

    /**
     * With subject.
     *
     * @param subject the subject
     * @return the builder
     */
    public Builder withSubject(String subject) {
      this.subject = subject;
      return this;
    }

    /**
     * With body.
     *
     * @param body the body
     * @return the builder
     */
    public Builder withBody(String body) {
      this.body = body;
      return this;
    }

    /**
     * With state name.
     *
     * @param stateName the state name
     * @return the builder
     */
    public Builder withStateName(String stateName) {
      this.stateName = stateName;
      return this;
    }

    /**
     * With start ts.
     *
     * @param startTs the start ts
     * @return the builder
     */
    public Builder withStartTs(long startTs) {
      this.startTs = startTs;
      return this;
    }

    /**
     * With end ts.
     *
     * @param endTs the end ts
     * @return the builder
     */
    public Builder withEndTs(long endTs) {
      this.endTs = endTs;
      return this;
    }

    /**
     * With status.
     *
     * @param status the status
     * @return the builder
     */
    public Builder withStatus(ExecutionStatus status) {
      this.status = status;
      return this;
    }

    /**
     * But.
     *
     * @return the builder
     */
    public Builder but() {
      return anEmailStateExecutionData()
          .withToAddress(toAddress)
          .withCcAddress(ccAddress)
          .withSubject(subject)
          .withBody(body)
          .withStateName(stateName)
          .withStartTs(startTs)
          .withEndTs(endTs)
          .withStatus(status);
    }

    /**
     * Builds the.
     *
     * @return the email state execution data
     */
    public EmailStateExecutionData build() {
      EmailStateExecutionData emailStateExecutionData = new EmailStateExecutionData();
      emailStateExecutionData.setToAddress(toAddress);
      emailStateExecutionData.setCcAddress(ccAddress);
      emailStateExecutionData.setSubject(subject);
      emailStateExecutionData.setBody(body);
      emailStateExecutionData.setStateName(stateName);
      emailStateExecutionData.setStartTs(startTs);
      emailStateExecutionData.setEndTs(endTs);
      emailStateExecutionData.setStatus(status);
      return emailStateExecutionData;
    }
  }
}
