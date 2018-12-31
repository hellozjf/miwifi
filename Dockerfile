FROM java:openjdk-8-jre-alpine

MAINTAINER hellozjf 908686171@qq.com

ARG JAR_FILE
ADD target/${JAR_FILE} miwifi.jar

EXPOSE 10124 10124

VOLUME /log

ENTRYPOINT ["java", "-jar", "miwifi.jar"]