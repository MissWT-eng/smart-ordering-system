# 只负责运行，不在容器内构建：jar 在本地打好后 COPY 进来，构建只需几秒
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# 本地 target 下打好的可执行 jar
COPY target/smart-ordering-system-0.0.1-SNAPSHOT.jar app.jar

# 2核4G 机器上给 JVM 留足余量：堆固定 768M，其余留给元空间、线程栈和系统
ENV TZ=Asia/Shanghai
ENTRYPOINT ["java", \
            "-Xmx768m", \
            "-XX:+UseG1GC", \
            "-Duser.timezone=Asia/Shanghai", \
            "-jar", "/app/app.jar"]

EXPOSE 8080
