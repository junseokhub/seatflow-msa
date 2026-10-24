FROM eclipse-temurin:21-jre AS builder
WORKDIR /app
COPY build/libs/ libs/
# 로컬 ./gradlew build는 *-plain.jar도 만들어서 실행 jar만 골라 추출 (Boot 3.3+ tools jarmode)
RUN java -Djarmode=tools -jar "$(ls libs/*.jar | grep -v -- '-plain\.jar$')" extract --layers --launcher --destination extracted

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/extracted/dependencies/ ./
COPY --from=builder /app/extracted/spring-boot-loader/ ./
COPY --from=builder /app/extracted/snapshot-dependencies/ ./
COPY --from=builder /app/extracted/application/ ./

# root가 아닌 기본 ubuntu 계정(1000)으로 실행
USER 1000:1000

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
