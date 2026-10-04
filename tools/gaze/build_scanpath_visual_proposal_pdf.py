from __future__ import annotations

import math
from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT
from reportlab.lib.pagesizes import A4, landscape
from reportlab.lib.styles import ParagraphStyle
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas
from reportlab.platypus import Paragraph


ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "output" / "pdf" / "newsmead-scanpath-visual-aid-proposal.pdf"
OUT.parent.mkdir(parents=True, exist_ok=True)

pdfmetrics.registerFont(TTFont("Segoe", r"C:\Windows\Fonts\segoeui.ttf"))
pdfmetrics.registerFont(TTFont("SegoeBold", r"C:\Windows\Fonts\segoeuib.ttf"))
pdfmetrics.registerFont(TTFont("Consolas", r"C:\Windows\Fonts\consola.ttf"))
pdfmetrics.registerFontFamily("Segoe", normal="Segoe", bold="SegoeBold")

W, H = landscape(A4)
TOTAL_PAGES = 7
C = canvas.Canvas(str(OUT), pagesize=(W, H), pageCompression=1)
C.setTitle("NewsMead: From Scanpath to Adaptive Visual Aid")
C.setAuthor("NewsMead project")
C.setSubject("Illustrated implementation proposal and evidence boundaries")

NAVY = colors.HexColor("#17233D")
BLUE = colors.HexColor("#3159CB")
BLUE_D = colors.HexColor("#2445A5")
PALE_BLUE = colors.HexColor("#EAF0FF")
TEAL = colors.HexColor("#0B9A8F")
TEAL_D = colors.HexColor("#087E76")
PALE_TEAL = colors.HexColor("#E6F6F2")
AMBER = colors.HexColor("#D38A18")
PALE_AMBER = colors.HexColor("#FFF2D5")
CORAL = colors.HexColor("#C85D50")
PALE_CORAL = colors.HexColor("#FFF0EC")
INK = colors.HexColor("#263249")
MUTED = colors.HexColor("#586579")
FAINT = colors.HexColor("#EDF1F7")
BG = colors.HexColor("#F7F9FC")
WHITE = colors.white


def rect(x, y, w, h, fill=WHITE, stroke=None, radius=10, sw=1):
    C.setLineWidth(sw)
    C.setFillColor(fill)
    C.setStrokeColor(stroke or fill)
    C.roundRect(x, y, w, h, radius, fill=1, stroke=1 if stroke else 0)


def line(x1, y1, x2, y2, color=FAINT, width=1):
    C.setStrokeColor(color)
    C.setLineWidth(width)
    C.line(x1, y1, x2, y2)


def arrow(x1, y1, x2, y2, color=BLUE, width=2, head=6):
    C.setStrokeColor(color)
    C.setFillColor(color)
    C.setLineWidth(width)
    C.line(x1, y1, x2, y2)
    a = math.atan2(y2-y1, x2-x1)
    p = C.beginPath()
    p.moveTo(x2, y2)
    p.lineTo(x2-head*math.cos(a-.48), y2-head*math.sin(a-.48))
    p.lineTo(x2-head*math.cos(a+.48), y2-head*math.sin(a+.48))
    p.close()
    C.drawPath(p, fill=1, stroke=0)


def curve_arrow(x1, y1, cx1, cy1, cx2, cy2, x2, y2, color=BLUE, width=2):
    C.setStrokeColor(color)
    C.setLineWidth(width)
    p = C.beginPath()
    p.moveTo(x1, y1)
    p.curveTo(cx1, cy1, cx2, cy2, x2, y2)
    C.drawPath(p)
    a = math.atan2(y2-cy2, x2-cx2)
    C.setFillColor(color)
    q = C.beginPath()
    q.moveTo(x2, y2)
    q.lineTo(x2-7*math.cos(a-.45), y2-7*math.sin(a-.45))
    q.lineTo(x2-7*math.cos(a+.45), y2-7*math.sin(a+.45))
    q.close()
    C.drawPath(q, fill=1, stroke=0)


def text(x, y, s, size=10, color=INK, bold=False, font=None):
    C.setFont(font or ("SegoeBold" if bold else "Segoe"), size)
    C.setFillColor(color)
    C.drawString(x, y, s)


def centered(x, y, s, size=10, color=INK, bold=False, font=None):
    C.setFont(font or ("SegoeBold" if bold else "Segoe"), size)
    C.setFillColor(color)
    C.drawCentredString(x, y, s)


def para(x, top, s, width, size=9.5, leading=None, color=INK, bold=False,
         max_h=None):
    style = ParagraphStyle(
        "p", fontName="SegoeBold" if bold else "Segoe",
        fontSize=size, leading=leading or size*1.37,
        textColor=color, alignment=TA_LEFT, spaceAfter=0, spaceBefore=0,
    )
    p = Paragraph(s, style)
    _, h = p.wrap(width, 1000)
    if max_h is not None and h > max_h + 0.5:
        raise ValueError(f"Text overflow ({h:.1f}>{max_h:.1f}): {s[:80]}")
    p.drawOn(C, x, top-h)
    return top-h


def tag(x, y, s, fill=PALE_BLUE, fg=BLUE_D, w=None, h=20):
    ww = w or max(46, pdfmetrics.stringWidth(s, "SegoeBold", 8.3)+16)
    rect(x, y, ww, h, fill=fill, radius=h/2)
    centered(x+ww/2, y+(h-8.3)/2+2, s, 8.3, fg, bold=True)
    return ww


def numbered_dot(x, y, n, fill=BLUE):
    C.setFillColor(WHITE)
    C.setStrokeColor(fill)
    C.setLineWidth(2)
    C.circle(x, y, 13, stroke=1, fill=1)
    centered(x, y-3.6, str(n), 10, fill, bold=True)


def base(page, section, title, subtitle=None):
    C.setFillColor(BG)
    C.rect(0, 0, W, H, fill=1, stroke=0)
    text(40, H-36, "NEWSMEAD  /  IMPLEMENTATION PROPOSAL", 8.5, BLUE_D, bold=True)
    text(40, H-72, title, 21, NAVY, bold=True)
    if subtitle:
        para(41, H-82, subtitle, W-82, 9.1, color=MUTED, max_h=30)
    line(40, 45, W-40, 45, FAINT, 1)
    text(40, 26, "Schematic examples  |  Proposed feature, not validated NewsMead behavior",
         7.6, MUTED)
    text(W-105, 26, f"{section}  /  {page} of {TOTAL_PAGES}", 7.6, MUTED, bold=True)


def source_note(s):
    text(W-40-pdfmetrics.stringWidth(s, "Segoe", 7.3), 52, s, 7.3, MUTED)


# Page 1 - definition and visible scanpath
base(1, "DEFINE", "What is a reading scanpath?",
     "A scanpath is the ordered path of gaze stops assigned to text, with time and location. "
     "Word and line paths are two resolutions of the same observations.")
tag(41, 471, "SCHEMATIC ARTICLE - NOT RECORDED NEWSMEAD DATA", PALE_AMBER, AMBER)
rect(40, 192, W-80, 270, WHITE, FAINT, 14)
text(65, 432, "Numbered circles are candidate fixations in time order", 10, NAVY, bold=True)

word_x4 = [(96,"The",10),(170,"path",11),(252,"runs",12),(337,"beside",13),
           (442,"the",14),(511,"river",15)]
word_x5 = [(96,"Families",16),(226,"visit",17),(313,"each",18),
           (399,"sunny",19),(493,"weekend",20)]
text(67, 375, "LINE 4", 8.2, TEAL_D, bold=True)
text(67, 279, "LINE 5", 8.2, TEAL_D, bold=True)
for x, word, idx in word_x4:
    rect(x, 350, pdfmetrics.stringWidth(word, "Segoe", 13)+18, 32, PALE_BLUE, radius=5)
    text(x+9, 360, word, 13, NAVY)
    centered(x+pdfmetrics.stringWidth(word, "Segoe", 13)/2+9, 337, f"w{idx}",
             7.5, MUTED, font="Consolas")
for x, word, idx in word_x5:
    rect(x, 254, pdfmetrics.stringWidth(word, "Segoe", 13)+18, 32, PALE_TEAL, radius=5)
    text(x+9, 264, word, 13, NAVY)
    centered(x+pdfmetrics.stringWidth(word, "Segoe", 13)/2+9, 241, f"w{idx}",
             7.5, MUTED, font="Consolas")

P = [(277,407),(367,407),(333,311),(466,407),(427,311)]
curve_arrow(291,408,314,425,327,425,353,409, BLUE, 1.8)
curve_arrow(376,395,397,360,359,335,341,324, BLUE, 1.8)
curve_arrow(344,309,390,306,399,382,451,400, BLUE, 1.8)
curve_arrow(463,395,488,352,466,329,439,316, BLUE, 1.8)
for n,(x,y) in enumerate(P,1):
    numbered_dot(x,y,n,BLUE if n<4 else TEAL)
text(65, 211, "Same gaze observations, two valid projections:", 9.3, MUTED)

rect(40, 125, 362, 55, PALE_BLUE, radius=10)
text(55, 156, "WORD FIXATION ORDER", 8.2, BLUE_D, bold=True)
text(55, 136, "w12 -> w13 -> w18 -> w14 -> w19", 12, NAVY, font="Consolas")
rect(415, 125, 387, 55, PALE_TEAL, radius=10)
text(430, 156, "LINE VISIT ORDER (SAME-LINE STOPS MERGED)", 8.2, TEAL_D, bold=True)
text(430, 136, "L4 -> L5 -> L4 -> L5", 12, NAVY, font="Consolas")
para(41, 111, "The return to L4 is an <b>observed line revisit</b>. It does not by itself mean confusion; "
     "the reader may be rereading usefully. [S14]", W-84, 9.2, color=INK, max_h=36)
source_note("[S14] Schotter et al. 2014")
C.showPage()


# Page 2 - derivation pipeline
base(2, "DERIVE", "How NewsMead would derive both paths",
     "Each stage keeps uncertainty visible. An unassigned interval breaks a sequence; it is not silently bridged.")

cards = [
    ("1  OBSERVE", "Capture time, raw and displayed x/y, validity reason, layout and scroll version."),
    ("2  DETECT", "Group contiguous, spatially compact samples into provisional fixation candidates."),
    ("3  ASSIGN", "Map each candidate to a word ordinal and physical line, or mark it ambiguous."),
    ("4  PRESERVE", "Keep fixation order; also merge eligible runs into word visits and line visits."),
    ("5  CLASSIFY", "Calculate signed word/line movement, residence, and explicit breaks."),
]
xs = [40, 198, 356, 514, 672]
for i, ((head, body), x) in enumerate(zip(cards, xs)):
    rect(x, 315, 130, 157, WHITE, FAINT, 11)
    tag(x+11, 439, head, PALE_BLUE if i!=2 else PALE_TEAL,
        BLUE_D if i!=2 else TEAL_D, w=108)
    para(x+12, 424, body, 106, 9.2, max_h=100)
    if i < 4:
        arrow(x+132, 391, xs[i+1]-5, 391, BLUE, 1.7, 5)

rect(40, 179, 364, 118, PALE_TEAL, radius=11)
text(56, 274, "ONE CONTINUOUS ELIGIBLE RUN", 9.2, TEAL_D, bold=True)
text(56, 245, "Fixations:  w12   w13   w13   w15", 11, NAVY, font="Consolas")
text(56, 220, "Word visits:  w12 -> w13 -> w15", 10.4, NAVY, font="Consolas")
para(56, 209, "The two w13 fixations remain in the full path; the visit stores their "
     "observed residence together.", 335, 8.6, max_h=29)

rect(419, 179, 383, 118, PALE_CORAL, radius=11)
text(435, 274, "BREAK = NO INFERRED TRANSITION", 9.2, CORAL, bold=True)
text(435, 245, "w12 -> w13   |  ?  |   w16", 11, NAVY, font="Consolas")
para(435, 231, "A gap, scroll, reflow, or ambiguous assignment interrupts the path. "
     "Do not label w13 -> w16 as a forward jump or skipped reading.", 350, 8.7, max_h=53)

rect(40, 71, W-80, 91, WHITE, FAINT, 11)
tag(55, 128, "LOCAL MEASUREMENT LIMIT", PALE_AMBER, AMBER)
para(55, 119,
     "The current offline detector's 100 ms / 3-sample / 125 ms gap settings are provisional. "
     "In the first natural-reading diagnostic, 35 of 87 candidate fixations had ambiguous word assignment "
     "and only four word transitions remained eligible after gap correction. Word-level cues require further validation.",
     W-110, 8.9, max_h=46)
source_note("Sources: [SG00], NewsMead reading-measurement-run-01")
C.showPage()


# Page 3 - order dictionary
base(3, "CLASSIFY", "Where the order classes come from",
     "Published labels describe gaze movement under the studies' conditions. NewsMead must validate its own assignments and event accuracy.")

rect(40, 180, 363, 295, WHITE, FAINT, 12)
tag(55, 440, "WORD FIXATION TRANSITIONS  [O22]", PALE_BLUE, BLUE_D)
para(55, 430, "Signed difference between <b>consecutive assigned word fixations</b> "
     "in document order.", 330, 8.8, max_h=42)
word_rows = [
    ("w12 -> w13", "+1", "Fwd"),
    ("w12 -> w15", ">+1", "Fwd+"),
    ("w12 -> w12", "0", "Rfx: same-word refixation"),
    ("w13 -> w12", "-1", "Bwd"),
    ("w15 -> w12", "<-1", "Bwd-"),
]
for i,(seq,delta,label) in enumerate(word_rows):
    y = 354-i*34
    rect(55, y, 332, 29, PALE_BLUE if i%2==0 else BG, radius=5)
    text(65, y+9, seq, 9.4, NAVY, font="Consolas")
    text(211, y+9, delta, 9, BLUE_D, font="Consolas")
    text(260, y+9, label, 8.6, INK, bold=True)
para(55, 211, "Olivier's HSMM phases are task-specific; they are <b>not</b> "
     "NewsMead difficulty states.", 331, 8.3, max_h=30)

rect(418, 180, 384, 295, WHITE, FAINT, 12)
tag(433, 440, "SEGMENTS AND RENDERED LINES  [Y17]", PALE_TEAL, TEAL_D)
para(433, 430, "Yamaya et al. labeled transitions between <b>sequential reading segments</b>, "
     "including the following:", 354, 8.8, max_h=43)
seg_rows = [
    ("L4 end -> L5 start", "Return sweep"),
    ("same line, move on", "Progressing"),
    ("same-line return", "Short Re-reading"),
    ("multiline return", "Long Re-reading"),
    ("next target missed", "Skipping"),
    ("resume after detour", "Resuming"),
]
for i,(seq,label) in enumerate(seg_rows):
    y = 360-i*28
    rect(433, y, 354, 24, PALE_TEAL if i%2==0 else BG, radius=5)
    text(443, y+7, seq, 8.5, NAVY, font="Consolas")
    text(631, y+7, label, 8.2, INK, bold=True)
para(433, 211, "NewsMead's line bins are operational; they are <b>not</b> "
     "Yamaya's validated classes. Missing gaze never proves skipping.", 354, 8.1, max_h=33)

rect(40, 99, W-80, 66, PALE_AMBER, radius=11)
text(55, 142, "Do not turn order into an unsupported reading state", 10.2, AMBER, bold=True)
para(55, 133, "A long HOLD is duration, not an order class. A missing assigned word is not proof that it was unread. "
     "A backward movement can be useful rereading. BREAK means uncertain measurement, not inattention.",
     W-110, 8.6, max_h=35)
source_note("[O22] Olivier 2022  |  [Y17] Yamaya 2017  |  [S14] Schotter 2014")
C.showPage()


# Page 4 - classification bridge
base(4, "BRIDGE", "From observed sequence to candidate aid",
     "The middle column is essential: an observed pattern can have several explanations. The aid is a testable response.")

col = [40, 204, 374, 580]
heads = [("ORDER EXAMPLE", 40), ("OBSERVABLE CLASS", 204),
         ("READING INTERPRETATION", 374), ("CANDIDATE ACTION", 580)]
for h,x in heads:
    text(x+9, 465, h, 8.1, MUTED, bold=True)

rows = [
    (
        "L4 end -> L5 start", "Return-sweep candidate",
        "Line navigation. A correction may still follow.",
        "Current / next-line cue [G24]",
        "Show while following or as the switch begins; test timing.",
        PALE_BLUE, BLUE_D
    ),
    (
        "w12 -> w13 -> w12", "Word revisit",
        "Could be useful rereading or place finding.",
        "No forced cue; test anchor [S14]",
        "Do not block returns or equate them with failure.",
        PALE_TEAL, TEAL_D
    ),
    (
        "w12 -> w12 + time", "Refixation + residence",
        "Could reflect word inspection or careful processing.",
        "Word magnifier trial [G24]",
        "Only when the exact word is reliable; compare no cue.",
        PALE_AMBER, AMBER
    ),
    (
        "L4 time high;<br/>word advance slow", "Pace descriptor",
        "Text, task, tracking error, or deliberate pace may explain it.",
        "Focus region trial [R10, P13]",
        "No direct benefit evidence; keep nearby text legible.",
        PALE_CORAL, CORAL
    ),
]
for i,row in enumerate(rows):
    seq,cl,interp,action,detail,fill,accent = row
    y=361-i*91
    rect(40,y,762,82,WHITE,FAINT,10)
    rect(40,y,5,82,accent,radius=2)
    para(50,y+60,seq,143,10.0,color=NAVY,bold=True,max_h=44)
    para(214,y+60,cl,145,9.1,color=accent,bold=True,max_h=43)
    para(384,y+61,interp,177,8.9,max_h=54)
    para(590,y+63,action,197,8.9,color=accent,bold=True,max_h=28)
    para(590,y+34,detail,194,8.1,color=MUTED,max_h=30)
    line(198,y+12,198,y+69,FAINT)
    line(368,y+12,368,y+69,FAINT)
    line(574,y+12,574,y+69,FAINT)

rect(40, 52, 762, 31, PALE_CORAL, radius=8)
text(54, 64, "If gaze / word / line assignment is ambiguous:", 9, CORAL, bold=True)
text(333, 64, "BREAK -> unclassified -> no new gaze-triggered aid", 9, NAVY, font="Consolas")
C.showPage()


# Page 5 - user-facing states
base(5, "RENDER", "How the aid would appear in NewsMead",
     "Screen states below are interface mockups, not screenshots. Position, contrast, width, and duration are parameters to test.")

def phone(x, title, mode, badge, color, caption1, caption2):
    centered(x+80, 476, title, 10, NAVY, bold=True)
    rect(x, 175, 160, 282, NAVY, radius=18)
    rect(x+6, 182, 148, 268, WHITE, radius=13)
    rect(x+49, 441, 62, 4, NAVY, radius=2)
    rect(x+6, 415, 148, 25, BLUE_D, radius=2)
    text(x+17, 423, "NewsMead", 10, WHITE, bold=True)
    text(x+16, 398, "City opens new parks", 8.8, NAVY, bold=True)
    lines = [
        "Residents visited the new",
        "riverside park on Sunday.",
        "The paths connect nearby",
        "streets to the waterfront.",
        "More signs will be added",
        "later this month."
    ]
    ys = [371,346,321,296,271,246]
    if mode=="line":
        rect(x+10,315,140,21,PALE_BLUE,radius=3)
        rect(x+10,315,4,21,BLUE,radius=1)
    if mode=="focus":
        C.setFillColor(colors.HexColor("#F1F4F8"))
        C.rect(x+10,342,140,44,fill=1,stroke=0)
        C.rect(x+10,238,140,47,fill=1,stroke=0)
        rect(x+10,288,140,52,PALE_TEAL,TEAL,5,1)
    for j,(s,yy) in enumerate(zip(lines,ys)):
        col = MUTED if mode=="focus" and j not in (2,3) else INK
        if mode=="word" and j==3:
            text(x+17,yy,"streets to the ",7.9,INK)
            target_x=x+17+pdfmetrics.stringWidth("streets to the ","Segoe",7.9)
            text(target_x,yy,"waterfront.",7.9,AMBER,bold=True)
            line(target_x,yy-2,target_x+pdfmetrics.stringWidth("waterfront.","SegoeBold",7.9),
                 yy-2,AMBER,1)
        else:
            text(x+17,yy,s,7.9,col)
    if mode=="word":
        rect(x+12,212,132,28,PALE_AMBER,AMBER,5,1)
        text(x+20,220,"waterfront",11, NAVY,bold=True)
    if mode=="focus":
        rect(x+13,289,3,44,TEAL,radius=1)
    tag(x+12,192,badge,PALE_BLUE if mode=="line" else
        PALE_TEAL if mode=="focus" else
        PALE_AMBER if mode=="word" else FAINT,color,w=136,h=17)
    para(x,156,caption1,160,8.4,bold=True,color=NAVY,max_h=29)
    para(x,122,caption2,160,8.0,color=MUTED,max_h=47)

phone(43,"NO CUE","base","ARTICLE UNCHANGED",MUTED,
      "Default or uncertain gaze","The reader can also turn the aid off.")
phone(242,"LINE CUE","line","LINE-LEVEL CANDIDATE",BLUE_D,
      "At line following / switch","Current line stays legible; next-line cue must arrive in time.")
phone(441,"WORD MAGNIFIER","word","WORD GATE REQUIRED",AMBER,
      "At confirmed word inspection","A local popover preserves surrounding text.")
phone(640,"FOCUS REGION","focus","EXPERIMENTAL",TEAL_D,
      "At sustained slow progress","Soft emphasis keeps adjacent words and lines readable.")

rect(40, 48, 762, 47, WHITE, FAINT, 9)
text(54, 80, "ADAPTIVE STRENGTH TO TEST", 8.4, BLUE_D, bold=True)
para(54, 75, "Cue prominence, supported span, and time on screen can vary with a validated event and reader preference. "
     "No fixed four-level ladder is required; an aid fades or clears when the target is lost, the layout changes, or the reader turns it off.",
     731, 8.2, max_h=30)
C.showPage()


# Page 6 - implementation and evidence
base(6, "VALIDATE", "What we build, and what must be demonstrated",
     "The studies justify measurements and candidate aids; the phone detector and the full adaptive chain are NewsMead's tests.")

rect(40, 421, 762, 62, PALE_AMBER, radius=10)
text(55, 461, "CURRENT NEWSMEAD CHECKPOINT", 9, AMBER, bold=True)
text(55, 439, "47.5% exact word targets", 12, NAVY, bold=True)
text(270, 439, "61.6% guided exact line", 12, NAVY, bold=True)
text(541, 439, "4 eligible word transitions", 12, NAVY, bold=True)
text(55, 425, "Single visible-dot known-target run; not natural reading accuracy",
     7.8, MUTED)
text(541, 425, "First natural-reading diagnostic", 7.8, MUTED)

rect(40, 300, 365, 105, WHITE, FAINT, 10)
tag(53, 374, "ALREADY IN THE PROJECT", PALE_BLUE, BLUE_D)
para(53, 368, "MGazeNet observations and article geometry; provisional offline word-visit analysis; "
     "WORD / LINE / FOCUS / REENTRY renderer assets. The live weighted controller is a legacy comparator, "
     "not this scanpath policy.", 338, 8.8, max_h=69)
rect(419, 300, 383, 105, WHITE, FAINT, 10)
tag(432, 374, "PROPOSED IMPLEMENTATION", PALE_TEAL, TEAL_D)
para(432, 368, "Extend offline replay with word and line events. Port the validated causal extractor on-device. "
     "Add a separate pattern-to-aid policy, renderer parameters, abstention, and an action log "
     "that reproduces each choice.", 355, 8.8, max_h=69)

gates=[
    ("1  MEASUREMENT", "Exact word / line assignment, false returns, eligible coverage, lag, gaps."),
    ("2  CLASSIFICATION", "Held-out readers and passages: event precision, recall, and unclassified rate."),
    ("3  INTERVENTION", "Compare no cue, fixed cue, and event-conditioned aid on independent outcomes."),
]
for i,(head,body) in enumerate(gates):
    x=40+i*257
    rect(x,195,248,90,WHITE,FAINT,10)
    text(x+13,261,head,9,BLUE_D if i<2 else TEAL_D,bold=True)
    para(x+13,250,body,222,8.7,max_h=51)

text(40, 177, "HOW A BENEFIT CLAIM WOULD BE TESTED", 8.8, NAVY, bold=True)
rect(40, 68, 366, 95, WHITE, FAINT, 10)
text(53, 142, "FAIR CONDITIONS", 9, BLUE_D, bold=True)
para(53, 132, "Compare <b>no cue</b>, a <b>fixed cue</b> with matched exposure, and an "
     "<b>event-conditioned cue</b>. Randomize and counterbalance comparable passages. "
     "Tune on development readers; evaluate on different readers and passages.",
     340, 8.5, max_h=58)
rect(420, 68, 382, 95, WHITE, FAINT, 10)
text(433, 142, "INDEPENDENT OUTCOMES", 9, TEAL_D, bold=True)
para(433, 132, "Measure place finding, word recognition where appropriate, comprehension, "
     "reading time, wrong and unwanted cues, distraction, and preference. "
     "Fewer triggers or shorter triggering dwell alone do not prove reading benefit.",
     354, 8.5, max_h=58)
C.showPage()


# Page 7 - primary evidence and transfer limits
base(7, "EVIDENCE", "What the studies actually support",
     "Primary studies provide parts of the chain. None validates the complete phone scanpath -> aid -> benefit pathway for NewsMead.")

def evidence_card(x, y, title, finding, limit, url, ref,
                  secondary_url=None, secondary_label=None):
    rect(x, y, 365, 125, WHITE, FAINT, 10)
    tw=tag(x+12, y+92, ref, PALE_BLUE if ref in ("[O22]","[Y17]","[SG00/C22]") else
        PALE_TEAL if ref=="[G24]" else PALE_AMBER,
        BLUE_D if ref in ("[O22]","[Y17]","[SG00/C22]") else
        TEAL_D if ref=="[G24]" else AMBER, h=18)
    text(x+20+tw, y+98, title, 9.2, NAVY, bold=True)
    para(x+12, y+87, finding, 339, 8.0, max_h=38)
    para(x+12, y+49, "<b>Limit:</b> "+limit, 339, 7.8, color=MUTED, max_h=31)
    if secondary_url:
        first=url.replace("https://","")
        second=secondary_label or secondary_url.replace("https://","")
        text(x+12, y+10, first, 7.0, BLUE_D)
        second_x=x+20+pdfmetrics.stringWidth(first,"Segoe",7.0)
        text(second_x, y+10, "|  "+second, 7.0, BLUE_D)
        C.linkURL(url,(x+11,y+7,second_x-3,y+23),relative=0,thickness=0)
        C.linkURL(secondary_url,(second_x,y+7,x+354,y+23),relative=0,thickness=0)
    else:
        text(x+12, y+10, url.replace("https://",""), 7.2, BLUE_D)
        C.linkURL(url,(x+11,y+7,x+354,y+23),relative=0,thickness=0)

evidence_card(
    40,348,"Olivier et al. (2022)",
    "15 retained young adults, EyeLink 1000. Five signed word-step codes fed a task-specific HSMM.",
    "Supports a word-order representation; its phase names are not phone difficulty labels.",
    "https://doi.org/10.16910/jemr.15.4.5","[O22]")
evidence_card(
    437,348,"Yamaya et al. (2017)",
    "11 readers, Tobii TX300. Six segment classes aided word mapping (87%); F1 was 0.47 for Short Re-reading and 0.31 for Skipping.",
    "These class scores and mapping accuracy do not transfer to NewsMead gaze.",
    "https://doi.org/10.2197/ipsjjip.25.100","[Y17]")
evidence_card(
    40,212,"Wang et al. (2024), GazePrompt",
    "13 low-vision adults, 120 Hz desktop reading aloud. Line-switch time fell (p = .014); line accuracy and word outcomes had no significant improvement.",
    "Supports testing line/word aids, not their efficacy in silent phone news reading.",
    "https://doi.org/10.1145/3613904.3642878","[G24]")
evidence_card(
    437,212,"Schotter et al. (2014)",
    "A trailing-mask reading experiment found comprehension suffered when readers could not return to earlier text.",
    "A backward path may be productive; it is not automatically a failure or aid trigger.",
    "https://doi.org/10.1177/0956797614531148","[S14]")
evidence_card(
    40,76,"Rayner et al. (2010); Paterson et al. (2013)",
    "Moving/filtered-window experiments show that text outside fixation contributes to reading; Paterson included adults aged 65+.",
    "They do not show that dimming surrounding text helps slow line progression.",
    "https://doi.org/10.3758/PBR.17.6.834","[R10/P13]",
    "https://pubmed.ncbi.nlm.nih.gov/23066801/","PMID 23066801")
evidence_card(
    437,76,"Salvucci & Goldberg (2000); Carr et al. (2022)",
    "Fixation detection has method precedents; vertical drift and multiline assignment remain difficult even with laboratory tracking.",
    "Neither paper validates NewsMead thresholds, exact-line placement, or false-return rates.",
    "https://doi.org/10.1145/355017.355028","[SG00/C22]",
    "https://doi.org/10.3758/s13428-021-01554-0","Carr DOI 10.3758/s13428-021-01554-0")
C.showPage()

C.save()
print(OUT)
