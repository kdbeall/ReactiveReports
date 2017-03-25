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

package com.sysalto.report

import com.sysalto.report.ReportTypes._
import com.sysalto.report.reportTypes.{LineDashType, RCell, RColor, RText}

import scala.collection.mutable.ListBuffer


class RectangleDsl(report: Report) {

  private var x1, y1, x2, y2, dradius = 0.0f
  private var from: RColor = _
  private var to: RColor = _
  private var dcolor: Option[RColor] = None
  private var dFillColor: Option[RColor] = None

  /*
  start point of the rectangle
   */
  def from(x1: Float, y1: Float): RectangleDsl = {
    this.x1 = x1
    this.y1 = y1
    this
  }

  /*
  end point of the rectangle.
   */
  def to(x1: Float, y1: Float): RectangleDsl = {
    this.x2 = x1
    this.y2 = y1
    this
  }

  /*
  radius
   */
  def radius(radius: Float): RectangleDsl = {
    this.dradius = radius
    this
  }

  /*
  set border color
 */
  def color(color: RColor): RectangleDsl = {
    this.dcolor = Some(color)
    this
  }

  /*
set fillColor color
*/
  def fillColor(color: RColor): RectangleDsl = {
    this.dFillColor = Some(color)
    this
  }

  /*
  fill with vertical shade
   */
  def verticalShade(from: RColor, to: RColor): RectangleDsl = {
    this.from = from
    this.to = to
    this
  }

  /*
  draw rectangle
   */
  def draw(): Unit = {
    val vrectangle = DRectangle(x1, y1, x2, y2, dradius)
    if (from != null) {
      report.verticalShade(vrectangle, from, to)
    } else {
      report.drawRectangle(x1, y1, x2, y2, dradius, dcolor, dFillColor)
    }
  }

}


class TextDsl(report: Report, var rText: RText) {

  def at(x: Float): Unit = {
    report.text(this.rText, x)
    this.rText = null
  }
}

class LineDsl(report: Report) {
  private var fromX, fromY = 0f
  private var toX, toY = 0f
  private var lcolor: RColor = RColor(0, 0, 0)
  private var lineDashType: Option[LineDashType] = None
  private var lineWidth = 1f

  /*
  start point of the line
   */
  def from(x: Float, y: Float): LineDsl = {
    fromX = x
    fromY = y
    this
  }

  /*
  end point of the line
   */
  def to(x: Float, y: Float = -1f): LineDsl = {
    toX = x
    toY = if (y == -1) fromY else y
    this
  }

  /*
  color of the line
   */
  def color(r: Int, g: Int, b: Int): LineDsl = {
    lcolor = RColor(r, g, b)
    this
  }

  /*
  line's type
   */
  def lineType(lineDashType: LineDashType): LineDsl = {
    this.lineDashType = Some(lineDashType)
    this
  }

  /*
  line's width. Default -1.
   */
  def width(lineWidth: Float): LineDsl = {
    this.lineWidth = lineWidth
    this
  }

  /*
  draws the line.
   */
  def draw(): Unit = {
    report.line(fromX, fromY, toX, toY, lineWidth, lcolor, lineDashType)
  }

}



