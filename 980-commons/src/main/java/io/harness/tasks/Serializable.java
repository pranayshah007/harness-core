package io.harness.tasks;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.IOException;

public interface Serializable {
    byte[] serialize() throws JsonProcessingException;
    ResponseData deserialize(byte[] data) throws IOException;
}
