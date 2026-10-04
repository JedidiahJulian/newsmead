"""Render the Markdown evidence map as a compact table-first PDF."""

from __future__ import annotations

import html
import re
from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.pagesizes import A4, landscape
from reportlab.lib.styles import ParagraphStyle
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import CondPageBreak, LongTable, PageBreak, Paragraph, SimpleDocTemplate, Spacer, TableStyle


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "docs" / "scanpath-visual-aid-evidence-map.md"
OUT = ROOT / "output" / "pdf" / "newsmead-scanpath-visual-aid-evidence-map.pdf"
OUT.parent.mkdir(parents=True, exist_ok=True)

pdfmetrics.registerFont(TTFont("Segoe", "C:/Windows/Fonts/segoeui.ttf"))
pdfmetrics.registerFont(TTFont("SegoeBold", "C:/Windows/Fonts/segoeuib.ttf"))
pdfmetrics.registerFontFamily("Segoe", normal="Segoe", bold="SegoeBold")

W, H = landscape(A4)
CONTENT = W - 72
NAVY = colors.HexColor("#1B2B47")
BLUE = colors.HexColor("#2857AA")
INK = colors.HexColor("#243043")
MUTED = colors.HexColor("#566375")
GRID = colors.HexColor("#D9E1EC")
PALE = colors.HexColor("#F5F8FC")

STYLE = {
    "title": ParagraphStyle("title", fontName="SegoeBold", fontSize=16, leading=19.5, textColor=NAVY, spaceAfter=8),
    "section": ParagraphStyle("section", fontName="SegoeBold", fontSize=11, leading=13.7, textColor=NAVY, spaceBefore=10, spaceAfter=6),
    "body": ParagraphStyle("body", fontName="Segoe", fontSize=8.2, leading=11, textColor=INK, spaceAfter=6),
    "list": ParagraphStyle("list", fontName="Segoe", fontSize=8.0, leading=10.8, textColor=INK, leftIndent=16, firstLineIndent=-15, spaceAfter=5),
    "cell": ParagraphStyle("cell", fontName="Segoe", fontSize=7.35, leading=10.0, textColor=INK),
    "head": ParagraphStyle("head", fontName="SegoeBold", fontSize=7.5, leading=9.4, textColor=colors.white),
}

LINK = re.compile(r"\[([^\]]+)\]\((https?://[^)]+)\)")
REPLACEMENTS = {
    "\u2011": "-", "\u2013": "-", "\u2014": "-", "\u2212": "-",
    "\u201c": '"', "\u201d": '"', "\u2018": "'", "\u2019": "'",
}


def inline(markdown: str) -> str:
    text = html.unescape(markdown.strip())
    for old, new in REPLACEMENTS.items():
        text = text.replace(old, new)
    links: list[str] = []

    def hold_link(match: re.Match[str]) -> str:
        label = html.escape(match.group(1)).replace("*", "")
        url = html.escape(match.group(2), quote=True)
        links.append(f'<link href="{url}" color="#2857AA"><u>{label}</u></link>')
        return f"ZZLINK{len(links) - 1}ZZ"

    text = LINK.sub(hold_link, text)
    text = re.sub(r"\[([^\]]+)\]\((?!https?://)[^)]+\)", r"\1", text)
    text = html.escape(text)
    text = re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", text)
    text = re.sub(r"\*(.+?)\*", r"<i>\1</i>", text)
    for i, link in enumerate(links):
        text = text.replace(f"ZZLINK{i}ZZ", link)
    return text


def para(markdown: str, kind: str = "body") -> Paragraph:
    return Paragraph(inline(markdown), STYLE[kind])


def widths_for(columns: int, first_heading: str) -> list[float]:
    if columns == 4:
        return [148, 181, 265, CONTENT - 594]
    if columns == 3:
        return [170, 265, CONTENT - 435]
    if columns == 2:
        return [68, CONTENT - 68] if first_heading.lower() == "key" else [164, CONTENT - 164]
    return [CONTENT / columns] * columns


def add_table(story: list, raw_rows: list[list[str]]) -> None:
    assert raw_rows and len(raw_rows) >= 2
    columns = len(raw_rows[0])
    assert all(len(row) == columns for row in raw_rows)
    data = [[para(cell, "head") for cell in raw_rows[0]]]
    data.extend([para(cell, "cell") for cell in row] for row in raw_rows[1:])
    result = LongTable(data, colWidths=widths_for(columns, raw_rows[0][0]), repeatRows=1, hAlign="LEFT")
    result.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), NAVY),
        ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, PALE]),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("LEFTPADDING", (0, 0), (-1, -1), 7),
        ("RIGHTPADDING", (0, 0), (-1, -1), 7),
        ("TOPPADDING", (0, 0), (-1, 0), 7),
        ("BOTTOMPADDING", (0, 0), (-1, 0), 7),
        ("TOPPADDING", (0, 1), (-1, -1), 6),
        ("BOTTOMPADDING", (0, 1), (-1, -1), 6),
        ("INNERGRID", (0, 1), (-1, -1), 0.25, GRID),
        ("BOX", (0, 0), (-1, -1), 0.5, GRID),
    ]))
    story.append(result)
    story.append(Spacer(1, 6))


def page_frame(canvas, doc) -> None:
    canvas.saveState()
    canvas.setStrokeColor(GRID)
    canvas.setLineWidth(0.6)
    canvas.line(36, H - 33, W - 36, H - 33)
    canvas.line(36, 35, W - 36, 35)
    canvas.setFont("SegoeBold", 7.6)
    canvas.setFillColor(BLUE)
    canvas.drawString(36, H - 25, "NEWSMEAD  /  BEHAVIOR TO VISUAL-AID EVIDENCE")
    canvas.setFont("Segoe", 7.2)
    canvas.setFillColor(MUTED)
    canvas.drawString(36, 22, "Primary-study findings and NewsMead decisions appear in separate columns.")
    canvas.drawRightString(W - 36, 22, f"Page {doc.page}")
    canvas.restoreState()


lines = SOURCE.read_text(encoding="utf-8").splitlines()
story: list = []
table_count = 0
row_count = 0
i = 0
while i < len(lines):
    line = lines[i].strip()
    if not line:
        i += 1
        continue
    if line.startswith("# "):
        story.append(para(line[2:], "title"))
        i += 1
        continue
    if line.startswith("## "):
        story.append(PageBreak() if line == "## Primary reference key" else CondPageBreak(100))
        story.append(para(line[3:], "section"))
        i += 1
        continue
    if line.startswith("|"):
        rows: list[list[str]] = []
        while i < len(lines) and lines[i].strip().startswith("|"):
            row = [part.strip() for part in lines[i].strip().strip("|").split("|")]
            if not all(re.fullmatch(r":?-{3,}:?", cell) for cell in row):
                rows.append(row)
            i += 1
        add_table(story, rows)
        table_count += 1
        row_count += len(rows) - 1
        continue
    if re.match(r"^\d+\.\s", line):
        story.append(para(line, "list"))
        i += 1
        continue
    paragraph = [line]
    i += 1
    while i < len(lines) and lines[i].strip() and not lines[i].lstrip().startswith(("## ", "# ", "|")):
        paragraph.append(lines[i].strip())
        i += 1
    story.append(para(" ".join(paragraph)))

assert table_count == 6, table_count
assert row_count == 34, row_count

doc = SimpleDocTemplate(
    str(OUT), pagesize=(W, H), leftMargin=36, rightMargin=36,
    topMargin=47, bottomMargin=44,
    title="NewsMead Scanpath Behavior to Visual-Aid Evidence Map",
    author="NewsMead project",
    subject="Primary-study evidence for candidate line, word, navigation, re-entry, and focus-region aids",
    pageCompression=1,
)
doc.build(story, onFirstPage=page_frame, onLaterPages=page_frame)
print(OUT)
