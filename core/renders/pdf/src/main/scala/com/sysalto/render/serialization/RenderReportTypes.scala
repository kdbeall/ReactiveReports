package com.sysalto.render.serialization

import java.awt.image.BufferedImage
import java.io.{ByteArrayOutputStream, File, FileOutputStream}
import java.net.URL
import java.nio.file.{Files, Paths}
import java.util.zip.Deflater

import com.sysalto.render.{PdfChart, PdfDraw}
import com.sysalto.render.PdfDraw.{DrawPoint, PdfGraphicFragment, roundRectangle}
import com.sysalto.render.basic.PdfBasic
import com.sysalto.render.basic.PdfBasic._
import com.sysalto.render.serialization.RenderProto.{PdfBaseItem_proto, PdfCatalog_proto}
import com.sysalto.render.util.PageTree.PageNode
import com.sysalto.render.util.fonts.parsers.FontParser.FontMetric
import com.sysalto.report.ReportTypes.BoundaryRect
import com.sysalto.report.reportTypes.{RFont, ReportColor, ReportTxt}
import com.sysalto.report.util.{PersistenceFactory, PersistenceUtil, RockDbUtil}
import javax.imageio.ImageIO

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ListBuffer


class RenderReportTypes(persistenceFactory: PersistenceFactory) {
	private[render] val ENCODING = "ISO-8859-1"
	//	private[this] val db = RockDbUtil()
	val persistenceUtil = persistenceFactory.open()
	private[this] val serializer = new RenderReportSerializer(this)

	private[render] abstract class PdfBaseItem(val id: Long) {
		var offset: Long = 0

		def content: Array[Byte]

		def write(pdfWriter: PdfWriter): Unit = {
			offset = pdfWriter.position
			setObject(this)
			pdfWriter << content
		}

		override def toString: String = {
			s"[${this.getClass.getTypeName}]\n" + content
		}

	}

	private[render] class PdfDests(id: Long, val dests: ListBuffer[(String, String)] = ListBuffer())
		extends PdfBaseItem(id) {
		override def content: Array[Byte] = {
			val head = dests.head
			s"""${id} 0 obj
				 |<</Names[(${head._1}) 2 0 R]>>
				 |endobj
				 |""".stripMargin.getBytes(ENCODING)
		}
	}


	private[render] class PdfDrawImage(val idPdfImage: Long, val x: Float, val y: Float, val scale: Float = 1, val opacity: Option[Float] = None)
		extends PdfGraphicFragment {
		private[this] val pdfImage = getObject[PdfImage](idPdfImage)
		private[this] val image = pdfImage.imageMeta
		private[this] val width = image.width * scale
		private[this] val height = image.height * scale
		private[this] val opacityStr = ""

		def content: String =
			s"""q
				 			 |$opacityStr
				 			 |$width 0 0 $height ${x} ${y} cm
				 			 |/${pdfImage.name} Do
				 			 | Q
    """.stripMargin

	}

	private[render] class PdfNames(id: Long, val idDest: Long)
		extends PdfBaseItem(id) {
		override def content: Array[Byte] = {
			s"""${id} 0 obj
				 |<</Dests ${idDest} 0 R>>
				 |endobj
				 |""".stripMargin.getBytes(ENCODING)
		}
	}


	class PdfFontStream(id: Long, val fontName: String, val fontMetric: FontMetric, val pdfCompression: Boolean)
		extends PdfBaseItem(id) {
		override def content: Array[Byte] = {
			val byteArray = Files.readAllBytes(Paths.get(fontName))
			val lg = byteArray.length
			s"""${id} 0 obj
				 			 | <</Length ${lg}/Length1 ${lg}>>stream
				 			 |""".stripMargin.getBytes(ENCODING) ++
				byteArray ++
				"\nendstream\nendobj\n".getBytes(ENCODING)
			writeData(id, byteArray, pdfCompression, true)
		}
	}


	class PdfFontDescriptor(id: Long, val idPdfFontStream: Long, val fontKeyName: String)
		extends PdfBaseItem(id) {
		override def content: Array[Byte] = {
			val pdfFontStream: PdfFontStream = getObject[PdfFontStream](idPdfFontStream)
			s"""${id} 0 obj
				 |    <</Type/FontDescriptor
				 |    /FontName/${fontKeyName}
				 |    /Flags ${pdfFontStream.fontMetric.fontDescriptor.get.flags}
				 |    /FontBBox[${pdfFontStream.fontMetric.fontDescriptor.get.fontBBox}]
				 |    /ItalicAngle ${pdfFontStream.fontMetric.fontDescriptor.get.italicAngle}
				 |    /Ascent ${pdfFontStream.fontMetric.fontDescriptor.get.ascent}
				 |    /Descent ${pdfFontStream.fontMetric.fontDescriptor.get.descent}
				 |    /CapHeight ${pdfFontStream.fontMetric.fontDescriptor.get.capHeight}
				 |    /StemV 0
				 |    /FontFile2 ${pdfFontStream.id} 0 R
				 |>>
				 |endobj
				 |""".stripMargin.getBytes(ENCODING)
		}
	}

	class FontEmbeddedDef(val idPdfFontDescriptor: Long, val idPdfFontStream: Long)


	class PdfFont(id: Long, val refName: String, val fontKeyName: String, val embeddedDefOpt: Option[FontEmbeddedDef] = None)
		extends PdfBaseItem(id) {
		override def content: Array[Byte] = {
			if (embeddedDefOpt.isEmpty) {
				s"""${id} 0 obj
					 |<<  /Type /Font
					 |/Subtype /Type1
					 |/BaseFont /${fontKeyName}
					 |/Encoding /WinAnsiEncoding
					 |>>
					 |endobj
					 |""".stripMargin.getBytes(ENCODING)
			} else {
				val fontEmbedeedDef = embeddedDefOpt.get
				val pdfFontStream = getObject[PdfFontStream](fontEmbedeedDef.idPdfFontStream)
				val pdfFontDescriptor = getObject[PdfFontDescriptor](fontEmbedeedDef.idPdfFontDescriptor)
				val withObj = pdfFontStream.fontMetric.fontDescriptor.get.glyphWidth
				val firstChar = withObj.firstChar
				val lastChar = withObj.lastChar
				s"""${id} 0 obj
					 | << /Type/Font
					 |   /Subtype/TrueType
					 |   /BaseFont/${fontKeyName}
					 |   /FirstChar ${firstChar}
					 |   /LastChar ${lastChar}
					 |   /Widths
					 |    [
					 |		 ${withObj.widthList.mkString(" ")}
					 |    ]
					 |   /FontDescriptor ${pdfFontDescriptor.id} 0 R
					 |   /Encoding/WinAnsiEncoding
					 				 |   >>
					 				 |endobj
					 				 |""".stripMargin.getBytes(ENCODING)
			}


		}
	}


	private[render] class PdfShaddingFctColor(id: Long, val color1: ReportColor, val color2: ReportColor)
		extends PdfBaseItem(id) {
		override def content: Array[Byte] = {
			val colorNbr1 = convertColor(color1)
			val colorNbr2 = convertColor(color2)

			s"""${id} 0 obj
				 			 |  <</FunctionType 2/Domain[0 1]/C0[${colorNbr1._1} ${colorNbr1._2} ${colorNbr1._3}]/C1[${colorNbr2._1} ${colorNbr2._2} ${colorNbr2._3}]/N 1>>
				 			 |endobj
				 			 |""".stripMargin.getBytes(ENCODING)
		}
	}

	private[render] class PdfColorShadding(id: Long, val x0: Float, val y0: Float, val x1: Float, val y1: Float, val idPdfShaddingFctColor: Long)
		extends PdfBaseItem(id) {
		override def content: Array[Byte] = {
			val pdfShaddingFctColor = getObject[PdfShaddingFctColor](idPdfShaddingFctColor)
			s"""${id} 0 obj
				 			 |  <</ShadingType 2/ColorSpace/DeviceRGB/Coords[$x0 $y0  $x1 $y1]/Function ${pdfShaddingFctColor.id} 0 R>>
				 			 |endobj
				 			 |""".stripMargin.getBytes(ENCODING)
		}
	}

	class PdfGPattern(id: Long, val idPdfShadding: Long) extends PdfBaseItem(id) {
		val name = "P" + id

		override def content: Array[Byte] = {
			s"""${id} 0 obj
				 			 |  <</PatternType 2/Shading ${idPdfShadding} 0 R/Matrix[1 0 0 1 0 0]>>
				 			 |endobj
				 			 |""".stripMargin.getBytes(ENCODING)
		}
	}

	class PdfRectangle(val x1: Long, val y1: Long, val x2: Long, val y2: Long, val radius: Float, val borderColor: Option[ReportColor],
	                   val fillColor: Option[ReportColor], val idPatternColor: Option[Long] = None) extends PdfGraphicFragment {
		override def content: String = {
			val patternColor = if (idPatternColor.isDefined) Some(getObject[PdfGPattern](idPatternColor.get)) else None
			val paternStr = if (patternColor.isDefined) {
				pattern(patternColor.get.name)
			} else ""
			val borderStr = if (borderColor.isDefined) border(borderColor.get) else ""
			val fillStr = if (fillColor.isDefined) fill(fillColor.get) else ""
			val operator = fillStroke(fillColor.isDefined || patternColor.isDefined, borderColor.isDefined)
			val rectangleStr = if (radius == 0) rectangle(x1, y1, x2 - x1, y2 - y1) else roundRectangle(x1, y1, x2, y2, radius)
			s"""${saveStatus}
				 |${paternStr}
				 |${borderStr}
				 |${fillStr}
				 |${rectangleStr}
				 | ${operator}
				 |${restoreStatus}
       """.stripMargin.trim
		}

	}


	abstract class PdfAnnotation(id: Long) extends PdfBaseItem(id)

	class ImageMeta(fileName: String) {
		val file = if (fileName.startsWith("http://") || fileName.startsWith("https://")) {
			val url = new URL(fileName)
			val img1 = ImageIO.read(url)
			val tempFile = File.createTempFile("rap", ".jpg")
			tempFile.deleteOnExit()
			ImageIO.write(img1, "jpg", tempFile)
			tempFile
		} else {
			new File(fileName)
		}
		val bimg: BufferedImage = ImageIO.read(file)
		val width: Int = bimg.getWidth()
		val height: Int = bimg.getHeight()
		val size: Long = file.length
		val baos = new ByteArrayOutputStream()
		ImageIO.write(bimg, "jpg", baos)
		baos.flush()
		val imageInByte: Array[Byte] = baos.toByteArray
		baos.close()
		val pixelSize: Int = bimg.getColorModel.getComponentSize(0)
	}

	class PdfImage(id: Long, val fileName: String) extends PdfBaseItem(id) {
		val name = "img" + id
		val imageMeta = new ImageMeta(fileName)


		override def content: Array[Byte] = {
			s"""${id} 0 obj
				 			 |  <<
				 			 | /Type /XObject
				 			 | /Subtype /Image
				 			 | /Width ${imageMeta.width}
				 			 | /Height ${imageMeta.height}
				 			 |  /ColorSpace /DeviceRGB
				 			 |  /BitsPerComponent ${imageMeta.pixelSize}
				 			 |  /Length ${imageMeta.imageInByte.length}
				 			 |  /Filter /DCTDecode
				 			 |  >>
				 			 |stream
				 			 |""".stripMargin.getBytes(ENCODING) ++
				imageMeta.imageInByte ++
				"\nendstream\nendobj\n".getBytes(ENCODING)
		}

	}

	private[render] abstract class PdfPageItem {
		def content: String
	}

	class PdfPageContent(id: Long, val pageItemList: List[PdfPageItem], val pdfCompression: Boolean)
		extends PdfBaseItem(id) {
		override def content: Array[Byte] = {
			val itemsStr = pageItemList.foldLeft("")((s1, s2) => s1 + "\n" + s2.content)
			writeData(id, itemsStr.getBytes(ENCODING), pdfCompression)
		}
	}

	class PdfPage(id: Long, var parentId: Long = 0, var pageWidth: Float, var pageHeight: Float,
	              var idFontList: List[Long] = List(), var idPdfPatternList: List[Long] = List(),
	              var idAnnotationList: List[Long] = List(),
	              var idImageList: mutable.Set[Long] = mutable.HashSet(), var idContentPageOpt: Option[Long] = None)
		extends PdfBaseItem(id) with PageNode {

		override def addChild(child: PageNode): Unit = {}

		override def content: Array[Byte] = {
			val contentStr = if (idContentPageOpt.isDefined) s"/Contents ${idContentPageOpt.get} 0 R" else ""
			val fontStr = "/Font<<" + idFontList.map(idFont => {
				val font = getObject[PdfFont](idFont)
				s"/${font.refName} ${font.id} 0 R"
			}).mkString("") + ">>"
			val patternStr = if (idPdfPatternList.isEmpty) "" else "/Pattern <<" +
				idPdfPatternList.map(idItem => {
					val item = getObject[PdfGPattern](idItem)
					s"/${item.name} ${item.id} 0 R"
				}).mkString(" ") + ">>"
			val imageStr = if (idImageList.isEmpty) "" else "/XObject <<" +
				idImageList.map(idItem => {
					val item = getObject[PdfImage](idItem)
					s"/${item.name} ${item.id} 0 R"
				}).mkString(" ") + ">>"
			val annotsStr = if (idAnnotationList.isEmpty) "" else "/Annots [" +
				idAnnotationList.map(idItem => {
					val item = getObject[PdfAnnotation](idItem)
					s"${item.id} 0 R"
				}).mkString(" ") + "]"
			val result =
				s"""${id} 0 obj
					 |<<  /Type /Page
					 |      /Parent ${parentId} 0 R
					 |      /MediaBox [ 0 0 ${pageWidth} ${pageHeight} ]
					 |      /TrimBox [ 0 0 ${pageWidth} ${pageHeight} ]
					 |      ${contentStr}
					 |      ${annotsStr}
					 |      /Resources
					 |        <<  ${fontStr}
					 |            ${patternStr}
					 |            ${imageStr}
					 |        >>
					 |>>
					 |endobj
					 |""".stripMargin
			result.replaceAll("(?m)^\\s+\\n", "").getBytes(ENCODING)
		}
	}


	private[render] class PdfPageList(id: Long, var parentId: Option[Long] = None, var pageList: ListBuffer[Long] = ListBuffer())
		extends PdfBaseItem(id) with PageNode {

		override def addChild(child: PageNode): Unit = {
			child match {
				case pdfPageList: PdfPageList => {
					pdfPageList.parentId = Some(this.id)
					pageList += pdfPageList.id
					leafNbr += child.leafNbr
					setObject(pdfPageList)
				}
				case pdfPage: PdfPage => {
					pageList += pdfPage.id
					pdfPage.parentId = id
					leafNbr += 1
					setObject(pdfPage)
				}
			}
			setObject(this)
		}

		override def content: Array[Byte] = {
			val parentIdStr = if (parentId.isDefined) s"/Parent ${parentId.get} 0 R" else ""
			val pageListStr = pageList.map(id => id + " 0 R").mkString("\n")
			s"""${id} 0 obj
				 			 |  <<  /Type /Pages ${parentIdStr}
				 			 |      /Kids [ ${pageListStr} ]
				 			 |      /Count ${leafNbr}
				 			 |  >>
				 			 |endobj
				 			 |""".stripMargin.getBytes(ENCODING)
		}
	}

	private[serialization] class PdfCatalog(id: Long, var idPdfPageListOpt: Option[Long] = None, var idPdfNamesOpt: Option[Long] = None)
		extends PdfBaseItem(id) {
		override def content: Array[Byte] = {
			val namesStr = if (idPdfNamesOpt.isEmpty) "" else s"/Names ${idPdfNamesOpt.get} 0 R"
			s"""${id} 0 obj
				 |<<  /Type /Catalog
				 |    /Pages ${idPdfPageListOpt.get} 0 R
				 |    ${namesStr}
				 |>>
				 |endobj
				 |""".stripMargin.getBytes(ENCODING)
		}
	}

	private[render] class PdfTxtFragment(val x: Float, val y: Float, val rtext: ReportTxt, val fontRefName: String,
	                                     val patternOpt: Option[PatternDraw] = None) extends Serializable

	private[serialization] class PdfText(val txtList: List[PdfTxtFragment])
		extends PdfPageItem {

		private[this] def escapeText(input: String): String = {
			val s1 = input.replace("\\", "\\\\")
			val s2 = s1.replace("(", "\\(")
			s2.replace(")", "\\)")
		}

		override def content: String = {
			if (txtList.isEmpty) {
				return ""
			}
			val txtListSimple = txtList.filter(txt => txt.patternOpt.isEmpty)
			val txtListPattern = txtList.filter(txt => txt.patternOpt.isDefined)
			val item = txtListSimple.head
			val color = convertColor(item.rtext.font.color)
			val firstItemTxt =
				s""" BT /${item.fontRefName} ${item.rtext.font.size} Tf
					 				 |  1 0 0 1 ${item.x.toLong} ${item.y.toLong} Tm
					 				 |  ${color._1} ${color._2} ${color._3} rg
					 				 |        (${escapeText(item.rtext.txt)}) Tj
       """.stripMargin

			val s2 = firstItemTxt + txtListSimple.tail.zipWithIndex.map {
				case (item, i) => {
					val color = convertColor(item.rtext.font.color)
					val xRel = txtListSimple(i + 1).x.toLong - txtListSimple(i).x.toLong
					val yRel = txtListSimple(i + 1).y.toLong - txtListSimple(i).y.toLong
					s"""  /${item.fontRefName} ${item.rtext.font.size} Tf
						 					 |  ${xRel} ${yRel} Td
						 					 |  ${color._1} ${color._2} ${color._3} rg
						 					 |  (${escapeText(item.rtext.txt)}) Tj
       """.stripMargin
				}
			}.mkString("")

			// pattern text
			val s3 = if (txtListPattern.isEmpty) ""
			else txtListPattern.map(txt => {
				val pattern = getObject[PdfGPattern](item.patternOpt.get.idPattern)
				s""" q
					 				 |/Pattern cs /${pattern.name} scn
					 				 |/${item.fontRefName} ${item.rtext.font.size} Tf
					 				 |  1 0 0 1 ${item.x.toLong} ${item.y.toLong} Tm
					 				 |  ${color._1} ${color._2} ${color._3} rg
					 				 |        (${escapeText(item.rtext.txt)}) Tj
					 				 |Q
       """.mkString("")
			})

			s"""${s2}
				 			 |${s3}
				 			 |      ET
       """.stripMargin
		}

	}

	private[render] class PatternDraw(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val idPattern: Long)


	abstract class PdfAction(id: Long) extends PdfBaseItem(id)


	class PdfGoToUrl(id: Long, val url: String) extends PdfAction(id) {
		override def content: Array[Byte] = {
			s"""${id} 0 obj
				 |<<
				 |  /Type /Action
				 |  /S /URI
				 |  /IsMap false
				 |  /URI(${url})
				 |>>
				 |endobj
				 |""".stripMargin.getBytes(ENCODING)
		}
	}


	private[render] class PdfLink(id: Long, val boundaryRect: BoundaryRect, val idAction: Long)
		extends PdfAnnotation(id) {
		override def content: Array[Byte] = {
			s"""${id} 0 obj
				 |  << /Type /Annot
				 |  /Subtype /Link
				 |  /Rect [${boundaryRect}]
				 |  /F 4
				 |  /Border [ 0 0 0 ]
				 |  /A ${idAction} 0 R
				 |>>
				 |endobj
				 |""".stripMargin.getBytes(ENCODING)
		}
	}

	class PdfGoToPage(id: Long, val pageNbr: Long, val left: Int, val top: Int)
		extends PdfAction(id) {
		override def content: Array[Byte] = {
			s"""${id} 0 obj
				 |<<
				 |  /Type /Action
				 |  /S /GoTo
				 |  /D [ ${pageNbr - 1} /Fit ]
				 |>>
				 |endobj
				 |""".stripMargin.getBytes(ENCODING)
		}
	}

	private[serialization] class PdfGraphic(val items: List[PdfGraphicFragment])
		extends PdfPageItem {
		override def content: String = {
			val str = items.map(item => {
				item.content
			}).foldLeft("")((s1, s2) => s1 + "\n" + s2)

			s"""q
				 			 |0 0 0 RG
				 			 |1 w
				 			 |${str}
				 			 |Q
 """.stripMargin
		}

	}



	class DirectDrawMovePoint(val x: Float, val y: Float) extends PdfGraphicFragment {
		override def content: String = {
			s"""${x} ${y} m \n"""
		}
	}

	class DirectDrawLine(val x: Float, val y: Float) extends PdfGraphicFragment {
		override def content: String = {
			s"""${x} ${y} l \n"""
		}
	}


	class DirectDrawCircle(val x: Float, val y: Float, val radius: Float) extends PdfGraphicFragment {
		override def content: String = PdfBasic.circle(new DrawPoint(x, y), radius)
	}

	class DirectDrawArc(val x: Float, val y: Float, val radius: Float, val startAngle: Float, val endAngle: Float) extends PdfGraphicFragment {
		override def content: String = {
			val p0 = new DrawPoint((x + radius * Math.cos(startAngle)).toFloat, (y + radius * Math.sin(startAngle)).toFloat)
			val lg = radius * 4 / 3.0 * Math.tan((endAngle - startAngle) * 0.25)
			val p1 = new DrawPoint((p0.x - lg * Math.sin(startAngle)).toFloat, (p0.y + lg * Math.cos(startAngle)).toFloat)
			val p3 = new DrawPoint((x + radius * Math.cos(endAngle)).toFloat, (y + radius * Math.sin(endAngle)).toFloat)
			val p2 = new DrawPoint((p3.x + lg * Math.sin(endAngle)).toFloat, (p3.y - lg * Math.cos(endAngle)).toFloat)
			s"""${p1.x} ${p1.y} ${p2.x} ${p2.y} ${p3.x} ${p3.y} c \n"""
		}
	}


	class DirectDrawStroke(val reportColor: ReportColor) extends PdfGraphicFragment {
		override def content: String = {
			val color = ReportColor.convertColor(reportColor)
			s"${color._1} ${color._2} ${color._3} RG\n"
		}
	}

	class DirectDrawFill(val reportColor: ReportColor) extends PdfGraphicFragment {
		override def content: String = {
			val color = ReportColor.convertColor(reportColor)
			s"${color._1} ${color._2} ${color._3} rg\n"
		}
	}

	class DirectDrawClosePath() extends PdfGraphicFragment {
		override def content: String = "h\n"
	}

	class DirectDraw(val code: String) extends PdfGraphicFragment {
		override def content: String = code
	}


	class DirectFillStroke(val fill: Boolean, val stroke: Boolean) extends PdfGraphicFragment {
		override def content: String = {
			(fill, stroke) match {
				case (true, true) => "B\n"
				case (true, false) => "f\n"
				case (false, true) => "S\n"
				case _ => ""
			}
		}
	}

	class DirectSaveStatus() extends PdfGraphicFragment {
		override def content: String = {
			"q\n"
		}
	}

	class DirectRestoreStatus() extends PdfGraphicFragment {
		override def content: String = {
			"Q\n"
		}
	}


	class DirectColorBorder(borderColor: ReportColor) extends PdfGraphicFragment {
		override def content: String = {
			val color = ReportColor.convertColor(borderColor)
			s"${color._1} ${color._2} ${color._3} RG\n"
		}
	}

	class DirectColorFill(fillColor: ReportColor) extends PdfGraphicFragment {
		override def content: String = {
			val color = ReportColor.convertColor(fillColor)
			s"${color._1} ${color._2} ${color._3} rg\n"
		}
	}

//	class DirectRectangle(x: Float, y: Float, width: Float, height: Float) extends PdfGraphicFragment {
//		override def content: String = {
//			s"""${x} ${y} ${width} ${height} re \n"""
//		}
//	}

	class DirectClosePath() extends PdfGraphicFragment {
		override def content: String = {
			"h\n"
		}
	}

	class DirectPattern(patternName: String) extends PdfGraphicFragment {
		override def content: String = {
			s"/Pattern cs /${patternName} scn"
		}
	}


	class DirectDrawRectangle(val x1: Float,val y1: Float, val x2: Float, val y2: Float) extends PdfGraphicFragment {
		override def content: String = {
			s"""${x1} ${y1} ${x2-x1} ${y2-y1} re \n"""
		}
	}


	private[serialization] class PdfWriter(name: String) {
		new File(name).delete()
		private[this] val writer = new FileOutputStream(name)
		private[render] var position: Long = 0

		def <<(str: String): Unit = {
			<<(str.getBytes(ENCODING))
		}

		def <<<(str: String): Unit = {
			val str1 = str + "\n"
			<<(str1.getBytes(ENCODING))
		}

		def <<(str: Array[Byte]): Unit = {
			writer.write(str)
			position += str.length
		}

		def close(): Unit = {
			writer.flush()
			writer.close()
		}
	}


	def writeData(id: Long, input: Array[Byte], pdfCompression: Boolean, hasLength1: Boolean = false): Array[Byte] = {
		val length1 = if (hasLength1) s"/Length1 ${input.size}" else ""
		val result = if (!pdfCompression) {
			s"""${id} 0 obj
				 |<</Length ${input.length} ${length1}>>
				 |stream
				 |""".stripMargin.getBytes(ENCODING) ++
				input ++
				s"""
					 |endstream
					 |endobj
					 |""".stripMargin.getBytes(ENCODING)
		} else {
			val compresser = new Deflater(Deflater.BEST_COMPRESSION)
			compresser.setInput(input)
			compresser.finish()
			val output = new Array[Byte](input.length)
			val compressedDataLength = compresser.deflate(output)
			compresser.end()
			val compressTxt = output.take(compressedDataLength)
			s"""${id} 0 obj
				 |<</Filter/FlateDecode/Length ${compressTxt.length} ${length1}>>
				 |stream
				 |""".stripMargin.getBytes(ENCODING) ++
				compressTxt ++
				s"""
					 |endstream
					 |endobj
					 |""".stripMargin.getBytes(ENCODING)
		}
		result
	}


	def setObject(obj: PdfBaseItem): Unit = {
		obj match {
			case pdfCatalog: PdfCatalog => {
				val cat1 = pdfCatalog.asInstanceOf[RenderReportTypes.this.serializer.renderReportTypes.PdfCatalog]
				val builder = serializer.PdfBaseItemSerializer.write(cat1)
				persistenceUtil.writeObject(pdfCatalog.id, builder.toByteArray)
			}
			case pdfPage: PdfPage => {
				val page = pdfPage.asInstanceOf[RenderReportTypes.this.serializer.renderReportTypes.PdfPage]
				val builder = serializer.PdfBaseItemSerializer.write(page)
				persistenceUtil.writeObject(pdfPage.id, builder.toByteArray)
			}
			case pdfFont: PdfFont => {
				val font = pdfFont.asInstanceOf[RenderReportTypes.this.serializer.renderReportTypes.PdfFont]
				val builder = serializer.PdfBaseItemSerializer.write(font)
				persistenceUtil.writeObject(pdfFont.id, builder.toByteArray)
			}
			case pdfPageContent: PdfPageContent => {
				val pdfPageContent1 = pdfPageContent.asInstanceOf[RenderReportTypes.this.serializer.renderReportTypes.PdfPageContent]
				val builder = serializer.PdfBaseItemSerializer.write(pdfPageContent1)
				persistenceUtil.writeObject(pdfPageContent1.id, builder.toByteArray)
			}
			case pdfPageList: PdfPageList => {
				val pdfPageList1 = pdfPageList.asInstanceOf[RenderReportTypes.this.serializer.renderReportTypes.PdfPageList]
				val builder = serializer.PdfBaseItemSerializer.write(pdfPageList1)
				persistenceUtil.writeObject(pdfPageList1.id, builder.toByteArray)
			}
			case pdfImage: PdfImage => {
				val pdfImage1 = pdfImage.asInstanceOf[RenderReportTypes.this.serializer.renderReportTypes.PdfImage]
				val builder = serializer.PdfBaseItemSerializer.write(pdfImage1)
				persistenceUtil.writeObject(pdfImage.id, builder.toByteArray)
			}
			case item: PdfColorShadding => {
				val item1 = item.asInstanceOf[RenderReportTypes.this.serializer.renderReportTypes.PdfColorShadding]
				val builder = serializer.PdfBaseItemSerializer.write(item1)
				persistenceUtil.writeObject(item.id, builder.toByteArray)
			}
			case item: PdfShaddingFctColor => {
				val item1 = item.asInstanceOf[RenderReportTypes.this.serializer.renderReportTypes.PdfShaddingFctColor]
				val builder = serializer.PdfBaseItemSerializer.write(item1)
				persistenceUtil.writeObject(item.id, builder.toByteArray)
			}
			case item: PdfGPattern => {
				val item1 = item.asInstanceOf[RenderReportTypes.this.serializer.renderReportTypes.PdfGPattern]
				val builder = serializer.PdfBaseItemSerializer.write(item1)
				persistenceUtil.writeObject(item.id, builder.toByteArray)
			}
			case item: PdfFontStream => {
				val item1 = item.asInstanceOf[RenderReportTypes.this.serializer.renderReportTypes.PdfFontStream]
				val builder = serializer.PdfBaseItemSerializer.write(item1)
				persistenceUtil.writeObject(item.id, builder.toByteArray)
			}
			case item: PdfFontDescriptor => {
				val item1 = item.asInstanceOf[RenderReportTypes.this.serializer.renderReportTypes.PdfFontDescriptor]
				val builder = serializer.PdfBaseItemSerializer.write(item1)
				persistenceUtil.writeObject(item.id, builder.toByteArray)
			}
			case item: PdfGoToPage => {
				val item1 = item.asInstanceOf[RenderReportTypes.this.serializer.renderReportTypes.PdfGoToPage]
				val builder = serializer.PdfBaseItemSerializer.write(item1)
				persistenceUtil.writeObject(item.id, builder.toByteArray)
			}

			case item: PdfLink => {
				val item1 = item.asInstanceOf[RenderReportTypes.this.serializer.renderReportTypes.PdfLink]
				val builder = serializer.PdfBaseItemSerializer.write(item1)
				persistenceUtil.writeObject(item.id, builder.toByteArray)
			}
			case item: PdfGoToUrl => {
				val item1 = item.asInstanceOf[RenderReportTypes.this.serializer.renderReportTypes.PdfGoToUrl]
				val builder = serializer.PdfBaseItemSerializer.write(item1)
				persistenceUtil.writeObject(item.id, builder.toByteArray)
			}


			case _ => {
				println("Unimplemented: " + obj)
			}
		}
	}

	def getObject[T <: PdfBaseItem](id: Long): T = {
		val bytes = persistenceUtil.readObject(id)
		val proto = PdfBaseItem_proto.parseFrom(bytes)
		val result = serializer.PdfBaseItemSerializer.read(proto)
		result.asInstanceOf[T]
	}


	def getAllItems(): List[java.lang.Long] = persistenceUtil.getAllKeys.asScala.toList

	def close(): Unit = {
		persistenceUtil.close()
	}


}
