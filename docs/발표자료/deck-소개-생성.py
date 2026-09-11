# -*- coding: utf-8 -*-
"""MLsoft 연차·복리후생 관리 시스템 V2 소개·보고용 PPT 생성. 원본은 이 스크립트이며 슬라이드는 직접 수정하지 않는다.

사용법:
    python -X utf8 docs/발표자료/deck-소개-생성.py
    python -X utf8 docs/발표자료/deck-소개-생성.py --output 다른경로.pptx
    python -X utf8 docs/발표자료/deck-소개-생성.py --export-png 임시검수폴더

필수: python-pptx 1.0.2, Pillow. POWERPNT가 없을 때만 COM으로 PNG를 내보낸다.
POWERPNT 실행 중에는 COM을 호출하지 않고 기하 검사 결과만 보고한다.
모든 흐름도·아키텍처·표는 편집 가능한 PowerPoint 네이티브 개체다.
2026-09-11 코드 대조: 취소 시점, 총관리자 자기 결재, 파기 전 재입사,
기존 감사 로그 보존 범위, 이메일 총 3회 시도 상한을 브리프보다 우선한다.
"""

from __future__ import annotations

import argparse
import json
import math
from pathlib import Path
import re
import subprocess
from functools import lru_cache

from PIL import ImageFont

from pptx import Presentation
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_SHAPE, MSO_CONNECTOR
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR, MSO_AUTO_SIZE
from pptx.oxml.xmlchemy import OxmlElement
from pptx.util import Inches, Pt


# 읽기용 자료에 맞춘 밝은 바탕과 연차ON의 네이비·파랑 계열.
FONT = "맑은 고딕"
BG = "F7F9FC"
INK = "172B49"
MUTED = "53657D"
BLUE = "245BCE"
TEAL = "087E8B"
LINE = "8C9CB2"
PALE = "EAF0FA"
WHITE = "FFFFFF"
GREEN = "176B50"
GREEN_BG = "E5F3ED"
RED = "A13B4A"
RED_BG = "FAE9EC"
GOLD = "8C610D"
GOLD_BG = "FFF2D3"
NAVY = "0D203D"
WIDTH, HEIGHT = 13.333333, 7.5
prs = Presentation()
prs.slide_width, prs.slide_height = Inches(WIDTH), Inches(HEIGHT)
prs.core_properties.title = "MLsoft 사내 연차·복리후생 관리 시스템"
prs.core_properties.subject = "미완성본 · 2026-09-11 기준"
prs.core_properties.author = "MLsoft"
prs.core_properties.language = "ko-KR"


def rgb(value):
    return RGBColor.from_string(value)


def fill(shape, color):
    shape.fill.solid()
    shape.fill.fore_color.rgb = rgb(color)


def format_frame(frame, content, size=18, color=INK, bold=False,
                 align=PP_ALIGN.LEFT, anchor=MSO_ANCHOR.TOP, margin=0):
    frame.clear()
    frame.word_wrap = True
    frame.auto_size = MSO_AUTO_SIZE.NONE
    frame.vertical_anchor = anchor
    frame.margin_left = frame.margin_right = Inches(margin)
    frame.margin_top = frame.margin_bottom = Inches(margin)
    for index, line in enumerate(content.split("\n")):
        p = frame.paragraphs[0] if index == 0 else frame.add_paragraph()
        p.alignment = align
        p.line_spacing = 1.13
        p.space_before = p.space_after = Pt(0)
        r = p.add_run()
        r.text = line
        r.font.name = FONT
        r.font.size = Pt(size)
        r.font.bold = bold
        r.font.color.rgb = rgb(color)
        # 동아시아 글꼴을 지정해 다른 PC의 테마 글꼴로 바뀌지 않도록 한다.
        prop = r._r.get_or_add_rPr()
        east = OxmlElement("a:ea")
        east.set("typeface", FONT)
        prop.append(east)
        prop.set("lang", "ko-KR")


def text(s, x, y, w, h, content, size=18, color=INK, bold=False,
         align=PP_ALIGN.LEFT, anchor=MSO_ANCHOR.TOP):
    shape = s.shapes.add_textbox(Inches(x), Inches(y), Inches(w), Inches(h))
    format_frame(shape.text_frame, content, size, color, bold, align, anchor)
    return shape


def rule(s, x, y, w, color=LINE, width=0.7):
    c = s.shapes.add_connector(MSO_CONNECTOR.STRAIGHT,
                               Inches(x), Inches(y), Inches(x + w), Inches(y))
    c.line.color.rgb = rgb(color)
    c.line.width = Pt(width)
    c.shadow.inherit = False
    return c


def node(s, x, y, w, h, title, body="", tone="normal", size=18, body_size=15):
    palette = {
        "normal": (WHITE, INK, LINE), "blue": (PALE, BLUE, BLUE),
        "green": (GREEN_BG, GREEN, GREEN), "red": (RED_BG, RED, RED),
        "gold": (GOLD_BG, GOLD, GOLD), "navy": (NAVY, WHITE, NAVY),
    }
    fc, tc, lc = palette[tone]
    shape = s.shapes.add_shape(MSO_SHAPE.RECTANGLE,
                              Inches(x), Inches(y), Inches(w), Inches(h))
    fill(shape, fc)
    shape.line.color.rgb = rgb(lc)
    shape.line.width = Pt(1.0)
    shape.shadow.inherit = False
    format_frame(shape.text_frame, title + ("\n" + body if body else ""),
                 size, tc, True, PP_ALIGN.CENTER, MSO_ANCHOR.MIDDLE, 0.10)
    for p in shape.text_frame.paragraphs[1:]:
        for run in p.runs:
            run.font.size = Pt(body_size)
            run.font.bold = False
    return shape


def decision(s, x, y, w, h, title, size=16):
    shape = s.shapes.add_shape(MSO_SHAPE.DIAMOND,
                              Inches(x), Inches(y), Inches(w), Inches(h))
    fill(shape, GOLD_BG)
    shape.line.color.rgb = rgb(GOLD)
    shape.line.width = Pt(1.1)
    shape.shadow.inherit = False
    format_frame(shape.text_frame, title, size, GOLD, True, PP_ALIGN.CENTER,
                 MSO_ANCHOR.MIDDLE, 0)
    shape.text_frame.word_wrap = False
    return shape


def arrow(s, points, color=LINE, dashed=False, both=False):
    """직교 선분을 네이티브 커넥터로 연결하고 마지막 선분에만 화살표를 붙인다."""
    # 화살표의 조건은 별도 텍스트로 배치해 선과 글자가 겹치지 않게 한다.
    for i, (start, end) in enumerate(zip(points, points[1:])):
        assert start[0] == end[0] or start[1] == end[1], "대각선 연결 금지"
        c = s.shapes.add_connector(MSO_CONNECTOR.STRAIGHT,
                                  Inches(start[0]), Inches(start[1]),
                                  Inches(end[0]), Inches(end[1]))
        c.line.color.rgb = rgb(color)
        c.line.width = Pt(1.5)
        c.shadow.inherit = False
        ln = c._element.spPr.get_or_add_ln()
        if dashed:
            dash = OxmlElement("a:prstDash")
            dash.set("val", "dash")
            ln.append(dash)
        if both and i == 0:
            start_arrow = OxmlElement("a:headEnd")
            start_arrow.set("type", "triangle")
            start_arrow.set("w", "sm")
            start_arrow.set("len", "sm")
            ln.append(start_arrow)
        if i == len(points) - 2:
            end_arrow = OxmlElement("a:tailEnd")
            end_arrow.set("type", "triangle")
            end_arrow.set("w", "sm")
            end_arrow.set("len", "sm")
            ln.append(end_arrow)


def label(s, x, y, w, content, color=MUTED, size=13):
    return text(s, x, y, w, 0.25, content, size, color, align=PP_ALIGN.CENTER)


def new_slide(title, subtitle="", source="", dark=False):
    s = prs.slides.add_slide(prs.slide_layouts[6])
    s.background.fill.solid()
    s.background.fill.fore_color.rgb = rgb(NAVY if dark else BG)
    if title:
        text(s, 0.65, 0.48, 12.0, 0.65, title, 32, WHITE if dark else INK, True)
    if subtitle:
        text(s, 0.68, 1.23, 12.0, 0.60, subtitle, 16.5, "B8CBE5" if dark else MUTED)
    text(s, 11.98, 7.03, 0.65, 0.25, f"{len(prs.slides):02}",
         11, "B8CBE5" if dark else MUTED, align=PP_ALIGN.RIGHT)
    if source:
        s.notes_slide.notes_text_frame.text = "내용 확인 근거(2026-09-11):\n" + source
    return s


def footnote(s, content, color=MUTED):
    text(s, 0.68, 6.63, 11.95, 0.40, content, 13, color)


ERD_ENTITIES = {}
ERD_RELATIONS = []
ERD_MARKS = set()


@lru_cache(maxsize=128)
def measure_font(size, bold=False):
    """맑은 고딕의 실제 글리프 폭을 4배 해상도로 측정한다."""
    filename = "malgunbd.ttf" if bold else "malgun.ttf"
    return ImageFont.truetype(str(Path("C:/Windows/Fonts") / filename), round(size * 4))


def text_width(value, size, bold=False):
    return measure_font(size, bold).getlength(value) / 4 / 72


def entity(s, name, x, y, columns, color=NAVY):
    """하나의 사각형에 진한 제목 띠와 편집 가능한 컬럼을 배치한다."""
    w, head, leading, pad = 2.30, .29, .157, .07
    lines = []
    for column in columns:
        # 긴 FK 참조만 둘째 줄로 내려 실제 컬럼명은 끊지 않는다.
        if text_width(column, 10) > w - 2 * pad and " → " in column:
            key, target = column.split(" → ", 1)
            lines.extend([key, "  → " + target])
        else:
            lines.append(column)
    h = head + .07 + len(lines) * leading + .035
    sh = s.shapes.add_shape(MSO_SHAPE.RECTANGLE, Inches(x), Inches(y), Inches(w), Inches(h))
    sh.name = "ERD|" + name
    sh.line.color.rgb = rgb(color)
    sh.line.width = Pt(.85)
    sh.shadow.inherit = False
    # 같은 위치에 두 색 정지점을 두어 상단 띠가 있는 단일 도형으로 만든다.
    sh.fill.gradient()
    grad = sh._element.spPr.xpath("./a:gradFill")[0]
    for item in list(grad):
        grad.remove(item)
    gs_list = OxmlElement("a:gsLst")
    stop = str(round(head / h * 100000))
    for pos, value in [("0", color), (stop, color), (stop, WHITE), ("100000", WHITE)]:
        gs = OxmlElement("a:gs")
        gs.set("pos", pos)
        srgb = OxmlElement("a:srgbClr")
        srgb.set("val", value)
        gs.append(srgb)
        gs_list.append(gs)
    grad.append(gs_list)
    linear = OxmlElement("a:lin")
    linear.set("ang", "5400000")
    linear.set("scaled", "0")
    grad.append(linear)
    tf = sh.text_frame
    format_frame(tf, name + "\n" + "\n".join(lines), 10, INK, margin=0)
    tf.margin_left = tf.margin_right = Inches(pad)
    tf.margin_top = Inches(.035)
    for i, p in enumerate(tf.paragraphs):
        p.line_spacing = Inches(head if i == 0 else leading)
        for run in p.runs:
            run.font.size = Pt(11 if i == 0 else 10)
            run.font.bold = i == 0
            run.font.color.rgb = rgb(WHITE if i == 0 else INK)
    ERD_ENTITIES[(len(prs.slides), name)] = sh
    return sh


def relation(s, child, fields, parent, points, color=BLUE, logical=False):
    """각 FK 열의 참조명과 이어지는 관계선. 같은 두 엔티티의 FK는 한 선으로 묶는다."""
    ERD_RELATIONS.extend((child, field, parent) for field in fields if not logical)
    for i, (start, end) in enumerate(zip(points, points[1:])):
        assert start[0] == end[0] or start[1] == end[1], "ERD 대각선 연결 금지"
        sh = s.shapes.add_connector(MSO_CONNECTOR.STRAIGHT, Inches(start[0]), Inches(start[1]),
                                   Inches(end[0]), Inches(end[1]))
        sh.name = f"관계|{child}|{','.join(fields)}|{parent}|{i}"
        sh.line.color.rgb = rgb(color)
        sh.line.width = Pt(1)
        sh.shadow.inherit = False
        if logical:
            dash = OxmlElement("a:prstDash")
            dash.set("val", "dash")
            sh._element.spPr.get_or_add_ln().append(dash)
    # 경로는 부모(1)에서 자식(N)으로 지정한다. 라벨은 선 옆 빈 공간에 둔다.
    for point, neighbor, value in [(points[0], points[1], "1"), (points[-1], points[-2], "N")]:
        key = (len(prs.slides), point, value)
        if key in ERD_MARKS:
            continue
        ERD_MARKS.add(key)
        dx, dy = neighbor[0] - point[0], neighbor[1] - point[1]
        if dx:
            tx = point[0] + .02 if dx > 0 else point[0] - .19
            ty = point[1] - .20 if value == "1" else point[1] + .02
        else:
            tx = point[0] - .20 if value == "1" else point[0] + .03
            ty = point[1] + .02 if dy > 0 else point[1] - .21
        mark = text(s, tx, ty, .17, .19, value, 11, color, True)
        mark.name = "관계수|" + child + "|" + parent + "|" + value


def rows(s, items, y=2.00, step=1.00):
    """소개 장표의 평면 행 구성. 별도 카드나 버튼을 만들지 않는다."""
    for i, (title, body) in enumerate(items):
        top = y + i * step
        text(s, 0.70, top, 0.6, 0.45, f"{i + 1:02}", 20, BLUE, True)
        text(s, 1.5, top, 10.95, 0.42, title, 21, INK, True)
        text(s, 1.5, top + 0.48, 10.95, 0.48, body, 16.5, MUTED)
        if i < len(items) - 1:
            rule(s, 1.5, top + step - 0.13, 10.8, "D5DDE9")


def table(s, headers, data, widths, x=0.68, y=1.95, row_h=0.46, sizes=None, header_h=None):
    # 머리글 행은 본문 행보다 낮게, 모든 셀은 세로 가운데 정렬. 표 셀은 tcPr의 anchor·여백을 따르므로
    # text_frame이 아니라 cell 속성으로 지정해야 실제로 적용된다.
    header_h = header_h if header_h is not None else min(0.5, max(0.42, row_h))
    total_h = header_h + row_h * len(data)
    shape = s.shapes.add_table(len(data) + 1, len(headers), Inches(x), Inches(y),
                               Inches(sum(widths)), Inches(total_h))
    t = shape.table
    for col, width in zip(t.columns, widths):
        col.width = Inches(width)
    rows = list(t.rows)
    rows[0].height = Inches(header_h)
    for row in rows[1:]:
        row.height = Inches(row_h)
    for i, values in enumerate([headers] + data):
        for j, value in enumerate(values):
            cell = t.cell(i, j)
            cell.fill.solid()
            cell.fill.fore_color.rgb = rgb(NAVY if i == 0 else (WHITE if i % 2 else PALE))
            size = 14.5 if sizes is None else sizes[j]
            format_frame(cell.text_frame, value, size,
                         WHITE if i == 0 else INK, i == 0,
                         PP_ALIGN.LEFT, MSO_ANCHOR.MIDDLE, 0)
            cell.vertical_anchor = MSO_ANCHOR.MIDDLE
            cell.margin_left = cell.margin_right = Inches(0.12)
            cell.margin_top = cell.margin_bottom = Inches(0.05)
            # 테이블의 테마 글꼴 및 기본 테두리를 명시적으로 대체한다.
            tcpr = cell._tc.get_or_add_tcPr()
            for side in ("lnL", "lnR", "lnT", "lnB"):
                edge = OxmlElement("a:" + side)
                edge.set("w", "6350")
                solid = OxmlElement("a:solidFill")
                color = OxmlElement("a:srgbClr")
                color.set("val", BG)
                solid.append(color)
                edge.append(solid)
                tcpr.append(edge)
    return shape


FLOW = "docs/11-프로젝트-흐름.md"
STATUS = "docs/12-남은-작업.md, 현재 상태 스냅샷"
LEAVE = "backend/src/main/java/com/mlsoft/backend/domain/leave/service/LeaveService.java"
EMAIL = "backend/src/main/java/com/mlsoft/backend/domain/email/service/EmailNotificationPublisher.java"
USER = "backend/src/main/java/com/mlsoft/backend/domain/user/service/UserService.java"


# 01. 표지 — 발송 목적과 기준일을 한눈에 보이게 한다.
s = new_slide("", source="docs/발표자료/브리프-소개-ppt-2026-09-11.md", dark=True)
text(s, 0.86, 1.95, 11.7, 2.10, "MLsoft 사내 연차·복리후생\n관리 시스템", 46, WHITE, True)
text(s, 0.92, 4.20, 11.5, 0.60, "시스템 구성 · 데이터베이스 · 기능 흐름", 24, "B8CBE5")
rule(s, 0.92, 5.08, 11.4, "365276")
text(s, 0.92, 5.47, 11.5, 0.45, "미완성본 · 2026-09-11 기준", 22, "84DCEA", True)

# 15. 전체 아키텍처.
s = new_slide("시스템 전체 구성", "브라우저 화면과 업무 서버를 하나의 애플리케이션 컨테이너로 배포합니다.", "CLAUDE.md, 아키텍처·배포\nDockerfile\ndocker-compose.prod.yml")
node(s, .72, 3.03, 2.22, 1.38, "브라우저", "React 화면", "normal", 24, 19)
node(s, 4.04, 2.72, 4.10, 1.85, "Spring Boot 서버", "화면 파일 제공 + 업무 API\n단일 앱 컨테이너 · 포트 8080", "navy", 24, 16)
arrow(s, [(2.94, 3.72), (4.04, 3.72)], BLUE, both=True)
label(s, 3.00, 3.15, .97, "요청·응답", BLUE)
for y, title, body in [(1.90, "Google OAuth2", "회사 계정 로그인"),
                       (3.10, "MySQL 8", "운영 데이터 저장"),
                       (4.30, "SMTP", "이메일 발송"),
                       (5.50, "공공데이터포털", "공휴일 정보 동기화")]:
    node(s, 9.52, y, 3.07, .88, title, body, size=18, body_size=14)
    center = y + .44
    arrow(s, [(8.14, 3.64), (8.76, 3.64), (8.76, center), (9.52, center)], LINE)
node(s, 4.04, 5.38, 4.10, .89, "내부 스케줄러", "매일 00:10 KST · 5개 작업", "blue", 19, 15)
arrow(s, [(6.09, 4.57), (6.09, 5.38)], BLUE, both=True)
footnote(s, "테스트 데이터베이스는 H2를 사용합니다. SMTP는 Gmail 앱 비밀번호 방식의 계정을 연동합니다.")

# 15b. 외부 연동 API — 셋 다 우리 장애로 번지지 않게 설계한 점을 같이 보여 준다.
s = new_slide("외부 연동 API", "세 가지 외부 서비스에 의존하며, 어느 하나가 멈춰도 업무 처리는 계속됩니다.",
              "backend/.../holiday/client/HolidayApiClient.java\nsecurity/CustomOAuth2UserService.java\nemail/service/MailSenderResolver.java\nLeaveScheduler.syncHolidaysForNewYear (0 5 0 1 1 *)")
table(s, ["외부 서비스", "용도 · 호출 시점", "실패하면", "인증 정보 보관"], [
    ["공공데이터포털\n특일정보 API",
     "공휴일 목록 조회 (연 단위)\n매년 1월 1일 00:05 자동 · 관리자 수동 동기화\n캐시가 비어 있을 때 최초 1회",
     "기존 공휴일 캐시 유지, 빈 결과로 계속\n연차 신청은 막히지 않음\n관리자 수동 동기화만 원인별 오류 표시",
     "관리자 화면에서 암호화 저장\n없으면 서버 환경변수"],
    ["Google OAuth2",
     "로그인 (프로필·이메일만 요청)\n회사 도메인 계정만 허용",
     "로그인 불가 · 안내 메시지\n허용 도메인이 아니면 거부",
     "서버 환경변수\n(클라이언트 ID · 시크릿)"],
    ["Gmail SMTP",
     "이메일 발송 (587 · STARTTLS)\n업무 사건 즉시 + 15분마다 재시도",
     "실패 이력 기록 · 최초 포함 총 3회 시도\n이후 관리자 수동 재발송\n업무 처리에는 영향 없음",
     "관리자 화면에서 암호화 저장\n없으면 서버 환경변수"],
], [2.2, 3.9, 3.55, 2.3], row_h=.92, sizes=[14, 13, 13, 13], header_h=.46)
text(s, .68, 6.26, 11.95, .29, "자격 증명은 마스킹해 표시합니다. 공휴일은 DB 캐시를 사용합니다.", 11, MUTED)
text(s, .68, 6.69, 11.95, .27, "메일은 업무 저장과 함께 큐에 적재 → 15분마다 재시도(최초 포함 총 3회) → 실패 건은 관리자 수동 재발송", 11, MUTED)

# 16. 백엔드 내부.
s = new_slide("서버 내부의 업무 구조", "12개 업무 영역을 나누고, 요청 처리와 업무 규칙·데이터 저장의 역할을 분리했습니다.", "CLAUDE.md, 계층 구조\nbackend/src/main/java/com/mlsoft/backend/domain/")
for x, title, body in [(0.73, "요청 접수", "입력과 역할 확인"), (3.87, "업무 규칙 처리", "잔액·승인자·상태 변경"),
                       (7.01, "저장소 접근", "조회와 저장"), (10.15, "업무 데이터", "상태와 이력 보관")]:
    node(s, x, 2.08, 2.47, 1.13, title, body, "blue" if x == 3.87 else "normal", 20, 14.5)
for a, b in [(3.20, 3.87), (6.34, 7.01), (9.48, 10.15)]:
    arrow(s, [(a, 2.64), (b, 2.64)])
text(s, .77, 3.73, 11.8, .45, "12개 업무 영역", 23, BLUE, True)
table(s, ["사람과 조직", "연차와 일정", "운영 지원"], [
    ["인증", "연차", "이메일"], ["구성원", "복리후생", "공휴일"],
    ["부서", "개인 일정", "외부 연동 자격 증명"], ["감사 기록", "정책", "공통 상태"],
], [3.94, 3.94, 3.94], y=4.29, row_h=.40, sizes=[16,16,16], header_h=.44)
text(s, .68, 6.56, 11.95, .40, "매 요청 서버가 재직·역할·온보딩 상태를 재확인 · 본인 결재 금지(총관리자 예외)\n마지막 총관리자 강등·퇴직 금지 · 본인 퇴직 처리 금지", 11, MUTED)

# 17. 프론트 구조.
s = new_slide("화면과 데이터의 연결", "17개 화면이 서버 데이터를 공통 방식으로 요청하고 갱신합니다.", "CLAUDE.md, 프론트엔드 데이터 흐름\nfrontend/src/pages/\nfrontend/src/api/index.js")
node(s, .75, 2.13, 3.28, 1.16, "서버 API 연결", "Axios로 요청·응답 처리", "normal", 22, 16)
node(s, 5.03, 2.13, 3.28, 1.16, "데이터 조회·캐시", "React Query로 공통 관리", "blue", 22, 16)
node(s, 9.31, 2.13, 3.28, 1.16, "업무 화면", "페이지가 데이터를 표시", "normal", 22, 16)
arrow(s, [(4.03, 2.71), (5.03, 2.71)], BLUE)
arrow(s, [(8.31, 2.71), (9.31, 2.71)], BLUE)
text(s, .77, 3.95, 5.75, .55, "공통 화면", 23, INK, True)
text(s, .77, 4.71, 5.53, 1.22,
     "대시보드 · 캘린더 · 내역 · 복리후생\n내 정보 · 결재 관리 · 팀 현황\n로그인 · 로그인 연결 · 온보딩", 17, MUTED)
text(s, 7.01, 3.95, 5.53, .55, "관리자 화면", 23, INK, True)
text(s, 7.01, 4.71, 5.53, 1.22,
     "구성원 · 부서 · 연차 정책 · 복리후생 정책\n처리 이력 · 이메일 · 외부 연동", 17, MUTED)
footnote(s, "권한에 맞춰 화면을 열고, 오류 알림을 공통 처리합니다. 디자인 값은 Tailwind v4의 CSS에서 관리합니다.")

# 15c. ERD 1. 실제 FK 18개를 12개 관계선으로 묶는다.
s = new_slide("ERD 1 · 사원·조직·연차·복리후생", "FK 열의 →는 참조 테이블, 1·N은 최대 관계 수입니다. 같은 두 테이블의 FK는 한 선으로 묶었습니다.",
              "db/schema.sql\n정정: department.parent_id는 논리 관계만 존재. 날짜별 행은 복합 UNIQUE이며 PK 선언 없음.")
a, b, c, d, e = .48, 3.03, 5.58, 8.13, 10.68
entity(s, "employment_periods", a, 2.10, ["id PK", "user_id FK → users", "seq", "hire_date", "retired_at"])
entity(s, "leave_reset_history", a, 4.58, ["id PK", "user_id FK → users", "reset_date", "prev_base_days", "new_base_days", "prev_use_days", "carried_bonus_days", "expired_days", "advance_settled"])
entity(s, "users", b, 2.10, ["id PK", "email", "name", "role", "department_id FK → department", "hire_date", "last_reset_date", "base_days", "bonus_days", "use_days", "advance_days", "onboarding_status", "is_active", "retired_at", "purged_at", "version"], BLUE)
entity(s, "department", b, 5.35, ["id PK", "name", "parent_id → department (논리)", "leader_id FK → users", "system_default", "active"])
entity(s, "leave_requests", c, 2.10, ["id PK", "user_id FK → users", "primary_approver_id FK → users", "sub_approver_id FK → users", "status", "leave_type", "days", "request_reason", "cancel_reason", "advance_used_days"])
entity(s, "leave_dates", c, 4.56, ["leave_requests_id FK", "  → leave_requests", "day", "UQ (leave_requests_id, day)"])
entity(s, "leave_action_history", c, 5.70, ["id PK", "leave_requests_id FK", "  → leave_requests", "user_id · actor_id FK → users", "action · comment"])
entity(s, "welfare_requests", d, 2.10, ["id PK", "user_id FK → users", "policy_id FK → welfare_policies", "primary_approver_id FK → users", "sub_approver_id FK → users", "status", "category", "target", "add_days", "reason"])
entity(s, "welfare_action_history", d, 4.98, ["id PK", "welfare_request_id FK", "  → welfare_requests", "user_id · actor_id FK → users", "action", "comment"])
entity(s, "welfare_policies", e, 2.10, ["id PK", "category", "target", "default_days", "default_evidence", "active"])
# 좌측은 근속·리셋, 우측은 신청·이력. 공유 선분은 같은 users 참조다.
relation(s, "employment_periods", ["user_id"], "users", [(b, 2.54), (a+2.30, 2.54)])
relation(s, "leave_reset_history", ["user_id"], "users", [(b, 3.00), (2.91, 3.00), (2.91, 4.98), (a+2.30, 4.98)])
relation(s, "leave_requests", ["user_id", "primary_approver_id", "sub_approver_id"], "users", [(b+2.30, 2.60), (c, 2.60)])
relation(s, "welfare_requests", ["user_id", "primary_approver_id", "sub_approver_id"], "users", [(4.85, 2.10), (4.85, 1.87), (8.48, 1.87), (8.48, 2.10)])
relation(s, "users", ["department_id"], "department", [(3.72, 5.35), (3.72, 5.007)])
relation(s, "department", ["leader_id"], "users", [(4.58, 5.007), (4.58, 5.35)], TEAL)
relation(s, "department", ["parent_id"], "department", [(3.03, 5.48), (2.91, 5.48), (2.91, 6.39), (3.03, 6.39)], GOLD, logical=True)
relation(s, "leave_dates", ["leave_requests_id"], "leave_requests", [(6.05, 4.065), (6.05, 4.56)], TEAL)
relation(s, "leave_action_history", ["leave_requests_id"], "leave_requests", [(7.88, 3.85), (8.00, 3.85), (8.00, 6.10), (7.88, 6.10)], TEAL)
relation(s, "leave_action_history", ["user_id", "actor_id"], "users", [(5.33, 4.75), (5.45, 4.75), (5.45, 6.18), (5.58, 6.18)])
relation(s, "welfare_requests", ["policy_id"], "welfare_policies", [(10.68, 2.82), (10.43, 2.82)], TEAL)
relation(s, "welfare_action_history", ["welfare_request_id"], "welfare_requests", [(9.35, 4.065), (9.35, 4.98)], TEAL)
relation(s, "welfare_action_history", ["user_id", "actor_id"], "users", [(5.33, 4.12), (5.45, 4.12), (5.45, 4.32), (10.55, 4.32), (10.55, 6.55), (9.75, 6.55), (9.75, 6.317)])
text(s, 10.70, 4.25, 2.24, 1.90, "점선: 부서의 논리 자기참조\n(실제 FK 제약 없음)\n\n날짜별 행: 복합 UQ\n근속 구간: 사원·순번 복합 UQ\n\nNULL 허용은 FK별로 다름", 11, MUTED)
text(s, .48, 6.96, 11.35, .21, "연차 일수: DECIMAL(4,1). 상태·종류: MySQL ENUM. 개인정보 파기는 행을 보존하고 식별값·사유 본문을 익명화합니다.", 11, MUTED)

# 15d. ERD 2. 실제 FK 9개, 독립 테이블 5개. users는 6장의 참조 상자다.
s = new_slide("ERD 2 · 이메일·일정·감사·정책", "users는 앞 장의 참조입니다. FK가 없는 정책·자격 증명·공휴일 테이블도 함께 표시합니다.", "db/schema.sql\n정정: schedule_dates는 복합 UNIQUE. 감사 로그의 actor_id와 target_user_id 모두 NULL 허용.")
entity(s, "users", a, 2.10, ["id PK", "name", "role"], BLUE)
entity(s, "email_history", b, 2.10, ["id PK", "user_id FK → users", "from_id FK → users", "email_type", "status", "retry_count", "sending_at", "sent_at", "error_message", "title"])
entity(s, "leave_reminder_dispatch", c, 2.10, ["id PK", "user_id FK → users", "email_history_id FK", "  → email_history", "cycle", "period_key", "reference_date", "next_reset_date", "remaining_days_snapshot", "result"])
entity(s, "schedule_entries", d, 2.10, ["id PK", "user_id FK → users", "schedule_type", "memo"])
entity(s, "schedule_dates", e, 2.10, ["schedule_entry_id FK", "  → schedule_entries", "day", "UQ (schedule_entry_id, day)"])
entity(s, "email_templates", a, 4.58, ["id PK", "template_key", "subject_template", "body_template", "version", "updated_by FK → users"])
entity(s, "admin_audit_log", b, 4.58, ["id PK", "actor_id FK → users (NULL 허용)", "target_user_id FK → users", "  (NULL 허용)", "action", "target_label", "before_value", "after_value"])
entity(s, "mail_credentials", c, 4.58, ["id PK", "provider", "username", "encrypted_secret", "active"])
entity(s, "holidays", d, 3.93, ["id PK", "date", "name", "year"])
entity(s, "leave_policy", e, 3.93, ["id PK", "years_of_service", "annual_leave_days", "active"])
entity(s, "leave_policy_config", d, 5.36, ["id PK", "name", "value"])
entity(s, "holiday_api_credentials", e, 5.36, ["id PK", "provider", "encrypted_api_key", "active"])
relation(s, "email_history", ["user_id", "from_id"], "users", [(2.78, 2.63), (3.03, 2.63)])
relation(s, "leave_reminder_dispatch", ["user_id"], "users", [(2.25, 2.10), (2.25, 1.87), (6.02, 1.87), (6.02, 2.10)])
relation(s, "schedule_entries", ["user_id"], "users", [(2.25, 2.10), (2.25, 1.87), (8.67, 1.87), (8.67, 2.10)])
relation(s, "leave_reminder_dispatch", ["email_history_id"], "email_history", [(5.33, 2.94), (5.58, 2.94)], TEAL)
relation(s, "email_templates", ["updated_by"], "users", [(1.18, 2.966), (1.18, 4.58)])
relation(s, "admin_audit_log", ["actor_id", "target_user_id"], "users", [(2.18, 2.966), (2.18, 4.31), (4.05, 4.31), (4.05, 4.58)])
relation(s, "schedule_dates", ["schedule_entry_id"], "schedule_entries", [(10.43, 2.64), (10.68, 2.64)], TEAL)
text(s, .48, 6.63, 7.3, .27, "소진 안내 중복 방지: UNIQUE (user_id, cycle, period_key). 날짜별 행은 복합 UQ이며 PK는 없습니다.", 11, MUTED)
text(s, .48, 6.96, 11.35, .21, "독립 테이블은 관계선을 두지 않습니다. 1·N은 최대 관계 수이며, NULL 허용 FK는 연결된 부모가 없을 수 있습니다.", 11, MUTED)

# 06. 신청과 결재.
s = new_slide("연차 신청과 결재", "현재 회차의 연차는 신청 즉시 차감됩니다. 승인 시 추가 차감은 없습니다.", LEAVE + "\n" + EMAIL)
node(s, .90, 2.18, 2.50, .95, "날짜·내용 작성", "승인자 확인 후 신청", size=18, body_size=14.5)
decision(s, 4.00, 1.96, 2.10, 1.40, "신청 가능?", 15)
node(s, 7.00, 2.18, 4.10, .95, "대기 상태 · 현재 회차 선차감", "승인자 두 명 중 한 명이 결재", "blue", 18, 14.5)
arrow(s, [(3.40, 2.66), (4.00, 2.66)])
arrow(s, [(6.10, 2.66), (7.00, 2.66)], BLUE)
label(s, 6.24, 2.20, .62, "가능", BLUE, 12)
node(s, .90, 4.47, 2.50, 1.10, "날짜·내용 수정", "검증·반려 사유 확인", "red", 18, 14)
arrow(s, [(5.05, 3.36), (5.05, 3.85), (2.15, 3.85), (2.15, 4.47)], RED)
text(s, 5.22, 3.47, 2.82, .28, "검증 실패: 사유 확인", 12.5, RED)
text(s, .94, 5.82, 3.15, .28, "수정 후 다시 신청", 14, RED, True)
# 검증 실패와 반려는 같은 수정 단계로 모아 왼쪽 여백으로 되돌린다.
arrow(s, [(.90, 5.02), (.48, 5.02), (.48, 2.66), (.90, 2.66)], RED)
decision(s, 8.55, 4.32, 2.10, 1.40, "결재 결과", 15)
arrow(s, [(9.60, 3.13), (9.60, 4.32)], BLUE)
node(s, 4.85, 4.47, 2.70, 1.10, "반려 · 잔액 복구", "반려 사유 입력", "red", 17, 14)
arrow(s, [(8.55, 5.02), (7.55, 5.02)], RED)
label(s, 7.63, 4.58, .82, "반려", RED, 12)
arrow(s, [(4.85, 5.02), (3.40, 5.02)], RED)
label(s, 3.48, 4.60, 1.27, "사유 확인", RED, 12)
node(s, 11.13, 4.47, 1.58, 1.10, "승인", "연차 사용", "green", 18, 14)
arrow(s, [(10.65, 5.02), (11.13, 5.02)], GREEN)
label(s, 10.67, 4.59, .43, "승인", GREEN, 11)
text(s, 5.23, 5.96, 7.22, .29, "확인 항목: 주말·공휴일·지난 날짜·중복·잔여일수", 13, MUTED)
text(s, .68, 6.46, 11.95, .56,
     "복리후생 신청도 같은 결재 흐름이며, 승인 시 보너스 연차로 가산합니다.\n메일: 신청 시 신청자 + 승인자 2명 / 결과는 재직 총관리자 전원도 수신합니다.", 12.5, MUTED)

# 07. 취소 — 브리프와 다른 현재 동작을 반영한다.
s = new_slide("연차 취소", "승인 여부와 사용 날짜에 따라 즉시 취소 또는 취소 결재로 나뉩니다.", LEAVE + ":413~479\n" + EMAIL)
node(s, .90, 2.25, 2.40, .96, "사원: 취소 요청", size=18)
decision(s, 4.00, 1.99, 2.40, 1.48, "승인된 건에\n지난 날짜 포함?", 14.5)
node(s, 7.20, 2.22, 2.20, 1.02, "즉시 취소", tone="green", size=20)
node(s, 10.00, 2.22, 2.58, 1.02, "취소 완료 · 잔액 복구", "필요하면 다시 신청\n8장 신청 단계로", "green", 16, 13)
arrow(s, [(3.30, 2.73), (4.00, 2.73)])
arrow(s, [(6.40, 2.73), (7.20, 2.73)], GREEN)
label(s, 6.42, 2.27, .74, "아니오", GREEN, 12)
arrow(s, [(9.40, 2.73), (10.00, 2.73)], GREEN)
node(s, 4.00, 4.15, 2.40, 1.02, "취소 결재 대기", "이때는 잔액 유지", "gold", 18, 14)
arrow(s, [(5.20, 3.47), (5.20, 4.15)], GOLD)
label(s, 5.35, 3.62, .55, "예", GOLD, 12)
decision(s, 7.00, 3.98, 2.00, 1.38, "취소 승인?", 15)
arrow(s, [(6.40, 4.67), (7.00, 4.67)])
# 즉시 취소와 취소 승인은 동일한 복구·재신청 단계로 합류한다.
arrow(s, [(8.00, 3.98), (8.00, 3.65), (11.29, 3.65), (11.29, 3.24)], GREEN)
text(s, 8.76, 3.73, 1.0, .22, "승인", 12, GREEN)
node(s, 10.00, 4.15, 2.58, 1.02, "승인 상태 유지", "잔액 복구 없음", "red", 18, 14)
arrow(s, [(9.00, 4.67), (10.00, 4.67)], RED)
label(s, 9.08, 4.20, .82, "거부", RED, 12)
arrow(s, [(11.29, 5.17), (11.29, 6.05), (.48, 6.05), (.48, 2.73), (.90, 2.73)], RED)
text(s, .94, 5.61, 6.65, .29, "취소 거부 후 다시 취소 요청 가능", 14, RED, True)
text(s, .68, 6.43, 11.95, .59,
     "승인 전 신청과 승인 후 오늘·미래 날짜만 있는 신청은 즉시 취소하며, 현재 회차 차감분을 복구합니다.\n메일: 취소 요청은 신청자 + 승인자 / 취소 결재 결과는 재직 총관리자 전원도 수신합니다.", 12.5, MUTED)

# 08. 회차별 잔액.
s = new_slide("연차 잔액이 계산되는 방식", "기산일은 연차가 새로 부여되는 기준일입니다. 신청 날짜별로 어느 회차인지 확인합니다.", "CLAUDE.md, 연차 잔액 모델\n" + LEAVE)
text(s, .75, 1.94, 11.90, .43, "잔여 연차 = 부여된 연차 + 보너스 연차 − 사용·대기 연차", 24, BLUE, True)
node(s, .80, 3.78, 2.50, 1.00, "날짜 선택·신청", "신청 날짜별 회차 구분", "blue", 18, 14)
decision(s, 4.10, 2.72, 3.10, 1.40, "잔여 충분 또는\n당겨쓰기\n허용·상한 내?", 14)
decision(s, 4.10, 4.65, 3.10, 1.40, "다음 회차 예약\n허용·한도 내?", 14)
arrow(s, [(3.30, 4.00), (3.65, 4.00), (3.65, 3.42), (4.10, 3.42)], BLUE)
text(s, 3.33, 3.05, .68, .23, "현재", 11.5, BLUE, align=PP_ALIGN.CENTER)
arrow(s, [(3.30, 4.55), (3.65, 4.55), (3.65, 5.35), (4.10, 5.35)])
text(s, 3.33, 5.59, .68, .23, "다음", 11.5, MUTED, align=PP_ALIGN.CENTER)
node(s, 8.10, 2.85, 4.40, 1.14, "접수 · 현재 회차 선차감", "부족분은 당겨쓰기로 기록\n다음 기산일의 새 연차에서 상환", "green", 18, 14)
arrow(s, [(7.20, 3.42), (8.10, 3.42)], GREEN)
label(s, 7.28, 2.99, .74, "가능", GREEN, 12)
node(s, 8.10, 4.77, 4.40, 1.14, "다음 회차 예약 접수", "현재 잔액 유지\n예약 한도에는 당겨쓰기 없음", "green", 18, 14)
arrow(s, [(7.20, 5.35), (8.10, 5.35)], GREEN)
label(s, 7.28, 4.91, .74, "가능", GREEN, 12)
node(s, .80, 2.65, 2.50, .95, "신청 제한", "날짜 줄여 재신청", "red", 17, 14)
node(s, .80, 5.23, 2.50, .92, "예약 불가·한도 초과", "날짜 줄여 재신청", "red", 16, 14)
# 현재 회차와 예약 실패를 위·아래로 나눠 되돌려서 선이 서로 교차하지 않는다.
arrow(s, [(5.65, 2.72), (5.65, 2.50), (2.05, 2.50), (2.05, 2.65)], RED)
text(s, 3.32, 2.65, .72, .23, "불가", 11.5, RED, align=PP_ALIGN.CENTER)
arrow(s, [(.80, 3.13), (.48, 3.13), (.48, 4.00), (.80, 4.00)], RED)
arrow(s, [(5.65, 6.05), (5.65, 6.33), (2.05, 6.33), (2.05, 6.15)], RED)
text(s, 5.84, 6.07, .83, .23, "불가", 11.5, RED)
arrow(s, [(.80, 5.69), (.48, 5.69), (.48, 4.55), (.80, 4.55)], RED)
footnote(s, "다음 1회차까지만 예약합니다. 현재 회차는 기산일부터 다음 기산일 전날까지입니다.")

# 09. 온보딩.
s = new_slide("첫 로그인과 입사 정보 확인", "온보딩이 완료되어야 다른 업무 화면을 사용할 수 있습니다.", "backend/src/main/java/com/mlsoft/backend/domain/auth/service/AuthService.java\n" + EMAIL)
node(s, .90, 2.18, 2.15, .95, "회사 계정 로그인", "회사 도메인 확인", size=17, body_size=14)
node(s, 3.50, 2.18, 2.40, .95, "자동 가입·정보 입력", "생일·입사일·직급", size=17, body_size=14)
decision(s, 6.50, 1.97, 2.35, 1.40, "최근 90일 내\n입사일인가?", 14.5)
node(s, 9.50, 2.18, 3.05, .95, "온보딩 완료 · 연차 산정", tone="green", size=18)
arrow(s, [(3.05, 2.67), (3.50, 2.67)])
arrow(s, [(5.90, 2.67), (6.50, 2.67)])
arrow(s, [(8.85, 2.67), (9.50, 2.67)], GREEN)
label(s, 8.90, 2.22, .54, "예", GREEN, 12)
node(s, 6.30, 3.83, 2.75, .95, "관리자 승인 대기", "연차 0일 · 총관리자 메일", "gold", 17, 13.5)
arrow(s, [(7.675, 3.37), (7.675, 3.83)], GOLD)
text(s, 7.83, 3.47, .9, .25, "아니오", 12, GOLD)
decision(s, 9.80, 3.62, 2.30, 1.37, "관리자 결재", 15)
arrow(s, [(9.05, 4.305), (9.80, 4.305)])
# 승인 결과는 처음의 자동 완료와 합류하고, 반려는 별도 재입력 단계를 거친다.
arrow(s, [(12.10, 4.305), (12.90, 4.305), (12.90, 2.67), (12.55, 2.67)], GREEN)
text(s, 12.18, 3.48, .66, .25, "승인", 12, GREEN)
node(s, 9.60, 5.45, 2.75, .93, "반려", "본인에게 결과 메일", "red", 18, 14)
arrow(s, [(10.95, 4.99), (10.95, 5.45)], RED)
text(s, 11.12, 5.08, .70, .25, "반려", 12, RED)
node(s, 6.30, 5.45, 2.75, .93, "입사일 재입력", "반려된 입사일 확인", "red", 18, 14)
arrow(s, [(9.60, 5.915), (9.05, 5.915)], RED)
node(s, 3.50, 3.83, 2.40, .95, "대기 중 1회 수정", "허용 시 입사일·생일 수정", "gold", 16.5, 12.5)
arrow(s, [(6.30, 4.305), (5.90, 4.305)], GOLD)
# 두 수정 경로는 왼쪽 외곽에서 합류해 입사일 판정으로 직접 돌아간다.
arrow(s, [(4.70, 4.78), (4.70, 5.10), (.55, 5.10), (.55, 3.50), (6.15, 3.50), (6.15, 2.67), (6.50, 2.67)], GOLD)
arrow(s, [(6.30, 5.915), (.55, 5.915), (.55, 5.10)], RED)
text(s, .94, 4.40, 2.20, .28, "수정 후 다시 판정", 14, GOLD, True)
text(s, .94, 5.51, 4.94, .27, "반려 후 재입력도 같은 판정으로", 13.5, RED)
text(s, .68, 6.46, 11.95, .56,
     "자동 승인 기간과 대기 중 수정 허용은 관리자 설정입니다. 수정 후 총관리자에게 알립니다.\n미래 입사일은 입력할 수 없습니다. 관리자 승인·반려 결과는 본인에게 메일로 안내합니다.", 12.5, MUTED)

# 11. 일일 자동 처리. 분기 대신 고정 순서를 크게 보여 준다.
s = new_slide("매일 00:10 자동 처리", "한국 시간 기준으로 다섯 작업을 정해진 순서대로 실행합니다.", "docs/09-스케줄러-설계.md\nbackend/src/main/java/com/mlsoft/backend/domain/leave/scheduler/LeaveScheduler.java")
steps = [
    ("① 기산일 리셋", "근속별 새 연차 부여\n당겨쓴 일수 상환"),
    ("② 월차 적립", "1년 미만 매월 1일\n입사일 + N개월"),
    ("③ 생일 반차", "보너스 0.5일 가산\n본인·총관리자 메일"),
    ("④ 소진 안내", "잔여 있는 사원에게\n설정 주기별 메일"),
    ("⑤ 퇴직자 파기", "자동 모드만 실행\n보존·예고 확인"),
]
xs = [.70, 3.17, 5.64, 8.11, 10.58]
for i, (title, body) in enumerate(steps):
    node(s, xs[i], 2.48, 2.05, 1.90, title, body, "blue" if i == 0 else "normal", 17, 14)
    if i < 4:
        arrow(s, [(xs[i] + 2.05, 3.43), (xs[i + 1], 3.43)], BLUE)
rule(s, .76, 5.04, 11.8, "CCD6E5")
text(s, .76, 5.36, 5.52, 1.05, "순서를 고정하는 이유\n잔액을 갱신한 뒤 생일 반차와 안내를 처리합니다.", 18, INK)
text(s, 6.84, 5.36, 5.67, 1.05, "사원 1명씩 독립 처리\n한 사원의 오류가 다른 사원에게 번지지 않습니다.", 18, INK)
footnote(s, "소진 안내는 같은 주기 중복 발송을 막습니다. 자동 파기 30일 예고·결과 메일은 총관리자에게 보냅니다.")

# 13. 퇴직 후 세 경로. 재입사를 파기 뒤에 잇지 않는다.
s = new_slide("퇴직 이후의 개인정보와 재입사", "퇴직 처리 후 복구·재입사·개인정보 파기를 구분해 관리합니다.", USER + ":434~648\nbackend/src/main/java/com/mlsoft/backend/domain/user/service/RetireePurgeService.java\nbackend/src/main/java/com/mlsoft/backend/domain/audit/service/AdminAuditService.java")
node(s, .72, 2.29, 2.23, 1.02, "퇴직 처리", "즉시 로그인 차단", "blue", 21, 16)
node(s, 3.77, 1.95, 3.39, .90, "퇴직 처리 복구", "잘못된 퇴직 처리 되돌리기", size=19, body_size=14)
node(s, 3.77, 3.45, 3.39, 1.05, "파기 전 재입사", "이전 근속 보존 · 새 입사일 적용", "green", 19, 13.5)
node(s, 3.77, 5.23, 3.39, 1.00, "보존 기간 후 파기", "수동 또는 자동 · 보류 가능", "gold", 19, 14)
arrow(s, [(2.95, 2.80), (3.38, 2.80), (3.38, 2.40), (3.77, 2.40)])
arrow(s, [(3.38, 2.80), (3.38, 3.98), (3.77, 3.98)], GREEN)
arrow(s, [(3.38, 3.98), (3.38, 5.73), (3.77, 5.73)], GOLD)
text(s, 7.83, 1.98, 4.74, .91, "로그인 가능 상태로 복구합니다.\n팀장직·이관된 결재는 자동 복구하지 않습니다.", 15.5, MUTED)
text(s, 7.83, 3.42, 4.74, 1.12, "연차는 0일에서 새로 시작합니다.\n과거 근속 구간은 별도로 남깁니다.\n파기된 계정은 신규 가입이 필요합니다.", 15.5, MUTED)
text(s, 7.83, 5.00, 4.79, 1.45, "기본 보존 3년 · 자동 파기는 30일 예고\n계정을 익명화하고 신청·메일 본문을 지웁니다.\n파기 처리 기록은 보존합니다.", 15.5, MUTED)
footnote(s, "본인 계정의 퇴직 처리는 막습니다. 파기된 개인정보는 퇴직 복구나 재입사로 되살릴 수 없습니다.")

# 18+19. 기술 스택을 좌우 두 개의 네이티브 표로 통합한다.
s = new_slide("기술 스택", "백엔드는 업무 규칙과 저장을, 프론트엔드는 화면과 서버 데이터 갱신을 담당합니다.", "backend/build.gradle\nfrontend/package.json\n" + STATUS)
text(s, .68, 1.96, 5.84, .38, "백엔드 · Java 21 / Spring Boot 4.1.0 / Gradle", 16, BLUE, True)
text(s, 6.86, 1.96, 5.78, .38, "프론트엔드 · React 19.2 / Vite 8 / Node.js", 16, BLUE, True)
table(s, ["기술·라이브러리", "용도"], [
    ["Spring Web MVC", "업무 REST API 제공"],
    ["Spring Data JPA / Hibernate", "업무 객체와 DB 연결"],
    ["Spring Security / OAuth2 Client", "Google 로그인·역할 제어"],
    ["jjwt 0.12.6", "JWT 발급·검증"],
    ["Spring Mail", "SMTP 이메일 발송"],
    ["Bean Validation", "입력 형식·요청 값 검증"],
    ["Lombok", "반복되는 Java 코드 축소"],
    ["mysql-connector-j / H2", "운영 DB 연결 / 테스트 DB"],
    ["JUnit 5 / Mockito", "업무 규칙·권한 자동 검증"],
], [3.06, 2.78], x=.68, y=2.52, row_h=.365, sizes=[11,12], header_h=.40)
table(s, ["기술·라이브러리", "용도"], [
    ["React 19.2 / react-dom", "화면·입력 상태 구성"],
    ["react-router-dom 7", "화면 이동·접근 제한"],
    ["@tanstack/react-query 5", "서버 데이터 캐시·갱신"],
    ["Axios", "요청·로그인 만료·오류 처리"],
    ["Tailwind CSS 4 / Vite 플러그인", "공통 CSS 디자인 값 적용"],
    ["Day.js", "날짜·회차 계산과 표시"],
    ["lucide-react / react-hot-toast", "아이콘·처리 결과 알림"],
    ["Vitest / Testing Library / jsdom", "화면·사용자 조작 자동 검증"],
    ["Oxlint", "기본 오류·규칙 위반 검사"],
], [3.15, 2.63], x=6.86, y=2.52, row_h=.365, sizes=[11,12], header_h=.40)
footnote(s, "Docker 단계별 빌드로 화면·서버를 통합하고, Docker Compose로 앱과 MySQL 8을 구성합니다.")

# 20+21. 구현 규모와 남은 운영 확인을 한 장에서 확인한다.
s = new_slide("구현 규모와 운영 전 확인", "구현 수치는 2026-09-11 코드 기준이며, 자동 테스트는 2026-09-11 실행 기준입니다.", "HTTP 매핑 88개 · 페이지 17개 · schema.sql 테이블 21개 · 활성 설정 12개\n" + STATUS)
for x, value, unit in [(.77,"88","업무 API"), (3.24,"17","화면"), (5.71,"21","DB 테이블"), (8.18,"12","관리자 설정"), (10.65,"829","자동 테스트")]:
    text(s, x, 1.98, 2.0, .74, value, 40, BLUE, True)
    text(s, x, 2.84, 2.0, .40, unit, 17)
rule(s, .77, 3.43, 11.80, "CCD6E5")
text(s, .77, 3.74, 5.52, .48, "구현된 업무", 23, BLUE, True)
text(s, .77, 4.39, 5.40, 1.88, "연차·복리후생 신청과 결재\n일정·조직·정책 관리\n매일 새벽 5개 자동 처리\n이메일 양식·발송·이력 관리\n퇴직자 파기와 재입사", 18)
text(s, 6.97, 3.74, 5.57, .48, "운영 전 확인", 23, GOLD, True)
text(s, 6.97, 4.39, 5.57, 1.99, "회사 Google 계정 로그인과\n실제 사원 데이터 확인\n실제 메일 수신·SMTP·공휴일 API 연동 확인\n운영 DB 스키마 반영과 배포 검증", 18)
footnote(s, "자동 테스트 기록: 백엔드 500건 + 프론트엔드 329건. 실제 운영 환경에서의 확인은 남아 있습니다.")


def shape_bounds(sh):
    return tuple(v / 914400 for v in (sh.left, sh.top, sh.left + sh.width, sh.top + sh.height))


def frame_fit_errors(frame, width, height):
    """폰트 실측 폭과 명시한 행간으로 잘림 가능성을 검사한다."""
    available_w = width - (frame.margin_left + frame.margin_right) / 914400
    available_h = height - (frame.margin_top + frame.margin_bottom) / 914400
    used_h = 0
    for p in frame.paragraphs:
        if not p.runs:
            continue
        size = max((r.font.size.pt if r.font.size else 18) for r in p.runs)
        length = sum(text_width(r.text, r.font.size.pt if r.font.size else size, bool(r.font.bold)) for r in p.runs)
        line_count = max(1, math.ceil((length - .001) / max(available_w, .001))) if frame.word_wrap else 1
        if not frame.word_wrap and length > available_w + .025:
            return "텍스트 폭 초과: " + p.text
        leading = p.line_spacing
        line_h = leading / 914400 if isinstance(leading, int) else (leading or 1.13) * size / 72
        used_h += line_count * line_h
        used_h += ((p.space_before or 0) + (p.space_after or 0)) / 914400
    if used_h > available_h + .035:
        return f"텍스트 높이 초과 {used_h:.3f}/{available_h:.3f}: {frame.text[:60]}"
    return None


def connector_crosses(sh, other):
    """직교 선분이 도형 내부를 관통하는지 판정한다. 끝점 접촉은 허용한다."""
    x1, y1, x2, y2 = shape_bounds(sh)
    left, top, right, bottom = shape_bounds(other)
    eps = .009
    is_diamond = other.shape_type == 1 and other.auto_shape_type == MSO_SHAPE.DIAMOND
    if is_diamond:
        cx, cy, rx, ry = (left+right)/2, (top+bottom)/2, (right-left)/2, (bottom-top)/2
        if abs(x2-x1) < eps:
            half = ry * max(0, 1 - abs(x1-cx)/rx)
            top, bottom = cy-half, cy+half
        else:
            half = rx * max(0, 1 - abs(y1-cy)/ry)
            left, right = cx-half, cx+half
    if abs(x2-x1) < eps:
        return left+eps < x1 < right-eps and min(y2,bottom)-max(y1,top) > eps
    if abs(y2-y1) < eps:
        return top+eps < y1 < bottom-eps and min(x2,right)-max(x1,left) > eps
    raise AssertionError("대각선 커넥터")


def validate_deck():
    """15장 구성·글꼴·FK·도형 경계·텍스트 적합·관통과 겹침을 함께 검사한다."""
    assert len(prs.slides) == 15, len(prs.slides)
    errors, minimum_font = [], 100.0
    tables = [i for i, sl in enumerate(prs.slides, 1) if any(sh.has_table for sh in sl.shapes)]
    assert tables == [3, 4, 14], tables
    for index, slide in enumerate(prs.slides, 1):
        objects, connectors = [], []
        for sh in slide.shapes:
            left, top, right, bottom = shape_bounds(sh)
            if min(left, top) < -.001 or right > WIDTH+.001 or bottom > HEIGHT+.001:
                errors.append((index, "페이지 경계 초과", sh.name))
            if sh.shape_type == 9:
                connectors.append(sh)
            else:
                objects.append(sh)
            frames = []
            if sh.has_text_frame:
                frames.append((sh.text_frame, sh.width/914400, sh.height/914400))
            if sh.has_table:
                frames.extend((cell.text_frame, sh.table.columns[j].width/914400, sh.table.rows[i].height/914400)
                              for i, row in enumerate(sh.table.rows) for j, cell in enumerate(row.cells))
            for frame, width, height in frames:
                issue = frame_fit_errors(frame, width, height)
                if issue:
                    errors.append((index, sh.name, issue))
                for pi, p in enumerate(frame.paragraphs):
                    for r in p.runs:
                        if r.text and r.font.size:
                            minimum_font = min(minimum_font, r.font.size.pt)
                            minimum = 10 if sh.name.startswith("ERD|") and pi > 0 else 11
                            if r.font.size.pt < minimum:
                                errors.append((index, "최소 글꼴 미달", r.text))
        for i, first in enumerate(objects):
            l1,t1,r1,b1 = shape_bounds(first)
            for second in objects[i+1:]:
                l2,t2,r2,b2 = shape_bounds(second)
                if min(r1,r2)-max(l1,l2) > .015 and min(b1,b2)-max(t1,t2) > .015:
                    errors.append((index, "도형 겹침", first.name, second.name))
        for line in connectors:
            for other in objects:
                if connector_crosses(line, other):
                    errors.append((index, "커넥터 관통", line.name, other.name))
    # 브리프의 기억값이 아니라 실제 스키마 FK 집합을 대조한다.
    schema = Path(__file__).resolve().parents[2] / "db" / "schema.sql"
    sql = schema.read_text(encoding="utf-8")
    foreign_keys = set()
    for name, body in re.findall(r"CREATE TABLE `([^`]+)` \((.*?)\) ENGINE", sql, re.S):
        foreign_keys.update((name, field, parent) for field, parent in re.findall(r"FOREIGN KEY \(`([^`]+)`\) REFERENCES `([^`]+)`", body))
    assert set(ERD_RELATIONS) == foreign_keys, {"누락": sorted(foreign_keys-set(ERD_RELATIONS)), "추가": sorted(set(ERD_RELATIONS)-foreign_keys)}
    assert len(ERD_ENTITIES) == 22 and len(foreign_keys) == 27
    for n in range(8, 12):
        slide = prs.slides[n-1]
        assert any(sh.shape_type == 1 and sh.auto_shape_type == MSO_SHAPE.DIAMOND for sh in slide.shapes), n
        assert any(sh.shape_type == 9 and sh._element.spPr.xpath("./a:ln/a:tailEnd") for sh in slide.shapes), n
    assert not errors, json.dumps(errors, ensure_ascii=False, indent=2)
    return {"장수": len(prs.slides), "최소글꼴pt": minimum_font,
            "네이티브표장": tables, "흐름도장": list(range(8, 12)), "ERD테이블수": 21,
            "스키마FK일치": len(foreign_keys), "논리자기참조": 1,
            "경계·겹침·관통·텍스트적합오류": len(errors)}


def export_png(pptx_path: Path, out_dir: Path):
    """열린 사용자 프레젠테이션을 건드리지 않고 이 파일만 내보낸다."""
    out_dir.mkdir(parents=True, exist_ok=True)
    ps_path = str(pptx_path.resolve()).replace("'", "''")
    ps_dir = str(out_dir.resolve()).replace("'", "''")
    command = f"""
$ErrorActionPreference = 'Stop'
if (Get-Process POWERPNT -ErrorAction SilentlyContinue) {{ exit 3 }}
$pptApp = New-Object -ComObject PowerPoint.Application
$presentation = $null
try {{
    $presentation = $pptApp.Presentations.Open('{ps_path}', $true, $false, $false)
    $presentation.Export('{ps_dir}', 'png', 1600, 900)
}} finally {{
    if ($null -ne $presentation) {{ $presentation.Close() }}
    if ($pptApp.Presentations.Count -eq 0) {{ $pptApp.Quit() }}
    [void][Runtime.InteropServices.Marshal]::ReleaseComObject($pptApp)
}}
"""
    result = subprocess.run(["powershell", "-NoProfile", "-NonInteractive", "-Command", command])
    if result.returncode == 3:
        return False
    result.check_returncode()
    return True


def main():
    default = Path(__file__).resolve().with_name("연차ON-프로젝트-소개-2026-09-11.pptx")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=default)
    parser.add_argument("--export-png", type=Path)
    args = parser.parse_args()
    result = validate_deck()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    prs.save(args.output)
    result["산출물"] = str(args.output.resolve())
    if args.export_png:
        if export_png(args.output, args.export_png):
            result["PNG검수폴더"] = str(args.export_png.resolve())
        else:
            result["PNG검수"] = "POWERPNT 실행 중: COM 미사용. PNG는 오케스트레이터가 확인"
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
