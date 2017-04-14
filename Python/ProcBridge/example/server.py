import procbridge
# from procbridge import procbridge
import sys


if __name__ == '__main__':

    host = '127.0.0.1'
    port = 8077

    # define request handler
    def request_handler(api: str, arg: dict) -> dict:

        if api == 'echo':
            return arg

        elif api == 'add':
            return {'result': sum(x for x in arg['elements'])}

        else:
            raise Exception('unknown api')

    # start socket server
    server = procbridge.ProcBridgeServer(host, port, request_handler)
    server.start()
    print('listening...')

    try:
        for line in sys.stdin:
            if line.strip() == 'exit':
                break
    except KeyboardInterrupt:
        pass

    server.stop()
    print('bye!')
