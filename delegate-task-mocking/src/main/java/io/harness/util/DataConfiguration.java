package io.harness.util;

import lombok.Data;

@Data
public class DataConfiguration {
    private String env;
    private String version;
    private String accountId;
    private String token;
    private String delegateName;
    private int delegateCount;
}
