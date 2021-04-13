package edu.uci.ics.amber.engine.recovery

import edu.uci.ics.amber.engine.architecture.messaginglayer.NetworkInputPort
import edu.uci.ics.amber.engine.common.ambermessage.{ControlPayload, WorkflowControlMessage}

class ControlLogManager(
    logStorage: LogStorage[WorkflowControlMessage],
    controlInputPort: NetworkInputPort[ControlPayload]
) extends LogManager(logStorage) {

  // For recovery, only need to replay control messages, and then it's done
  logStorage.load().foreach { msg =>
    controlInputPort.handleAfterFIFO(msg.from, msg.sequenceNumber, msg.payload)
  }
  setRecoveryCompleted()

  def persistControlMessage(msg: WorkflowControlMessage): Unit = {
    logStorage.persistElement(msg)
  }

}
