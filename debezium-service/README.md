# Debezium Service Application

The application starts the thread for starting up debezium engine and push the events into redis streams. Configurations for source database are given in config.yml.

# Steps to run debezium service in local:

1. Click on run button in `DebeziumServiceApplication.java` and modify run configurations as follows: <br />

   Target expression:<br />
   `//debezium-service/service:module`<br />

   Executable flags:<br />
    `server`<br />
    `/Users/shaliniagrawal/Documents/harness-core/debezium-service/config/config.yml` -> path of config.yml of Debezium service<br />

   Bazel command:<br />
   `run`
   
2. Add the `apiKey` in `cfClientConfig` of `config.yml` of debezium service. The value of api key can be found here: <br />