/*
 *  This file is part of the ReactiveReports project.
 *  Copyright (c) 2017 Sysalto Corporation.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3
 * as published by the Free Software Foundation with the addition of the
 * following permission added to Section 15 as permitted in Section 7(a):
 * FOR ANY PART OF THE COVERED WORK IN WHICH THE COPYRIGHT IS OWNED BY
 * Sysalto. Sysalto DISCLAIMS THE WARRANTY OF NON INFRINGEMENT
 * OF THIRD PARTY RIGHTS.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public License
 * along with this program; if not, see https://www.gnu.org/licenses/agpl-3.0.en.html.
 */

package com.sysalto.report.util

import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.stream.scaladsl.{Flow, Source}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.NotUsed

trait GroupUtilTrait {

  implicit class SourceGroup[T](s: Source[T, NotUsed]) {
    def group: Source[(Option[T], Option[T], Option[T]), NotUsed] = s.via(new GroupTransform)
  }

  implicit class FlowGroup[T](f: Flow[T, T, NotUsed]) {
    def group: Flow[T, (Option[T], Option[T], Option[T]), NotUsed] = f.via(new GroupTransform)
  }

  case class Group[T](name: String, get: (T) => Any)

  class GroupTransform[T] extends GraphStage[FlowShape[T, (Option[T], Option[T], Option[T])]] {
    private val in = Inlet[T]("GroupTransform.in")
    private val out = Outlet[(Option[T], Option[T], Option[T])]("GroupTransform.out")
    override val shape: FlowShape[T, (Option[T], Option[T], Option[T])] = FlowShape.of(in, out)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
      private var prev: Option[T] = None
      private var next: Option[T] = None
      private var crt: Option[T] = None
      setHandler(out, new OutHandler {
        override def onPull(): Unit = {
          pull(in)
        }
      })
      setHandler(in, new InHandler {
        override def onPush(): Unit = {
          val elem = grab(in)
          if (crt.isEmpty) {
            crt = Some(elem)
            pull(in)
          } else {
            if (next.isEmpty) {
              next = Some(elem)
              push(out, (prev, crt, next))
              prev = crt
              crt = next
              next = None
            }
          }
        }

        override def onUpstreamFinish(): Unit = {
          emit(out, (prev, crt, next))
          completeStage()
        }
      })
    }
  }

  class GroupUtil[T](groupList: List[Group[T]])
                    (implicit tag1: reflect.ClassTag[T]) {

    @annotation.tailrec
    final def isHeader(name: String, rec: (Option[T], Option[T], Option[T])): Boolean = {
      rec match {
        case (prev: Option[T], crt: Option[T], _: Option[T]) =>
          if (prev.isEmpty) {
            return true
          }
          val findOpt = groupList.zipWithIndex.find { case (item1, _) => item1.name == name }
          if (findOpt.isEmpty) {
            throw new Exception(s"the group $name not found")
          }
          val (item, idx) = findOpt.get
          val fct = item.get
          if (idx == 0) {
            fct(prev.get) != fct(crt.get)
          } else {
            val result = fct(prev.get) != fct(crt.get)
            if (result) {
              result
            } else {
              isHeader(groupList(idx - 1).name, rec)
            }
          }
      }
    }

    @annotation.tailrec
    final def isFooter(name: String, rec: (Option[T], Option[T], Option[T])): Boolean = {
      rec match {
        case (_: Option[T], crt: Option[T], next: Option[T]) =>
          if (next.isEmpty) {
            return true
          }
          val findOpt = groupList.zipWithIndex.find { case (item1, _) => item1.name == name }
          if (findOpt.isEmpty) {
            throw new Exception(s"the group $name not found")
          }
          val (item, idx) = findOpt.get
          val fct = item.get
          if (idx == 0) {
            fct(next.get) != fct(crt.get)
          } else {
            val result = fct(next.get) != fct(crt.get)
            if (result) {
              result
            } else {
              isFooter(groupList(idx - 1).name, rec)
            }
          }
      }
    }
  }

  def getRec[T](rec: (Option[T], Option[T], Option[T])): T = {
    rec match {
      case (_: Option[T], crt: Option[T], _: Option[T]) =>
        crt.get
    }
  }

  def isFirstRecord[T](rec: (Option[T], Option[T], Option[T])): Boolean = {
    rec._1.isEmpty
  }

  def isLastRecord[T](rec: (Option[T], Option[T], Option[T])): Boolean = {
    rec._3.isEmpty
  }
}


object GroupUtil extends GroupUtilTrait