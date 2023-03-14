package io.harness.idp.namespace;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.common.io.Resources;
import io.harness.exception.InvalidRequestException;
import io.harness.jackson.JsonNodeUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class Merging {

    private String readFile(String filename) {
        ClassLoader classLoader = getClass().getClassLoader();
        try {
            return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new InvalidRequestException("Could not read resource file: " + filename);
        }
    }

    public String asYaml(String jsonString) throws JsonProcessingException, IOException {
        // parse JSON
        JsonNode jsonNodeTree = new ObjectMapper().readTree(jsonString);
        // save it as YAML
        String jsonAsYaml = new YAMLMapper().configure(YAMLGenerator.Feature.MINIMIZE_QUOTES, true).writeValueAsString(jsonNodeTree);
        return jsonAsYaml;
    }

    public void testingMerging() throws Exception{
    String main = readFile("test.yaml");
    String update = readFile("test2.yaml");

    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    JsonNode jsonNodeMain = mapper.readTree(main);
    System.out.println("++++++++++++++++++"+asYaml(jsonNodeMain.toString()));
    JsonNode jsonNodeUpdate = mapper.readTree(update);

    JsonNode test = JsonNodeUtils.merge(jsonNodeMain, jsonNodeUpdate);
    System.out.println("+++++++++++++++++++"+asYaml(jsonNodeUpdate.toString()));
    System.out.println("+++++++++++++++++++"+asYaml(test.toString()));

    }
}
