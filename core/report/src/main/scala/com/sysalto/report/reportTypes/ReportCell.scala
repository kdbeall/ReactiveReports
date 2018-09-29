/*
 * ReactiveReports - Free Java /Scala Reporting Library.
 * Copyright (C) 2017 SysAlto Corporation. All rights reserved.
 *
 * This program is part of ReactiveReports.
 *
 * ReactiveReports is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ReactiveReports is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY. Without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with ReactiveReports.
 * If not, see https://www.gnu.org/licenses/lgpl-3.0.en.html.
 */


package com.sysalto.report.reportTypes

import com.sysalto.report.ReportTypes.{BoundaryRect, WrapBox}
import com.sysalto.report.{Report, WrapAlign}


/*
class for wrapping text
 */
case class ReportCell(txt: List[ReportTxt], var margin: ReportMargin = ReportMargin(0, 0), var align: WrapAlign.Value = WrapAlign.NO_WRAP) {

	def this(txt: List[ReportTxt]) = {
		this(txt, ReportMargin(0, 0), WrapAlign.NO_WRAP)
	}

	def this(rtext: ReportTxt) = {
		this(List(rtext))
	}

	def this(rtext: ReportTxt, left: Float, right: Float) = {
		this(List(rtext), ReportMargin(left, right), WrapAlign.NO_WRAP)
	}

	/*
	align left
	 */
	def leftAlign(): ReportCell = {
		align = WrapAlign.WRAP_LEFT
		this
	}

	/*
	align center
	 */
	def centerAlign(): ReportCell = {
		align = WrapAlign.WRAP_CENTER
		this
	}

	/*
	align right
	 */
	def rightAlign(): ReportCell = {
		align = WrapAlign.WRAP_RIGHT
		this
	}

	/*
	define boundaries
	 */

	def inside(row:ReportRow,name:String): ReportCell = {
		this.margin = row.getColumnBound(name)
		this
	}

	def inside(margin: ReportMargin): ReportCell = {
		this.margin = margin
		this
	}

	def inside(left: Float, right: Float): ReportCell = {
		this.margin = ReportMargin(left, right)
		this
	}


	/*
	define only left boundary
	 */
	def at(x: Float): ReportCell = {
		margin = ReportMargin(x, Float.MaxValue)
		this
	}

	def calculate(report: Report): WrapBox = report.wrap(txt, margin.left, report.getY, margin.right, Float.MaxValue, WrapAlign.WRAP_LEFT, simulate = true).get


	def getBoundaryRect(report: Report): BoundaryRect = {
		val box = calculate(report)
		new BoundaryRect(margin.left, report.getYPosition(box.currentY)+report.lineHeight, margin.right, report.getYPosition(box.initialY))
	}
}

object ReportCell {
	def apply(rtext: ReportTxt): ReportCell = ReportCell(List(rtext))

	def apply(list: RTextList): ReportCell = ReportCell(list.list.toList)
}

