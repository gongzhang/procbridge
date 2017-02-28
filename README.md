# 1. Introduction

**ProcBridge** is a lightweight **socket-based** IPC (*Inter-Process Communication*) protocol, and it uses UTF-8 **JSON** text to encodes requests and responses. Currently we have **Java** and **Python** implementations for ProcBridge.

# 2. Protocol Design

![Protocol Design](https://github.com/gongzhang/proc-bridge/blob/master/Resources/Protocol.png)

- **FLAG** (2 bytes): two fixed ASCII charactors `'p'` and `'b'` in lowercase
- **VERSION** (2 bytes): `0x1` and `0x0` indicate the major/minor version of the protocol
- **STATUS CODE** (1 bytes): a flag that indicates the body is a request or response
- **RESERVED BYTES** (2 bytes)
- **BODY LENGTH** (4 bytes): an unsigned little-endian integer
- **BODY**: an UTF-8 encoded JSON text, always an JSON object `{ ... }`.

# 3. Implementation (Java and Python)

# 4. Repo Content

# 5. Contacts
