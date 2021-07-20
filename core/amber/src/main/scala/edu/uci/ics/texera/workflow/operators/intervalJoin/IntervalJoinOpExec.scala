package edu.uci.ics.texera.workflow.operators.intervalJoin

import java.sql.Timestamp
import edu.uci.ics.amber.engine.common.InputExhausted
import edu.uci.ics.amber.engine.common.amberexception.WorkflowRuntimeException
import edu.uci.ics.amber.engine.common.virtualidentity.LinkIdentity
import edu.uci.ics.amber.error.WorkflowRuntimeError
import edu.uci.ics.texera.workflow.common.operators.OperatorExecutor
import edu.uci.ics.texera.workflow.common.tuple.Tuple
import edu.uci.ics.texera.workflow.common.tuple.schema.{
  Attribute,
  AttributeType,
  OperatorSchemaInfo,
  Schema
}

import scala.collection.mutable.ListBuffer

/** This Operator have two assumptions:
  * 1. The tuples in both inputs come in ascending order
  * 2. The left input join key takes as points, join condition is: left key in the range of (right key, right key + constant)
  */
class IntervalJoinOpExec(
    val operatorSchemaInfo: OperatorSchemaInfo,
    val desc: IntervalJoinOpDesc
) extends OperatorExecutor {

  var leftTable: ListBuffer[Tuple] = new ListBuffer[Tuple]()
  var rightTable: ListBuffer[Tuple] = new ListBuffer[Tuple]()
  val leftTableSchema: Schema = operatorSchemaInfo.inputSchemas(0)
  val rightTableSchema: Schema = operatorSchemaInfo.inputSchemas(1)

  //if right table has tuple smaller than smallest tuple in left table, delete it
  private def removeTooSmallTupleInRightCache(leftTableSmallestTuple: Tuple): Unit = {
    while (rightTable.nonEmpty) {
      if (
        intervalCompare(
          leftTableSmallestTuple.getField(desc.leftAttributeName),
          rightTable.head.getField(desc.rightAttributeName),
          leftTableSchema
            .getAttribute(desc.leftAttributeName)
            .getType
        ) > 0
      ) {
        rightTable.remove(0)
      } else {
        return
      }
    }

  }

  //if left table has tuple smaller than smallest tuple in right table, delete it
  private def removeTooSmallTupleInLeftCache(rightTableSmallestTuple: Tuple): Unit = {
    while (leftTable.nonEmpty) {
      if (
        intervalCompare(
          leftTable.head.getField(desc.leftAttributeName),
          rightTableSmallestTuple.getField(desc.rightAttributeName),
          leftTableSchema
            .getAttribute(desc.leftAttributeName)
            .getType
        ) < 0
      ) {
        leftTable.remove(0)
      } else {
        return
      }
    }

  }

  override def processTexeraTuple(
      tuple: Either[Tuple, InputExhausted],
      input: LinkIdentity
  ): Iterator[Tuple] = {
    tuple match {
      case Left(currentTuple) =>
        if (input == desc.leftInputLink) {
          leftTable += currentTuple
          if (rightTable.nonEmpty) {
            removeTooSmallTupleInRightCache(leftTable.head)
            rightTable
              .filter(rightTableTuple => {
                intervalCompare(
                  currentTuple.getField(desc.leftAttributeName),
                  rightTableTuple.getField(desc.rightAttributeName),
                  leftTableSchema
                    .getAttribute(desc.leftAttributeName)
                    .getType
                ) == 0
              })
              .map(rightTuple => joinTuples(currentTuple, rightTuple))
              .toIterator
          } else {
            Iterator()
          }
        } else {
          rightTable += currentTuple
          if (leftTable.nonEmpty) {
            removeTooSmallTupleInLeftCache(rightTable.head)
            leftTable
              .filter(leftTableTuple => {
                intervalCompare(
                  leftTableTuple.getField(desc.leftAttributeName),
                  currentTuple.getField(desc.rightAttributeName),
                  leftTableSchema
                    .getAttribute(desc.leftAttributeName)
                    .getType
                ) == 0
              })
              .map(leftTuple => joinTuples(leftTuple, currentTuple))
              .toIterator
          } else {
            Iterator()
          }
        }
      case Right(_) =>
        Iterator()
    }
  }

  private def joinTuples(leftTuple: Tuple, rightTuple: Tuple): Tuple = {
    val builder = Tuple
      .newBuilder(operatorSchemaInfo.outputSchema)
      .add(leftTuple)
    for (i <- 0 until rightTuple.getFields.size()) {
      val attributeName = rightTuple.getSchema.getAttributeNames.get(i)
      val attribute = rightTuple.getSchema.getAttribute(attributeName)
      builder.add(
        new Attribute(
          if (leftTableSchema.getAttributeNames.contains(attributeName))
            attributeName + "#@1"
          else attributeName,
          attribute.getType
        ),
        rightTuple.getFields.get(i)
      )
    }
    builder.build()
  }

  private def processNumValue[T](
      pointValue: T,
      leftBoundValue: T,
      rightBoundValue: T
  )(implicit ev$1: T => Ordered[T]): Int = {
    if (desc.includeLeftBound && desc.includeRightBound) {
      if (pointValue >= leftBoundValue && pointValue <= rightBoundValue) 0
      else if (pointValue < leftBoundValue) -1
      else 1
    } else if (desc.includeLeftBound && !desc.includeRightBound) {
      if (pointValue >= leftBoundValue && pointValue < rightBoundValue) 0
      else if (pointValue < leftBoundValue) -1
      else 1
    } else if (!desc.includeLeftBound && desc.includeRightBound) {
      if (pointValue > leftBoundValue && pointValue <= rightBoundValue) 0
      else if (pointValue <= leftBoundValue) -1
      else 1
    } else {
      if (pointValue > leftBoundValue && pointValue < rightBoundValue) 0
      else if (pointValue <= leftBoundValue) -1
      else 1
    }
  }

  private def intervalCompare[K](
      point: K,
      leftBound: K,
      dataType: AttributeType
  ): Int = {
    var result: Int = -2
    if (dataType == AttributeType.LONG) {
      val pointValue: Long = point.asInstanceOf[Long]
      val leftBoundValue: Long = leftBound.asInstanceOf[Long]
      val constantValue: Long = desc.constant
      val rightBoundValue: Long = leftBoundValue + constantValue
      result = processNumValue[Long](
        pointValue,
        leftBoundValue,
        rightBoundValue
      )

    } else if (dataType == AttributeType.DOUBLE) {
      val pointValue: Double = point.asInstanceOf[Double]
      val leftBoundValue: Double = leftBound.asInstanceOf[Double]
      val constantValue: Double = desc.constant.asInstanceOf[Double]
      val rightBoundValue: Double = leftBoundValue + constantValue
      result = processNumValue[Double](
        pointValue,
        leftBoundValue,
        rightBoundValue
      )
    } else if (dataType == AttributeType.INTEGER) {
      val pointValue: Int = point.asInstanceOf[Int]
      val leftBoundValue: Int = leftBound.asInstanceOf[Int]
      val constantValue: Int = desc.constant.asInstanceOf[Int]
      val rightBoundValue: Int = leftBoundValue + constantValue
      result = processNumValue[Int](
        pointValue,
        leftBoundValue,
        rightBoundValue
      )
    } else if (dataType == AttributeType.TIMESTAMP) {
      val pointValue: Timestamp = point.asInstanceOf[Timestamp]
      val leftBoundValue: Timestamp = leftBound.asInstanceOf[Timestamp]
      val rightBoundValue: Timestamp =
        desc.timeIntervalType match {
          case Some(TimeIntervalType.YEAR) =>
            Timestamp.valueOf(leftBoundValue.toLocalDateTime.plusYears(desc.constant))
          case Some(TimeIntervalType.MONTH) =>
            Timestamp.valueOf(leftBoundValue.toLocalDateTime.plusMonths(desc.constant))
          case Some(TimeIntervalType.DAY) =>
            Timestamp.valueOf(leftBoundValue.toLocalDateTime.plusDays(desc.constant))
          case Some(TimeIntervalType.HOUR) =>
            Timestamp.valueOf(leftBoundValue.toLocalDateTime.plusHours(desc.constant))
          case Some(TimeIntervalType.MINUTE) =>
            Timestamp.valueOf(leftBoundValue.toLocalDateTime.plusMinutes(desc.constant))
          case Some(TimeIntervalType.SECOND) =>
            Timestamp.valueOf(leftBoundValue.toLocalDateTime.plusSeconds(desc.constant))
          case None =>
            Timestamp.valueOf(leftBoundValue.toLocalDateTime.plusDays(desc.constant))
        }
      result = processNumValue(
        pointValue.getTime,
        leftBoundValue.getTime,
        rightBoundValue.getTime
      )
    } else {
      val err = WorkflowRuntimeError(
        "The data type can not support comparation: " + dataType.toString,
        "HashJoinOpExec",
        Map("stacktrace" -> Thread.currentThread().getStackTrace.mkString("\n"))
      )
      throw new WorkflowRuntimeException(err)
    }
    result

  }

  override def open(): Unit = {}

  override def close(): Unit = {}
}
