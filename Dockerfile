FROM --platform=$TARGETOS/$TARGETARCH eclipse-temurin:24-jre-alpine

WORKDIR /usr/app
COPY build/install/germany-stations .

ENTRYPOINT ["/usr/app/bin/germany-stations"]
