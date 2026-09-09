# Local IP-location data

The backend reads `geoip/dbip-city-lite.mmdb` (override `GEOIP_DATABASE_PATH`).
Do not place this database in the frontend or commit its binary to Git.

Source: https://db-ip.com/db/download/ip-to-city-lite
Release: September 2026. License: CC BY 4.0, https://creativecommons.org/licenses/by/4.0/
Database data is provided by DB-IP, https://db-ip.com/.
The public map includes the required DB-IP attribution link.

Download (server-side; no candidate IP is transmitted):
https://download.db-ip.com/free/dbip-city-lite-2026-09.mmdb.gz

Compressed SHA-256: `c5d05b35a45c3eea0cadc728c8f5ad751693d4e270529b731442172a73f05954`
Decompressed SHA-1 published by DB-IP: `6ea870a637b5460023643fc18fdea84dcea14b9c`

For local setup, download and verify the archive, then decompress it to the path above.
Docker downloads and verifies this pinned release in a separate stage and copies only
the database into the runtime image. It does not download at startup or perform an
external lookup per submission. Update the release and verified checksum monthly in
Dockerfile and this document, then rebuild/restart. Stop the backend before replacing
a locally memory-mapped database on Windows. A missing database produces an explicit
unavailable map, never fabricated locations.
