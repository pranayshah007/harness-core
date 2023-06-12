FROM redhat/ubi8-minimal:8.7

# Any future release patches or updated source lists needs to be added here.

USER root

COPY start.sh start.sh

RUN chmod 777 start.sh

COPY --from=adoptopenjdk/openjdk11:jre-11.0.19_7-ubi-minimal /opt/java/openjdk/ /opt/java/openjdk/

ENV JAVA_HOME=/opt/java/openjdk/
ENV PATH="$JAVA_HOME/bin:${PATH}"

COPY delegate_task_mocking.jar delegate_task_mocking.jar


