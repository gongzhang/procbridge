# import procbridge
from procbridge import procbridge


host = '127.0.0.1'
port = 51456

client = procbridge.ProcBridge(host, port)

print(client.request('dialog', {'type': 'question', 'message': 'Do you know this is send from a Python script?'}))

# print(client.request('add', {
#     'elements': [1, 2, 3, 4, 5]
# }))
