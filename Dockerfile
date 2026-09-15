FROM eclipse-temurin:21-jdk

WORKDIR /app

COPY . .

RUN chmod +x ./gradlew
RUN ./gradlew clean build -x test

EXPOSE 10000

ENV PORT=10000

CMD ["sh", "-c", "java -jar build/libs/*.jar"]
