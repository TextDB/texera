import pytest
from pyarrow.flight import Action

from .common import serialize_arguments
from .rpc_server import RPCServer


class TestRPCServer:
    @pytest.fixture()
    def server(self):
        return RPCServer()

    def test_server_can_register_control_actions_with_lambda(self, server):
        with server:
            assert "hello" not in server._procedures
            server.register("hello", lambda: None)
            assert "hello" in server._procedures

    def test_server_can_register_control_actions_with_function(self, server):
        def hello():
            return None

        with server:
            assert "hello" not in server._procedures
            server.register("hello", hello)
            assert "hello" in server._procedures

    def test_server_can_register_control_actions_with_callable_class(self, server):
        class Hello:
            def __call__(self, ):
                return None

        with server:
            assert "hello" not in server._procedures
            server.register("hello", Hello())
            assert "hello" in server._procedures

    def test_server_can_invoke_registered_control_actions(self, server):
        procedure_contents = {
            "hello": "hello world",
            "get an int": 12,
            "get a float": 1.23,
            "get a tuple": (5, None, 123.4),
            "get a list": [5, (None, 123.4)],
            "get a dict": {"entry": [5, (None, 123.4)]}
        }
        with server:
            for name, result in procedure_contents.items():
                server.register(name, lambda: result)
                assert name in server._procedures
                assert next(server.do_action(None, Action(name, b''))).body.to_pybytes() \
                       == str(result).encode('utf-8')

    def test_server_can_invoke_registered_control_actions_with_args(self, server):
        with server:
            name = "echo"
            result = b"hello"
            serialized_args = serialize_arguments("hello")
            server.register(name, lambda x: x)
            assert name in server._procedures
            assert next(server.do_action(None, Action(name, serialized_args))).body.to_pybytes() \
                   == result

    def test_server_can_invoke_registered_control_actions_with_args2(self, server):
        with server:
            name = "add"
            result = b"3"
            serialized_args = serialize_arguments(1, 2)
            server.register(name, lambda a, b: a + b)
            assert name in server._procedures
            assert next(server.do_action(None, Action(name, serialized_args))).body.to_pybytes() \
                   == result

    def test_server_can_invoke_registered_control_actions_with_args_exception(self, server):
        with server:
            name = "div"
            serialized_args = serialize_arguments(1, 0)
            server.register(name, lambda a, b: a / b)
            assert name in server._procedures
            with pytest.raises(ZeroDivisionError):
                next(server.do_action(None, Action(name, serialized_args))).body.to_pybytes()

    def test_server_can_invoke_registered_lambda_with_args_and_ack(self, server):
        with server:
            name = "i need an ack"
            serialized_args = serialize_arguments("some input for lambda")

            server.register(name, RPCServer.ack()(lambda _: "random output"))
            assert name in server._procedures
            assert next(server.do_action(None, Action(name, serialized_args))).body.to_pybytes() \
                   == b'ack'

    def test_server_can_invoke_registered_function_with_args_and_ack(self, server):
        with server:
            name = "i need an ack"
            serialized_args = serialize_arguments("some input for function")

            @RPCServer.ack
            def handler(_):
                return "random output"

            server.register(name, handler)
            assert name in server._procedures
            assert next(server.do_action(None, Action(name, serialized_args))).body.to_pybytes() \
                   == b'ack'

    def test_server_can_invoke_registered_callable_class_with_args_and_ack(self, server):
        with server:
            name = "i need an ack"
            serialized_args = serialize_arguments("some input for callable class")

            class Handler:
                @RPCServer.ack
                def __call__(self, _):
                    return "random output"

            server.register(name, Handler())
            assert name in server._procedures
            assert next(server.do_action(None, Action(name, serialized_args))).body.to_pybytes() \
                   == b'ack'
