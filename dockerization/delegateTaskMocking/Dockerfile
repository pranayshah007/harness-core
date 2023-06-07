FROM ubuntu:20.04

# Any future release patches or updated source lists needs to be added here.

USER root

COPY start.sh start.sh

RUN chmod 777 start.sh

COPY --from=adoptopenjdk/openjdk11:x86_64-ubuntu-jre-11.0.14_9 /opt/java/openjdk/ /opt/java/openjdk/

ENV JAVA_HOME=/opt/java/openjdk/
ENV PATH="$JAVA_HOME/bin:${PATH}"

COPY delegate_task_mocking.jar delegate_task_mocking.jar


