FROM public.ecr.aws/docker/library/alpine:3.22 AS geoip
ARG GEOIP_RELEASE=2026-09
ARG GEOIP_SHA256=c5d05b35a45c3eea0cadc728c8f5ad751693d4e270529b731442172a73f05954
RUN apk add --no-cache curl ca-certificates
RUN mkdir -p /geoip && \
    curl --fail --location --retry 3 --max-time 180 "https://download.db-ip.com/free/dbip-city-lite-${GEOIP_RELEASE}.mmdb.gz" -o /tmp/city.mmdb.gz && \
    echo "${GEOIP_SHA256}  /tmp/city.mmdb.gz" | sha256sum -c - && \
    gzip -dc /tmp/city.mmdb.gz > /geoip/dbip-city-lite.mmdb

FROM public.ecr.aws/amazoncorretto/amazoncorretto:17
WORKDIR /app
COPY target/challenge-platform-0.0.1-SNAPSHOT.jar app.jar
COPY --from=geoip /geoip/dbip-city-lite.mmdb /app/geoip/dbip-city-lite.mmdb
# For the restricted CloudFront -> ALB deployment; override to 0 for direct/local Docker access.
ENV GEOIP_DATABASE_PATH=/app/geoip/dbip-city-lite.mmdb TRUSTED_PROXY_HOPS=2
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]
