import pytest

from worker import Tuple
from worker.models.control_payload import ControlPayload
from worker.models.generated.virtualidentity_pb2 import LinkIdentity, ActorVirtualIdentity
from worker.models.internal_queue import InternalQueue, InputTuple, ControlElement, SenderChangeMarker, EndMarker, EndOfAllMarker
from worker.models.tuple import InputExhausted
from worker.util.stable_priority_queue import PrioritizedItem


class TestInternalQueue:
    @pytest.fixture
    def internal_queue(self):
        return InternalQueue()

    def test_internal_queue_cannot_put_non_internal_queue_element(self, internal_queue):
        with pytest.raises(TypeError):
            internal_queue.put(1)

        with pytest.raises(TypeError):
            internal_queue.put(PrioritizedItem((1, 1), InputTuple(Tuple())))

        with pytest.raises(TypeError):
            internal_queue.put(Tuple())

        with pytest.raises(TypeError):
            internal_queue.put(None)

        with pytest.raises(TypeError):
            internal_queue.put(InputExhausted())

    def test_internal_queue_can_put_internal_queue_element(self, internal_queue):
        internal_queue.put(InputTuple(Tuple()))
        internal_queue.put(ControlElement(ControlPayload(), ActorVirtualIdentity()))
        internal_queue.put(SenderChangeMarker(LinkIdentity()))
        internal_queue.put(EndMarker())
        internal_queue.put(EndOfAllMarker())

    def test_internal_queue_should_emit_control_first(self, internal_queue):

        elements = [InputTuple(Tuple()), SenderChangeMarker(LinkIdentity()),
                    EndMarker(), EndOfAllMarker()]
        for element in elements:
            internal_queue.put(element)

        # enqueue a ControlElement:
        control = ControlElement(ControlPayload(), ActorVirtualIdentity())
        internal_queue.put(control)

        assert internal_queue.get() == control

    def test_internal_queue_should_emit_stable_result(self, internal_queue):
        elements1 = [InputTuple(Tuple()), SenderChangeMarker(LinkIdentity()),
                     EndMarker(), EndOfAllMarker()]
        for element in elements1:
            internal_queue.put(element)

        # enqueue a ControlElement:
        control1 = ControlElement(ControlPayload(), ActorVirtualIdentity())
        internal_queue.put(control1)

        elements2 = [EndOfAllMarker(), InputTuple(Tuple()), InputTuple(Tuple()),
                     InputTuple(Tuple()), EndMarker(), InputTuple(Tuple()),
                     InputTuple(Tuple()), SenderChangeMarker(LinkIdentity())]
        for element in elements2:
            internal_queue.put(element)

        # enqueue a ControlElement:
        control2 = ControlElement(ControlPayload(), ActorVirtualIdentity())
        internal_queue.put(control2)

        assert internal_queue.get() == control1
        assert internal_queue.get() == control2

        for element in elements1 + elements2:
            assert internal_queue.get() == element
