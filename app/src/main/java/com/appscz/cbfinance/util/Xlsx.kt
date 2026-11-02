package com.appscz.cbfinance.util

import android.util.Xml
import com.appscz.cbfinance.data.TableData
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Escritor y lector mínimo de archivos Excel (.xlsx) sin dependencias externas. */
object Xlsx {

    private fun esc(s: String): String {
        val sb = StringBuilder(s.length + 8)
        for (ch in s) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                else -> if (ch.code >= 32 || ch == '\n' || ch == '\t') sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun colName(i: Int): String {
        var n = i
        val sb = StringBuilder()
        while (n >= 0) {
            sb.insert(0, ('A'.code + n % 26).toChar())
            n = n / 26 - 1
        }
        return sb.toString()
    }

    private fun putEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    fun write(out: OutputStream, t: TableData) {
        val zip = ZipOutputStream(out)
        val sheetName = esc(t.sheet.replace(Regex("[\\\\/?*\\[\\]:]"), " ").take(31))
        putEntry(
            zip, "[Content_Types].xml",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>"""
        )
        putEntry(
            zip, "_rels/.rels",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""
        )
        putEntry(
            zip, "xl/workbook.xml",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="$sheetName" sheetId="1" r:id="rId1"/></sheets></workbook>"""
        )
        putEntry(
            zip, "xl/_rels/workbook.xml.rels",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>"""
        )
        val color = t.headerColor.ifBlank { "4472C4" }
        putEntry(
            zip, "xl/styles.xml",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font></fonts><fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF$color"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1" applyAlignment="1"><alignment horizontal="center"/></xf></cellXfs></styleSheet>"""
        )
        // Anchos de columna
        val widths = IntArray(t.headers.size) { t.headers[it].length + 2 }
        for (r in t.rows) {
            for (c in r.indices) {
                if (c < widths.size) {
                    val len = (r[c]?.toString() ?: "").length + 2
                    if (len > widths[c]) widths[c] = len
                }
            }
        }
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><cols>""")
        for (i in widths.indices) {
            sb.append("""<col min="${i + 1}" max="${i + 1}" width="${minOf(widths[i], 60)}" customWidth="1"/>""")
        }
        sb.append("</cols><sheetData>")
        sb.append("""<row r="1">""")
        for (i in t.headers.indices) {
            sb.append("""<c r="${colName(i)}1" s="1" t="inlineStr"><is><t>${esc(t.headers[i])}</t></is></c>""")
        }
        sb.append("</row>")
        for ((ri, r) in t.rows.withIndex()) {
            val rn = ri + 2
            sb.append("""<row r="$rn">""")
            for (ci in r.indices) {
                val v = r[ci]
                val ref = colName(ci) + rn
                when (v) {
                    null -> {}
                    is Number -> sb.append("""<c r="$ref"><v>${v}</v></c>""")
                    else -> sb.append("""<c r="$ref" t="inlineStr"><is><t xml:space="preserve">${esc(v.toString())}</t></is></c>""")
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        putEntry(zip, "xl/worksheets/sheet1.xml", sb.toString())
        zip.finish()
        zip.flush()
    }

    // ------------------------------------------------------------------ LECTURA
    private fun colIndex(ref: String): Int {
        var n = 0
        for (ch in ref) {
            if (ch in 'A'..'Z') n = n * 26 + (ch - 'A' + 1) else break
        }
        return n - 1
    }

    /** Lee la primera hoja y devuelve las filas como mapas (encabezado -> valor). */
    fun read(input: InputStream, fileName: String): List<Map<String, String>> {
        if (fileName.lowercase().endsWith(".csv")) return readCsv(input)
        if (fileName.lowercase().endsWith(".xls")) {
            throw IllegalArgumentException("El formato .xls antiguo no es compatible. Guarda el archivo como .xlsx e inténtalo de nuevo.")
        }
        var shared: ByteArray? = null
        var sheet: ByteArray? = null
        var sheetName = ""
        val zip = ZipInputStream(input)
        var e = zip.nextEntry
        while (e != null) {
            val name = e.name
            if (name == "xl/sharedStrings.xml") {
                shared = zip.readBytes()
            } else if (name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml")) {
                if (sheet == null || name < sheetName) {
                    sheet = zip.readBytes()
                    sheetName = name
                }
            }
            e = zip.nextEntry
        }
        if (sheet == null) throw IllegalArgumentException("No se pudo leer el archivo Excel (hoja no encontrada).")
        val strings = ArrayList<String>()
        if (shared != null) {
            val p = Xml.newPullParser()
            p.setInput(ByteArrayInputStream(shared), "UTF-8")
            var ev = p.eventType
            var inSi = false
            var inT = false
            val cur = StringBuilder()
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    if (p.name == "si") {
                        inSi = true; cur.setLength(0)
                    } else if (p.name == "t" && inSi) inT = true
                } else if (ev == XmlPullParser.TEXT) {
                    if (inT) cur.append(p.text)
                } else if (ev == XmlPullParser.END_TAG) {
                    if (p.name == "t") inT = false
                    else if (p.name == "si") {
                        inSi = false; strings.add(cur.toString())
                    }
                }
                ev = p.next()
            }
        }
        val rows = ArrayList<MutableMap<Int, String>>()
        val p = Xml.newPullParser()
        p.setInput(ByteArrayInputStream(sheet), "UTF-8")
        var ev = p.eventType
        var curRow: MutableMap<Int, String>? = null
        var cellType = ""
        var cellCol = 0
        var inV = false
        var inT = false
        val txt = StringBuilder()
        var inCell = false
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (p.name) {
                    "row" -> curRow = HashMap()
                    "c" -> {
                        inCell = true
                        cellType = p.getAttributeValue(null, "t") ?: ""
                        val ref = p.getAttributeValue(null, "r") ?: ""
                        cellCol = if (ref.isNotEmpty()) colIndex(ref) else (curRow?.size ?: 0)
                        txt.setLength(0)
                    }
                    "v" -> inV = true
                    "t" -> if (inCell) inT = true
                }
            } else if (ev == XmlPullParser.TEXT) {
                if (inV || inT) txt.append(p.text)
            } else if (ev == XmlPullParser.END_TAG) {
                when (p.name) {
                    "v" -> inV = false
                    "t" -> inT = false
                    "c" -> {
                        val raw = txt.toString()
                        val value = when (cellType) {
                            "s" -> raw.trim().toIntOrNull()?.let { if (it >= 0 && it < strings.size) strings[it] else "" } ?: ""
                            "b" -> if (raw.trim() == "1") "TRUE" else "FALSE"
                            "e" -> ""
                            else -> raw
                        }
                        if (curRow != null && value.isNotEmpty()) curRow[cellCol] = value
                        inCell = false
                    }
                    "row" -> {
                        val r = curRow
                        if (r != null) rows.add(r)
                        curRow = null
                    }
                }
            }
            ev = p.next()
        }
        return toMaps(rows.map { r ->
            val max = (r.keys.maxOrNull() ?: -1) + 1
            List(max) { i -> r[i] ?: "" }
        })
    }

    private fun toMaps(table: List<List<String>>): List<Map<String, String>> {
        if (table.isEmpty()) return emptyList()
        val headers = table[0].map { it.trim() }
        val out = ArrayList<Map<String, String>>()
        for (i in 1 until table.size) {
            val r = table[i]
            if (r.all { it.isBlank() }) continue
            val m = LinkedHashMap<String, String>()
            for (c in headers.indices) {
                if (headers[c].isEmpty()) continue
                m[headers[c]] = if (c < r.size) r[c] else ""
            }
            out.add(m)
        }
        return out
    }

    private fun readCsv(input: InputStream): List<Map<String, String>> {
        val text = input.readBytes().toString(Charsets.UTF_8).removePrefix("\uFEFF")
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        val sep = if (lines[0].count { it == ';' } > lines[0].count { it == ',' }) ';' else ','
        val table = lines.map { splitCsvLine(it, sep) }
        return toMaps(table)
    }

    private fun splitCsvLine(line: String, sep: Char): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var q = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (q) {
                if (ch == '"') {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        cur.append('"'); i++
                    } else q = false
                } else cur.append(ch)
            } else {
                if (ch == '"') q = true
                else if (ch == sep) {
                    out.add(cur.toString()); cur.setLength(0)
                } else cur.append(ch)
            }
            i++
        }
        out.add(cur.toString())
        return out
    }
}
