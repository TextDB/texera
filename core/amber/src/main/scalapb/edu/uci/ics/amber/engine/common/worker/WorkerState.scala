// Generated by the Scala Plugin for the Protocol Buffer Compiler.
// Do not edit!
//
// Protofile syntax: PROTO3

package edu.uci.ics.amber.engine.common.worker

sealed abstract class WorkerState(val value: _root_.scala.Int) extends _root_.scalapb.GeneratedEnum {
  type EnumType = WorkerState
  def isUninitialized: _root_.scala.Boolean = false
  def isReady: _root_.scala.Boolean = false
  def isRunning: _root_.scala.Boolean = false
  def isPaused: _root_.scala.Boolean = false
  def isCompleted: _root_.scala.Boolean = false
  def companion: _root_.scalapb.GeneratedEnumCompanion[WorkerState] = edu.uci.ics.amber.engine.common.worker.WorkerState
  final def asRecognized: _root_.scala.Option[edu.uci.ics.amber.engine.common.worker.WorkerState.Recognized] = if (isUnrecognized) _root_.scala.None else _root_.scala.Some(this.asInstanceOf[edu.uci.ics.amber.engine.common.worker.WorkerState.Recognized])
}

object WorkerState extends _root_.scalapb.GeneratedEnumCompanion[WorkerState] {
  sealed trait Recognized extends WorkerState
  implicit def enumCompanion: _root_.scalapb.GeneratedEnumCompanion[WorkerState] = this
  @SerialVersionUID(0L)
  case object Uninitialized extends WorkerState(0) with WorkerState.Recognized {
    val index = 0
    val name = "Uninitialized"
    override def isUninitialized: _root_.scala.Boolean = true
  }
  
  @SerialVersionUID(0L)
  case object Ready extends WorkerState(1) with WorkerState.Recognized {
    val index = 1
    val name = "Ready"
    override def isReady: _root_.scala.Boolean = true
  }
  
  @SerialVersionUID(0L)
  case object Running extends WorkerState(2) with WorkerState.Recognized {
    val index = 2
    val name = "Running"
    override def isRunning: _root_.scala.Boolean = true
  }
  
  @SerialVersionUID(0L)
  case object Paused extends WorkerState(3) with WorkerState.Recognized {
    val index = 3
    val name = "Paused"
    override def isPaused: _root_.scala.Boolean = true
  }
  
  @SerialVersionUID(0L)
  case object Completed extends WorkerState(4) with WorkerState.Recognized {
    val index = 4
    val name = "Completed"
    override def isCompleted: _root_.scala.Boolean = true
  }
  
  @SerialVersionUID(0L)
  final case class Unrecognized(unrecognizedValue: _root_.scala.Int) extends WorkerState(unrecognizedValue) with _root_.scalapb.UnrecognizedEnum
  
  lazy val values = scala.collection.immutable.Seq(Uninitialized, Ready, Running, Paused, Completed)
  def fromValue(__value: _root_.scala.Int): WorkerState = __value match {
    case 0 => Uninitialized
    case 1 => Ready
    case 2 => Running
    case 3 => Paused
    case 4 => Completed
    case __other => Unrecognized(__other)
  }
  def javaDescriptor: _root_.com.google.protobuf.Descriptors.EnumDescriptor = WorkerProto.javaDescriptor.getEnumTypes().get(0)
  def scalaDescriptor: _root_.scalapb.descriptors.EnumDescriptor = WorkerProto.scalaDescriptor.enums(0)
}