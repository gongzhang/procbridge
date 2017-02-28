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

# 3. Usage

With ProcBridge, clients can send high-level requests to server. Each request has two parts: an **API name** and an optional **JSON body**. The **API name** is any non-empty string that defined on server-side. The **JSON body** can by any valid JSON. The server will handle the request and send response back to clients, which is also an arbitrary JSON object.

In the following examples, the server defines two APIs. One is `echo`, which directly send back the JSON object sent from client. The other is `add`, which sums up a group of integers and send the result back to client.

## 3.1 Python Example

- Server
```python
host = '127.0.0.1'
port = 8077

def request_handler(api: str, arg: dict) -> dict:
    if api == 'echo':
        return arg
    elif api == 'add':
        return {'result': sum(x for x in arg['elements'])}
    else:
        raise Exception('unknown api')

server = ProcBridgeServer(host, port, request_handler)
server.start()
```

- Client
```python
host = '127.0.0.1'
port = 8077

client = ProcBridge(host, port)

print(client.request('echo', {}))  # prints "{}"

print(client.request('add', {      # prints "{result: 15}"
    'elements': [1, 2, 3, 4, 5]
}))
```

## 3.2 Java Example

- Server
```java
int port = 8877;
long timeout = 10000; // 10 seconds

ProcBridgeServer server = new ProcBridgeServer(port, timeout, new Object() {

    @APIHandler JSONObject echo(JSONObject arg) {
        return arg;
    }
    
    @APIHandler JSONObject add(JSONObject arg) {
        JSONArray elements = arg.getJSONArray("elements");
        int sum = 0;
        for (int i = 0; i < elements.length(); i++) {
            sum += elements.getInt(i);
        }
        JSONObject result = new JSONObject();
        result.put("result", sum);
        return result;
    }
    
});

server.start();
```

- Client
```java
String host = "127.0.0.1";
int port = 8877;
long timeout = 10000; // 10 seconds

ProcBridge pb = new ProcBridge(host, port, timeout);
JSONObject resp;

resp = pb.request("echo", "{}");
System.out.println(resp); // prints "{}"

resp = pb.request("add", "{elements: [1, 2, 3, 4, 5]}");
System.out.println(resp); // prints "{result: 15}"
```

# 4. Repository Content

- `Java/ProcBridge`: Java implementation and examples, can be imported in IntelliJ IDEA.
- `Python/ProcBridge`: Python implementation and examples, can be imported in PyCharm.

# 5. Contacts

Feel free to open issues or contact me:

- Gong Zhang (gong@me.com)

