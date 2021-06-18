from time import sleep

import pytest

from worker.models.internal_queue import InternalQueue
from worker.threads.network_receiver import NetworkReceiver


class TestNetworkReceiver:
    @pytest.mark.timeout(0.5)
    def test_network_receiver_can_stop(self):
        network_receiver = NetworkReceiver(InternalQueue(), host="localhost", port=5555)
        network_receiver.start()
        sleep(0.1)
        assert network_receiver.is_alive()
        network_receiver.stop()
        sleep(0.1)
        assert not network_receiver.is_alive()
        network_receiver.join()
