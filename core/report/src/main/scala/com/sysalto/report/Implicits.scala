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

import com.sysalto.report.util.{GroupUtilTrait, ReportColumnUtil, ResultSetStreamUtil}

object Implicits extends ScalaReportUtil with GroupUtilTrait with ResultSetStreamUtil {
  val RText = com.sysalto.report.ReportTypes.RText
  val LineDashType = com.sysalto.report.ReportTypes.LineDashType
  val RMargin = ReportColumnUtil.RMargin
  val Row = ReportColumnUtil.Row
  val Column = ReportColumnUtil.Column
  val Flex = ReportColumnUtil.Flex
  val RCell = com.sysalto.report.RCell
  val Report = com.sysalto.report.Report
  type Report = com.sysalto.report.Report
  val RRow = com.sysalto.report.ReportTypes.RRow
  val ReportCheckpoint = com.sysalto.report.ReportTypes.ReportCheckpoint
  type ReportCheckpoint = com.sysalto.report.ReportTypes.ReportCheckpoint
  val ReportCut = com.sysalto.report.ReportTypes.ReportCut
  type ReportItem = com.sysalto.report.ReportTypes.ReportItem
  val RColor = com.sysalto.report.ReportTypes.RColor

  val Source = akka.stream.scaladsl.Source
  val Sink = akka.stream.scaladsl.Sink
  val Await = scala.concurrent.Await
  val Duration = scala.concurrent.duration.Duration

}
