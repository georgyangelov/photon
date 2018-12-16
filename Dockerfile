FROM rust:latest
RUN cargo install cargo-build-deps

RUN cd / && USER=root cargo new --lib photon

WORKDIR /photon

COPY Cargo.toml Cargo.lock ./
RUN cargo build-deps

COPY . .
