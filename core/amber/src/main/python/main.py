import sys
import time

import pandas
import random
from overrides import overrides
from typing import Iterator, Union

from core import Tuple
from core.data_processor import DataProcessor
from core.models.tuple import InputExhausted
from core.udf import UDFOperator
from edu.uci.ics.amber.engine.common import LinkIdentity


class EchoOperator(UDFOperator):
    @overrides
    def process_texera_tuple(self, tuple_: Union[Tuple, InputExhausted], input_: LinkIdentity) -> Iterator[Tuple]:
        if isinstance(tuple_, Tuple):
            # time.sleep(0.1)
            yield tuple_


class TrainOperator(UDFOperator):
    def __init__(self):
        super(TrainOperator, self).__init__()
        self.records = list()

    def train(self) -> "model":
        return {"predict": True, "f1-score": random.random()}

    @overrides
    def process_texera_tuple(self, tuple_: Union[Tuple, InputExhausted], input_: LinkIdentity) -> Iterator[Tuple]:
        if isinstance(tuple_, Tuple):
            self.records.append(tuple_)
            time.sleep(0.01)
        elif isinstance(tuple_, InputExhausted):
            model = self.train()
            yield pandas.concat([self.records[-1], pandas.Series(model)])


if __name__ == '__main__':
    data_processor = DataProcessor(host="localhost", input_port=int(sys.argv[1]), output_port=int(sys.argv[2]),
                                   udf_operator=EchoOperator())
    data_processor.start()
    data_processor.join()
