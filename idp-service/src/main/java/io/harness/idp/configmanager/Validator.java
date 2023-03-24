package io.harness.idp.configmanager;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.harness.idp.configmanager.utils.ConfigManagerUtils;

public class Validator {


     public void validator() throws Exception {


    String yaml = ConfigManagerUtils.readFile("configs/integrations/bitbucket.yaml");
        System.out.println("Yaml ---> "+yaml);
    String schema = ConfigManagerUtils.readFile("configs/integrations/schema.json");
        System.out.println("Json ---> "+schema);

    ObjectMapper objectMapper = new ObjectMapper();
    System.out.println("----------->"+ConfigManagerUtils.validateSchemaForYaml(yaml,ConfigManagerUtils.getJsonSchemaFromJsonNode(objectMapper.readTree(schema))));

}
}
