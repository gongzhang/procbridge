from procbridge import ProcBridge


host = '127.0.0.1'
port = 8077

client = ProcBridge(host, port)

print(client.request('echo', {}))

print(client.request('add', {
    'elements': [1, 2, 3, 4, 5]
}))
