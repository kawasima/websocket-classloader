websocket-classloader
=====================

A ClassLoader loading remote java class via WebSocket.
[![Build Status](https://travis-ci.org/kawasima/websocket-classloader.png?branch=master)](https://travis-ci.org/kawasima/websocket-classloader)

## Usage

Deploy ClassProvider to JSR-356 container, e.g. undertow, tomcat.

Use class loader as following:

```java
ClassLoader cl = new WebSocketClassLoader("ws://class-provider-host:port");
Class<?> hogeClass = cl.loadClass("org.example.HogeHoge", true);
```

## Architecture

                class binary format
            +-----------------------------------------------+
            v                                               |
    +----------------------+  loadClass request     +---------------+
    |   Thin Application   |  (WebSocket)           | ClassProvider |
    | WebSocketClassLoader | ---------------------> |               |
    +----------------------+                        +---------------+



## License

Apache License 2.0
(c) 2014-2017 Yoshitaka Kawashima

