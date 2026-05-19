package com.filex.event;

/**
 * Event published when application configuration changes.
 *
 * <p>Allows components to react to configuration updates without polling or tight coupling to the
 * config manager.
 */
public final class ConfigChangedEvent extends AppEvent {

  private final String configKey;
  private final String oldValue;
  private final String newValue;

  public ConfigChangedEvent(String configKey, String oldValue, String newValue) {
    super("ConfigManager");
    this.configKey = configKey;
    this.oldValue = oldValue;
    this.newValue = newValue;
  }

  public String getConfigKey() {
    return configKey;
  }

  public String getOldValue() {
    return oldValue;
  }

  public String getNewValue() {
    return newValue;
  }

  @Override
  public String toString() {
    return "ConfigChangedEvent{"
        + "configKey='"
        + configKey
        + '\''
        + ", oldValue='"
        + oldValue
        + '\''
        + ", newValue='"
        + newValue
        + '\''
        + ", eventId='"
        + eventId()
        + '\''
        + '}';
  }
}
