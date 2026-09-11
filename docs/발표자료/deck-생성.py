# -*- coding: utf-8 -*-
"""연차ON 프로젝트 설명 자료(미완성본) — WBS 형태 PPT 생성 스크립트.

기능이 추가되면 이 파일의 WBS 항목·수치를 고치고 다시 돌린다.
장표를 손으로 고치면 다음 갱신 때 덮어써지므로 원본은 항상 이 스크립트다.

    pip install python-pptx
    python docs/발표자료/deck-생성.py "docs/발표자료/연차ON-프로젝트-설명(미완성본)-<날짜>.pptx"

수치(API 88·화면 17·테이블 21·설정 12 등)는 실측값이다. 문서에 적어 두고 끝내지 말고
코드를 다시 세어 맞춘다 — 엔티티 수를 잘못 세고 있던 전례가 있다.
"""
import sys
from pptx import Presentation
from pptx.util import Inches, Pt, Emu
from pptx.dml.color import RGBColor
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR

# ── 팔레트: 제품(연차ON)의 다크 네이비를 그대로 쓴다 ──────────────
GROUND = RGBColor(0x0B, 0x12, 0x20)
SURFACE = RGBColor(0x12, 0x1B, 0x2B)
SURFACE2 = RGBColor(0x18, 0x24, 0x38)
LINE = RGBColor(0x26, 0x34, 0x4C)
TEXT = RGBColor(0xE8, 0xEE, 0xFA)
MUTED = RGBColor(0x9A, 0xAB, 0xC4)
FAINT = RGBColor(0x6F, 0x80, 0x99)
ACCENT = RGBColor(0x4F, 0xB8, 0xE8)
OK = RGBColor(0x4F, 0xD3, 0x9C)
WARN = RGBColor(0xED, 0xB4, 0x4F)
CRIT = RGBColor(0xF2, 0x70, 0x7A)

SANS = "맑은 고딕"
MONO = "Consolas"

W, H = Inches(13.333), Inches(7.5)
prs = Presentation()
prs.slide_width, prs.slide_height = W, H
BLANK = prs.slide_layouts[6]


def bg(slide, color=GROUND):
    f = slide.background.fill
    f.solid()
    f.fore_color.rgb = color


def box(slide, l, t, w, h, fill=None, line=None, lw=1.0, radius=False):
    from pptx.enum.shapes import MSO_SHAPE
    shape = slide.shapes.add_shape(
        MSO_SHAPE.ROUNDED_RECTANGLE if radius else MSO_SHAPE.RECTANGLE, l, t, w, h)
    if radius:
        shape.adjustments[0] = 0.06
    if fill is None:
        shape.fill.background()
    else:
        shape.fill.solid()
        shape.fill.fore_color.rgb = fill
    if line is None:
        shape.line.fill.background()
    else:
        shape.line.color.rgb = line
        shape.line.width = Pt(lw)
    shape.shadow.inherit = False
    return shape


def text(slide, l, t, w, h, runs, size=14, color=TEXT, bold=False, font=SANS,
         align=PP_ALIGN.LEFT, spacing=1.25, anchor=MSO_ANCHOR.TOP):
    tb = slide.shapes.add_textbox(l, t, w, h)
    tf = tb.text_frame
    tf.word_wrap = True
    tf.vertical_anchor = anchor
    tf.margin_left = tf.margin_right = tf.margin_top = tf.margin_bottom = 0
    items = runs if isinstance(runs, list) else [runs]
    for i, item in enumerate(items):
        p = tf.paragraphs[0] if i == 0 else tf.add_paragraph()
        p.alignment = align
        p.line_spacing = spacing
        if isinstance(item, str):
            item = [(item, {})]
        for txt, opt in item:
            r = p.add_run()
            r.text = txt
            r.font.size = Pt(opt.get("size", size))
            r.font.bold = opt.get("bold", bold)
            r.font.color.rgb = opt.get("color", color)
            r.font.name = opt.get("font", font)
    return tb


def slide_base(title, eyebrow=None, wbs=None):
    s = prs.slides.add_slide(BLANK)
    bg(s)
    box(s, Inches(0.7), Inches(0.62), Inches(0.06), Inches(0.42), fill=ACCENT)
    if eyebrow:
        text(s, Inches(0.95), Inches(0.55), Inches(9.0), Inches(0.28),
             eyebrow, size=11, color=ACCENT, bold=True, font=MONO)
        text(s, Inches(0.95), Inches(0.85), Inches(11.6), Inches(0.5),
             title, size=27, bold=True)
    else:
        text(s, Inches(0.95), Inches(0.62), Inches(11.6), Inches(0.5),
             title, size=27, bold=True)
    if wbs:
        text(s, Inches(11.2), Inches(0.58), Inches(1.4), Inches(0.3),
             wbs, size=11, color=FAINT, font=MONO, align=PP_ALIGN.RIGHT)
    # 하단 미완성본 표시
    text(s, Inches(0.95), Inches(6.95), Inches(6.0), Inches(0.25),
         "미완성본 — 기능이 계속 추가됩니다 · 2026-09-10", size=9, color=FAINT, font=MONO)
    return s


def wbs_rows(slide, rows, top=Inches(1.65), left=Inches(0.95), width=Inches(11.5),
             rh=Inches(0.60), gap=Inches(0.07)):
    """rows: (번호, 기능명, 설명, 상태) — 상태: 'done'|'part'|'todo'|''"""
    y = top
    for no, name, desc, state in rows:
        box(slide, left, y, width, rh, fill=SURFACE, line=LINE, lw=0.75, radius=True)
        cy = y + Emu(int((rh - Inches(0.30)) / 2))
        text(slide, left + Inches(0.16), cy + Inches(0.03), Inches(0.72), Inches(0.3),
             no, size=11, color=ACCENT, font=MONO, bold=True)
        text(slide, left + Inches(0.95), cy, Inches(3.0), Inches(0.32),
             name, size=13, bold=True)
        text(slide, left + Inches(4.05), cy + Inches(0.03), Inches(6.5), Inches(0.3),
             desc, size=11, color=MUTED)
        if state:
            label, col = {"done": ("완료", OK), "part": ("부분", WARN),
                          "todo": ("예정", CRIT)}[state]
            text(slide, left + width - Inches(0.85), cy + Inches(0.04), Inches(0.7), Inches(0.28),
                 label, size=10, color=col, font=MONO, bold=True, align=PP_ALIGN.RIGHT)
        y = y + rh + gap
    return y


def stat_cards(slide, cards, top=Inches(1.9), left=Inches(0.95), width=Inches(11.5), h=Inches(1.35)):
    n = len(cards)
    gap = Inches(0.18)
    cw = int((width - gap * (n - 1)) / n)
    for i, (label, value, sub) in enumerate(cards):
        x = left + i * (cw + gap)
        box(slide, x, top, cw, h, fill=SURFACE, line=LINE, lw=0.75, radius=True)
        text(slide, x + Inches(0.22), top + Inches(0.18), cw - Inches(0.4), Inches(0.25),
             label, size=10, color=FAINT, font=MONO)
        text(slide, x + Inches(0.22), top + Inches(0.46), cw - Inches(0.4), Inches(0.45),
             value, size=26, bold=True, color=ACCENT)
        text(slide, x + Inches(0.22), top + Inches(0.98), cw - Inches(0.4), Inches(0.25),
             sub, size=10, color=MUTED)


def note_box(slide, top, msg, color=ACCENT, left=Inches(0.95), width=Inches(11.5), h=Inches(0.95)):
    box(slide, left, top, Inches(0.05), h, fill=color)
    box(slide, left + Inches(0.05), top, width - Inches(0.05), h, fill=SURFACE2, line=None)
    text(slide, left + Inches(0.3), top + Inches(0.16), width - Inches(0.6), h - Inches(0.3),
         msg, size=12, color=TEXT, spacing=1.35)


# ══════════════ 1. 표지 ══════════════
s = prs.slides.add_slide(BLANK)
bg(s)
box(s, Inches(0), Inches(0), Inches(0.14), H, fill=ACCENT)
text(s, Inches(1.1), Inches(1.9), Inches(10), Inches(0.4),
     "MLsoft 사내 연차·복리후생 관리 시스템 V2", size=14, color=ACCENT, font=MONO, bold=True)
text(s, Inches(1.1), Inches(2.35), Inches(11), Inches(1.2),
     "연차ON — 프로젝트 전체 설명", size=44, bold=True)
text(s, Inches(1.1), Inches(3.55), Inches(10.5), Inches(0.9),
     [[("작은 기능 하나까지 WBS로 훑어보는 자료입니다. ", {}),
       ("개발자가 코드를 열기 전에 구조를 잡고,", {"color": MUTED})],
      [("발표·설명 자리에서 그대로 쓸 수 있도록 만들었습니다.", {"color": MUTED})]],
     size=15, spacing=1.5)
box(s, Inches(1.1), Inches(4.75), Inches(3.5), Inches(0.5), fill=RGBColor(0x2E, 0x24, 0x11),
    line=WARN, lw=1.0, radius=True)
text(s, Inches(1.35), Inches(4.87), Inches(3.2), Inches(0.3),
     "⚠  미완성본 (Draft)", size=13, color=WARN, bold=True)
text(s, Inches(1.1), Inches(5.5), Inches(10), Inches(0.8),
     [[("2026-09-10 기준 · 기능이 계속 추가되는 중이며, 이 문서도 함께 갱신됩니다.", {"color": MUTED})],
      [("현재 상태의 단일 원본은 저장소의 docs/12-남은-작업.md 입니다.", {"color": FAINT, "font": MONO, "size": 11})]],
     size=12, spacing=1.5)

# ══════════════ 2. 읽는 법 ══════════════
s = slide_base("이 문서를 읽는 법", eyebrow="HOW TO READ")
rows = [
    ("①", "WBS 번호", "1.3 처럼 대분류.소분류로 매깁니다. 코드·문서를 찾을 때 그대로 검색어가 됩니다", ""),
    ("②", "상태 배지", "완료 = 코드와 테스트가 있음 · 부분 = 일부만 · 예정 = 아직 없음", ""),
    ("③", "근거", "슬라이드 아래 회색 글씨가 실제 파일·문서 위치입니다", ""),
]
wbs_rows(s, rows, top=Inches(1.6), rh=Inches(0.56), gap=Inches(0.06))
note_box(s, Inches(3.5),
         "미완성본으로 표시한 이유 — 이 시스템은 아직 운영에 올리지 않았고, 지금도 기능이 붙고 있습니다. "
         "여기 적힌 '완료'는 코드와 자동 테스트가 있다는 뜻이지 실사용 검증이 끝났다는 뜻이 아닙니다. "
         "실제 사람이 브라우저에서 확인해야 하는 항목은 별도 QA 체크리스트로 관리합니다.", color=WARN, h=Inches(1.2))
text(s, Inches(0.95), Inches(5.05), Inches(11.5), Inches(1.5),
     [[("발표에서 이렇게 쓰세요", {"bold": True, "size": 14})],
      [("· 5분 설명 → 3 · 4 · 7번 슬라이드(개요 · 규모 · WBS 지도)만", {"color": MUTED})],
      [("· 개발자 온보딩 → 8~18번 WBS 상세 + 19~24번 설계 결정", {"color": MUTED})],
      [("· 인수인계 → 26번(남은 작업) + 27번(배포 절차)", {"color": MUTED})]],
     size=13, spacing=1.6)

# ══════════════ 3. 한눈에 ══════════════
s = slide_base("무엇을 하는 시스템인가", eyebrow="OVERVIEW")
text(s, Inches(0.95), Inches(1.55), Inches(11.5), Inches(0.6),
     [[("사원이 연차를 신청하고, 팀장이 결재하고, 관리자가 정책과 조직을 관리한다.", {"bold": True, "size": 17})]],
     size=17)
cards = [
    ("사원", "연차 · 복리후생", "신청 · 취소 · 내 현황"),
    ("팀장", "결재", "승인 · 반려 · 팀 현황"),
    ("총관리자", "정책 · 조직 · 발송", "설정 · 구성원 · 이메일"),
]
stat_cards(s, cards, top=Inches(2.3), h=Inches(1.3))
note_box(s, Inches(3.95),
         "이전 버전(참고자료/MLsoft)을 분석해 다시 설계한 V2입니다. 연차 계산이 여러 곳에 흩어져 어긋나던 문제를 "
         "'도메인 메서드 한 곳'으로 모으고, 자동 적립·소멸을 스케줄러로 옮긴 것이 가장 큰 변화입니다.",
         h=Inches(1.0))
text(s, Inches(0.95), Inches(5.25), Inches(11.5), Inches(1.2),
     [[("핵심 흐름 한 줄", {"bold": True, "size": 13})],
      [("Google 로그인 → 온보딩(생일·입사일) → 연차 신청(선차감) → 팀장 결재 → 기산일마다 자동 리셋",
        {"color": ACCENT, "font": MONO, "size": 12})]],
     size=13, spacing=1.6)

# ══════════════ 4. 규모 ══════════════
s = slide_base("규모 — 2026-09-10 실측", eyebrow="SCALE")
stat_cards(s, [
    ("API", "88", "컨트롤러 18개"),
    ("화면", "17", "React 페이지"),
    ("DB", "21", "테이블 · ENUM 15컬럼"),
    ("자동 검증", "832", "백엔드 500 · 프론트 332"),
], top=Inches(1.7))
stat_cards(s, [
    ("도메인", "12", "패키지 단위 분리"),
    ("엔티티", "19", "JPA @Entity"),
    ("정책 설정", "12", "관리자가 조정 가능"),
    ("자동화 잡", "6", "일일 5 + 새해 1"),
], top=Inches(3.3))
note_box(s, Inches(5.0),
         "숫자는 문서에 적어 두고 끝내지 않고, 실제 코드를 세어 맞춥니다. 예전에 문서와 코드가 어긋나 "
         "엔티티 수를 잘못 세고 있던 적이 있어(BaseTimeEntity를 함께 셈) 지금은 실측 명령을 남겨 둡니다.",
         color=MUTED, h=Inches(0.95))

# ══════════════ 5. 스택·구조 ══════════════
s = slide_base("기술 스택과 구조", eyebrow="ARCHITECTURE")
left = Inches(0.95)
box(s, left, Inches(1.6), Inches(5.6), Inches(2.2), fill=SURFACE, line=LINE, lw=0.75, radius=True)
text(s, left + Inches(0.3), Inches(1.8), Inches(5.0), Inches(1.9),
     [[("백엔드", {"bold": True, "size": 14, "color": ACCENT})],
      [("Spring Boot 4 · Java 21 · JPA(Hibernate)", {"color": MUTED, "size": 12})],
      [("MySQL 8 (운영) · H2 (테스트)", {"color": MUTED, "size": 12})],
      [("도메인별 패키지 — controller/dto/entity/repository/service", {"color": MUTED, "size": 12})]],
     size=12, spacing=1.5)
box(s, Inches(6.85), Inches(1.6), Inches(5.6), Inches(2.2), fill=SURFACE, line=LINE, lw=0.75, radius=True)
text(s, Inches(7.15), Inches(1.8), Inches(5.0), Inches(1.9),
     [[("프론트엔드", {"bold": True, "size": 14, "color": ACCENT})],
      [("React 19 · Vite(Rolldown) · React Query", {"color": MUTED, "size": 12})],
      [("Tailwind v4 CSS-first — 토큰은 index.css @theme", {"color": MUTED, "size": 12})],
      [("api → hooks → page 3층 (쿼리 키 규칙으로 일괄 무효화)", {"color": MUTED, "size": 12})]],
     size=12, spacing=1.5)
note_box(s, Inches(4.05),
         "배포는 단일 컨테이너입니다 — 프론트 빌드 산출물을 백엔드 static에 넣어 하나의 포트(8080)로 서비스합니다. "
         "같은 오리진이라 CORS와 쿠키 문제가 처음부터 생기지 않습니다.", h=Inches(0.9))
text(s, Inches(0.95), Inches(5.2), Inches(11.5), Inches(1.2),
     [[("도메인 12개", {"bold": True, "size": 13})],
      [("audit · auth · common · credential · department · email · holiday · leave · policy · schedule · user · welfare",
        {"color": FAINT, "font": MONO, "size": 11})]],
     size=13, spacing=1.6)

# ══════════════ 6. 인증 흐름 ══════════════
s = slide_base("인증과 접근 제어", eyebrow="AUTH", wbs="WBS 1")
steps = [("1", "Google OAuth2", "회사 도메인 검증 후 자동 가입"),
         ("2", "JWT 발급", "HttpOnly 쿠키 · 세션 없음(STATELESS)"),
         ("3", "매 요청 가드", "OnboardingCheckInterceptor가 DB를 다시 본다")]
x = Inches(0.95)
for i, (n, t, d) in enumerate(steps):
    bx = x + i * Inches(3.95)
    box(s, bx, Inches(1.6), Inches(3.7), Inches(1.5), fill=SURFACE, line=LINE, lw=0.75, radius=True)
    text(s, bx + Inches(0.25), Inches(1.78), Inches(3.2), Inches(0.3), n, size=11, color=ACCENT, font=MONO, bold=True)
    text(s, bx + Inches(0.25), Inches(2.08), Inches(3.2), Inches(0.3), t, size=15, bold=True)
    text(s, bx + Inches(0.25), Inches(2.45), Inches(3.2), Inches(0.5), d, size=11, color=MUTED)
note_box(s, Inches(3.35),
         "JWT는 발급된 뒤의 상태 변화를 담지 못합니다. 그래서 매 요청마다 DB를 보고 ① 퇴직자 차단 "
         "② 역할이 바뀌었으면 권한 재구성 ③ 온보딩 미확정 차단 — 세 가지를 다시 검사합니다. "
         "관리자가 권한을 내리면 그 즉시 다음 요청부터 막힙니다.", h=Inches(1.15))
text(s, Inches(0.95), Inches(4.85), Inches(11.5), Inches(1.6),
     [[("놓치기 쉬운 것", {"bold": True, "size": 13, "color": WARN})],
      [("온보딩 완료 판별은 입사일이 있는지가 아니라 onboarding_status == COMPLETED 입니다.", {"color": MUTED})],
      [("입사일은 자가 신고라, 90일보다 과거를 적으면 연차 0으로 '승인 대기'에 들어가는데 그때도 입사일은 채워져 있습니다.", {"color": MUTED})],
      [("옛 기준을 쓰면 미확정 입사일이 스케줄러와 승인자 후보의 입력이 됩니다.", {"color": MUTED})]],
     size=12, spacing=1.55)

# ══════════════ 7. WBS 지도 ══════════════
s = slide_base("WBS 전체 지도", eyebrow="WORK BREAKDOWN")
groups = [
    ("1", "인증·온보딩", "로그인 · 온보딩 · 접근 제어", "7"),
    ("2", "연차", "신청 · 결재 · 잔액 · 이력", "17"),
    ("3", "복리후생", "신청 · 결재 · 정책", "12"),
    ("4", "캘린더·일정", "통합 캘린더 · 개인 일정 · 공휴일", "8"),
    ("5", "조직·구성원", "구성원 · 부서 · 퇴직/재입사", "21"),
    ("6", "정책·설정", "연차 정책 · 시스템 설정 12키", "5"),
    ("7", "이메일", "건별 알림 · 일괄 발송 · 연동", "13"),
    ("8", "자동화", "스케줄러 5잡 + 새해 공휴일", "—"),
    ("9", "감사·수명주기", "감사 로그 · 파기 · 재입사", "5"),
]
cols, y0 = 3, Inches(1.72)
cw, ch = Inches(3.75), Inches(1.28)
for i, (no, name, desc, api) in enumerate(groups):
    r, c = divmod(i, cols)
    bx = Inches(0.95) + c * (cw + Inches(0.13))
    by = y0 + r * (ch + Inches(0.13))
    box(s, bx, by, cw, ch, fill=SURFACE, line=LINE, lw=0.75, radius=True)
    text(s, bx + Inches(0.22), by + Inches(0.16), Inches(0.5), Inches(0.3), no, size=13, color=ACCENT, font=MONO, bold=True)
    text(s, bx + Inches(0.7), by + Inches(0.14), Inches(2.4), Inches(0.32), name, size=14, bold=True)
    text(s, bx + Inches(0.22), by + Inches(0.6), Inches(3.3), Inches(0.5), desc, size=11, color=MUTED)
    text(s, bx + cw - Inches(0.75), by + Inches(0.16), Inches(0.5), Inches(0.28),
         ("API " + api) if api != "—" else "잡 6", size=9, color=FAINT, font=MONO, align=PP_ALIGN.RIGHT)
note_box(s, Inches(6.05),
         "이 번호는 코드와 문서를 찾을 때 그대로 검색어가 됩니다 — 이어지는 슬라이드가 같은 번호 체계를 씁니다.",
         color=MUTED, h=Inches(0.62))



def src(slide, msg, top=Inches(6.55)):
    text(slide, Inches(0.95), top, Inches(11.5), Inches(0.3), msg,
         size=9.5, color=FAINT, font=MONO)


# ══════════════ WBS 1 ══════════════
s = slide_base("1. 인증 · 온보딩", eyebrow="WBS 1 — AUTH & ONBOARDING", wbs="API 7")
wbs_rows(s, [
    ("1.1", "Google 로그인", "회사 도메인만 통과 · 처음 들어온 계정은 자동 가입", "done"),
    ("1.2", "JWT 쿠키 발급", "HttpOnly 쿠키 · 서버 세션 없음 · 로그아웃 시 즉시 만료", "done"),
    ("1.3", "온보딩 입력", "생일 · 입사일 · 직급을 사원이 직접 입력", "done"),
    ("1.4", "자동 승인 판정", "입사일이 최근 90일 안이면 즉시 완료, 밖이면 관리자 승인 대기", "done"),
    ("1.5", "대기 중 1회 수정", "잘못 적은 입사일을 승인 전에 스스로 한 번 고칠 수 있다", "done"),
    ("1.6", "관리자 온보딩 결재", "대기 목록 · 승인 · 반려 — 승인 시점에 연차를 산정", "done"),
    ("1.7", "전역 요청 가드", "매 요청 DB 재조회 — 퇴직 · 역할 변경 · 온보딩 미완료 차단", "done"),
], top=Inches(1.55))
src(s, "domain/auth · security/ · OnboardingCheckInterceptor  |  화면: 로그인 · 온보딩 · 구성원>온보딩 승인")

# ══════════════ WBS 2-A ══════════════
s = slide_base("2. 연차 — 신청과 결재", eyebrow="WBS 2 — ANNUAL LEAVE (1/2)", wbs="API 17")
wbs_rows(s, [
    ("2.1", "연차 신청", "여러 날짜를 한 건으로 · 종일 / 오전 반차 / 오후 반차", "done"),
    ("2.2", "신청 검증", "주말 · 공휴일 · 지난 날짜 · 중복 기간 · 1건당 날짜 상한", "done"),
    ("2.3", "승인자 지정", "기본 승인자 + 서브 승인자 · 본인 건은 본인이 결재 못 함 (총관리자만 예외)", "done"),
    ("2.4", "승인 / 반려", "사유를 남긴다 · 반려하면 선차감했던 잔액을 되돌린다", "done"),
    ("2.5", "취소", "승인 전·미래 날짜만이면 바로 취소, 지난 날짜가 섞이면 취소 요청 → 재결재", "done"),
    ("2.6", "처리 이력", "신청별 처리 내역 · 내가 결재한 건 · 내가 낸 건", "done"),
    ("2.7", "팀 · 전사 조회", "팀장은 팀, 관리자는 전사 + 연차 사용률 집계", "done"),
], top=Inches(1.55))
src(s, "domain/leave/{controller,service}  |  화면: 대시보드 · 캘린더 · 결재함 · 팀 현황 · 이력")

# ══════════════ WBS 2-B ══════════════
s = slide_base("2. 연차 — 잔액이 계산되는 방식", eyebrow="WBS 2 — ANNUAL LEAVE (2/2)", wbs="API 17")
box(s, Inches(0.95), Inches(1.5), Inches(11.5), Inches(0.72), fill=SURFACE2, line=LINE, lw=0.75, radius=True)
text(s, Inches(1.25), Inches(1.68), Inches(11), Inches(0.4),
     [[("잔여 = base_days(정책 부여) + bonus_days(복리후생 가산) − use_days(사용·대기)",
        {"font": MONO, "size": 15, "color": ACCENT, "bold": True})]], size=15)
wbs_rows(s, [
    ("2.8", "신청 시 선차감", "승인이 아니라 신청(PENDING) 시점에 깎는다 — 중복 소진 방지", "done"),
    ("2.9", "회차 창", "[기산일, +1년) 안의 날짜만 지금 잔액에서 깎는다", "done"),
    ("2.10", "다음 회차 예약", "다음 1회차까지 미리 신청 가능 · 한도는 따로 계산", "done"),
    ("2.11", "당겨쓰기", "부족분을 advance_days로 기록 → 다음 기산일에 상환", "done"),
    ("2.12", "동시 신청 방지", "@Version 낙관적 락 — 두 창에서 동시에 내도 초과되지 않음", "done"),
    ("2.13", "잔액 요약", "현재 회차 · 다음 회차 예약 · 결재 대기 일수를 나눠서 보여준다", "done"),
], top=Inches(2.45), rh=Inches(0.58), gap=Inches(0.07))
src(s, "User.java(도메인 메서드) · LeaveService · docs/09  |  자세한 이유는 뒤 설계 결정 장에")

# ══════════════ WBS 3 ══════════════
s = slide_base("3. 복리후생", eyebrow="WBS 3 — WELFARE", wbs="API 12")
wbs_rows(s, [
    ("3.1", "정책 등록", "구분(경조·포상 등) · 대상 · 지급 일수를 관리자가 정의", "done"),
    ("3.2", "정책 목록 · 수정 · 삭제", "같은 구분+대상 조합 중복은 막는다", "done"),
    ("3.3", "복리후생 신청", "정책을 고르고 사유와 기간을 적어 낸다", "done"),
    ("3.4", "승인 / 반려", "승인하면 그만큼 bonus_days(보너스 연차)로 가산", "done"),
    ("3.5", "취소", "연차와 같은 상태 전이를 공유한다 (PENDING · CANCEL_PENDING …)", "done"),
    ("3.6", "이력", "내 신청 · 내 결재 · 전사 이력", "done"),
], top=Inches(1.6))
note_box(s, Inches(5.85),
         "복리후생 승인 결과가 연차 잔액(bonus_days)으로 흘러 들어갑니다 — 두 도메인이 만나는 유일한 지점입니다.",
         color=MUTED, h=Inches(0.6))
src(s, "domain/welfare  |  화면: 복리후생 · 관리>복리후생 정책  |  RequestStatus를 연차와 공유")

# ══════════════ WBS 4 ══════════════
s = slide_base("4. 캘린더 · 개인 일정 · 공휴일", eyebrow="WBS 4 — CALENDAR", wbs="API 8")
wbs_rows(s, [
    ("4.1", "통합 캘린더", "연차 · 개인 일정 · 공휴일을 한 화면에서 본다", "done"),
    ("4.2", "날짜 눌러 신청", "여러 날 선택 → 오른쪽 패널에서 바로 신청", "done"),
    ("4.3", "개인 일정", "외근 · 출장 · 재택 · 교육 — 결재 없고 연차도 안 깎인다", "done"),
    ("4.4", "일정 메모", "본인과 총관리자에게만 응답에 담긴다", "done"),
    ("4.5", "공휴일 동기화", "공공데이터 API에서 연 단위로 가져와 저장", "done"),
    ("4.6", "키보드 · 모바일", "← → 로 월 이동 · 모바일은 하단 시트로 선택", "done"),
], top=Inches(1.6), rh=Inches(0.5), gap=Inches(0.06))
note_box(s, Inches(5.35),
         "개인 일정을 연차와 같은 테이블에 넣지 않은 이유 — 잔액을 안 깎고 결재도 없어서, 한 엔티티에 얹으면 "
         "신청·결재·취소 흐름마다 “이건 건너뛰기” 분기가 생깁니다. 그 분기 산재가 예전 버전의 버그 원인이었습니다.",
         color=MUTED, h=Inches(1.0))
src(s, "domain/schedule · domain/holiday · CalendarPage.jsx", top=Inches(6.6))

# ══════════════ WBS 5-A ══════════════
s = slide_base("5. 조직과 구성원", eyebrow="WBS 5 — ORGANIZATION (1/2)", wbs="API 21")
wbs_rows(s, [
    ("5.1", "구성원 목록", "검색 · 부서 · 역할 필터 · 재직 / 퇴직 탭", "done"),
    ("5.2", "역할 변경", "사원 / 팀장 / 총관리자 — 마지막 관리자는 강등 못 함", "done"),
    ("5.3", "부서 이동", "팀장이면 후임을 먼저 지정해야 옮길 수 있다", "done"),
    ("5.4", "연차 수동 보정", "관리자가 부여 일수를 직접 조정 (입사일 정정 등)", "done"),
    ("5.5", "부서 관리", "트리 구조 · 상위 부서 · 팀장 지정 · 비활성화", "done"),
    ("5.6", "미배정 부서", "이름 변경 · 이동 · 삭제가 막힌 시스템 기본 부서", "done"),
    ("5.7", "내 정보", "직급(과장 · 선임연구원 …)을 사원이 직접 입력", "done"),
], top=Inches(1.55))
src(s, "domain/user · domain/department  |  화면: 관리>구성원 · 관리>부서 · 내 정보")

# ══════════════ WBS 5-B ══════════════
s = slide_base("5. 퇴직 · 데이터 파기 · 재입사", eyebrow="WBS 5 — LIFECYCLE (2/2)", wbs="API 21")
wbs_rows(s, [
    ("5.8", "퇴직 처리", "그 즉시 로그인까지 막힌다 · 본인 계정은 처리 불가", "done"),
    ("5.9", "퇴직 취소", "잘못 누른 퇴직을 되돌린다 (데이터 그대로)", "done"),
    ("5.10", "데이터 파기", "행은 남기고 이름 · 이메일 · 사유 본문을 지운다 (익명화)", "done"),
    ("5.11", "파기 보류", "감사 · 분쟁 중인 사원을 파기 대상에서 제외", "done"),
    ("5.12", "자동 파기 모드", "퇴직 3년 경과 → 30일 예고 메일 → 자동 파기", "done"),
    ("5.13", "재입사", "이전 근속을 구간으로 보존하고 연차는 새로 시작", "done"),
], top=Inches(1.6), rh=Inches(0.5), gap=Inches(0.06))
note_box(s, Inches(5.35),
         "파기는 행 삭제가 아니라 익명화입니다. 통계와 감사 로그가 무너지지 않게 뼈대는 남기고 개인을 특정할 수 있는 "
         "값만 제거합니다 — 이름은 퇴직사원#123, 이메일은 무효 주소로 바뀌고 자유 텍스트는 비웁니다.",
         color=MUTED, h=Inches(1.0))
src(s, "User.purge() · RetireePurgeService · docs/설계-초안/퇴직자-데이터-파기-설계", top=Inches(6.6))

# ══════════════ WBS 6 ══════════════
s = slide_base("6. 정책 · 시스템 설정", eyebrow="WBS 6 — POLICY", wbs="API 5")
wbs_rows(s, [
    ("6.1", "연차 정책 표", "근속 연수 구간별 부여 일수 — 관리자가 수정", "done"),
    ("6.2", "시스템 설정 12키", "동작을 바꾸는 값들을 화면에서 조정", "done"),
], top=Inches(1.55), rh=Inches(0.5))
text(s, Inches(0.95), Inches(2.85), Inches(11.5), Inches(0.3),
     "설정 12개 (2026-09-10 · PolicyConfigKey enum 기준)", size=13, bold=True)
keys = [
    ("연차 당겨쓰기 허용", "끄면 잔여 부족 시 신청 자체가 거절"),
    ("당겨쓰기 상한", "기본 5일 · 최대 25일"),
    ("신청 1건당 날짜 수", "기본 30일"),
    ("다음 회차 예약 허용", "기본 켜짐 · 끄면 현재 회차만"),
    ("보너스 연차 이월", "기본 꺼짐"),
    ("월차 적립 상한", "1년 미만 사원 · 기본 11일"),
    ("온보딩 자동 승인 기간", "기본 90일 · 밖이면 관리자 승인"),
    ("대기 중 입사일 수정", "기본 켜짐 · 1회 한정"),
    ("소진 안내 기준일", "안내 대상 판정 기준 · 기본 30일"),
    ("자동 발송 주기", "NONE / D30 / D60 / D90 / QUARTER"),
    ("퇴직자 파기 모드", "MANUAL(기본) / AUTO"),
    ("퇴직자 보존 기간", "기본 3년 · 3~10년 (근로기준법 기준)"),
]
for i, (k, v) in enumerate(keys):
    r, c = divmod(i, 2)
    bx = Inches(0.95) + c * Inches(5.8)
    by = Inches(3.25) + r * Inches(0.53)
    box(s, bx, by, Inches(5.6), Inches(0.46), fill=SURFACE, line=LINE, lw=0.5, radius=True)
    text(s, bx + Inches(0.18), by + Inches(0.11), Inches(2.6), Inches(0.28), k, size=11, bold=True)
    text(s, bx + Inches(2.9), by + Inches(0.13), Inches(2.55), Inches(0.26), v, size=9.5, color=MUTED)
src(s, "PolicyConfigKey enum 한 곳이 키·타입·기본값·범위·라벨을 정의 — 상수 한 줄이면 관리자 화면까지 따라온다")

# ══════════════ WBS 7 ══════════════
s = slide_base("7. 이메일 · 외부 연동", eyebrow="WBS 7 — EMAIL", wbs="API 13")
wbs_rows(s, [
    ("7.1", "건별 알림", "신청 접수 · 결재 결과 · 소진 안내 · 파기 예고", "done"),
    ("7.2", "발송 큐", "실패 자동 재시도 · 서버가 중간에 죽어도 멈춘 건을 되살린다", "done"),
    ("7.3", "일괄 발송", "대상 목록을 골라 한 번에 · 1회 한도 있음", "done"),
    ("7.4", "양식 관리", "제목 · 본문 편집 + 치환 변수 미리보기", "done"),
    ("7.5", "발송 이력", "상태 · 실패 사유 · 실패 건만 재발송", "done"),
    ("7.6", "외부 연동", "메일 SMTP · 공휴일 API 키 등록, 연결 테스트", "done"),
], top=Inches(1.6), rh=Inches(0.5), gap=Inches(0.06))
note_box(s, Inches(5.35),
         "자격 증명은 저장만 되고 절대 다시 내려오지 않습니다 — 조회 응답에는 마스킹된 값만 담습니다. "
         "관리자 화면을 여는 것만으로 앱 비밀번호가 새는 경로를 처음부터 만들지 않기 위해서입니다.",
         color=WARN, h=Inches(1.0))
src(s, "domain/email · domain/credential  |  전부 SYSTEM_ADMIN 전용", top=Inches(6.6))

# ══════════════ WBS 8 ══════════════
s = slide_base("8. 자동화 — 매일 00:10에 도는 5개 잡", eyebrow="WBS 8 — SCHEDULER", wbs="잡 6")
jobs = [("①", "기산일 리셋", "1주년마다 연차 재부여\n당겨쓴 빚 상환"),
        ("②", "월차 적립", "1년 미만 사원에게\n입사일+N개월마다 1일"),
        ("③", "생일 반차", "생일이면 보너스\n0.5일 가산"),
        ("④", "소진 안내", "잔여가 남은 사원에게\n안내 메일"),
        ("⑤", "퇴직자 파기", "자동 모드일 때\n예고 후 익명화")]
x0 = Inches(0.95)
cw = Inches(2.16)
for i, (n, t, d) in enumerate(jobs):
    bx = x0 + i * (cw + Inches(0.18))
    box(s, bx, Inches(1.6), cw, Inches(1.75), fill=SURFACE, line=LINE, lw=0.75, radius=True)
    text(s, bx + Inches(0.18), Inches(1.74), Inches(1.8), Inches(0.3), n, size=15, color=ACCENT, bold=True)
    text(s, bx + Inches(0.18), Inches(2.08), Inches(1.85), Inches(0.32), t, size=12.5, bold=True)
    text(s, bx + Inches(0.18), Inches(2.45), Inches(1.85), Inches(0.8),
         [l for l in d.split("\n")], size=9.5, color=MUTED, spacing=1.3)
    if i < 4:
        text(s, bx + cw + Inches(0.01), Inches(2.3), Inches(0.16), Inches(0.3),
             "›", size=16, color=FAINT, align=PP_ALIGN.CENTER)
note_box(s, Inches(3.55),
         "이 순서는 취향이 아니라 데이터 의존성입니다. 리셋이 보너스 연차를 갈아 끼우므로 ③이 먼저면 생일 반차가 그날 증발하고, "
         "②가 먼저면 1주년에 하루짜리 유령 적립이 남습니다. ④가 맨 뒤인 것도 같은 이유 — 앞 잡들이 잔액을 바꾸므로 먼저 돌면 "
         "안내 메일이 갱신 전 숫자를 적어 보냅니다.", color=WARN, h=Inches(1.15))
text(s, Inches(0.95), Inches(5.05), Inches(11.5), Inches(1.4),
     [[("· 사원 1명 = 트랜잭션 1개. 전체를 한 트랜잭션으로 묶으면 한 사람의 충돌로 전원이 롤백됩니다", {"color": MUTED})],
      [("· 새해 첫날에는 공휴일 동기화 잡이 한 번 더 돕니다 (그래서 잡은 6개)", {"color": MUTED})],
      [("· 테스트에서는 크론이 돌지 않습니다 — @Profile(!test) 가드", {"color": MUTED})]],
     size=12, spacing=1.6)
src(s, "domain/leave/scheduler · docs/09-스케줄러-설계.md", top=Inches(6.6))

# ══════════════ WBS 9 ══════════════
s = slide_base("9. 감사 로그와 데이터 수명주기", eyebrow="WBS 9 — AUDIT", wbs="API 5")
wbs_rows(s, [
    ("9.1", "관리자 감사 로그", "누가 · 언제 · 누구에게 · 무엇을 했는지 전부 남긴다", "done"),
    ("9.2", "액션 필터", "역할 변경 · 퇴직 · 파기 등 행위 종류로 검색", "done"),
    ("9.3", "연차 리셋 이력", "기산일마다 이월 · 상환 스냅샷을 남긴다", "done"),
    ("9.4", "파기 기록의 예외", "파기해도 감사 로그 행은 남긴다 — 단 이름은 담기지 않는다", "done"),
    ("9.5", "근속 구간", "재입사 사원의 이전 근무 기간을 별도 테이블로 보존", "done"),
], top=Inches(1.6), rh=Inches(0.52), gap=Inches(0.06))
note_box(s, Inches(4.8),
         "감사 로그를 파기 대상에서 뺀 것은 의도된 예외입니다. 언제 무엇을 파기했는지까지 지우면 파기 자체를 "
         "증명할 수 없습니다. 대신 로그 본문에 이름을 넣지 않아 로그만으로 개인을 특정하지 못하게 했습니다.",
         color=MUTED, h=Inches(1.0))
src(s, "domain/audit · employment_periods · LeaveResetHistory", top=Inches(6.2))


# ══════════════ 설계 결정 도입 ══════════════
s = slide_base("여기부터는 “왜 이렇게 만들었는가”", eyebrow="DESIGN DECISIONS")
text(s, Inches(0.95), Inches(1.55), Inches(11.5), Inches(0.9),
     [[("기능 목록만 보면 놓치는 것들이 있습니다. 코드를 고칠 때 반드시 알아야 하는, "
        "그리고 한 번씩 실제로 사고가 났던 결정 다섯 가지입니다.", {"color": MUTED})]],
     size=14, spacing=1.5)
items = [
    ("A", "회차 창", "연차를 언제 깎을지 — 날짜가 속한 회차로 판단한다"),
    ("B", "파생 필드", "당겨쓴 일수는 저장하는 값이 아니라 계산되는 값이다"),
    ("C", "스케줄러 경계", "순서와 트랜잭션 단위가 곧 데이터 정합성이다"),
    ("D", "스키마 함정", "테스트가 절대 못 잡고 기동도 막지 못하는 변경이 있다"),
    ("E", "보안 판단", "막을 것과 열어 둘 것을 나눈 기준"),
]
y = Inches(2.7)
for k, t, d in items:
    box(s, Inches(0.95), y, Inches(11.5), Inches(0.66), fill=SURFACE, line=LINE, lw=0.75, radius=True)
    text(s, Inches(1.2), y + Inches(0.18), Inches(0.5), Inches(0.3), k, size=14, color=ACCENT, font=MONO, bold=True)
    text(s, Inches(1.85), y + Inches(0.17), Inches(2.6), Inches(0.32), t, size=14, bold=True)
    text(s, Inches(4.6), y + Inches(0.2), Inches(7.4), Inches(0.3), d, size=12, color=MUTED)
    y = y + Inches(0.76)

# ══════════════ A. 회차 창 ══════════════
s = slide_base("A. 회차 창 — 미래 날짜를 지금 잔액에서 깎지 않는다", eyebrow="DECISION A")
text(s, Inches(0.95), Inches(1.45), Inches(11.5), Inches(0.35),
     "연차는 회사 기준 연도가 아니라 사원마다 다른 “입사 기념일(기산일)”을 기준으로 부여됩니다.",
     size=13, color=MUTED)
# 타임라인
ty = Inches(2.35)
box(s, Inches(1.0), ty, Inches(4.6), Inches(0.62), fill=RGBColor(0x14, 0x33, 0x44), line=ACCENT, lw=1.0, radius=True)
text(s, Inches(1.2), ty + Inches(0.16), Inches(4.2), Inches(0.3),
     "현재 회차  [2026-03-01,  2027-03-01)", size=12, color=ACCENT, font=MONO, bold=True)
box(s, Inches(5.75), ty, Inches(4.6), Inches(0.62), fill=SURFACE, line=LINE, lw=1.0, radius=True)
text(s, Inches(5.95), ty + Inches(0.16), Inches(4.2), Inches(0.3),
     "다음 회차  [2027-03-01,  2028-03-01)", size=12, color=MUTED, font=MONO)
box(s, Inches(10.5), ty, Inches(1.95), Inches(0.62), fill=SURFACE2, line=None, radius=True)
text(s, Inches(10.7), ty + Inches(0.16), Inches(1.6), Inches(0.3),
     "그 이후 = 거절", size=11, color=CRIT, font=MONO)

rows = [
    ("2026-11-20 하루 신청", "현재 회차 창 안 → use_days 즉시 −1", OK),
    ("2027-05-10 하루 신청", "다음 회차 → 지금 잔액은 그대로, 예약분으로만 집계", ACCENT),
    ("2028-02-01 신청", "다음 1회차를 넘어감 → 거절 (LEAVE_DATE_TOO_FAR)", CRIT),
]
y = Inches(3.3)
for a, b, c in rows:
    box(s, Inches(0.95), y, Inches(11.5), Inches(0.6), fill=SURFACE, line=LINE, lw=0.6, radius=True)
    text(s, Inches(1.2), y + Inches(0.16), Inches(3.3), Inches(0.3), a, size=12, bold=True, font=MONO)
    text(s, Inches(4.7), y + Inches(0.17), Inches(7.5), Inches(0.3), b, size=12, color=c)
    y = y + Inches(0.68)
note_box(s, Inches(5.45),
         "왜 나눴나 — 다음 기산일 이후의 날짜는 아직 부여되지도 않은 연차입니다. 그걸 지금 잔액에서 깎으면 "
         "올해 쓸 수 있는 날이 부당하게 줄고, 기산일이 지나면 같은 날이 두 번 깎이는 형태로 어긋납니다. "
         "그래서 신청·복구·리셋 이월이 전부 같은 “회차 창” 하나를 기준으로 판단합니다.", h=Inches(0.9))
src(s, "불변식: use_days = 살아 있는 신청 중 현재 회차 창 안의 날짜 합", top=Inches(6.55))

# ══════════════ B. 파생 필드 ══════════════
s = slide_base("B. 당겨쓴 일수는 “저장”이 아니라 “계산”이다", eyebrow="DECISION B")
box(s, Inches(0.95), Inches(1.5), Inches(11.5), Inches(0.75), fill=SURFACE2, line=LINE, lw=0.75, radius=True)
text(s, Inches(1.25), Inches(1.7), Inches(11), Inches(0.4),
     [[("advance_days = max(0,  use_days − base_days − bonus_days)",
        {"font": MONO, "size": 16, "color": ACCENT, "bold": True})]], size=16)
text(s, Inches(0.95), Inches(2.5), Inches(11.5), Inches(1.6),
     [[("겪은 문제", {"bold": True, "size": 14, "color": WARN})],
      [("당겨쓴 일수를 여러 곳에서 각자 더하고 빼다 보니, 어떤 경로로 취소하느냐에 따라 값이 달라졌습니다. "
        "화면마다 다른 숫자가 나오는 전형적인 형태입니다.", {"color": MUTED})]],
     size=12.5, spacing=1.5)
text(s, Inches(0.95), Inches(4.0), Inches(11.5), Inches(2.0),
     [[("고친 방식", {"bold": True, "size": 14, "color": OK})],
      [("· 이 필드에 값을 쓰는 코드는 User.syncAdvanceDays() 하나뿐입니다", {"color": MUTED})],
      [("· 잔액 3필드를 바꾸는 도메인 메서드 6개가 마지막에 반드시 그걸 부릅니다 (리셋도 예외 없이)", {"color": MUTED})],
      [("· 저장 직전 @PrePersist/@PreUpdate가 한 번 더 재계산합니다 — 그물이지 대체재는 아닙니다", {"color": MUTED})],
      [("· 채무 계산식도 User.carryOverDebt 한 곳뿐 — 이력이 자기 식을 갖고 있던 게 실제 결함이었습니다", {"color": MUTED})]],
     size=12.5, spacing=1.5)
src(s, "같은 원칙: 다음 회차 예약분도 필드로 두지 않고 leave_dates에서 집계한다")

# ══════════════ C. 스케줄러 경계 ══════════════
s = slide_base("C. 스케줄러에서 순서와 트랜잭션이 곧 정합성", eyebrow="DECISION C")
cards2 = [
    ("실행 순서", "리셋 → 월차 → 생일 → 안내 → 파기",
     "바꾸면 생일 반차가 증발하거나\n1주년에 유령 적립이 남는다"),
    ("트랜잭션 단위", "사원 1명 = 1트랜잭션",
     "낙관적 락이 있어 한 덩어리로 묶으면\n1명 충돌에 전원 롤백"),
    ("루프의 위치", "진입점에서 사원별로 돈다",
     "서비스가 자기 메서드를 부르면\n프록시를 안 타 경계가 안 생긴다"),
]
for i, (t, v, d) in enumerate(cards2):
    bx = Inches(0.95) + i * Inches(3.9)
    box(s, bx, Inches(1.6), Inches(3.65), Inches(2.1), fill=SURFACE, line=LINE, lw=0.75, radius=True)
    text(s, bx + Inches(0.25), Inches(1.8), Inches(3.2), Inches(0.3), t, size=11, color=FAINT, font=MONO)
    text(s, bx + Inches(0.25), Inches(2.12), Inches(3.2), Inches(0.5), v, size=13, bold=True, color=ACCENT)
    text(s, bx + Inches(0.25), Inches(2.75), Inches(3.2), Inches(0.85),
         [l for l in d.split("\n")], size=10.5, color=MUTED, spacing=1.35)
text(s, Inches(0.95), Inches(4.0), Inches(11.5), Inches(2.2),
     [[("작지만 값비쌌던 두 가지", {"bold": True, "size": 14, "color": WARN})],
      [("· 월차 지급일은 직전 지급일 +1개월이 아니라 “입사일 + N개월”로 계산합니다. 한 달씩 더하면 "
        "말일 클램프가 누적돼(1/31 → 2/28 → 3/28) 지급일이 앞당겨집니다", {"color": MUTED})],
      [("· 지급 횟수는 날짜에서 역산하지 않고 카운터로 셉니다. 역산하면 1/31과 2/28 사이가 “0개월”이라 "
        "매달 다시 지급됩니다", {"color": MUTED})],
      [("· 리셋의 이월 집계는 회차마다 그 회차 기산일로 다시 셉니다. 마지막 기준으로 한 번에 계산하면 "
        "1차 연도 귀속분이 한 회차 일찍 빠집니다", {"color": MUTED})]],
     size=12, spacing=1.45)
src(s, "docs/09-스케줄러-설계.md — 검산 포함")

# ══════════════ D. 스키마 함정 ══════════════
s = slide_base("D. 테스트가 절대 못 잡는 변경이 있다", eyebrow="DECISION D")
text(s, Inches(0.95), Inches(1.45), Inches(11.5), Inches(0.35),
     "enum에 상수 한 줄을 추가하는 것도 스키마 변경입니다. 이건 실제로 두 번 겪었습니다.", size=13, color=MUTED)
tr = [
    ("왜 테스트가 못 잡나", "테스트 DB(H2)는 매번 엔티티에서 스키마를 새로 만들어 새 값이 항상 들어 있다", CRIT),
    ("왜 기동도 안 막나", "운영의 스키마 검증은 ENUM 값 목록까지 보지 않는다 — 기동은 정상", CRIT),
    ("그래서 어디서 터지나", "그 값을 처음 저장하는 사용자 요청에서 500. 컬럼 누락보다 조용하다", CRIT),
    ("실제로 먼저 터진 것", "ENUM보다 NOT NULL 해제 누락이 먼저였다 (파기 시 사유 본문 비우기)", WARN),
    ("깔아 둔 그물", "SchemaEnumConsistencyTest가 코드와 schema.sql을 전수 대조해 먼저 깨뜨린다", OK),
]
y = Inches(2.05)
for a, b, c in tr:
    box(s, Inches(0.95), y, Inches(11.5), Inches(0.66), fill=SURFACE, line=LINE, lw=0.6, radius=True)
    text(s, Inches(1.2), y + Inches(0.19), Inches(3.2), Inches(0.3), a, size=12.5, bold=True, color=c)
    text(s, Inches(4.6), y + Inches(0.2), Inches(7.5), Inches(0.3), b, size=11.5, color=MUTED)
    y = y + Inches(0.75)
note_box(s, Inches(5.9),
         "엔티티를 바꾸면 두 가지를 함께 해야 합니다 — 빈 DB용 schema.sql과, 이미 데이터가 있는 DB용 backfill SQL. "
         "하나만 하면 배포가 기동에서 멈추는데, 그건 의도된 동작입니다.", color=WARN, h=Inches(0.8))

# ══════════════ E. 보안 판단 ══════════════
s = slide_base("E. 막을 것과 열어 둘 것을 나눈 기준", eyebrow="DECISION E")
sec = [
    ("본인은 본인을 결재할 수 없다", "총관리자만 예외입니다 — 그 위에 결재선이 없어 막으면 자기 연차를 처리할 사람이 없습니다."),
    ("본인 계정은 퇴직 처리 불가", "퇴직은 즉시 로그인까지 막혀 스스로 되돌릴 수 없습니다 — 역할 자가 강등보다 나쁩니다."),
    ("팀장인 사원은 부서를 못 옮긴다", "후임 팀장을 먼저 지정하게 막습니다. 자동으로 팀장 자리를 비우면 그 부서의 결재가 조용히 멈춥니다."),
    ("마지막 관리자는 강등 불가", "아무도 관리 화면에 못 들어가는 상태를 만들지 않습니다."),
    ("시크릿은 쓰기 전용", "SMTP 비밀번호·API 키는 어떤 조회 경로로도 원문이 내려가지 않습니다."),
    ("시연 데이터는 로컬 전용", "프로필 가드를 넓히면 시연 계정이 운영 DB에서 실제 사원과 섞입니다."),
]
y = Inches(1.6)
for a, b in sec:
    box(s, Inches(0.95), y, Inches(11.5), Inches(0.72), fill=SURFACE, line=LINE, lw=0.6, radius=True)
    text(s, Inches(1.2), y + Inches(0.1), Inches(11.0), Inches(0.28), a, size=12.5, bold=True)
    text(s, Inches(1.2), y + Inches(0.4), Inches(11.0), Inches(0.26), b, size=10.5, color=MUTED)
    y = y + Inches(0.8)
src(s, "선택의 공통 기준 — 되돌릴 수 없는 상태를 사용자가 혼자 만들 수 있으면 막는다", top=Inches(6.5))

# ══════════════ 검증 현황 ══════════════
s = slide_base("어디까지 확인했나", eyebrow="VERIFICATION")
stat_cards(s, [
    ("백엔드 테스트", "500", "서비스 · 스케줄러 · 스키마 대조"),
    ("프론트 테스트", "332", "41개 파일"),
    ("정적 검사", "0", "lint 경고 · 빌드 정상"),
    ("브라우저 QA", "22", "화면 캡처로 기록"),
], top=Inches(1.65))
text(s, Inches(0.95), Inches(3.3), Inches(11.5), Inches(0.35),
     "브라우저로 직접 돌려서 잡은 것 (2026-09-09)", size=14, bold=True)
qa = [
    ("B-1", "주말·공휴일을 고른 채로 신청 버튼이 눌렸다", "선택 단계에서 막도록 수정", OK),
    ("B-2", "퇴직 탭에서 가로 스크롤 시 관리 버튼이 가려졌다", "관리 열 고정", OK),
    ("B-3", "재입사 모달에 직급 변경 결과가 안 보였다", "변화 있는 항목만 노출", OK),
    ("B-4", "캘린더에서 키보드로 월 이동이 안 됐다", "← → PageUp/Down · Home 지원", OK),
    ("B-5", "모바일에서 여러 날 선택이 어려웠다", "하단 시트 + 적용 버튼", OK),
]
y = Inches(3.75)
for a, b, c, col in qa:
    box(s, Inches(0.95), y, Inches(11.5), Inches(0.5), fill=SURFACE, line=LINE, lw=0.6, radius=True)
    text(s, Inches(1.2), y + Inches(0.13), Inches(0.6), Inches(0.28), a, size=11, color=ACCENT, font=MONO, bold=True)
    text(s, Inches(1.9), y + Inches(0.12), Inches(5.2), Inches(0.28), b, size=11.5)
    text(s, Inches(7.3), y + Inches(0.13), Inches(4.3), Inches(0.28), c, size=11, color=col)
    y = y + Inches(0.56)
src(s, "docs/13-QA-체크리스트.md · docs/설계-초안/사용자-QA-마무리-2026-09-08.md + 캡처 22장", top=Inches(6.55))

# ══════════════ 남은 작업 ══════════════
s = slide_base("남은 작업 — 그래서 미완성본입니다", eyebrow="OPEN ITEMS")
rem = [
    ("관리자 문의", "사원이 관리자에게 문의를 보내는 창구", "설계 전 · 기능 미착수", "todo"),
    ("트랜잭션 경계 정리", "일부 관리자 작업의 경계·스키마 재검토 (SEC-9)", "설계 필요", "todo"),
    ("운영 스키마 반영", "backfill SQL 18개를 운영 DB에 순서대로 적용", "배포 시 사람이 확인", "part"),
    ("마이그레이션 도구", "지금은 손으로 SQL 관리 — Flyway 도입 필요", "운영 전환 조건", "todo"),
    ("사람이 하는 QA", "권한·렌더·배포 설정 등 자동 테스트가 못 잡는 항목", "체크리스트로 관리", "part"),
    ("staging 검증", "실제 메일 발송·공휴일 API를 붙인 환경에서 확인", "환경 필요", "todo"),
]
y = Inches(1.65)
for a, b, c, st in rem:
    box(s, Inches(0.95), y, Inches(11.5), Inches(0.68), fill=SURFACE, line=LINE, lw=0.7, radius=True)
    text(s, Inches(1.2), y + Inches(0.1), Inches(3.4), Inches(0.28), a, size=13, bold=True)
    text(s, Inches(1.2), y + Inches(0.38), Inches(6.2), Inches(0.26), b, size=10.5, color=MUTED)
    text(s, Inches(7.8), y + Inches(0.22), Inches(3.0), Inches(0.28), c, size=11, color=MUTED)
    label, col = {"todo": ("예정", CRIT), "part": ("부분", WARN)}[st]
    text(s, Inches(11.55), y + Inches(0.22), Inches(0.7), Inches(0.28), label,
         size=10, color=col, font=MONO, bold=True, align=PP_ALIGN.RIGHT)
    y = y + Inches(0.74)
note_box(s, Inches(6.12),
         "현재 상태의 단일 원본은 docs/12-남은-작업.md 입니다. 이 장표와 어긋나면 그쪽이 맞습니다.",
         color=MUTED, h=Inches(0.55))

# ══════════════ 배포 ══════════════
s = slide_base("배포는 어떻게 하나", eyebrow="DEPLOY")
steps2 = [("1", "이미지 빌드", "프론트 빌드 결과를 백엔드 static에 넣어\n컨테이너 하나로 합친다"),
          ("2", "스키마 준비", "빈 DB면 schema.sql\n기존 DB면 backfill SQL 18개"),
          ("3", "기동 검사", "쿠키 보안 설정·허용 도메인이 비면\n서버가 아예 안 뜬다 (의도)")]
for i, (n, t, d) in enumerate(steps2):
    bx = Inches(0.95) + i * Inches(3.9)
    box(s, bx, Inches(1.6), Inches(3.65), Inches(2.0), fill=SURFACE, line=LINE, lw=0.75, radius=True)
    text(s, bx + Inches(0.25), Inches(1.8), Inches(3.2), Inches(0.3), n, size=11, color=ACCENT, font=MONO, bold=True)
    text(s, bx + Inches(0.25), Inches(2.1), Inches(3.2), Inches(0.32), t, size=14, bold=True)
    text(s, bx + Inches(0.25), Inches(2.55), Inches(3.2), Inches(0.9),
         [l for l in d.split("\n")], size=10.5, color=MUTED, spacing=1.35)
box(s, Inches(0.95), Inches(3.9), Inches(11.5), Inches(0.62), fill=SURFACE2, line=LINE, lw=0.75, radius=True)
text(s, Inches(1.25), Inches(4.07), Inches(11), Inches(0.3),
     "docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build",
     size=12.5, color=ACCENT, font=MONO)
text(s, Inches(0.95), Inches(4.8), Inches(11.5), Inches(1.6),
     [[("배포 전 반드시 확인", {"bold": True, "size": 14, "color": WARN})],
      [("· 운영은 Hibernate가 테이블을 만들지 않습니다 — 스키마는 사람이 넣습니다", {"color": MUTED})],
      [("· backfill SQL은 전부 멱등하게 써 두었으니 두 번 돌아도 안전합니다", {"color": MUTED})],
      [("· 시크릿 파일은 git과 도커 빌드 컨텍스트 둘 다에서 빼야 합니다 (별개입니다)", {"color": MUTED})],
      [("· 관리자 지정은 최초 가입 때만 적용됩니다 — 기존 계정 승격은 DB 수정 + 재로그인", {"color": MUTED})]],
     size=12, spacing=1.5)

# ══════════════ 마무리 ══════════════
s = prs.slides.add_slide(BLANK)
bg(s)
box(s, Inches(0), Inches(0), Inches(0.14), H, fill=ACCENT)
text(s, Inches(1.1), Inches(1.5), Inches(10), Inches(0.4),
     "WRAP UP", size=12, color=ACCENT, font=MONO, bold=True)
text(s, Inches(1.1), Inches(1.95), Inches(11), Inches(0.7),
     "여기까지가 지금의 전부입니다", size=34, bold=True)
text(s, Inches(1.1), Inches(3.0), Inches(11), Inches(1.2),
     [[("· 사원이 내고, 팀장이 결재하고, 관리자가 정책을 정하는 흐름은 끝까지 동작합니다", {"color": MUTED})],
      [("· 자동 적립·소멸·파기는 매일 도는 잡이 대신 합니다", {"color": MUTED})],
      [("· 아직 운영에 올리지 않았고, 관리자 문의 등 몇 가지가 남아 있습니다", {"color": MUTED})]],
     size=14, spacing=1.6)
box(s, Inches(1.1), Inches(4.5), Inches(11.1), Inches(1.5), fill=SURFACE, line=LINE, lw=0.75, radius=True)
text(s, Inches(1.45), Inches(4.75), Inches(10.4), Inches(1.1),
     [[("피드백을 받고 싶은 부분", {"bold": True, "size": 14, "color": ACCENT})],
      [("① 회차 창 규칙이 실제 인사 운영과 맞는지  ② 퇴직자 파기 기본값(수동 / 3년 보존)이 적절한지  "
        "③ 재입사자 연차를 새로 시작하는 게 맞는지  ④ 관리자 문의 기능의 형태", {"color": MUTED, "size": 12.5})]],
     size=13, spacing=1.6)
text(s, Inches(1.1), Inches(6.4), Inches(11), Inches(0.3),
     "미완성본 · 2026-09-10 · 최신 상태는 저장소 docs/12-남은-작업.md",
     size=10, color=FAINT, font=MONO)

for _i, _sl in enumerate(prs.slides, start=1):
    if _i == 1:
        continue
    text(_sl, Inches(12.0), Inches(6.93), Inches(0.42), Inches(0.25), str(_i),
         size=10, color=FAINT, font=MONO, align=PP_ALIGN.RIGHT)

prs.save(sys.argv[1] if len(sys.argv) > 1 else "deck.pptx")
print("slides:", len(prs.slides.__iter__.__self__._sldIdLst))
