FROM gradle:8.9-jdk17

WORKDIR /app

COPY . .

EXPOSE 8080

ENTRYPOINT ["gradle", ":manager:bootRun", "--no-daemon"]
