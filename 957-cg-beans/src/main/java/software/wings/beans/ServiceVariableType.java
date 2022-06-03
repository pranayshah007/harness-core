package software.wings.beans;

public enum ServiceVariableType {
  /**
   * Text type.
   */
  TEXT,
  /**
   * Lb type.
   */
  LB(true),
  /**
   * Encrypted text type.
   */
  ENCRYPTED_TEXT,
  /**
   * Artifact type.
   */
  ARTIFACT;

  private boolean settingAttribute;

  ServiceVariableType() {}

  ServiceVariableType(boolean settingAttribute) {
    this.settingAttribute = settingAttribute;
  }

  /**
   * Is setting attribute boolean.
   *
   * @return the boolean
   */
  public boolean isSettingAttribute() {
    return settingAttribute;
  }
}
