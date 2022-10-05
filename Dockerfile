FROM rust:1.64.0-slim as keep_my_jcloud_builder
RUN cargo install \
    --git https://github.com/peterwilli/KeepMyJCloud.git \ 
    --rev 83e890a5f2da04b21353034c1c6235c9c7378e7e

FROM gradle:jdk18-jammy as thingy_builder
WORKDIR /usr/src/app
COPY . .
RUN gradle :shadowJar

FROM debian:bookworm-slim
RUN echo "Installing system dependencies" && \
    apt-get update && \
    apt-get install -y software-properties-common python3-pip wget curl && \ 
    wget -O- https://apt.corretto.aws/corretto.key | apt-key add - && \
    add-apt-repository 'deb https://apt.corretto.aws stable main' && \
    apt-get update && \
    apt-get install -y java-18-amazon-corretto-jdk && \
    echo "Installing python dependencies" && \
    pip3 install jcloud && \
    apt-get remove -y wget software-properties-common && \
    apt-get autoremove -y && \
    rm -rf corretto.key && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /opt/thingy
COPY --from=keep_my_jcloud_builder /usr/local/cargo/bin/keep_my_jcloud /usr/local/bin/keep_my_jcloud
COPY --from=thingy_builder /usr/src/app/build/libs/Thingy-*-all.jar /opt/thingy/Thingy.jar
COPY ./extras/docker_init /usr/local/bin/init
COPY ./flow_server.yml /opt/thingy
HEALTHCHECK --interval=30s --timeout=10s CMD curl --fail http://localhost:8000/info || exit 1
CMD ["init"]
