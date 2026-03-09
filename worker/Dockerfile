FROM gradle:8.9-jdk17

WORKDIR /app

COPY . .

EXPOSE 8081

ENTRYPOINT ["gradle", ":worker:bootRun", "--no-daemon"]
