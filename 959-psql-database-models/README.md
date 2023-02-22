### How to auto generate code (POJOs)  
Make sure local timescale db instance is running and the new migrations are applied, then follow below steps.
1. Add the table name in this file 959-psql-database-models/pom.xml, if POJO for a new table is required.
2. Add the repository-level and module-level `pom.xml` files (Do-Not-Commit-These-Files). 
   Refer here for the pom files: 
   https://harness.atlassian.net/wiki/spaces/FS/pages/21310341316/POM.xml+files+for+POJOs+using+jOOQ
3. Run below commands to re-generate the codes. 
    ```shell
    hello:~/harness/portal
    \> cd 959-psql-database-models/ && mvn jooq-codegen:generate && cd ..
    ```
4. Optionally you can run below command to clean and get the actual change set.
    ```shell
    hello:~/harness/portal
    \> git diff --diff-filter=d --name-only | grep "959-psql-database-models/src/generated/" | xargs clang-format -i
    ```


### References: 
-  https://www.jooq.org/doc/3.0/manual/
-  [How+to+use+jOOQ+for+your+SQL+Queries](https://harness.atlassian.net/wiki/spaces/FS/pages/1938752476/How+to+use+jOOQ+for+your+SQL+Queries)
-  [TimescaleDB+Access+using+jOOQ](https://harness.atlassian.net/wiki/spaces/CE/pages/1260683495/TimescaleDB+Access+using+jOOQ)
-  [jOOQ+Library+POV](https://harness.atlassian.net/wiki/spaces/FS/pages/1902936127/jOOQ+Library+POV)
-  Recording [link](https://harness-io.zoom.us/rec/share/8cdJQQGPPum_TSW61f8r4iiJve9zLdOGY-9x1UU1f5Gz6_IUDjjI4rgx_3URHNfv.0uZ0iOJm9-xZObwq), Passcode: Ej9H11$p