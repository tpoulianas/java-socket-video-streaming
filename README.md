# java-socket-video-streaming
Client–Server video streaming prototype in Java (Sockets) with FFmpeg integration. University project for “Multimedia Communications”.

# Java Client–Server Video Streaming (Sockets + FFmpeg)

University project for the course **Multimedia Communications**.

## Overview
This repository contains a simple **client–server** prototype for streaming media over the network using **Java Sockets**.
The server handles streaming logic, while the client connects and receives the stream.

## Tech Stack
- Java
- TCP Sockets
- FFmpeg Java libraries (server side)

## Files
- `StreamingServer.java` – server implementation
- `StreamingClient.java` – client implementation
- `Μεταγλώττιση + Εκτέλεση των αρχείων.txt` – build/run commands

## Build & Run

### Server
```bash
javac -cp ".:/home/ice21390304/Java/ffmpeg-java-libs/*" StreamingServer.java
java -cp ".:/home/ice21390304/Java/ffmpeg-java-libs/*" StreamingServer
