FROM gradle:jdk18-jammy as thingy_builder
WORKDIR /usr/src/app
COPY . .
RUN gradle :shadowJar

FROM debian:bullseye
RUN echo "Installing system dependencies" && \
    apt-get update && \
    apt-get install -y software-properties-common python3-pip wget curl && \ 
    wget -O- https://apt.corretto.aws/corretto.key | apt-key add - && \
    add-apt-repository 'deb https://apt.corretto.aws stable main' && \
    apt-get update && \
    apt-get install -y java-18-amazon-corretto-jdk && \
    apt-get remove -y wget software-properties-common && \
    apt-get autoremove -y && \
    rm -rf corretto.key && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /opt/thingy/data
COPY --from=thingy_builder /usr/src/app/build/libs/Thingy-*-all.jar /opt/thingy/Thingy.jar
CMD ["java", "-jar", "/opt/thingy/Thingy.jar"]