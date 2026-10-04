"""Build the study-linked, table-first scanpath classification dictionary."""

from __future__ import annotations

from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT
from reportlab.lib.pagesizes import A4, landscape
from reportlab.lib.styles import ParagraphStyle
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    CondPageBreak,
    LongTable,
    PageBreak,
    Paragraph,
    SimpleDocTemplate,
    Spacer,
    Table,
    TableStyle,
)


ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "output" / "pdf" / "newsmead-scanpath-classification-map.pdf"
OUT.parent.mkdir(parents=True, exist_ok=True)

pdfmetrics.registerFont(TTFont("Segoe", "C:/Windows/Fonts/segoeui.ttf"))
pdfmetrics.registerFont(TTFont("SegoeBold", "C:/Windows/Fonts/segoeuib.ttf"))
pdfmetrics.registerFontFamily("Segoe", normal="Segoe", bold="SegoeBold")

PAGE_W, PAGE_H = landscape(A4)
WIDTH = PAGE_W - 72
NAVY = colors.HexColor("#1B2B47")
BLUE = colors.HexColor("#2857AA")
INK = colors.HexColor("#243043")
MUTED = colors.HexColor("#566375")
GRID = colors.HexColor("#D9E1EC")
PALE = colors.HexColor("#F5F8FC")
WHITE = colors.white

STUDIES = {
    "O22": ("Olivier et al. (2022)", "https://doi.org/10.16910/jemr.15.4.5"),
    "Y17": ("Yamaya et al. (2017)", "https://doi.org/10.2197/ipsjjip.25.100"),
    "W24": ("Wang et al. (2024)", "https://arxiv.org/html/2402.12772"),
    "P19": ("Parker et al. (2019)", "https://doi.org/10.1016/j.visres.2018.12.007"),
    "C22": ("Carr et al. (2022)", "https://doi.org/10.3758/s13428-021-01554-0"),
    "M11": ("von der Malsburg & Vasishth (2011)", "https://doi.org/10.1016/j.jml.2011.02.004"),
    "PA22": ("Parshina et al. (2022)", "https://doi.org/10.1002/rrq.414"),
}

S = {
    "title": ParagraphStyle(
        "title", fontName="SegoeBold", fontSize=16.5, leading=20,
        textColor=NAVY, spaceAfter=4,
    ),
    "intro": ParagraphStyle(
        "intro", fontName="Segoe", fontSize=8.6, leading=11.6,
        textColor=MUTED, spaceAfter=7,
    ),
    "section": ParagraphStyle(
        "section", fontName="SegoeBold", fontSize=11.2, leading=14,
        textColor=NAVY, spaceBefore=10, spaceAfter=4, keepWithNext=True,
    ),
    "subsection": ParagraphStyle(
        "subsection", fontName="SegoeBold", fontSize=9.3, leading=12,
        textColor=BLUE, spaceBefore=8, spaceAfter=3, keepWithNext=True,
    ),
    "context": ParagraphStyle(
        "context", fontName="Segoe", fontSize=7.7, leading=10.3,
        textColor=MUTED, spaceAfter=5,
    ),
    "cell": ParagraphStyle(
        "cell", fontName="Segoe", fontSize=7.6, leading=10.1,
        textColor=INK, alignment=TA_LEFT,
    ),
    "head": ParagraphStyle(
        "head", fontName="SegoeBold", fontSize=7.7, leading=9.4,
        textColor=WHITE,
    ),
    "foot": ParagraphStyle(
        "foot", fontName="Segoe", fontSize=7.4, leading=9.5,
        textColor=MUTED, spaceBefore=4,
    ),
}


def p(value: str, style: str = "cell") -> Paragraph:
    return Paragraph(value, S[style])


def source(key: str) -> str:
    name, url = STUDIES[key]
    return f'<link href="{url}" color="#2857AA"><u>{key}</u></link> {name}'


def label_row(
    label: str,
    level: str,
    rule: str,
    observation: str,
    status: str,
    source_key: str | None,
) -> list[Paragraph]:
    provenance = f"<b>{status}</b>"
    if source_key:
        provenance += f"<br/>{source(source_key)}"
    return [
        p(f"<b>{label}</b><br/><font color='#566375'>{level}</font>"),
        p(rule),
        p(observation),
        p(provenance),
    ]


def table(data: list[list[Paragraph]], widths: list[float]) -> LongTable:
    result = LongTable(data, colWidths=widths, repeatRows=1, hAlign="LEFT")
    result.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, 0), NAVY),
                ("ROWBACKGROUNDS", (0, 1), (-1, -1), [WHITE, PALE]),
                ("VALIGN", (0, 0), (-1, -1), "TOP"),
                ("LEFTPADDING", (0, 0), (-1, -1), 7),
                ("RIGHTPADDING", (0, 0), (-1, -1), 7),
                ("TOPPADDING", (0, 0), (-1, 0), 7),
                ("BOTTOMPADDING", (0, 0), (-1, 0), 7),
                ("TOPPADDING", (0, 1), (-1, -1), 6),
                ("BOTTOMPADDING", (0, 1), (-1, -1), 6),
                ("LINEBELOW", (0, 0), (-1, 0), 0.6, NAVY),
                ("INNERGRID", (0, 1), (-1, -1), 0.25, GRID),
                ("BOX", (0, 0), (-1, -1), 0.5, GRID),
            ]
        )
    )
    return result


def add_label_table(story: list, rows: list[tuple[str, str, str, str, str, str | None]]) -> None:
    headings = ["Label and spatial level", "Schematic rule or path", "Observed behavior and limit", "Status and primary study"]
    data = [[p(value, "head") for value in headings]]
    data.extend(label_row(*row) for row in rows)
    story.append(table(data, [137, 166, 290, WIDTH - 593]))


def add_heading(story: list, title: str, context: str, *, major: bool = False) -> None:
    story.append(CondPageBreak(95))
    story.append(p(title, "section" if major else "subsection"))
    story.append(p(context, "context"))


def page_frame(canvas, doc) -> None:
    canvas.saveState()
    canvas.setStrokeColor(GRID)
    canvas.setLineWidth(0.6)
    canvas.line(36, PAGE_H - 33, PAGE_W - 36, PAGE_H - 33)
    canvas.line(36, 35, PAGE_W - 36, 35)
    canvas.setFont("SegoeBold", 7.6)
    canvas.setFillColor(BLUE)
    canvas.drawString(36, PAGE_H - 25, "NEWSMEAD  /  SCANPATH CLASSIFICATION DICTIONARY")
    canvas.setFont("Segoe", 7.2)
    canvas.setFillColor(MUTED)
    canvas.drawString(36, 22, "Schematic paths; published labels and NewsMead examples are identified in each row.")
    canvas.drawRightString(PAGE_W - 36, 22, f"Page {doc.page}")
    canvas.restoreState()


word_rows = [
    ("Fwd+", "Word fixation", "w12 -&gt; w15; more than one word forward.",
     "The next word-assigned fixation lands farther ahead. Intervening words are not proven unread.",
     "Published movement code", "O22"),
    ("Fwd", "Word fixation", "w12 -&gt; w13; one word forward.",
     "The next assigned fixation lands on the following word. One step does not identify a reading phase.",
     "Published movement code", "O22"),
    ("Rfx", "Word fixation", "w12 -&gt; w12; same word.",
     "Two successive fixations are assigned to one word. This is not a later revisit, a long fixation, or a line hold.",
     "Published movement code", "O22"),
    ("Bwd", "Word fixation", "w12 -&gt; w11; one word backward.",
     "The next assigned fixation lands on the preceding word. The return may be useful rereading.",
     "Published movement code", "O22"),
    ("Bwd-", "Word fixation", "w12 -&gt; w9; more than one word backward.",
     "The next assigned fixation lands farther back. Distance does not explain why the reader returned.",
     "Published movement code", "O22"),
]

line_rows = [
    ("Line following", "Physical line", "Successive fixations remain assigned to L4.",
     "The identified line stays stable. This alone does not establish progress from word to word within L4.",
     "System recognition rule", "W24"),
    ("Line switching", "Physical lines", "L4 end -&gt; L5 start with return-sweep geometry.",
     "The system recognizes movement toward the following line. Its geometric thresholds depend on the desktop layout.",
     "System recognition rule", "W24"),
    ("Line jumping", "Physical lines", "L4 -&gt; L2 without detected sweep; stable for three fixations.",
     "A different line is selected. It might be a revisit, bypass, or misclassification while back-tracing.",
     "System recognition rule", "W24"),
]

segment_rows = [
    ("Return Sweep", "Reading segments; multiline", "End of L4 -&gt; beginning of L5.",
     "Ordinary line change. A leftward movement alone is insufficient. Study: 940 labeled cases; F1 0.91.",
     "Annotated transition", "Y17"),
    ("Progressing", "Reading segments; same line", "S1 -&gt; a farther-right S2 on the same line.",
     "Forward continuation after segmentation split a line. Study: 99 labeled cases; F1 0.66.",
     "Annotated transition", "Y17"),
    ("Short Re-reading", "Reading segments; same line", "A segment returns to earlier text on one line.",
     "Same-line rereading or an overshoot correction; it does not prove misunderstanding. Study: 133 cases; F1 0.47.",
     "Annotated transition", "Y17"),
    ("Long Re-reading", "Reading segments; multiline", "Current segment -&gt; earlier-line segment.",
     "Return to earlier multiline material; the reason is unknown from geometry. Study: 180 cases; F1 0.81.",
     "Annotated transition", "Y17"),
    ("Skipping", "Reading segments", "Current segment -&gt; farther target before line traversal ends.",
     "Bypass under this study's annotation rule; intervening text is not proven unread. Study: 52 cases; F1 0.31.",
     "Annotated transition", "Y17"),
    ("Resuming", "Reading segments; path history", "Detour -&gt; latest previous reading point.",
     "Return after long rereading or skipping; identifying it requires prior path history. Study: 202 cases; F1 0.53.",
     "Annotated transition", "Y17"),
]

multiline_rows = [
    ("Return sweep", "Multiline movement", "Near one line's end -&gt; near the next line's start.",
     "A normal multiline reading movement, even though screen x moves left.",
     "Observed oculomotor term", "P19"),
    ("Undersweep and correction", "Multiline movement", "Sweep lands short -&gt; corrective leftward saccade.",
     "The correction approaches the next line's start. This leftward step need not be rereading.",
     "Observed oculomotor term", "P19"),
    ("Within-line regression", "Physical line", "Return to earlier text on the same line.",
     "Used in natural examples and simulations of line assignment; not a fitted behavior taxonomy.",
     "Method example", "C22"),
    ("Between-line regression", "Physical lines", "Return to an earlier physical line.",
     "A challenging case for vertical-drift correction and line assignment.",
     "Method example", "C22"),
]

path_rows = [
    ("Sweep with correction", "Line and multiline path", "L4(end) -&gt; L5(mid) -&gt; L5(start).",
     "Normal line change can include a leftward correction on L5.",
     "Published movement pattern", "P19"),
    ("Return then resume", "Segment episode", "Current segment -&gt; earlier-line segment -&gt; prior frontier.",
     "Composes Yamaya's Long Re-reading and Resuming relations; the exact composite is an illustration, not a separately validated class.",
     "Published components", "Y17"),
    ("Word return", "Word path", "w12 -&gt; w13 -&gt; w12.",
     "Composition of Olivier's Fwd then Bwd codes. No purpose or aid need follows from this short path.",
     "NewsMead example", "O22"),
    ("Repeated line revisit", "Physical-line path", "L4 -&gt; L5 -&gt; L4 -&gt; L5.",
     "A proposed description of repeated line visits. No cited study names this exact cycle as a difficulty class.",
     "NewsMead example", None),
    ("Long line residence", "Physical-line timing", "Eligible time accumulated while assigned to L4.",
     "Duration is not an order label. Interpret with line/text amount, eligible time, and assignment quality.",
     "Outside order dictionary", None),
    ("Gap-spanning jump", "Missing sequence evidence", "w12 -&gt; [gap] -&gt; w16.",
     "Break continuity at the missing interval; do not call the unobserved interval skipping.",
     "Not an observed reader path", None),
]

cluster_rows = [
    ("A", "Spanish sentence-region path", "Return to sentence beginning; reread all or part.",
     "Only this main pattern was significantly more frequent for garden-path than comparison sentences; it appeared in both conditions.",
     "Empirical cluster", "M11"),
    ("B", "Spanish sentence-region path", "One saccade back to early sentence material.",
     "Brief return. The path alone does not identify its cognitive purpose.",
     "Empirical cluster", "M11"),
    ("C", "Spanish sentence-region path", "Shift back near the disambiguating region.",
     "Possible rechecking, with oculomotor and other processing explanations.",
     "Empirical cluster", "M11"),
    ("D-Q finer solution", "Spanish sentence-region paths",
     "D resembles A; E rereads the first line; F-M refine B; N-P refine C; Q blends B/C.",
     "Exploratory 14-cluster solution dependent on analysis choices; these are not universal reading states.",
     "Exploratory subclusters", "M11"),
    ("Fluent", "Russian whole-sentence word path",
     "Mostly forward, fast, few regressions, more unfixated words.",
     "Exploratory path family across reader groups, not a momentary proficiency score.",
     "Empirical cluster", "PA22"),
    ("Intermediate", "Russian whole-sentence word path",
     "Slower, longer fixations, short regressions, little skipping.",
     "Authors suggest local processing challenges in some groups; one return is not diagnostic.",
     "Empirical cluster", "PA22"),
    ("Beginner", "Russian whole-sentence word path",
     "Slowest, long fixations, frequent word/sentence rereading, little skipping.",
     "Authors suggest broader language-integration challenges in some groups; not a NewsMead aid class.",
     "Empirical cluster", "PA22"),
]

phase_rows = [
    ("NR - Normal reading", "Modeled run of word codes", "Mix of forward steps and refixations.",
     "A modeled phase in the study's topic-judgment task; one Fwd or Rfx event cannot identify it.",
     "Hidden semi-Markov phase", "O22"),
    ("IS - Information search", "Modeled run of word codes", "More direction changes and refixations.",
     "Task-specific modeled phase; the name does not diagnose a reader from one path.",
     "Hidden semi-Markov phase", "O22"),
    ("FR - Fast reading", "Modeled run of word codes", "Higher probability of Fwd+ across the run.",
     "One forward leap does not establish speed or comprehension.",
     "Hidden semi-Markov phase", "O22"),
    ("SC - Slow confirmation", "Modeled run of word codes", "More backward moves near task completion.",
     "One backward move does not establish confirmation or difficulty.",
     "Hidden semi-Markov phase", "O22"),
]

assert sum(map(len, [word_rows, line_rows, segment_rows, multiline_rows, path_rows, cluster_rows, phase_rows])) == 35


def add_source_table(story: list) -> None:
    headings = ["Key", "Primary paper and direct link", "Study setting used here", "Dictionary contribution"]
    data = [[p(value, "head") for value in headings]]
    rows = [
        ("O22", "Olivier, Guérin-Dugué &amp; Durand (2022). <i>Hidden Semi-Markov Models to Segment Reading Phases from Eye Movements.</i>",
         "15 retained French-speaking adults; short-text topic judgment; EyeLink 1000.",
         "Five word displacement codes and four interpreted model phases."),
        ("Y17", "Yamaya, Topić &amp; Aizawa (2017). <i>Vertical Error Correction Using Classification of Transitions between Sequential Reading Segments.</i>",
         "11 readers; 33 English-document trials; Tobii TX300; 1,606 labeled transitions.",
         "Six segment-transition labels, with study-specific classifier results."),
        ("W24", "Wang et al. (2024). <i>GazePrompt: Enhancing Low Vision People's Reading Experience with Gaze-Aware Augmentations.</i>",
         "13 low-vision adults; controlled reading aloud; 120 Hz Tobii tracker.",
         "Three desktop system recognition rules for line navigation."),
        ("P19", "Parker, Slattery &amp; Kirkby (2019). <i>Return-sweep saccades during reading in adults and children.</i>",
         "Multiline reading in laboratory eye-tracking studies.",
         "Return sweeps and undersweep corrections."),
        ("C22", "Carr et al. (2022). <i>Algorithms for the automated correction of vertical drift in eye-tracking data.</i>",
         "Natural examples and simulated line-assignment conditions.",
         "Within- and between-line regressions as measurement cases."),
        ("M11", "von der Malsburg &amp; Vasishth (2011). <i>What is the scanpath signature of syntactic reanalysis?</i>",
         "44 undergraduates; Spanish sentence reading.",
         "Whole-path A/B/C clusters and exploratory D-Q solution."),
        ("PA22", "Parshina et al. (2022). <i>Monolingual and Bilingual Reading Processes in Russian: An Exploratory Scanpath Analysis.</i>",
         "Russian whole-sentence reading across age and language groups.",
         "Fluent, intermediate, and beginner path clusters."),
    ]
    for key, paper, setting, contribution in rows:
        url = STUDIES[key][1]
        linked = f'<link href="{url}" color="#2857AA">{paper}</link><br/><font color="#2857AA">{url}</font>'
        data.append([p(f"<b>{key}</b>"), p(linked), p(setting), p(contribution)])
    story.append(table(data, [48, 320, 204, WIDTH - 572]))


story: list = [
    p("Scanpath classification map and dictionary", "title"),
    p(
        "A grouped lookup of the labels relevant to the NewsMead scanpath-to-visual-aid proposal. "
        "Each row names the spatial level, sequence or decision rule, observed reading behavior, "
        "source status, and primary study. These entries are a measurement vocabulary, not validated phone diagnoses or aid triggers.",
        "intro",
    ),
    p(
        "<b>Notation:</b> w = document-order word; L = physical text line; S = reading segment. "
        "Arrows are schematic. A segment may cover part of a line or a different span; it is not automatically a physical line. "
        "Sequence length does not turn a path description into a study-derived cluster.",
        "context",
    ),
]

add_heading(
    story,
    "A. Movements and recognition labels",
    "A movement describes a local transition; a recognition rule uses fixation history or geometry. The four tables below remain grouped by word, line, segment, and multiline level.",
    major=True,
)
add_heading(
    story,
    "A1. Word-to-word fixation transitions",
    "Olivier et al. encoded signed word distance between successive word-assigned fixations. Their word mapping used a task/language-specific spatial window; these are not difficulty grades.",
)
add_label_table(story, word_rows)
add_heading(
    story,
    "A2. Line-fixation recognition rules",
    "GazePrompt used these rules in a controlled desktop low-vision reading aid. A stable assigned line alone says nothing about within-line word progression.",
)
add_label_table(story, line_rows)
add_heading(
    story,
    "A3. Transitions between reading segments",
    "Yamaya et al. grouped horizontally progressive fixations into segments before assigning six transition labels. Their classifier used two preceding and two following transitions, so reported scores do not establish a causal live detector.",
)
add_label_table(story, segment_rows)
story.append(p(
    "<b>Y17 study-level result:</b> six-class F1 0.78 and subsequent fixation-to-word mapping accuracy 87% in that setting. "
    "Short Re-reading and Skipping were weaker (F1 0.47 and 0.31). These figures do not validate NewsMead phone placement.",
    "foot",
))
add_heading(
    story,
    "A4. Multiline movement terms and alignment cases",
    "Return-sweep corrections and regressions can look similar if line geometry is ignored. Carr's terms appear here as measurement cases, not a fitted behavior taxonomy.",
)
add_label_table(story, multiline_rows)

add_heading(
    story,
    "B. Ordered path and episode descriptions",
    "These rows describe several movements in time. The source-status column identifies published patterns, compositions of published relations, proposed NewsMead examples, and non-order cases.",
    major=True,
)
add_label_table(story, path_rows)

add_heading(
    story,
    "C. Empirical clusters of sentence paths",
    "These labels came from similarity among whole sentence paths in specific studies. They are not diagnoses from one fixation, one regression, or an arbitrary arrow count.",
    major=True,
)
add_label_table(story, cluster_rows)

add_heading(
    story,
    "D. Model-inferred phases of word-code runs",
    "Olivier et al. fitted five hidden states and grouped them into four interpreted phases. Phase duration counts fixation steps; it is not one long fixation.",
    major=True,
)
add_label_table(story, phase_rows)

story.append(PageBreak())
add_heading(
    story,
    "Primary-study reference key",
    "The short key in each row links to the study. The settings below explain why published labels cannot simply be transferred to NewsMead's phone gaze pipeline.",
    major=True,
)
add_source_table(story)
story.append(Spacer(1, 5))
boundary = Table(
    [[p("<b>NewsMead use boundary</b>"),
      p("First preserve observable word and line visits, timing, mapping quality, and gaps. "
        "Validate detection on NewsMead data before applying any label; test visual-aid placement, strength, and benefit against independent reading outcomes. "
        "Published clusters and phases can guide exploratory analysis, but they do not establish a momentary aid need.")]],
    colWidths=[137, WIDTH - 137],
)
boundary.setStyle(TableStyle([
    ("BACKGROUND", (0, 0), (-1, -1), PALE),
    ("BOX", (0, 0), (-1, -1), 0.5, GRID),
    ("VALIGN", (0, 0), (-1, -1), "TOP"),
    ("LEFTPADDING", (0, 0), (-1, -1), 8),
    ("RIGHTPADDING", (0, 0), (-1, -1), 8),
    ("TOPPADDING", (0, 0), (-1, -1), 8),
    ("BOTTOMPADDING", (0, 0), (-1, -1), 8),
]))
story.append(boundary)

doc = SimpleDocTemplate(
    str(OUT),
    pagesize=(PAGE_W, PAGE_H),
    leftMargin=36,
    rightMargin=36,
    topMargin=47,
    bottomMargin=44,
    title="NewsMead Scanpath Classification Map and Dictionary",
    author="NewsMead project",
    subject="Study-linked table of word, line, segment, path, cluster, and phase labels",
    pageCompression=1,
)
doc.build(story, onFirstPage=page_frame, onLaterPages=page_frame)
print(OUT)
